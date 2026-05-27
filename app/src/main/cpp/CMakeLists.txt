cmake_minimum_required(VERSION 3.10.2)
project("gamemonitor")

# 设置 SDK 根目录快捷变量
set(NCNN_SDK_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/ncnn_sdk/ncnn-20240410-android-vulkan)

# 1. 让 CMake 自动加载官方配套的 cmake 链接规则（精准匹配 arm64-v8a 架构）
set(ncnn_DIR ${NCNN_SDK_ROOT}/${ANDROID_ABI}/lib/cmake/ncnn)
find_package(ncnn REQUIRED)

find_package(OpenMP REQUIRED)
find_library(VULKAN_LIB vulkan)

# 2. 修正头文件包含路径：退回 include 根层级
# 这样代码里的 #include "ncnn/net.h" 就能通过 include/ncnn/net.h 被完美找到！
include_directories(${NCNN_SDK_ROOT}/arm64-v8a/include)

add_library(native-lib SHARED
            native-lib.cpp
            ObjectDetector.cpp)

# 3. 链接官方全家桶
target_link_libraries(native-lib
                      ncnn
                      OpenMP::OpenMP_CXX
                      log
                      jnigraphics
                      android
                      ${VULKAN_LIB})
