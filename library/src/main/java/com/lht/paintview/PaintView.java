package com.lht.paintview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.lht.paintview.pojo.DrawPath;
import com.lht.paintview.pojo.DrawPoint;
import com.lht.paintview.pojo.DrawShape;
import com.lht.paintview.pojo.StrokePaint;

import java.util.ArrayList;

/**
 * Created by lht on 16/10/17.
 */

public class PaintView extends View {

    private OnDrawListener mOnDrawListener;
    public interface OnDrawListener {
        void afterDraw(ArrayList<DrawShape> mDrawShapes);
    }

    //背景色
    private int mBgColor = Color.WHITE;
    //绘制标记Paint
    private ArrayList<StrokePaint> mPaintList = new ArrayList<>();

    //背景图
    private Bitmap mBgBitmap = null;
    //绘制背景图Paint
    private Paint mBitmapPaint;

    //当前坐标
    private float mCurrentX, mCurrentY;
    //当前绘制路径
    private Path mCurrentPath;

    //绘制list
    private ArrayList<DrawShape> mDrawShapes = new ArrayList<>();
    private boolean bPathDrawing = false;

    //手势
    private final static int SINGLE_FINGER = 1, DOUBLE_FINGER = 2;
    private enum MODE {
        NONE, DRAG, ZOOM
    }
    private MODE mode = MODE.NONE;

    //中心点
    private float mCenterX, mCenterY;

    //当次两指间距
    private float mCurrentLength = 0;
    //当次位移
    private float mCurrentDistanceX, mCurrentDistanceY;
    //当次缩放
    private float mCurrentScale;

    //整体矩阵
    private Matrix mMainMatrix = new Matrix();
    //当次矩阵
    private Matrix mCurrentMatrix = new Matrix();

    public PaintView(Context context) {
        super(context);
        init();
    }

    public PaintView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public void setOnDrawListener(OnDrawListener onDrawListener) {
        mOnDrawListener = onDrawListener;
    }

    private void init() {
        setDrawingCacheEnabled(true);

        initPaint();
        mBitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    }

    /**
     * 初始化画笔
     */
    private void initPaint() {
        StrokePaint paint = new StrokePaint(Paint.ANTI_ALIAS_FLAG);
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);// 设置外边缘
        paint.setStrokeCap(Paint.Cap.ROUND);// 形状

        mPaintList.add(paint);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        switch (mode) {
            case DRAG:
                mMainMatrix.postTranslate(mCurrentDistanceX, mCurrentDistanceY);
                mCurrentMatrix.setTranslate(mCurrentDistanceX, mCurrentDistanceY);
                break;
            case ZOOM:
                mMainMatrix.postScale(mCurrentScale, mCurrentScale, mCenterX, mCenterY);
                mCurrentMatrix.setScale(mCurrentScale, mCurrentScale, mCenterX, mCenterY);
                scaleStrokeWidth(mCurrentScale);
                break;
            case NONE:
                mCurrentMatrix.reset();
                break;
        }

