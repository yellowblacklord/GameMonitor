cmake_minimum_required(VERSION 3.10.2)
project("gamemonitor")

# 1. 直接将 NCNN 的 cmake 查找路径指向解压出来的目录下
set(ncnn_DIR ${CMAKE_CURRENT_SOURCE_DIR}/ncnn_sdk/ncnn-20240410-android-vulkan/${ANDROID_ABI}/lib/cmake/ncnn)
find_package(ncnn REQUIRED)

find_package(OpenMP REQUIRED)
find_library(VULKAN_LIB vulkan)

# 2. 引入头文件路径
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/ncnn_sdk/ncnn-20240410-android-vulkan/include/ncnn)

add_library(native-lib SHARED
            native-lib.cpp
            ObjectDetector.cpp)

# 3. 链接目标：直接写 ncnn 即可，官方脚本会自动展开并补齐所有 glslang 符号和依赖
target_link_libraries(native-lib
                      ncnn
                      OpenMP::OpenMP_CXX
                      log
                      jnigraphics
                      android
                      ${VULKAN_LIB})
