package com.lht.paintview.pojo;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;

/**
 * Created by lht on 16/10/17.
 */

public class DrawPoint extends DrawShape {

    private float x, y;

    public DrawPoint(float x, float y, Paint paint) {
        this.x = x;
        this.y = y;
        this.paint = paint;
    }

    @Override
    public void draw(Canvas canvas, Matrix matrix) {
        canvas.drawPoint(x, y, paint);
    }
}
