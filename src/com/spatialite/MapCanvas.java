package com.spatialite;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jsqlite.Callback;
import jsqlite.Exception;
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
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

public class MapCanvas extends Activity {
	private static final String TAG = TestActivity.class.getName();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		MyView mv = new MyView(this);
		setContentView(mv);

	}

	private class GeoPoint {
		float x, y;
		String name;
	}

	public class MyView extends View {
		/**
		 * 分别表示：SEL选择属性 DRAG 拖动 ZOOM 放大缩小
		 */
		private static final int NONE = 0;
		private static final int ZOOM = 1;
		// 设置默认模式
		private int m_nMode = NONE;
		PointF m_pStart = new PointF();
		PointF m_pMid = new PointF();
		float m_fOldDist = 1f;
		private ArrayList<GeoPoint> pointArrays = new ArrayList<GeoPoint>();
		private float rate;
		private float leavel = 1;
		int w =300;
		int h = 400;
		MyView(Context context) {
			super(context);
			getData();
		}

		@Override
		protected void onDraw(Canvas canvas) {
			super.onDraw(canvas);
			w = getWidth();
			h = getHeight();
			Paint paint = new Paint();
			float rate1 = w / h;
			if (rate1 > (1308585.40 - 319224.01) / (5214373.42 / 3934674.16))
				rate = (float) (h / (5214373.42 / 3934674.16));
			else
				rate = (float) (w / (1308585.40 - 319224.01));
			paint.setColor(Color.BLUE);
			paint.setStyle(Style.FILL);
			canvas.drawRect(new Rect(0, 0, w, h), paint);

			paint.setColor(Color.GREEN);
			canvas.drawText("Hello", 10, 50, paint);

			paint.setColor(Color.RED);
			paint.setStyle(Style.FILL);
			int len = pointArrays.size();
			for (int i = 0; i < len; i++) {
				canvas.drawCircle(transData(pointArrays.get(i).x, 0), transData(pointArrays.get(i).y, 1), 10, paint);
			}
		}

		@Override
		public boolean onTouchEvent(MotionEvent event) {
			switch (event.getAction() & MotionEvent.ACTION_MASK) {
			case MotionEvent.ACTION_DOWN:
				float x = event.getX();
				float y = event.getY();
				// 测试数据为读取出来存放在arraylist中，下一步改成直接从数据库中查询。
				Iterator<GeoPoint> it = pointArrays.iterator();
				while (it.hasNext()) {
					GeoPoint tmp = it.next();
					float tmpx, tmpy;
					tmpx = transData(tmp.x, 0);
					tmpy = transData(tmp.y, 1);
					float distan = ((x - tmpx) * (x - tmpx) + (y + tmpy - getHeight())
							* (y + tmpy - getHeight()));
					if (distan <= 200) {

						Toast toast = Toast.makeText(getApplicationContext(),
								tmp.name, Toast.LENGTH_SHORT);
						toast.setGravity(Gravity.LEFT | Gravity.TOP,
								(int) (x + 5), (int) (y + 5));
						toast.show();
					}
				}
				break;
			case MotionEvent.ACTION_POINTER_1_DOWN:
				m_fOldDist = spacing(event);
				if (m_fOldDist > 10f) {
					m_nMode = ZOOM;
				}
				break;
			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_POINTER_1_UP:
				m_nMode = NONE;
				break;
			case MotionEvent.ACTION_MOVE:
				if (m_nMode == ZOOM) {
					float a_fNewDist = spacing(event);
					if (a_fNewDist - m_fOldDist > 30
							|| a_fNewDist - m_fOldDist < -30) {
						zoom(a_fNewDist-m_fOldDist);
					}
				}
				else
					drag();
				postInvalidate();
				break;
			}
			return true;
		}
		private void zoom(float dis){
			float len=(float) Math.sqrt(w*w+h*h);
			leavel+=dis/(len*5);
			if(leavel<0.5)
				leavel= (float) 0.5;
		}
		private void drag(){
			
		}
		
		/**
		 * 将投影坐标转换成屏幕坐标。
		 * @param f 坐标值
		 * @param i 0表示x，1表示y
		 * @return
		 */
		private float transData(float f, int i) {
			float result = 0;
			if (i == 0)
				result = (float) ((f - 319224.61) * rate);
			else
				result = (float) (h - ((f - 3934674.16) * rate));

			return result*leavel;
		}

		private float spacing(MotionEvent event) {

			float x = event.getX(0) - event.getX(1);
			float y = event.getY(0) - event.getY(1);
			return (float) Math.sqrt(x * x + y * y);
		}

		private void getData() {
			try {
				Class.forName("SQLite.JDBCDriver").newInstance();
				jsqlite.Database db = new jsqlite.Database();
				// 检测、创建数据库的文件夹
				File dir = new File("/mnt/sdcard/");
				if (!dir.exists()) {
					dir.mkdir();
				}
				// 如果文件夹已经存在了
				else {
					// 检查文件是否存在
					dir = new File("/mnt/sdcard/", "test-2.3.sqlite");
					if (!dir.exists()) {
						Log.v(TAG, "不存在地理数据库！");
						Toast.makeText(getApplicationContext(), "不存在数据库",
								Toast.LENGTH_SHORT).show();
						return;
					}
				}
				// db.open(Environment.getExternalStorageDirectory() +
				// "/download/test-2.3.sqlite",jsqlite.Constants.SQLITE_OPEN_READONLY);
				Toast.makeText(getApplicationContext(), "正在加载数据库……",
						Toast.LENGTH_SHORT).show();
				db.open("/mnt/sdcard/test-2.3.sqlite",
						jsqlite.Constants.SQLITE_OPEN_READONLY);
				Callback cb = new Callback() {
					@Override
					public void columns(String[] coldata) {
						// Log.v(TAG, "Columns: " + Arrays.toString(coldata));
					}

					@Override
					public void types(String[] types) {
						// Log.v(TAG, "Types: " + Arrays.toString(types));
					}

					@Override
					public boolean newrow(String[] rowdata) {
						Log.v(TAG, "Row: " + Arrays.toString(rowdata));
						String regEx = "POINT\\(([0-9]+\\.[0-9]+)\\s([0-9]+\\.[0-9]+)\\)";
						String str = Arrays.toString(rowdata);
						Pattern p = Pattern.compile(regEx);
						Matcher m = p.matcher(str);
						boolean rs = m.find();
						if (rs) {
							Float X = new Float(m.group(1));
							Float Y = new Float(m.group(2));
							GeoPoint gp = new GeoPoint();
							gp.x = X.floatValue();
							gp.y = Y.floatValue();
							gp.name = rowdata[0];
							pointArrays.add(gp);
						}
						return false;
					}
				};

				String query = "SELECT name, peoples, AsText(Geometry) from Towns where peoples > 350000";
				db.exec(query, cb);
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			} catch (InstantiationException e) {
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

}
