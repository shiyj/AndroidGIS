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
import com.vividsolutions.jts.simplify.DouglasPeuckerSimplifier;

import android.R.bool;
import android.R.color;
import android.R.integer;
import android.R.string;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

public class MapCanvas extends Activity {

	private static final String TAG = TestActivity.class.getName();
	private final jsqlite.Database mDatabase;
	private Envelope mGeomEnvelope;
	private MyView mView;
	private ProgressDialog mProgressDialog;

	public MapCanvas() throws Exception {
		mGeomEnvelope = new Envelope();
		mDatabase = new jsqlite.Database();
		// mDatabase.open("/mnt/sdcard/test-2.3.sqlite",
		// jsqlite.Constants.SQLITE_OPEN_READONLY);
		mDatabase.open("/mnt/sdcard/test.sqlite",
				jsqlite.Constants.SQLITE_OPEN_READONLY);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Hide the title bar
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		// set the full to screen
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);
		mProgressDialog = new ProgressDialog(this);
		mProgressDialog.setTitle("Getting data");
		mProgressDialog.setMessage("***I'm Loading***");
		mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		mView = new MyView(this);
		setContentView(mView);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
			mView.zoomIn();
			return true;
		} else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
			mView.zoomOut();
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	/**
	 * @author engin
	 * 
	 */
	public class MyView extends View {
		/**
		 * 分别表示：SELECT选择属性 DRAG 拖动 ZOOM 放大缩小
		 */
		private static final int NONE = 0;
		private static final int ZOOM = 1;
		private static final int DRAG = 2;
		private static final int SELECT = 3;

		private ArrayList<Geometry> m_geoms;
		private Paint paint = new Paint();
		// 设置默认模式
		private int m_nMode = DRAG;
		// 触摸标记
		private double m_startX = 0;
		private double m_startY;
		private double m_endX;
		private double m_endY;
		private double m_fOldDist = 1f;

		private double rate;// 全屏时比例
		private double leavel = 1;
		// 相对于原数据坐标的平移位置dx,dy。
		private double dx = 0;
		private double dy = 0;
		int view_w = 300;
		int view_h = 400;

		MyView(Context context) {
			super(context);
			getData();
		}

		Path path = new Path();

		@Override
		protected void onDraw(Canvas canvas) {
			super.onDraw(canvas);
			Log.w("绘图", "开始绘图");
			view_w = getWidth();
			view_h = getHeight();

			paint.setColor(Color.BLUE);
			paint.setStyle(Style.FILL);
			canvas.drawText("Hello", 10, 50, paint);
			// canvas.drawRect(new Rect(0, 0, view_w, view_h), paint);
			if (null == m_geoms) {
				return;
			}
			DrawGeomtry(canvas);
			Log.w("绘图", "绘图结束");
		}

		@Override
		public boolean onTouchEvent(MotionEvent event) {
			switch (event.getAction() & MotionEvent.ACTION_MASK) {

			case MotionEvent.ACTION_DOWN:
				// 触摸获取位置查询
				m_endX = m_startX = event.getX();
				m_endY = m_startY = event.getY();
				query(m_startX, m_startY);
				break;
			case MotionEvent.ACTION_UP:
				m_endX = m_startX = event.getX();
				m_endY = m_startY = event.getY();
				break;
			case MotionEvent.ACTION_POINTER_1_DOWN:
				// 计算两点的距离，大于10f才认为是两根手指。
				m_fOldDist = spacing(event);
				if (m_fOldDist > 10f) {
					m_nMode = ZOOM;
				}
				break;
			case MotionEvent.ACTION_POINTER_1_UP:
				// 中途其中一根手指离开屏幕，退出缩放模式。
				// 同时将开始标记点记为当前。
				m_nMode = DRAG;
				m_endX = m_startX = event.getX();
				m_endY = m_startY = event.getY();
				break;
			case MotionEvent.ACTION_MOVE:
				m_endX = event.getX();
				m_endY = event.getY();
				if (m_nMode == ZOOM) {
					double a_fNewDist = spacing(event);
					if (a_fNewDist - m_fOldDist > 14
							|| a_fNewDist - m_fOldDist < -14) {
						zoom(a_fNewDist - m_fOldDist);
						m_fOldDist = a_fNewDist;
					}
				} else if (m_nMode == DRAG) {
					if (m_endX - m_startX > 10 || m_endX - m_startX < -10
							|| m_endY - m_startY > 10
							|| m_endY - m_startY < -10) {
						drag();
						m_endX = m_startX = event.getX();
						m_endY = m_startY = event.getY();
						postInvalidate();
					}
				}
				break;
			}
			return true;
		}

		private void ValidateDragDistance() {
			// 不能超出地图范围。
			double width = mGeomEnvelope.getWidth();
			double height = mGeomEnvelope.getHeight();
			if (dx > width) {
				dx = width;
			} else if (dx < -width) {
				dx = -width;
			}
			if (dy > height) {
				dy = height;
			} else if (dy < -height) {
				dy = -height;
			}
		}

		/**
		 * if the leavel is the minimal value ,don't drag it anymore.
		 * 
		 * @return Whether the leavel should change
		 */
		private boolean ValidateLeavel() {
			double min_leavel = rate / 2;
			if (leavel < min_leavel) {
				leavel = min_leavel;
				return false;
			}
			return true;
		}

		private void ExecuedZoom(double zoomRate) {
			double originx, originy, destx, desty;
			originx = antTransData(view_w / 2, 0);
			originy = antTransData(view_h / 2, 1);
			leavel += zoomRate * leavel;
			if (!ValidateLeavel())
				return;
			destx = antTransData(view_w / 2, 0);
			desty = antTransData(view_h / 2, 1);
			dx += destx - originx;
			dy += desty - originy;
			ValidateDragDistance();
			postInvalidate();
		}

		private void zoom(double dis) {
			double zoomRate = dis
					/ Math.sqrt(view_w * view_w + view_h * view_h);
			ExecuedZoom(zoomRate);
		}

		public void zoomIn() {
			ExecuedZoom(0.2);
		}

		public void zoomOut() {
			ExecuedZoom(-0.2);
		}

		private void drag() {
			double tmp_dx = 0;// 直接赋值给dx会影响dy的判断
			if (m_endX - m_startX > 10 || m_endX - m_startX < -10) {
				tmp_dx = antTransData(m_endX, 0) - antTransData(m_startX, 0);
			}
			if (m_endY - m_startY > 10 || m_endY - m_startY < -10) {
				dy += (antTransData(m_endY, 1) - antTransData(m_startY, 1));
			}
			dx += tmp_dx;
			ValidateDragDistance();
		}

		private void query(double x, double y) {

		}

		/**
		 * 将数据坐标转换成屏幕坐标。
		 * 
		 * @param f
		 *            坐标值
		 * @param i
		 *            0表示x，1表示y;
		 * @return
		 */
		private double transData(double f, double i) {
			double result = 0;
			if (i == 0) {
				double minx;
				minx = mGeomEnvelope.getMinX();
				result = (f + dx - minx) * leavel;
			} else {
				double miny;
				miny = mGeomEnvelope.getMinY();
				result = (view_h - (f + dy - miny) * leavel);
			}
			return result;
		}

		/**
		 * 将屏幕坐标转换成数据坐标。
		 * 
		 * @param f
		 *            坐标值
		 * @param i
		 *            0表示x，1表示y;
		 * @return
		 */
		private double antTransData(double f, double i) {
			double result = 0;
			if (i == 0) {
				double minx;
				minx = mGeomEnvelope.getMinX();
				result = (f / leavel + minx - dx);
			} else {
				double miny;
				miny = mGeomEnvelope.getMinY();
				result = ((view_h - f) / leavel + miny - dy);
			}
			return result;
		}

		// 计算触摸两个点的距离。
		private double spacing(MotionEvent event) {
			double x = event.getX(0) - event.getX(1);
			double y = event.getY(0) - event.getY(1);
			return Math.sqrt(x * x + y * y);
		}

		private void DrawGeomtry(Canvas canvas) {
			int geoms_len = m_geoms.size();
			for (int i = 0; i < geoms_len; i++) {
				Geometry geometry = m_geoms.get(i);
				String type = geometry.getGeometryType();

				if (type.equals("MultiPoint")) {
					DrawPoints(canvas, geometry);
				} else if (type.equals("Point")) {
					DrawPoints(canvas, geometry);
				} else if (type.equals("MultiLineString")) {
					DrawLineStrings(canvas, geometry);
				} else if (type.equals("LineString")) {
					DrawLineStrings(canvas, geometry);
				} else if (type.equals("MultiPolygon")) {
					DrawPolygons(canvas, geometry);
				} else if (type.equals("Polygon")) {
					DrawPolygons(canvas, geometry);
				}
			}
		}

		private void DrawPoints(Canvas canvas, Geometry geometry) {
			String type = geometry.getGeometryType();

			if (type.equals("MultiPoint")) {
				int len = geometry.getNumGeometries();
				for (int i = 0; i < len; i++) {
					Geometry subGeometry = geometry.getGeometryN(i);
					DrawPoints(canvas, subGeometry);
				}
			} else if (type.equals("Point")) {
				paint.setColor(Color.RED);
				paint.setStyle(Style.FILL);
				Point pt = (Point) geometry;
				canvas.drawCircle((float) transData(pt.getX(), 0),
						(float) transData(pt.getY(), 1), 10, paint);
			}

		}

		/**
		 * @param canvas
		 * @param mulLines
		 * 
		 */
		private void DrawLineStrings(Canvas canvas, Geometry geometry) {
			// double distanceTolerance = 5/rate;
			// if (distanceTolerance > 0.001) {
			// DouglasPeuckerSimplifier lineSimplifier = new
			// DouglasPeuckerSimplifier(geometry);
			// lineSimplifier.setDistanceTolerance(distanceTolerance);
			// lineSimplifier.setEnsureValid(false);
			// geometry = lineSimplifier.getResultGeometry();
			// }
			String type = geometry.getGeometryType();
			if (type.equals("MultiLineString")) {
				int len = geometry.getNumGeometries();
				for (int i = 0; i < len; i++) {
					Geometry subGeometry = geometry.getGeometryN(i);
					DrawLineStrings(canvas, subGeometry);
				}
			} else if (type.equals("LineString")) {
				paint.setColor(Color.GREEN);
				LineString lineString = (LineString) geometry;
				int pointLen = lineString.getNumPoints();
				for (int j = 0; j < pointLen - 1; j++) {
					Point ptStart = lineString.getPointN(j);
					Point ptStop = lineString.getPointN(j + 1);
					canvas.drawLine((float) transData(ptStart.getX(), 0),
							(float) transData(ptStart.getY(), 1),
							(float) transData(ptStop.getX(), 0),
							(float) transData(ptStop.getY(), 1), paint);
					ptStart = null;
					ptStop = null;
				}
			}
			geometry = null;
		}

		/**
		 * @param canvas
		 * @param mulPolygon
		 * 
		 */
		private void DrawPolygons(Canvas canvas, Geometry geometry) {
			String type = geometry.getGeometryType();

			if (type.equals("Polygon")) {
				Polygon pg = (Polygon) geometry;
				int innerRines = pg.getNumInteriorRing();
				for (int j = 0; j < innerRines; j++) {
					DrawPolygon(canvas, pg.getInteriorRingN(j), true);
				}
				LineString outterRine = pg.getExteriorRing();
				DrawPolygon(canvas, outterRine, false);
			} else if (type.equals("MultiPolygon")) {
				int pglen = geometry.getNumGeometries();
				for (int i = 0; i < pglen; i++) {
					Geometry subGeometry = geometry.getGeometryN(i);
					DrawPolygons(canvas, subGeometry);
				}
			}
		}

		private void DrawPolygon(Canvas canvas, LineString polygon,
				Boolean isInerRing) {
			if (isInerRing) {
				paint.setColor(Color.GRAY);
				paint.setAlpha(0);
			} else {
				paint.setColor(Color.BLUE);
			}
			paint.setStyle(Style.FILL);
			path.reset();
			int len = polygon.getNumPoints();
			// no data
			if (len < 1) {
				return;
			}
			Point pt0 = polygon.getPointN(0);
			path.moveTo((float) transData(pt0.getX(), 0),
					(float) transData(pt0.getY(), 1));
			for (int i = 1; i < len; i++) {
				Point pt = polygon.getPointN(i);
				path.lineTo((float) transData(pt.getX(), 0),
						(float) transData(pt.getY(), 1));
			}
			path.close();
			canvas.drawPath(path, paint);
		}

		Handler handlerGetData = new Handler() {
			@Override
			public void handleMessage(Message msg) {
				switch (msg.what) {
				case MessageType.SEND_LENGTH:
					mProgressDialog.setMax(((Integer) (msg.obj)).intValue());
					mProgressDialog.show();
					break;
				case MessageType.SEND_ENVELOPE:
					double[] env = (double[]) msg.obj;
					double geom_width,
					geom_height;
					geom_width = env[2] - env[0];
					geom_height = env[3] - env[1];
					// 只有一个点的图层，原比例无缩放。
					if (geom_height < 1e-8 && geom_width < 1e-8) {
						rate = 1;
						mGeomEnvelope.init(view_w, 0, view_h, 0);
					} else {
						double rate1 = view_h / view_w;
						// 屏幕比外接矩形窄
						if (rate1 > geom_height / geom_width) {
							rate = view_w / geom_width;
							double d_height = (view_h / rate - geom_height) / 2;
							mGeomEnvelope.init(env[2], env[0], env[3]
									+ d_height, env[1] - d_height);
						} else {
							rate = view_h / geom_height;
							double d_with = (view_w / rate - geom_width) / 2;
							mGeomEnvelope.init(env[2] + d_with,
									env[0] - d_with, env[3], env[1]);
						}
					}
					leavel = rate;
					break;
				case MessageType.SEND_PROGRESS:
					mProgressDialog.setProgress(((Integer) (msg.obj))
							.intValue());
					break;
				case MessageType.SEND_GEOMETRIES:
					if (null != m_geoms) {
						m_geoms.clear();
					}
					m_geoms = (ArrayList<Geometry>) msg.obj;
					if (null == m_geoms) {
						Log.w("绘图", "未取得数据……");
						mProgressDialog.dismiss();
						return;
					}
					mProgressDialog.dismiss();
					postInvalidate();
					break;
				default:
					break;
				}
			}
		};

		private void getData() {
			QueryDataThread qdThread = new QueryDataThread(mDatabase,
					handlerGetData);
			qdThread.start();
			qdThread = null;
		}
	}
}
