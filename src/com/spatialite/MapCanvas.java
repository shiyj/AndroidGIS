package com.spatialite;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jsqlite.Callback;
import jsqlite.Stmt;
import jsqlite.Exception;
//引入JTS
import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKBReader;

import android.R.integer;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class MapCanvas extends Activity {
	
	private static final String TAG = TestActivity.class.getName();
	private final jsqlite.Database mDatabase;
	private GeometryCollection mGeometryCollection;
	private Envelope mGeomEnvelope;
	private MyView mView;
	public MapCanvas()throws Exception {
		mDatabase = new jsqlite.Database();
		mDatabase.open("/mnt/sdcard/test-2.3.sqlite", jsqlite.Constants.SQLITE_OPEN_READONLY);
	}
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Toast.makeText(getApplicationContext(), "正在加载数据库……",
				Toast.LENGTH_SHORT).show();
		mView = new MyView(this);
		setContentView(mView);
	}
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
    	 if (keyCode == KeyEvent.KEYCODE_VOLUME_UP){
    		 mView.zoomIn();
    		 return true;
    	 } else  if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN){
    		 mView.zoomOut();
    		 return true;
    	 }
         return super.onKeyDown(keyCode, event);
    }

	public class MyView extends View {
		/**
		 * 分别表示：SELECT选择属性 DRAG 拖动 ZOOM 放大缩小
		 */
		private static final int NONE = 0;
		private static final int ZOOM = 1;
		private static final int DRAG = 2;
		private static final int SELECT = 3;
		
		private Paint paint = new Paint();
		// 设置默认模式
		private int m_nMode = DRAG;
		//触摸标记
		private double m_startX=0;
		private double m_startY;
		private double m_endX;
		private double m_endY;
		private double m_fOldDist = 1f;
		
		private double rate;//全屏时比例
		private double leavel = 1;
		//相对于原数据坐标的平移位置dx,dy。
		private double dx=0;
		private double dy=0;
		int view_w =300;
		int view_h = 400;
		MyView(Context context) {
			super(context);
			getData();
		}

		@Override
		protected void onDraw(Canvas canvas) {
			super.onDraw(canvas);
			view_w=getWidth();
			view_h=getHeight();
			
			paint.setColor(Color.BLUE);
			paint.setStyle(Style.FILL);
			//canvas.drawRect(new Rect(0, 0, view_w, view_h), paint);
			if(null==mGeometryCollection)
			{
				return;
			}
			DrawGeomtry(canvas,mGeometryCollection);
		}

		@Override
		public boolean onTouchEvent(MotionEvent event) {
			switch (event.getAction() & MotionEvent.ACTION_MASK) {
			
			case MotionEvent.ACTION_DOWN:
				//触摸获取位置查询
				m_endX = m_startX= event.getX();
				m_endY = m_startY = event.getY();
				query(m_startX,m_startY);
				break;
			case MotionEvent.ACTION_UP:
				m_endX = m_startX= event.getX();
				m_endY = m_startY = event.getY();
				break;
			case MotionEvent.ACTION_POINTER_1_DOWN:
				//计算两点的距离，大于10f才认为是两根手指。
				m_fOldDist = spacing(event);
				if (m_fOldDist > 10f) 
				{
					m_nMode = ZOOM;
				}
				break;
			case MotionEvent.ACTION_POINTER_1_UP:
				//中途其中一根手指离开屏幕，退出缩放模式。
				//同时将开始标记点记为当前。
				m_nMode = DRAG;
				m_endX = m_startX= event.getX();
				m_endY = m_startY = event.getY();
				break;
			case MotionEvent.ACTION_MOVE:
				m_endX = event.getX();
				m_endY = event.getY();
				if (m_nMode == ZOOM) 
				{
					double a_fNewDist = spacing(event);
					if (a_fNewDist - m_fOldDist > 14 
							|| a_fNewDist - m_fOldDist < -14){
						zoom(a_fNewDist-m_fOldDist);
						m_fOldDist = a_fNewDist;
					}
				}
				else if(m_nMode == DRAG)
				{
					if(m_endX-m_startX >10 || m_endX-m_startX < -10
							|| m_endY - m_startY > 10
							|| m_endY - m_startY < -10) {
						drag();
						m_endX = m_startX= event.getX();
						m_endY = m_startY = event.getY();
					}
				}
				postInvalidate();
				break;
			}
			return true;
		}
		private void zoom(double dis){
			double min_leavel = rate/2;
			if(leavel<min_leavel){
				leavel=  min_leavel;
				return;
			}
			double len = Math.sqrt(view_w*view_w+view_h*view_h);
			double originx,originy;
			originx = transData(view_w/2, 0);
			originy = transData(view_h/2, 1);
			leavel = (1+dis/len)*leavel;
			m_endX=antTransData(originx, 0);
			m_endY=antTransData(originy, 1);
			m_startX = view_w/2;
			m_startY = view_h/2;
			drag();
			m_endX = m_startX = view_w/2;
			m_endY = m_startY = view_h/2;
		}
		public void zoomIn() {
			
			double originx,originy;
			originx = transData(view_w/2, 0);
			originy = transData(view_h/2, 1);
			leavel += 0.2*leavel;
			m_endX=antTransData(originx, 0);
			m_endY=antTransData(originy, 1);
			m_startX = view_w/2;
			m_startY = view_h/2;
			drag();
			m_endX = m_startX = view_w/2;
			m_endY = m_startY = view_h/2;
			postInvalidate();
		}
		public void zoomOut() {
			double originx,originy;
			originx = transData(view_w/2, 0);
			originy = transData(view_h/2, 1);
			leavel -= 0.2*leavel;
			m_endX=antTransData(originx, 0);
			m_endY=antTransData(originy, 1);
			m_startX = view_w/2;
			m_startY = view_h/2;
			drag();
			m_endX = m_startX = view_w/2;
			m_endY = m_startY = view_h/2;
			postInvalidate();
		}
		private void drag(){
			double tmp_dx=0;//直接赋值给dx会影响dy的判断
			if (m_endX - m_startX > 10 || m_endX - m_startX < -10) {
				tmp_dx = antTransData(m_endX, 0) - antTransData(m_startX, 0);
			}
			if (m_endY - m_startY > 10 || m_endY - m_startY < -10) {
				dy += (antTransData(m_endY, 1) - antTransData(m_startY, 1));
			}
			dx += tmp_dx;
			//不能超出地图范围。
			double width = mGeomEnvelope.getWidth();
			double height = mGeomEnvelope.getHeight();
			if(dx > width){
				dx = width;
			}
			else if(dx < -width){
				dx = -width;
			}
			if(dy > height){
				dy = height;
			}
			else if (dy < -height) {
				dy = -height;
			}
		}
		private void query(double x,double y){
			
		}
		/**
		 * 将数据坐标转换成屏幕坐标。
		 * @param f 坐标值
		 * @param i 0表示x，1表示y;
		 * @return
		 */
		private double transData(double f, double i) {
			double result = 0;
			if (i == 0)	{
				double minx;
				minx = mGeomEnvelope.getMinX();
				result = (f + dx - minx)*leavel;
			}
			else {
				double miny;
				miny = mGeomEnvelope.getMinY();
				result = (view_h - (f + dy - miny)*leavel);
			}
			return result;
		}
		/**
		 * 将屏幕坐标转换成数据坐标。
		 * @param f 坐标值
		 * @param i 0表示x，1表示y;
		 * @return
		 */
		private double antTransData(double f, double i) {
			double result = 0;
			if (i == 0)	{
				double minx;
				minx = mGeomEnvelope.getMinX();
				result = (f/leavel + minx - dx);
			}
			else {
				double miny;
				miny = mGeomEnvelope.getMinY();
				result = ((view_h -f)/leavel + miny - dy);
			}
			return result;
		}
		

		//计算触摸两个点的距离。
		private double spacing(MotionEvent event) {

			double x = event.getX(0) - event.getX(1);
			double y = event.getY(0) - event.getY(1);
			return  Math.sqrt(x * x + y * y);
		}

		private void DrawGeomtry(Canvas canvas,GeometryCollection geomCollection)
		{			
			String type= geomCollection.getGeometryType();
			if(type.equals("MultiPoint"))
			{
				MultiPoint mulPoint=(MultiPoint)geomCollection;
				DrawPoint(canvas,mulPoint);
			}
		}
		private void DrawPoint(Canvas canvas,MultiPoint mulPoint)
		{
			paint.setColor(Color.GREEN);
			canvas.drawText("Hello", 10, 50, paint);

			paint.setColor(Color.RED);
			paint.setStyle(Style.FILL);
			
			int len = mulPoint.getNumGeometries();
			for (int i = 0; i < len; i++) {
				Point pt = (Point)mulPoint.getGeometryN(i);
				canvas.drawCircle((float)transData(pt.getX(), 0), (float)transData(pt.getY(), 1), 10, paint);
			}
		}
		private void getData() {
			try {
				// Create query
				//String query = "SELECT name, AsBinary(ST_Transform(geometry,4326)) from Towns where peoples > 350000";
				String query = "SELECT name, AsBinary(ST_Transform(geometry,4326)) from Towns";
				Stmt stmt = mDatabase.prepare(query);
				ArrayList<Geometry> geoms = new ArrayList<Geometry>();
				//geoms = new Geometry[colum_count];
				GeometryFactory geomFactory = new GeometryFactory();
				while(stmt.step()) {
					// Create JTS geometry from binary representation
					// returned from database
					try {
						geoms.add(new WKBReader().read(stmt.column_bytes(1)));
						Geometry ge= new WKBReader().read(stmt.column_bytes(1));
						String type =ge.getGeometryType();
						type.equals("Point");
					} catch (ParseException e) {
						Log.e(TAG, e.getMessage());
					}
				}
				Point[] arrGeom = (Point[])geoms.toArray(new Point[geoms.size()]);//new Geometry[geoms.size()];			
				mGeometryCollection = geomFactory.createMultiPoint(arrGeom);
				mGeomEnvelope = mGeometryCollection.getEnvelopeInternal();
				double geom_width,geom_height;
				geom_width = mGeomEnvelope.getWidth();
				geom_height = mGeomEnvelope.getHeight();
				//只有一个点的图层，原比例无缩放。
				if(geom_height<1e-8 && geom_width <1e-8){
					rate = 1;
					mGeomEnvelope.init(view_w,0,view_h,0);
				}
				else {
					double rate1 = view_h/view_w;
					double minx,miny,maxx,maxy;
					minx = mGeomEnvelope.getMinX();
					miny = mGeomEnvelope.getMinY();
					maxx = mGeomEnvelope.getMaxX();
					maxy = mGeomEnvelope.getMaxY();
					//屏幕比外接矩形窄
					if(rate1 > geom_height/geom_width){
						rate = view_w/geom_width;
						double d_height = (view_h/rate - geom_height)/2;
						mGeomEnvelope.init(maxx, minx, maxy + d_height, miny - d_height);
					}
					else {
						rate = view_h/geom_height;
						double d_with  = (view_w/rate - geom_width)/2;
						mGeomEnvelope.init(maxx + d_with,minx - d_with, maxy, miny);
					}
				}
				leavel = rate;
				stmt.close();
			} catch (Exception e) {
				Log.e(TAG, e.getMessage());
			}
		}
	}
}
