#include "ObjectDetector.h"
#include <android/log.h>
#include <algorithm>

#define LOG_TAG "GameDetector"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

ObjectDetector::ObjectDetector() : is_initialized(false) {}
ObjectDetector::~ {
    net320.clear();
    net416.clear();
}

bool ObjectDetector::loadModel(const char* param320, const char* bin320, const char* param416, const char* bin416) {
    std::lock_guard<std::mutex> lock(lck);
    
    net320.opt.num_threads = 4;
    net416.opt.num_threads = 4;
    
    // 安全降级：直接默认用纯 CPU 线程以求稳定，防止部分手机 Vulkan 驱动闪退
    net320.opt.use_vulkan_compute = false;
    net416.opt.use_vulkan_compute = false;

    if (net320.load_param(param320) != 0 || net320.load_model(bin320) != 0) {
        LOGD("❌ 加载 320 模型组件失败！路径: %s", param320);
        return false;
    }
    if (net416.load_param(param416) != 0 || net416.load_model(bin416) != 0) {
        LOGD("❌ 加载 416 模型组件失败！路径: %s", param416);
        return false;
    }
    is_initialized = true;
    LOGD("🔥 [C++喜报] 双模型加载成功！输出节点已修正为 out0，协同引擎已就绪。");
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
    if (!is_initialized || rgba_buf == nullptr) return final_results;

    // 1. 运行 320 基础模型
    ncnn::Mat in320 = ncnn::Mat::from_pixels_resize(rgba_buf, ncnn::Mat::PIXEL_RGBA2RGB, width, height, 320, 320);
    const float norm_vals[3] = {1/255.f, 1/255.f, 1/255.f};
    in320.substract_mean_normalize(0, norm_vals);

    ncnn::Extractor ex320 = net320.create_extractor();
    ex320.input("in0", in320);
    
    ncnn::Mat out320;
    // 【重大Debug修正】：根据项目审计报告，YOLOv8 导出的标准节点名称为 "out0" 而不是 "413"
    ex320.extract("out0", out320); 

    std::vector<ObjectBox> proposals320;
    generateBboxes(out320, proposals320, 0.45f, 320);

    std::sort(proposals320.begin(), proposals320.end(), [](const ObjectBox& a, const ObjectBox& b) { return a.score > b.score; });
    std::vector<int> picked320;
    nmsSortedBboxes(proposals320, picked320, 0.45f);

    // 2. 双模型串联协同核心逻辑
    for (int idx : picked320) {
        ObjectBox box = proposals320[idx];
        
        // 缩放回手机屏幕的真实绝对坐标
        float real_x1 = (box.x1 / 320.f) * width;
        float real_y1 = (box.y1 / 320.f) * height;
        float real_x2 = (box.x2 / 320.f) * width;
        float real_y2 = (box.y2 / 320.f) * height;
        
        // 边界安全裁剪，严防越界
        real_x1 = std::max(0.f, std::min(real_x1, (float)width));
        real_y1 = std::max(0.f, std::min(real_y1, (float)height));
        real_x2 = std::max(0.f, std::min(real_x2, (float)width));
        real_y2 = std::max(0.f, std::min(real_y2, (float)height));

        box.x1 = real_x1; box.y1 = real_y1; box.x2 = real_x2; box.y2 = real_y2;
        final_results.push_back(box);

        // 如果识别到了活着的"敌人(0)"或"人机(3)"，对其上半身进行二次切片识别
        if (box.label == 0 || box.label == 3) {
            int roi_w = real_x2 - real_x1;
            int roi_h = (real_y2 - real_y1) * 0.45f; // 精确锁定身体上半部分及头部预估范围
            
            int rx = (int)real_x1;
            int ry = (int)real_y1;
            if (rx + roi_w > width) roi_w = width - rx;
            if (ry + roi_h > height) roi_h = height - ry;

            if (roi_w <= 10 || roi_h <= 10) continue;

            // 【安全切图方案】：放弃危险的裸指针偏移，直接由 ncnn 从整张原图 RGBA 中安全裁剪出 ROI 区域
            ncnn::Mat in_full = ncnn::Mat::from_pixels(rgba_buf, ncnn::Mat::PIXEL_RGBA2RGB, width, height);
            ncnn::Mat in416;
            ncnn::copy_cut_border(in_full, in416, ry, height - (ry + roi_h), rx, width - (rx + roi_w));
            
            // 将切出来的局部区域缩放到 416x416
            ncnn::Mat in416_resized;
            ncnn::resize_bilinear(in416, in416_resized, 416, 416);
            in416_resized.substract_mean_normalize(0, norm_vals);

            ncnn::Extractor ex416 = net416.create_extractor();
            ex416.input("in0", in416_resized);
            ncnn::Mat out416;
            // 【重大Debug修正】：416 模型输出节点同步修正为 "out0"
            ex416.extract("out0", out416); 

            std::vector<ObjectBox> proposals416;
            generateBboxes(out416, proposals416, 0.50f, 416);

            for (auto& head_box : proposals416) {
                if (head_box.label == 1 || head_box.label == 4 || head_box.label == 6) {
                    ObjectBox real_target;
                    // 将局部绝对坐标映射回全屏幕绝对坐标系统
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
    // 对应标准扁平化 YOLOv8/v5 导出格式解析
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
