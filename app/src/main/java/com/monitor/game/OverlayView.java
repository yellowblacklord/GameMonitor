package com.monitor.game;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.view.View;
public class OverlayView extends View {
    private Paint boxPaint;
    private Paint textPaint;
    private float[] detectionResults = null;
    public OverlayView(Context context) {
        super(context);
        boxPaint = new Paint(); boxPaint.setAntiAlias(true); boxPaint.setStyle(Paint.Style.STROKE); boxPaint.setStrokeWidth(4.0f);
        textPaint = new Paint(); textPaint.setAntiAlias(true); textPaint.setColor(Color.WHITE); textPaint.setTextSize(30.0f);
    }
    public void updateResults(float[] results) { this.detectionResults = results; postInvalidate(); }
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        if (detectionResults == null || detectionResults.length == 0) return;
        int numObjects = (int) detectionResults[0];
        int step = 6;
        for (int i = 0; i < numObjects; i++) {
            int base = 1 + i * step;
            float x1 = detectionResults[base+0]; float y1 = detectionResults[base+1]; float x2 = detectionResults[base+2]; float y2 = detectionResults[base+3];
            int label = (int) detectionResults[base+4]; float score = detectionResults[base+5];
            boxPaint.setColor(Color.RED);
            canvas.drawRect(x1, y1, x2, y2, boxPaint);
            canvas.drawText("Label: " + label, x1, y1 - 10, textPaint);
        }
    }
}
