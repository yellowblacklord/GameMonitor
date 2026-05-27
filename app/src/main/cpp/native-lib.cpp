#include <jni.h>
#include <android/bitmap.h>
#include "ObjectDetector.h"

static ObjectDetector* detector = nullptr;

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_monitor_game_Engine_initDetector(JNIEnv* env, jobject thiz, 
                                         jstring param320, jstring bin320, 
                                         jstring param416, jstring bin416) {
    if (detector == nullptr) {
        detector = new ObjectDetector();
    }
    const char* p320 = env->GetStringUTFChars(param320, 0);
    const char* b320 = env->GetStringUTFChars(bin320, 0);
    const char* p416 = env->GetStringUTFChars(param416, 0);
    const char* b416 = env->GetStringUTFChars(bin416, 0);

    bool success = detector->loadModel(p320, b320, p416, b416);

    env->ReleaseStringUTFChars(param320, p320);
    env->ReleaseStringUTFChars(bin320, b320);
    env->ReleaseStringUTFChars(param416, p416);
    env->ReleaseStringUTFChars(bin416, b416);
    return success;
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_com_monitor_game_Engine_detectScreen(JNIEnv* env, jobject thiz, jobject bitmap) {
    if (detector == nullptr) return nullptr;

    AndroidBitmapInfo info;
    void* pixels = nullptr;
    
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0 || AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        return nullptr;
    }

    std::vector<ObjectBox> results = detector->detectFrame((unsigned char*)pixels, info.width, info.height);
    
    AndroidBitmap_unlockPixels(env, bitmap);

    int num_objects = results.size();
    int step = 6; 
    jfloatArray result_array = env->NewFloatArray(1 + num_objects * step);
    
    float* buffer = new float[1 + num_objects * step];
    buffer[0] = (float)num_objects;
    
    for (int i = 0; i < num_objects; i++) {
        buffer[1 + i * step + 0] = results[i].x1;
        buffer[1 + i * step + 1] = results[i].y1;
        buffer[1 + i * step + 2] = results[i].x2;
        buffer[1 + i * step + 3] = results[i].y2;
        buffer[1 + i * step + 4] = (float)results[i].label;
        buffer[1 + i * step + 5] = results[i].score;
    }

    env->SetFloatArrayRegion(result_array, 0, 1 + num_objects * step, buffer);
    delete[] buffer;
    
    return result_array;
}
