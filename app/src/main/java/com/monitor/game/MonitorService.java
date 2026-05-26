package com.monitor.game;

import android.app.Service;
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
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.view.Gravity;
import android.view.WindowManager;
import java.nio.ByteBuffer;

public class MonitorService extends Service {
    private WindowManager windowManager;
    private OverlayView overlayView;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    
    private HandlerThread captureThread;
    private Handler captureHandler;
    private boolean isRunning = false;
    private Engine engine; // 实例化的推理引擎

    private static Intent projectionIntent = null;
    private static int projectionResultCode = 0;

    public static void setProjectionData(int resultCode, Intent data) {
        projectionResultCode = resultCode;
        projectionIntent = data;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        engine = new Engine(); // 初始化引擎
        
        // 1. 初始化全屏全透悬浮窗
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlayView = new OverlayView(this);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | 
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.LEFT;
        windowManager.addView(overlayView, params);

        // 2. 开启工作线程
        captureThread = new HandlerThread("CaptureInferenceThread");
        captureThread.start();
        captureHandler = new Handler(captureThread.getLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (projectionIntent != null && !isRunning) {
            MediaProjectionManager projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            mediaProjection = projectionManager.getMediaProjection(projectionResultCode, projectionIntent);
            
            int width = getResources().getDisplayMetrics().widthPixels;
            int height = getResources().getDisplayMetrics().heightPixels;
            int dpi = getResources().getDisplayMetrics().densityDpi;

            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
            virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenCapture", width, height, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, captureHandler
            );

            isRunning = true;
            captureHandler.post(new CaptureRunnable());
        }
        return START_STICKY;
    }

    private class CaptureRunnable implements Runnable {
        @Override
        public void run() {
            if (!isRunning) return;

            Image image = imageReader.acquireLatestImage();
            if (image != null) {
                int width = image.getWidth();
                int height = image.getHeight();
                Image.Plane[] planes = image.getPlanes();
                ByteBuffer buffer = planes[0].getBuffer();
                
                int pixelStride = planes[0].getPixelStride();
                int rowStride = planes[0].getRowStride();
                int rowPadding = rowStride - pixelStride * width;

                Bitmap bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
                bitmap.copyPixelsFromBuffer(buffer);
                image.close(); 

                Bitmap croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height);
                bitmap.recycle();

                // 核心：直接调用，接收 float[] 数组
                float[] results = engine.detectScreen(croppedBitmap);
                croppedBitmap.recycle();

                if (results != null) {
                    overlayView.updateResults(results);
                }
            }

            if (isRunning) {
                captureHandler.postDelayed(this, 16); 
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        if (virtualDisplay != null) virtualDisplay.release();
        if (imageReader != null) imageReader.close();
        if (mediaProjection != null) mediaProjection.stop();
        if (overlayView != null) windowManager.removeView(overlayView);
        captureThread.quitSafely();
    }
}
