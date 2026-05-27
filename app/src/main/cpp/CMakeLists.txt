cmake_minimum_required(VERSION 3.10.2)
project("gamemonitor")

set(NCNN_SDK_DIR ${CMAKE_CURRENT_SOURCE_DIR}/ncnn_sdk)
include_directories(${NCNN_SDK_DIR}/include)

add_library(ncnn STATIC IMPORTED)
set_target_properties(ncnn PROPERTIES IMPORTED_LOCATION ${NCNN_SDK_DIR}/libs/${ANDROID_ABI}/libncnn.a)

find_package(OpenMP REQUIRED)

# 让 NDK 自动去系统寻找标准 vulkan 库路径
find_library(VULKAN_LIB vulkan)

add_library(native-lib SHARED
            native-lib.cpp
            ObjectDetector.cpp)

target_link_libraries(native-lib
                      ncnn
                      OpenMP::OpenMP_CXX
                      log
                      jnigraphics
                      android
                      ${VULKAN_LIB}) # 使用绝对路径链接，杜绝找不到的情况
