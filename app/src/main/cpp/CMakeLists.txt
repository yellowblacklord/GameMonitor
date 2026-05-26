cmake_minimum_required(VERSION 3.10.2)
project(game_monitor)

# 1. 设定本地依赖文件夹基准线
set(NCNN_SDK_DIR ${CMAKE_CURRENT_SOURCE_DIR}/ncnn_sdk)

# 2. 强行引入头文件和当前目录搜索路径
include_directories(${NCNN_SDK_DIR}/include)
include_directories(${CMAKE_CURRENT_SOURCE_DIR})

# 3. 手动硬编码链接我们刚才从系统捞过来的 log 库
add_library(log_lib SHARED IMPORTED)
set_target_properties(log_lib PROPERTIES
    IMPORTED_LOCATION ${NCNN_SDK_DIR}/libs/system_libs/liblog.so
)

# 4. 手动硬编码链接我们刚才从系统捞过来的 jnigraphics 图形库
add_library(jni_graphics SHARED IMPORTED)
set_target_properties(jni_graphics PROPERTIES
    IMPORTED_LOCATION ${NCNN_SDK_DIR}/libs/system_libs/libjnigraphics.so
)

# 5. 显式导入本地的 NCNN 静态库
add_library(ncnn STATIC IMPORTED)
set_target_properties(ncnn PROPERTIES
    IMPORTED_LOCATION ${NCNN_SDK_DIR}/libs/arm64-v8a/libncnn.a
)

# 6. 配置需要生成的最终核心目标
add_library(native-lib SHARED
    native-lib.cpp
    ObjectDetector.cpp
)

# 7. 强行捆绑链接在一起
target_link_libraries(native-lib
    ncnn
    log_lib
    jni_graphics
)
