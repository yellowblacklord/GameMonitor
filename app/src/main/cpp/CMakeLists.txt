cmake_minimum_required(VERSION 3.22.1)

project("gamemonitor_native")

# 1. 强行激活 Android 的 OpenMP (多线程多核加速) 编译支持
find_package(OpenMP REQUIRED)
if(OpenMP_CXX_FOUND)
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} ${OpenMP_C_FLAGS}")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${OpenMP_CXX_FLAGS}")
    set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} ${OpenMP_SHARED_LINKER_FLAGS}")
endif()

# 2. 定义 ncnn 基础路径
set(NCNN_SDK_DIR ${CMAKE_CURRENT_SOURCE_DIR}/ncnn_sdk)

# 3. 导入项目的头文件包含路径
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${NCNN_SDK_DIR}/include
    ${NCNN_SDK_DIR}/include/ncnn
)

# 4. 以静态库形式声明预编译的 libncnn
add_library(ncnn STATIC IMPORTED)
set_target_properties(ncnn PROPERTIES IMPORTED_LOCATION ${NCNN_SDK_DIR}/libs/arm64-v8a/libncnn.a)

# 5. 编译你自己的本地业务源码
add_library(native-lib SHARED
            native-lib.cpp
            ObjectDetector.cpp)

# 6. 核心：将所有依赖项一股脑按顺序死死绑定在一起
target_link_libraries(native-lib
                      ncnn                      # 你的核心神经网络库
                      OpenMP::OpenMP_CXX        # 拯救 __kmpc_fork_call 报错
                      log                       # 拯救 liblog.so 报错
                      jnigraphics               # 拯救 AndroidBitmap 报错
                      atomic                    # 原子操作支持
                      android)                  # 系统基础支持
