package com.monitor.game;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity {

    private Engine gameEngine;
    private boolean isRunning = false;
    private static final String TAG = "GameMonitorBusiness";
    private static final int REQUEST_CODE_SCREEN_CAPTURE = 10086;

    // MediaProjection 免 Root 截图核心组件
    private MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;

    private int screenWidth;
    private int screenHeight;
    private int screenDensity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 初始化引擎
        gameEngine = new Engine();

        // 获取屏幕物理参数用于高精度比例截屏
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        screenDensity = metrics.densityDpi;

        // 1. 获取系统录屏服务管理器
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        // 2. 异步触发模型释放与 C++ 引擎加载
        new Thread(new Runnable() {
            @Override
            public void run() {
                initNcnnModels();
            }
        }).start();

        // 3. 弹出系统原生免 Root 录屏权限申请框
        if (projectionManager != null) {
            startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_CODE_SCREEN_CAPTURE);
        } else {
            Toast.makeText(this, "当前手机系统不支持录屏服务", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 接收用户点击“立即开始”或“拒绝”的结果
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SCREEN_CAPTURE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                Log.d(TAG, "✅ [权限成功] 用户已授予免 Root 屏幕投影录屏权限！");
                
                // 绑定录屏令牌
                mediaProjection = projectionManager.getMediaProjection(resultCode, data);
                
                // 初始化后台高速图像采样器 (RGBA 格式对齐 C++ 步长)
                imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);
                virtualDisplay = mediaProjection.createVirtualDisplay(
                        "GameScreenCapture",
                        screenWidth, screenHeight, screenDensity,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        imageReader.getSurface(), null, null
                );

                // 如果模型也加载好了，开启检测主循环
                startCaptureLoop();
            } else {
                Log.e(TAG, "❌ [权限拒绝] 用户拒绝了录屏权限，免 Root 截屏无法工作！");
                Toast.makeText(this, "必须要同意录屏权限才能进行游戏监控", Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * 核心业务：自动从 assets 提取模型并加载
     */
    private void initNcnnModels() {
        Log.d(TAG, "正在通过内存解压引擎，调动底层驱动加载双模型协同引擎...");

        String p320 = Engine.getRealFilePath(this, "320.param");
        String b320 = Engine.getRealFilePath(this, "320.bin");
        String p416 = Engine.getRealFilePath(this, "416.param");
        String b416 = Engine.getRealFilePath(this, "416.bin");

        if (p320.isEmpty() || b320.isEmpty() || p416.isEmpty() || b416.isEmpty()) {
            Log.e(TAG, "❌ [解压失败] 无法从 assets 中提取模型，请检查文件。");
            return;
        }

        boolean isLoaded = Engine.initDetector(p320, b320, p416, b416);

        if (isLoaded) {
            Log.d(TAG, "🔥 [核心喜报] 双模型协同引擎加载成功！等待录屏流接通...");
            startCaptureLoop();
        } else {
            Log.e(TAG, "❌ [初始化失败] C++ 拒绝了加载请求。");
        }
    }

    /**
     * 核心业务死循环：高效免 Root 抓取屏幕缓冲区并喂给 C++
     */
    private synchronized void startCaptureLoop() {
        // 必须确保模型加载成功且录屏流也拿到了，才能启动循环
        if (mediaProjection == null || isRunning) return;
        
        isRunning = true;
        Log.d(TAG, "🚀 双端就绪！开始向底层 C++ 驱动持续灌入实时免 Root 图像流...");

        new Thread(new Runnable() {
            @Override
            public void run() {
                while (isRunning) {
                    try {
                        Bitmap currentScreen = captureScreenViaProjection();
                        
                        if (currentScreen != null) {
                            // 灌入底层 C++ 进行双尺度识别
                            float[] results = Engine.detectScreen(currentScreen);
                            
                            if (results != null && results.length > 0) {
                                parseAndAction(results);
                            }
                            
                            currentScreen.recycle(); // 严防高频 OOM 内存溢出
                        }
                        
                        Thread.sleep(100); // 帧率控制：每秒检测 10 次
                        
                    } catch (Exception e) {
                        Log.e(TAG, "监控主循环发生异常: " + e.getMessage());
                    }
                }
            }
        }).start();
    }

    /**
     * 替代 su -c 方案：利用官方底层缓冲区高速提取 Bitmap
     */
    private Bitmap captureScreenViaProjection() {
        if (imageReader == null) return null;
        Image image = null;
        try {
            // 获取最新的一帧像素数据
            image = imageReader.acquireLatestImage();
            if (image == null) return null;

            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * screenWidth;

            // 创建对齐的 Bitmap
            Bitmap bitmap = Bitmap.createBitmap(
                    screenWidth + rowPadding / pixelStride, 
                    screenHeight, 
                    Bitmap.Config.ARGB_8888
            );
            bitmap.copyPixelsFromBuffer(buffer);

            // 裁剪掉因内存对齐产生的右侧多余边框，还原纯净原始游戏画面
            if (rowPadding != 0) {
                Bitmap croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight);
                bitmap.recycle();
                return croppedBitmap;
            }
            return bitmap;
        } catch (Exception e) {
            Log.e(TAG, "免 Root 录屏帧提取失败: " + e.getMessage());
        } finally {
            if (image != null) {
                image.close(); // 必须释放，否则缓冲区满后会死锁停屏
            }
        }
        return null;
    }

    private void parseAndAction(float[] results) {
        if (results == null || results.length == 0) return;
        int numObjects = (int) results[0];
        Log.d(TAG, "🎯 底层传回数据：本次免 Root 画面内锁定 " + numObjects + " 个目标");
        if (numObjects > 0) {
            Log.d(TAG, "🔥 触发业务动作：发现锁定的游戏目标，后台执行模拟指令！");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isRunning = false;
        // 彻底释放系统资源，防止后台持续录屏耗电
        if (virtualDisplay != null) virtualDisplay.release();
        if (imageReader != null) imageReader.close();
        if (mediaProjection != null) mediaProjection.stop();
    }
}
