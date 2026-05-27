cmake_minimum_required(VERSION 3.10.2)
project("gamemonitor")

# 统一设置库文件的绝对父路径
set(LIB_DIR "${CMAKE_CURRENT_SOURCE_DIR}/ncnn_sdk/ncnn-20240410-android-vulkan/${ANDROID_ABI}/lib")

# 1. 完美引入头文件路径
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

# 核心解药：引入被遗漏的操作系统平台底层锁依赖
add_library(osdepend_lib STATIC IMPORTED)
set_target_properties(osdepend_lib PROPERTIES IMPORTED_LOCATION "${LIB_DIR}/libOSDependent.a")

find_package(OpenMP REQUIRED)
find_library(VULKAN_LIB vulkan)

# 3. 构建核心业务源码
add_library(native-lib SHARED
            native-lib.cpp
            ObjectDetector.cpp)

# 4. 完美闭环的黄金链接顺序：
# ncnn -> glslang -> MachineIndependent -> OSDependent -> GenericCodeGen
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
