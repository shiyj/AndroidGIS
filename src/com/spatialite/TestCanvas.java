package com.spatialite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

public class TestCanvas extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		MyView mv = new MyView(this);
		setContentView(mv);
	}

	class MyView extends View {
	                Canvas mCanvas;
	                Bitmap mBitmap, bmp1, bmp2;
	                Paint mPaint;
	                int flag = 0;
	                float movex, movey, lastx, lasty, initx, inity, initx2, inity2;

	                public MyView(Context context) {
	                        super(context);
	                        init();
	                }
	                public void init() {
	                        mBitmap = Bitmap.createBitmap(400,600, Config.ARGB_8888);
	                        mCanvas = new Canvas(mBitmap);                        
	                        bmp1 = BitmapFactory.decodeResource(getResources(),
	                                        R.drawable.icon);// 这两个图片要换
	                        bmp2 = BitmapFactory.decodeResource(getResources(),
	                                        R.drawable.eclipse);                        
	                        mPaint = new Paint();
	                        mPaint.setColor(0xFFFFFFFF);
	                        drawImage(bmp2, 50, 0);
	                        drawImage(bmp1, 50, 300);
	                }

	                protected void onDraw(Canvas canvas) {
	                        canvas.drawColor(0xa1a1a1);
	                        canvas.drawBitmap(mBitmap, 0, 0, mPaint);
	                        super.onDraw(canvas);
	                }

	                List<HashMap> list = new ArrayList<HashMap>();
	                HashMap map = null;

	                /* 在给定的位置画位图记录好他们的初始化坐标，放入一个集合中 */
	                public void drawImage(Bitmap bmp, float x, float y) {
	                        
	                        if(bmp == null) return;
	                        
	                        /* 记录好图片坐标 */
	                        map = new HashMap();
	                        map.put("x", x);
	                        map.put("y", y);
	                        map.put("bmp", bmp);
	                        list.add(map);
	                        Log.d("MyView", "drawImage--->" + initx + ":" + inity);
	                        mCanvas.drawBitmap(bmp, x, y, mPaint);
	                        invalidate();
	                }

	                /* 传入点击的坐标点获得位图 */
	                public Bitmap bmpClick(float mx, float my) {
	                        Bitmap bmpy = null;
	                        HashMap hashMap = null;
	                        for (Iterator<HashMap> it = list.iterator(); it.hasNext();) {
	                                hashMap = it.next();
	                                float dx = Float.parseFloat(hashMap.get("x").toString());// 获取到它的初始化X坐标
	                                float dy = Float.parseFloat(hashMap.get("y").toString());// 获取到它的初始化Y坐标
	                                bmpy = (Bitmap) hashMap.get("bmp");// 获取到它的初始化的位图
	                                Log.i("MyView", "bmpClick---> " + dx + ":" + dy);
	                                if (mx >= dx && mx <= bmpy.getWidth() + dx && my >= dy
	                                                && my <= bmpy.getHeight() + dy) {
	                                        return bmpy;
	                                }

	                        }
	                        return null;

	                }

	                @Override
	                public boolean onTouchEvent(MotionEvent event) {
	                        float x = event.getX();
	                        float y = event.getY();
	                        switch (event.getAction()) {                        
	                                //点击时的(x,y)，记录初始位置
	                                case MotionEvent.ACTION_DOWN:
	                                        mDragBitmap = bmpClick(x, y);
	                                        if (mDragBitmap != null) {
	                                                lastx = x;
	                                                lasty = y;
	                                        }
	                                        break;
	                                //移动时更新(x,y)，同时更新图片
	                                case MotionEvent.ACTION_MOVE:
	                                        movex = x - lastx;
	                                        movey = y - lasty;
	                                        //drawImage(mDragBitmap, movex + initx, movex + inity);
	                                        break;
	                                        
	                                //弹起时更新(x,y)，并记录位置
	                                case MotionEvent.ACTION_UP:
	                                        initx += movex;
	                                        inity += movey;
	                                        movex = 0;
	                                        movey = 0;
	                                        drawImage(mDragBitmap, movex + initx, movex + inity);
	                                        break;
	                                }
	                        return true;
	                }
	        }
	        
	        private Bitmap mDragBitmap = null;
}
