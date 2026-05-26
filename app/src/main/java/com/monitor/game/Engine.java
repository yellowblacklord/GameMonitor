package com.monitor.game;

import android.graphics.Bitmap;

public class Engine {
    static {
        try { System.loadLibrary("native-lib"); }
        catch (UnsatisfiedLinkError e) { android.util.Log.e("GameEngine", "❌ 核心驱动加载失败！"); }
    }
    public native boolean initDetector(String param320, String bin320, String param416, String bin416);
    // 修改返回类型为 float[]
    public native float[] detectScreen(Bitmap bitmap);
}
