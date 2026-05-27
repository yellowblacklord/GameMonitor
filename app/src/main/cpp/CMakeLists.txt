cmake_minimum_required(VERSION 3.10.2)
project("gamemonitor")

# 统一指向解压后的架构库包根目录
set(NCNN_DIR ${CMAKE_CURRENT_SOURCE_DIR}/ncnn_sdk/ncnn-20240410-android-vulkan/${ANDROID_ABI})

# 1. 精确配置头文件路径
include_directories(${NCNN_DIR}/include)
include_directories(${NCNN_DIR}/include/ncnn)

# 2. 依次注册所有相互依赖的静态库绝对路径
set(LIB_NCNN "${NCNN_DIR}/lib/libncnn.a")
set(LIB_GLSLANG "${NCNN_DIR}/lib/libglslang.a")
set(LIB_SPIRV "${NCNN_DIR}/lib/libSPIRV.a")
set(LIB_MACHINE "${NCNN_DIR}/lib/libMachineIndependent.a")
set(LIB_CODEGEN "${NCNN_DIR}/lib/libGenericCodeGen.a")

find_package(OpenMP REQUIRED)
find_library(VULKAN_LIB vulkan)

# 3. 编译核心源码
add_library(native-lib SHARED
            native-lib.cpp
            ObjectDetector.cpp)

# 4. 使用终极链接组顺序：
# 按照依赖金字塔递增：ncnn 依赖 glslang，glslang 依赖 MachineIndependent 
# 结尾多放一个 libglslang.a 和 libMachineIndependent.a 兜底，强制粉碎循环符号死锁
target_link_libraries(native-lib
                      ${LIB_NCNN}
                      ${LIB_SPIRV}
                      ${LIB_CODEGEN}
                      ${LIB_MACHINE}
                      ${LIB_GLSLANG}
                      ${LIB_MACHINE}
                      ${LIB_GLSLANG}
                      OpenMP::OpenMP_CXX
                      log
                      jnigraphics
                      android
                      ${VULKAN_LIB})
