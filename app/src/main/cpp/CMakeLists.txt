cmake_minimum_required(VERSION 3.10.2)
project("gamemonitor")

# 【核心解药】强制 NDK 的 Clang 编译器将 OpenMP 的运行时库静态链接到我们的 So 中
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -static-openmp")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -static-openmp")

# 统一设置库文件的绝对父路径
set(LIB_DIR "${CMAKE_CURRENT_SOURCE_DIR}/ncnn_sdk/ncnn-20240410-android-vulkan/${ANDROID_ABI}/lib")

# 1. 引入头文件路径
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/ncnn_sdk/ncnn-20240410-android-vulkan/${ANDROID_ABI}/include)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/ncnn_sdk/ncnn-20240410-android-vulkan/${ANDROID_ABI}/include/ncnn)

# 2. 依次注册所有 6 个 NCNN 核心底座静态库目标
add_library(ncnn_lib STATIC IMPORTED)
set_target_properties(ncnn_lib PROPERTIES IMPORTED_LOCATION "${LIB_DIR}/libncnn.a")

add_library(glslang_lib STATIC IMPORTED)
set_target_properties(glslang_lib PROPERTIES IMPORTED_LOCATION "${LIB_DIR}/libglslang.a")

add_library(spirv_lib STATIC IMPORTED)
set_target_properties(spirv_lib PROPERTIES IMPORTED_LOCATION "${LIB_DIR}/libSPIRV.a")

add_library(machine_lib STATIC IMPORTED)
set_target_properties(machine_lib PROPERTIES IMPORTED_LOCATION "${LIB_DIR}/libMachineIndependent.a")

add_library(codegen_lib STATIC IMPORTED)
set_target_properties(codegen_lib PROPERTIES IMPORTED_LOCATION "${LIB_DIR}/libGenericCodeGen.a")

add_library(osdepend_lib STATIC IMPORTED)
set_target_properties(osdepend_lib PROPERTIES IMPORTED_LOCATION "${LIB_DIR}/libOSDependent.a")

find_package(OpenMP REQUIRED)
find_library(VULKAN_LIB vulkan)

# 3. 构建核心业务源码
add_library(native-lib SHARED
            native-lib.cpp
            ObjectDetector.cpp)

# 4. 完美闭环的链接顺序
target_link_libraries(native-lib
                      ncnn_lib
                      spirv_lib
                      glslang_lib
                      machine_lib
                      osdepend_lib
                      codegen_lib
                      glslang_lib
                      machine_lib
                      osdepend_lib
                      OpenMP::OpenMP_CXX
                      log
                      jnigraphics
                      android
                      ${VULKAN_LIB})
