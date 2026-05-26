package com.monitor.game;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {

    private Engine gameEngine;
    private boolean isRunning = false;
    private static final String TAG = "GameMonitorBusiness";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 1. 实例化核心引擎
        gameEngine = new Engine();
        
        // 2. 启动异步线程加载模型
        new Thread(new Runnable() {
            @Override
            public void run() {
                initNcnnModels();
            }
        }).start();
    }

    /**
     * 核心业务：定位并加载 320 与 416 双尺度 YOLOv8 模型
     */
    private void initNcnnModels() {
        String modelDir = getFilesDir().getAbsolutePath() + "/models/";
        
        File dir = new File(modelDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        Log.d(TAG, "正在调动底层驱动加载双模型协同引擎...");
        
        boolean isLoaded = gameEngine.initDetector(
                modelDir + "yolov8n-320.param",
                modelDir + "yolov8n-320.bin",
                modelDir + "yolov8n-416.param",
                modelDir + "yolov8n-416.bin"
        );

        if (isLoaded) {
            Log.d(TAG, "🔥 [核心喜报] 双模型协同引擎初始化成功！开始灌入实时图像流...");
            startCaptureLoop();
        } else {
            Log.e(TAG, "❌ [初始化失败] 请检查模型文件是否已完整放入路径: " + modelDir);
        }
    }

    /**
     * 核心业务死循环：高速抓取屏幕并喂给 C++ 引擎
     */
    private void startCaptureLoop() {
        isRunning = true;
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (isRunning) {
                    try {
                        // 1. 获取当前游戏画面的实时截图
                        Bitmap currentScreen = captureScreenViaSystem();
                        
                        if (currentScreen != null) {
                            // 2. 将全彩 Bitmap 图像直接灌入底层 C++ 驱动，这里已完美修复为 float[] 接收！
                            float[] results = gameEngine.detectScreen(currentScreen);
                            
                            // 3. 解析底层传回的坐标与识别数据
                            if (results != null && results.length > 0) {
                                parseAndAction(results);
                            }
                            
                            // 物理回收 Bitmap 内存，严防高频死循环导致的内存溢出 (OOM)
                            currentScreen.recycle();
                        }
                        
                        // 4. 帧率控制：每秒检测 10 次
                        Thread.sleep(100);
                        
                    } catch (Exception e) {
                        Log.e(TAG, "监控主循环发生异常: " + e.getMessage());
                    }
                }
            }
        }).start();
    }

    /**
     * 业务行为消费：解析 C++ 传回的识别包并触发自动化动作
     */
    private void parseAndAction(float[] results) {
        if (results == null || results.length == 0) return;
        
        // 数组的第 0 位代表检测到的目标总数量
        int numObjects = (int) results[0];
        Log.d(TAG, "🎯 底层协同引擎透传回的数据：本次画面共锁定 " + numObjects + " 个目标");
        
        if (numObjects > 0) {
            // 说明画面里有敌人/目标，这里可以执行你的自动化模拟点击等业务
            Log.d(TAG, "🔥 触发业务动作：发现锁定的游戏目标，执行模拟指令！");
        }
    }

    /**
     * 跨进程安全截屏：抓取当前手机系统的屏幕数据
     */
    private Bitmap captureScreenViaSystem() {
        try {
            Process process = Runtime.getRuntime().exec("su -c screencap -p");
            InputStream is = process.getInputStream();
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            if (bitmap != null) {
                return bitmap;
            }
        } catch (Exception e) {
            Log.e(TAG, "高级系统截图捕获失败（可能缺少 root 权限或悬浮窗无障碍特权）");
        }
        return null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isRunning = false;
    }
}
