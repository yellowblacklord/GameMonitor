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
        gameEngine = new Engine();
        new Thread(this::initNcnnModels).start();
    }

    private void initNcnnModels() {
        String modelDir = getFilesDir().getAbsolutePath() + "/models/";
        File dir = new File(modelDir);
        if (!dir.exists()) dir.mkdirs();
        boolean isLoaded = gameEngine.initDetector(
                modelDir + "yolov8n-320.param", modelDir + "yolov8n-320.bin",
                modelDir + "yolov8n-416.param", modelDir + "yolov8n-416.bin"
        );
        if (isLoaded) startCaptureLoop();
    }

    private void startCaptureLoop() {
        isRunning = true;
        new Thread(() -> {
            while (isRunning) {
                try {
                    Bitmap currentScreen = captureScreenViaSystem();
                    if (currentScreen != null) {
                        byte[] resultBytes = gameEngine.detectScreen(currentScreen);
                        if (resultBytes != null && resultBytes.length > 0) parseAndAction(resultBytes);
                        currentScreen.recycle();
                    }
                    Thread.sleep(100);
                } catch (Exception e) { Log.e(TAG, "Loop error: " + e.getMessage()); }
            }
        }).start();
    }

    private void parseAndAction(byte[] results) {
        String resultStr = new String(results);
        Log.d(TAG, "Data: " + resultStr);
    }

    private Bitmap captureScreenViaSystem() {
        try {
            Process process = Runtime.getRuntime().exec("su -c screencap -p");
            return BitmapFactory.decodeStream(process.getInputStream());
        } catch (Exception e) { return null; }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isRunning = false;
    }
}
