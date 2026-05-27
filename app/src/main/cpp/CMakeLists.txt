cmake_minimum_required(VERSION 3.10.2)
project("gamemonitor")

set(NCNN_SDK_DIR ${CMAKE_CURRENT_SOURCE_DIR}/ncnn_sdk)
include_directories(${NCNN_SDK_DIR}/include)

# 注册 5 个静态库的绝对路径
set(LIB_NCNN "${NCNN_SDK_DIR}/libs/${ANDROID_ABI}/libncnn.a")
set(LIB_GLSLANG "${NCNN_SDK_DIR}/libs/${ANDROID_ABI}/libglslang.a")
set(LIB_SPIRV "${NCNN_SDK_DIR}/libs/${ANDROID_ABI}/libSPIRV.a")
set(LIB_MACHINE "${NCNN_SDK_DIR}/libs/${ANDROID_ABI}/libMachineIndependent.a")
set(LIB_CODEGEN "${NCNN_SDK_DIR}/libs/${ANDROID_ABI}/libGenericCodeGen.a")

find_package(OpenMP REQUIRED)
find_library(VULKAN_LIB vulkan)

add_library(native-lib SHARED
            native-lib.cpp
            ObjectDetector.cpp)

# 核心大招：使用 -Wl,--start-group 和 -Wl,--end-group 强行包裹所有 NCNN 静态组件
# 这样链接器就会在这些库之间不断回头扫描，直到彻底解开 InitGlobalLock() 等一切相互依赖
target_link_libraries(native-lib
                      -Wl,--start-group
                      ${LIB_NCNN}
                      ${LIB_GLSLANG}
                      ${LIB_SPIRV}
                      ${LIB_MACHINE}
                      ${LIB_CODEGEN}
                      -Wl,--end-group
                      OpenMP::OpenMP_CXX
                      log
                      jnigraphics
                      android
                      ${VULKAN_LIB})
