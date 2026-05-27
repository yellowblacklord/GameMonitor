cmake_minimum_required(VERSION 3.10.2)
project("gamemonitor")

set(NCNN_SDK_DIR ${CMAKE_CURRENT_SOURCE_DIR}/ncnn_sdk)
include_directories(${NCNN_SDK_DIR}/include)

# 注册 5 个静态库
add_library(ncnn STATIC IMPORTED)
set_target_properties(ncnn PROPERTIES IMPORTED_LOCATION ${NCNN_SDK_DIR}/libs/${ANDROID_ABI}/libncnn.a)

add_library(glslang STATIC IMPORTED)
set_target_properties(glslang PROPERTIES IMPORTED_LOCATION ${NCNN_SDK_DIR}/libs/${ANDROID_ABI}/libglslang.a)

add_library(SPIRV STATIC IMPORTED)
set_target_properties(SPIRV PROPERTIES IMPORTED_LOCATION ${NCNN_SDK_DIR}/libs/${ANDROID_ABI}/libSPIRV.a)

add_library(MachineIndependent STATIC IMPORTED)
set_target_properties(MachineIndependent PROPERTIES IMPORTED_LOCATION ${NCNN_SDK_DIR}/libs/${ANDROID_ABI}/libMachineIndependent.a)

add_library(GenericCodeGen STATIC IMPORTED)
set_target_properties(GenericCodeGen PROPERTIES IMPORTED_LOCATION ${NCNN_SDK_DIR}/libs/${ANDROID_ABI}/libGenericCodeGen.a)

find_package(OpenMP REQUIRED)
find_library(VULKAN_LIB vulkan)

add_library(native-lib SHARED
            native-lib.cpp
            ObjectDetector.cpp)

# 调整后的金字塔链接顺序：被依赖越深的库，越往右边放。
# 末尾追加 MachineIndependent 和 glslang 再次扫描，彻底解决 InitGlobalLock 找不到的问题
target_link_libraries(native-lib
                      ncnn
                      SPIRV
                      GenericCodeGen
                      MachineIndependent
                      glslang
                      MachineIndependent
                      glslang
                      OpenMP::OpenMP_CXX
                      log
                      jnigraphics
                      android
                      ${VULKAN_LIB})
