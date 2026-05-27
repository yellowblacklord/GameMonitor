cmake_minimum_required(VERSION 3.10.2)
project("gamemonitor")

# 设置本地静态库的根目录
set(NCNN_SDK_DIR ${CMAKE_CURRENT_SOURCE_DIR}/ncnn_sdk)

# 1. 完美引入头文件
include_directories(${NCNN_SDK_DIR}/include)
include_directories(${NCNN_SDK_DIR}/include/ncnn)

# 2. 注册你本地 Termux 已经下载好的满血版 libncnn.a
add_library(ncnn STATIC IMPORTED)
set_target_properties(ncnn PROPERTIES IMPORTED_LOCATION ${NCNN_SDK_DIR}/libs/${ANDROID_ABI}/libncnn.a)

find_package(OpenMP REQUIRED)
find_library(VULKAN_LIB vulkan)

# 3. 编译你的业务源码
add_library(native-lib SHARED
            native-lib.cpp
            ObjectDetector.cpp)

# 4. 极致精简链接：直接依靠 NDK 自带的 vulkan 库补齐所有 glslang 锁符号，绝不冗余
target_link_libraries(native-lib
                      ncnn
                      OpenMP::OpenMP_CXX
                      log
                      jnigraphics
                      android
                      ${VULKAN_LIB})
