package com.spatialite;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

public class MultiTouch extends View
{
  /**
   * 用来控制画图的边距
   */
  private float m_fMargins = 120;
  /**
   * 表示不处理
   */
  private static final int NONE = 0;
  private static final int DRAG = 1;
  /**
   * 放大缩小
   */
  private static final int ZOOM = 2;
  /**
   * 设置模式0不处理2表示放大缩小
   */
  private int m_nMode = NONE;
  /**
   * 开始2点的x，y的值
   */
  PointF m_pStart = new PointF();
   /**
   * 新的2点的x，y的值
   */
  PointF m_pMid = new PointF();
  /**
   * 一开始的2个点的距离
   */
  float m_fOldDist = 1f;
  /**
   * 图离屏幕最大的距离
   */
  private int m_nMaximum = 150;
  /**
   * 图离屏幕最小的距离
   */
  private int m_nMinimun = 30;
  /**
   * 每次扩大的速度
   */
  private int m_nStep =1;
  /**
   * 误差范围是正负30
   */
  private int m_nErrorValue = 30;
  public MultiTouch(Context context)
  {
    super(context);
  }

  public MultiTouch(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    // TODO Auto-generated constructor stub
  }

  @Override
  protected void onDraw(Canvas canvas)
  {
    // TODO Auto-generated method stub
    // 简单的画一个有圆角的矩形通过刷新画布实现重绘m_fMargins是控制圆角矩形和画布之间的距离的变量--->就是m_fMargins来控制矩形的大小
    super.onDraw(canvas);
    Paint mPaint = new Paint();
    mPaint.setColor(Color.BLUE);
     mPaint.setAntiAlias(true);
     RectF rectF = new RectF(m_fMargins, m_fMargins, canvas.getWidth()
         - m_fMargins, canvas.getHeight() - m_fMargins);
     canvas.drawRoundRect(rectF, 5, 5, mPaint);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event)
  {
    switch (event.getAction() & MotionEvent.ACTION_MASK)
    {
      
      case MotionEvent.ACTION_DOWN:
        // 設置初始點位置,后面用来判断双点是否不动
        m_pStart.set(event.getX(), event.getY());
        m_nMode = DRAG;
        break;
       case MotionEvent.ACTION_POINTER_1_DOWN:
         // 得到一开始的两个点的区域
         m_fOldDist = spacing(event);
         // 判断2个点的距离在10之内就表示2个点的距离很近不处理
         if (m_fOldDist > 10f)
         {
           // 大于就取到2个点的距离
           midPoint(m_pMid, event);
           // 将mode设置为zoom为2表示2个点都按下.在Move中判断是否移动
           m_nMode = ZOOM;
         }
         break;
       case MotionEvent.ACTION_UP:
       case MotionEvent.ACTION_POINTER_1_UP:
         // 如果抬起一个点就将mode设置为0表示不处理
        m_nMode = NONE;
        break;
      case MotionEvent.ACTION_MOVE:
        // 如果为zoom那么就进入表示2个点按下并且在动
        if (m_nMode == ZOOM)
        {
          // 得到新的两点的距离
           float a_fNewDist = spacing(event);
           // 这里判断是否是2个点动的距离过小如果相等就表示2个点没有动（是否加入误差？）
          Log.e("======m_pStart.x======"+m_pStart.x, "======event.getX()======"+event.getX());
           if (m_pStart.x+m_nErrorValue<event.getX()||m_pStart.x-m_nErrorValue>event.getX()||m_pStart.y+m_nErrorValue<event.getY()||m_pStart.y-m_nErrorValue>event.getY())
          {
             // 新的2个点的距离在10以上表示间距大处理否则不处理
             if (a_fNewDist > 10f)
             {
               // 如果新的距离大于老的距离表示放大,否则就缩小
               if (m_fOldDist <a_fNewDist )//大
               {
                 float scale = a_fNewDist / m_fOldDist;
                 if (m_fMargins < m_nMinimun)
                 {
                   m_fMargins = m_fMargins;
                 }
                 else
                 {
                  m_fMargins -= scale+m_nStep;
                 }
                 m_fOldDist = a_fNewDist;
               }
               if (m_fOldDist > a_fNewDist )//小
               {
                 float scale = a_fNewDist / m_fOldDist;
                if (m_fMargins > m_nMaximum)
                {
                  m_fMargins = m_fMargins;
                }
                else
                {
                  m_fMargins += scale+m_nStep;
                }
                m_fOldDist = a_fNewDist;
               }
             }
            postInvalidate();
           }
         }
        break;
    }
    return true;
  }
  /**
   * 确定2个点的距离
    * 
   * @param event
   *          传入的是onTouchEvent(MotionEvent event)终端额event
   * @return 返回的是2个手指之间的区域的float值
   */
  private float spacing(MotionEvent event)
  {
    float x = event.getX(0) - event.getX(1);
    float y = event.getY(0) - event.getY(1);
    return (float) Math.sqrt(x * x + y * y);
   }
  /**
   * 计算第一次2个手指的中间点
   * 
   * @param point 计算？
   * @param event 传入的是onTouchEvent(MotionEvent event)中的event
   */
  private void midPoint(PointF point, MotionEvent event)
  {
    float x = event.getX(0) + event.getX(1);
    float y = event.getY(0) + event.getY(1);
    point.set(x / 2, y / 2);
   }
}
