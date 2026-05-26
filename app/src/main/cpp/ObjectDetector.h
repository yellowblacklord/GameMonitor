#ifndef OBJECT_DETECTOR_H
#define OBJECT_DETECTOR_H

#include "ncnn/net.h"
#include "ndk_struct.h"
#include <vector>
#include <mutex>

class ObjectDetector {
public:
    ObjectDetector();
    ~ObjectDetector();
    
    bool loadModel(const char* param320, const char* bin320, const char* param416, const char* bin416);
    std::vector<ObjectBox> detectFrame(const unsigned char* rgba_buf, int width, int height);

private:
    ncnn::Net net320;
    ncnn::Net net416;
    bool is_initialized;
    std::mutex lck;

    void generateBboxes(const ncnn::Mat& out, std::vector<ObjectBox>& proposals, float score_threshold, int model_size);
    void nmsSortedBboxes(const std::vector<ObjectBox>& faceobjects, std::vector<int>& picked, float nms_threshold);
};

#endif // OBJECT_DETECTOR_H
