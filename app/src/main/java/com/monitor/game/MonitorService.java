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
    // ... (前部分代码保持不变)
    
    private class CaptureRunnable implements Runnable {
        @Override
        public void run() {
            if (!isRunning) return;
            Image image = imageReader.acquireLatestImage();
            if (image != null) {
                // ... (Bitmap 转换代码保持不变)
                Bitmap croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, image.getWidth(), image.getHeight());
                bitmap.recycle();

                // 现在可以直接接收 float[]，无需转换
                float[] results = new Engine().detectScreen(croppedBitmap);
                croppedBitmap.recycle();
                
                if (results != null) {
                    overlayView.updateResults(results);
                }
            }
            if (isRunning) captureHandler.postDelayed(this, 16);
        }
    }
    // ... (onDestroy 保持不变)
}
