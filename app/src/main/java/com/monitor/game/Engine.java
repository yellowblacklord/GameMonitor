package com.monitor.game;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class Engine {
    static {
        // 只加载主动态库，OpenMP 已经静态打包进去了
        System.loadLibrary("native-lib");
    }

    /**
     * 核心辅助函数：将 assets 内部压缩的模型文件物理释放到 App 的私有沙盒缓存目录（Cache）中
     * 从而生成一个支持 C++ fopen/load_param 读取的真实绝对路径。
     */
    public static String getRealFilePath(Context context, String assetName) {
        File file = new File(context.getCacheDir(), assetName);
        
        // 如果文件已经在缓存区存在且大小不为 0，说明之前已经释放过，直接返回绝对路径，避免重复拷贝
        if (file.exists() && file.length() > 0) {
            return file.getAbsolutePath();
        }
        
        try {
            InputStream is = context.getAssets().open(assetName);
            FileOutputStream os = new FileOutputStream(file);
            byte[] buffer = new byte[4096];
            int byteCount;
            while ((byteCount = is.read(buffer)) != -1) {
                os.write(buffer, 0, byteCount);
            }
            os.flush();
            is.close();
            os.close();
            return file.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 供上层 MainActivity 调用的满血版初始化方法
     */
    public static boolean startEngine(Context context) {
        // 1. 获取 assets 目录中 4 个模型文件在手机里的实际绝对路径
        // 【注意】：请务必核对这四个字符串是否与你在 MT 管理器 assets 文件夹里看到的文件名（大小写及后缀）完全一致！
        String p320 = getRealFilePath(context, "yolov5s_320.param"); 
        String b320 = getRealFilePath(context, "yolov5s_320.bin");
        String p416 = getRealFilePath(context, "yolov5s_416.param");
        String b416 = getRealFilePath(context, "yolov5s_416.bin");

        // 2. 将得到的真实路径（例如 /data/user/0/com.monitor.game/cache/yolov5s_320.param）传递给 C++
        boolean success = initDetector(p320, b320, p416, b416);
        
        android.util.Log.d("GameDetector", "C++ 双模型引擎最终加载结果: " + success);
        return success;
    }

    // 对应你 C++ 层的 native 方法声明
    public static native boolean initDetector(String param320, String bin320, String param416, String bin416);
    public static native float[] detectScreen(android.graphics.Bitmap bitmap);
}
