#include "ObjectDetector.h"
#include <android/log.h>
#include <algorithm>

#define LOG_TAG "GameDetector"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

ObjectDetector::ObjectDetector() : is_initialized(false) {}
ObjectDetector::~ObjectDetector() {
    net320.clear();
    net416.clear();
}

bool ObjectDetector::loadModel(const char* param320, const char* bin320, const char* param416, const char* bin416) {
    std::lock_guard<std::mutex> lock(lck);
    
    // 统一配置多线程（CPU 模式下使用）
    net320.opt.num_threads = 4;
    net416.opt.num_threads = 4;
    
    // 【第一轮尝试】：优先开启高端的 GPU Vulkan 加速
    LOGD("=== 尝试第一轮初始化：启用 Vulkan GPU 加速 ===");
    net320.opt.use_vulkan_compute = true;
    net416.opt.use_vulkan_compute = true;

    int r1 = net320.load_param(param320);
    int r2 = net320.load_model(bin320);
    int r3 = net416.load_param(param416);
    int r4 = net416.load_model(bin416);

    if (r1 == 0 && r2 == 0 && r3 == 0 && r4 == 0) {
        is_initialized = true;
        LOGD("🔥 [极速喜报] 双模型 Vulkan GPU 加速初始化成功！协同引擎已全面就绪。");
        return true;
    }

    // 【第二轮尝试】：如果上面失败了，说明 Vulkan 冲突，强制关闭 GPU 降级为全 CPU 兼容模式
    LOGD("⚠️ 第一轮 GPU 初始化失败(错误码:%d,%d,%d,%d)，正在启动安全气囊：降级为纯 CPU 模式重新加载...", r1, r2, r3, r4);
    net320.clear();
    net416.clear();

    net320.opt.use_vulkan_compute = false;
    net416.opt.use_vulkan_compute = false;

    if (net320.load_param(param320) != 0 || net320.load_model(bin320) != 0) {
        LOGD("❌ [终极失败] 纯 CPU 模式加载 320 模型依然失败，请检查模型文件本身是否损坏！");
        return false;
    }
    if (net416.load_param(param416) != 0 || net416.load_model(bin416) != 0) {
        LOGD("❌ [终极失败] 纯 CPU 模式加载 416 模型依然失败，请检查模型文件本身是否损坏！");
        return false;
    }

    is_initialized = true;
    LOGD("🚀 [兼容成功] 双模型纯 CPU 多线程协同引擎初始化成功！已成功绕过 Vulkan 限制。");
    return true;
}

void ObjectDetector::nmsSortedBboxes(const std::vector<ObjectBox>& faceobjects, std::vector<int>& picked, float nms_threshold) {
    picked.clear();
    const int n = faceobjects.size();
    std::vector<float> areas(n);
    for (int i = 0; i < n; i++) {
        areas[i] = (faceobjects[i].x2 - faceobjects[i].x1) * (faceobjects[i].y2 - faceobjects[i].y1);
    }
    for (int i = 0; i < n; i++) {
        const ObjectBox& a = faceobjects[i];
        int keep = 1;
        for (int j = 0; j < (int)picked.size(); j++) {
            const ObjectBox& b = faceobjects[picked[j]];
            float inter_x1 = std::max(a.x1, b.x1);
            float inter_y1 = std::max(a.y1, b.y1);
            float inter_x2 = std::min(a.x2, b.x2);
            float inter_y2 = std::min(a.y2, b.y2);
            float inter_w = std::max(0.f, inter_x2 - inter_x1);
            float inter_h = std::max(0.f, inter_y2 - inter_y1);
            float inter_area = inter_w * inter_h;
            float union_area = areas[i] + areas[picked[j]] - inter_area;
            if (inter_area / union_area > nms_threshold)
                keep = 0;
        }
        if (keep) picked.push_back(i);
    }
}

