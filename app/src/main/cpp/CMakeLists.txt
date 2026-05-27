cmake_minimum_required(VERSION 3.10.2)
project("gamemonitor")

# 统一设置库文件的绝对父路径
set(LIB_DIR "${CMAKE_CURRENT_SOURCE_DIR}/ncnn_sdk/ncnn-20240410-android-vulkan/${ANDROID_ABI}/lib")

# 1. 完美引入头文件路径
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/ncnn_sdk/ncnn-20240410-android-vulkan/${ANDROID_ABI}/include)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/ncnn_sdk/ncnn-20240410-android-vulkan/${ANDROID_ABI}/include/ncnn)

# 2. 将 5 个静态库全部打包定义为标准的局部 IMPORTED 目标（这能从根本上避免路径截断）
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

find_package(OpenMP REQUIRED)
find_library(VULKAN_LIB vulkan)

# 3. 构建你的核心业务源码
add_library(native-lib SHARED
            native-lib.cpp
            ObjectDetector.cpp)

# 4. 传递给链接器的只有极简的目标别名，同时使用双重交错排列粉碎循环依赖锁
target_link_libraries(native-lib
                      ncnn_lib
                      spirv_lib
                      codegen_lib
                      machine_lib
                      glslang_lib
                      machine_lib
                      glslang_lib
                      OpenMP::OpenMP_CXX
                      log
                      jnigraphics
                      android
                      ${VULKAN_LIB})
