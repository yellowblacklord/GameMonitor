package com.monitor.game;

import android.graphics.Bitmap;

public class Engine {
    // 关键：必须在类加载时，第一时间强行把 C++ 库灌入内存
    static {
        System.loadLibrary("native-lib");
    }

    // 底层 C++ 协同接口定义
    public native boolean initDetector(String param320, String bin320, String param416, String bin416);
    public native float[] detectScreen(Bitmap bitmap);
}