        canvas.drawColor(mBgColor);
        canvas.drawBitmap(mBgBitmap, mMainMatrix, mBitmapPaint);
        for (DrawShape shape : mDrawShapes) {
            shape.draw(canvas, mCurrentMatrix);
        }
    }

    private void touchDown(float x, float y) {
        mCurrentX = x;
        mCurrentY = y;
    }

    private void touchMove(float x, float y) {
        final float previousX = mCurrentX;
        final float previousY = mCurrentY;

        final float dx = Math.abs(x - previousX);
        final float dy = Math.abs(y - previousY);

        //两点之间的距离大于等于3时，生成贝塞尔绘制曲线
        if (dx >= 3 || dy >= 3) {
            if (!bPathDrawing) {
                mCurrentPath = new Path();
                mCurrentPath.moveTo(previousX, previousY);
                mDrawShapes.add(
                        new DrawPath(mCurrentPath, getCurrentPaint()));
                bPathDrawing = true;
            }

            //设置贝塞尔曲线的操作点为起点和终点的一半
            float cX = (x + previousX) / 2;
            float cY = (y + previousY) / 2;

            //二次贝塞尔，实现平滑曲线；previousX, previousY为操作点，cX, cY为终点
            mCurrentPath.quadTo(previousX, previousY, cX, cY);

            //第二次执行时，第一次结束调用的坐标值将作为第二次调用的初始坐标值
            mCurrentX = x;
            mCurrentY = y;
        }
    }

    private void touchUp(float x, float y) {
        if (!bPathDrawing && x == mCurrentX && y == mCurrentY) {
            mDrawShapes.add(
                    new DrawPoint(x, y, getCurrentPaint()));
        }
        bPathDrawing = false;

        if (mOnDrawListener != null) {
            mOnDrawListener.afterDraw(mDrawShapes);
        }
    }

    //两点按下
    private void doubleFingerDown(MotionEvent event) {
        mCenterX = (event.getX(0) + event.getX(1)) / 2;
        mCenterY = (event.getY(0) + event.getY(1)) / 2;

        mCurrentLength = getDistance(event);
    }

    //两点移动
    private void doubleFingerMove(MotionEvent event) {
        //当前中心点
        float curCenterX = (event.getX(0) + event.getX(1)) / 2;
        float curCenterY = (event.getY(0) + event.getY(1)) / 2;

        //当前两点间距离
        float curLength = getDistance(event);

        //拖动
        if (Math.abs(mCurrentLength - curLength) < 5) {
            mode = MODE.DRAG;
            mCurrentDistanceX = curCenterX - mCenterX;
            mCurrentDistanceY = curCenterY - mCenterY;
        }
        //放大 || 缩小
        else if (mCurrentLength < curLength || mCurrentLength > curLength){
            mode = MODE.ZOOM;
            mCurrentScale = curLength / mCurrentLength;
        }

        mCenterX = curCenterX;
        mCenterY = curCenterY;

        mCurrentLength = curLength;
    }

    /**
     * 获取两个触控点之间的距离
     * @param event
     * @return 两个触控点之间的距离
     */
    private float getDistance(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);

        return (float)Math.sqrt(x * x + y * y);
    }

    /**
     * 撤销
     */
    public boolean undo() {
        if (mDrawShapes != null && mDrawShapes.size() > 0) {
            mDrawShapes.remove(mDrawShapes.size() - 1);
            invalidate();
        }

        if (mOnDrawListener != null) {
            mOnDrawListener.afterDraw(mDrawShapes);
        }

        return mDrawShapes != null && mDrawShapes.size() > 0;
    }

    /**
     * 设置背景颜色
     * @param color
     */
    public void setBgColor(int color) {
        mBgColor = color;
    }

    /**
     * 设置笔的颜色
     * @param color 0xaarrggbb
     */
    public void setColor(int color) {
        StrokePaint paint = new StrokePaint(getCurrentPaint());
        paint.setColor(color);
        mPaintList.add(paint);
    }

    /**
     * 设置笔的宽度
     * @param width
     */
    public void setStrokeWidth(int width) {
        StrokePaint paint = new StrokePaint(getCurrentPaint());
        paint.setWidth(width);
        mPaintList.add(paint);
    }

    /**
     * 获取绘制后截图
     * @return
     */
    public Bitmap getBitmap() {
        destroyDrawingCache();
        return getDrawingCache();
    }

    /**
     * 设置画布原始图案
     * @param bitmap
     */
    public void setBitmap(Bitmap bitmap) {
        mBgBitmap = bitmap;
    }

    /**
     * 获得当前笔迹
     */
    private StrokePaint getCurrentPaint() {
        return mPaintList.get(mPaintList.size() - 1);
    }

    /**
     * 缩放所有笔迹
     */
    private void scaleStrokeWidth(float scale) {
        for (StrokePaint paint: mPaintList) {
            paint.setScale(paint.getScale() * scale);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        mode = MODE.NONE;
        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            //多点按下
            case MotionEvent.ACTION_POINTER_DOWN:
                doubleFingerDown(event);
                break;
            //单点按下
            case MotionEvent.ACTION_DOWN:
                touchDown(x, y);
                break;
            //移动
            case MotionEvent.ACTION_MOVE:
                //单点移动
                if (event.getPointerCount() == SINGLE_FINGER) {
                    touchMove(x, y);
                }
                //多点移动
                else if (event.getPointerCount() == DOUBLE_FINGER) {
                    doubleFingerMove(event);
                }
                break;
            //单点抬起
            case MotionEvent.ACTION_UP:
                touchUp(x, y);
                break;
        }
        invalidate();
        return true;
    }
}