std::vector<ObjectBox> ObjectDetector::detectFrame(const unsigned char* rgba_buf, int width, int height) {
    std::vector<ObjectBox> final_results;
    if (!is_initialized) return final_results;

    ncnn::Mat in320 = ncnn::Mat::from_pixels_resize(rgba_buf, ncnn::Mat::PIXEL_RGBA2RGB, width, height, 320, 320);
    const float norm_vals[3] = {1/255.f, 1/255.f, 1/255.f};
    in320.substract_mean_normalize(0, norm_vals);

    ncnn::Extractor ex320 = net320.create_extractor();
    ex320.input("in0", in320);
    
    ncnn::Mat out320;
    ex320.extract("413", out320); 

    std::vector<ObjectBox> proposals320;
    generateBboxes(out320, proposals320, 0.45f, 320);

    std::sort(proposals320.begin(), proposals320.end(), [](const ObjectBox& a, const ObjectBox& b) { return a.score > b.score; });
    std::vector<int> picked320;
    nmsSortedBboxes(proposals320, picked320, 0.45f);

    for (int idx : picked320) {
        ObjectBox box = proposals320[idx];
        
        float real_x1 = (box.x1 / 320.f) * width;
        float real_y1 = (box.y1 / 320.f) * height;
        float real_x2 = (box.x2 / 320.f) * width;
        float real_y2 = (box.y2 / 320.f) * height;
        
        box.x1 = real_x1; box.y1 = real_y1; box.x2 = real_x2; box.y2 = real_y2;
        final_results.push_back(box);

        if (box.label == 0 || box.label == 3) {
            int roi_w = real_x2 - real_x1;
            int roi_h = (real_y2 - real_y1) * 0.45f;
            
            int rx = std::max(0, (int)real_x1);
            int ry = std::max(0, (int)real_y1);
            if (rx + roi_w > width) roi_w = width - rx;
            if (ry + roi_h > height) roi_h = height - ry;

            if (roi_w <= 0 || roi_h <= 0) continue;

            ncnn::Mat in416 = ncnn::Mat::from_pixels_resize(rgba_buf + (ry * width + rx) * 4, ncnn::Mat::PIXEL_RGBA2RGB, roi_w, roi_h, 416, 416);
            in416.substract_mean_normalize(0, norm_vals);

            ncnn::Extractor ex416 = net416.create_extractor();
            ex416.input("in0", in416);
            ncnn::Mat out416;
            ex416.extract("413", out416);

            std::vector<ObjectBox> proposals416;
            generateBboxes(out416, proposals416, 0.50f, 416);

            for (auto& head_box : proposals416) {
                if (head_box.label == 1 || head_box.label == 4 || head_box.label == 6) {
                    ObjectBox real_target;
                    real_target.x1 = rx + (head_box.x1 / 416.f) * roi_w;
                    real_target.y1 = ry + (head_box.y1 / 416.f) * roi_h;
                    real_target.x2 = rx + (head_box.x2 / 416.f) * roi_w;
                    real_target.y2 = ry + (head_box.y2 / 416.f) * roi_h;
                    real_target.label = head_box.label;
                    real_target.score = head_box.score;
                    final_results.push_back(real_target);
                }
            }
        }
    }
    return final_results;
}

void ObjectDetector::generateBboxes(const ncnn::Mat& out, std::vector<ObjectBox>& proposals, float score_threshold, int model_size) {
    for (int i = 0; i < out.h; i++) {
        const float* values = out.row(i);
        float score = values[4]; 
        if (score > score_threshold) {
            int best_label = 0;
            float max_class_score = -1.f;
            for (int j = 5; j < out.w; j++) {
                if (values[j] > max_class_score) {
                    max_class_score = values[j];
                    best_label = j - 5;
                }
            }
            float final_score = score * max_class_score;
            if (final_score > score_threshold) {
                ObjectBox box;
                float cx = values[0];
                float cy = values[1];
                float bw = values[2];
                float bh = values[3];
                
                box.x1 = cx - bw * 0.5f;
                box.y1 = cy - bh * 0.5f;
                box.x2 = cx + bw * 0.5f;
                box.y2 = cy + bh * 0.5f;
                box.score = final_score;
                box.label = best_label;
                proposals.push_back(box);
            }
        }
    }
}
