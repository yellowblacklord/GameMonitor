cmake_minimum_required(VERSION 3.10.2)
project("gamemonitor")

set(NCNN_SDK_DIR ${CMAKE_CURRENT_SOURCE_DIR}/ncnn_sdk)

# 让路径只到 include 层，这样代码里写 "ncnn/net.h" 就能完美匹配了
include_directories(${NCNN_SDK_DIR}/include)

add_library(ncnn STATIC IMPORTED)
set_target_properties(ncnn PROPERTIES IMPORTED_LOCATION ${NCNN_SDK_DIR}/libs/${ANDROID_ABI}/libncnn.a)

find_package(OpenMP REQUIRED)

add_library(native-lib SHARED
            native-lib.cpp
            ObjectDetector.cpp)

target_link_libraries(native-lib
                      ncnn
                      OpenMP::OpenMP_CXX
                      log
                      jnigraphics
                      android
                      vulkan)
