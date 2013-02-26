package com.spatialite;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jsqlite.Exception;
import jsqlite.Stmt;
//引入JTS
import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.index.strtree.STRtree;

import android.R.bool;
import android.R.integer;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Paint.Style;
import android.graphics.drawable.AnimationDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;

public class MapCanvas extends Activity {
	private String cacheFileName = "geoinker_db_dir.gik";
	private final jsqlite.Database mDatabase;
	private Envelope mGeomEnvelope;
	private MyView mView;
	private ProgressDialog mProgressDialog;
	private Intent mIntent;

	private boolean mIsDynamicDrawing = false;

	public MapCanvas() {
		mGeomEnvelope = new Envelope();
		mDatabase = new jsqlite.Database();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mIntent = this.getIntent();
		Bundle dataBundle = mIntent.getExtras();
		String dataSource = dataBundle.getString("datasource");
		// open the datasource
		try {
			mDatabase.open(dataSource, jsqlite.Constants.SQLITE_OPEN_READONLY);
			// if open successed,save the directory for next launch
			saveCurrentDir(dataSource);

		} catch (Exception e) {
			throwErrToMain(e.getMessage());
		}
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
		showTabelSelect(getGeomtryTable());
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

	private void saveCurrentDir(String content) {
		try {
			FileOutputStream outputStream = openFileOutput(cacheFileName,
					Activity.MODE_PRIVATE);
			outputStream.write(content.getBytes());
			outputStream.flush();
			outputStream.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void showTabelSelect(ArrayList<String> tableName) {
		// not a spatialite database
		if (null == tableName || 0 == tableName.size()) {
			throwErrToMain("not a spatialite database");
			return;
		}
		final String[] tableNameStrings = (String[]) tableName
				.toArray(new String[tableName.size()]);
		final boolean[] tableSelect = new boolean[tableName.size()];
		tableName = null;
		AlertDialog.Builder selectTableBuilder = new Builder(this);
		selectTableBuilder.setTitle("Select the layer(s)");
		OnMultiChoiceClickListener multiLIstener = new OnMultiChoiceClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which,
					boolean isChecked) {
				tableSelect[which] = isChecked;
			}
		};
		selectTableBuilder.setMultiChoiceItems(tableNameStrings, null,
				multiLIstener);
		OnClickListener loadListener = new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				ArrayList<String> selectedTable = new ArrayList<String>();
				for (int i = 0; i < tableSelect.length; i++) {
					if (tableSelect[i]) {
						selectedTable.add(tableNameStrings[i]);
					}
				}
				if (0 == selectedTable.size()) {
					throwErrToMain("You have not select a single layer!");
					return;
				}
				mView.getData(selectedTable);
				dialog.dismiss();
			}
		};
		selectTableBuilder.setPositiveButton("OK", loadListener);
		OnClickListener cancleListener = new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				MapCanvas.this.finish();
			}
		};
		selectTableBuilder.setNegativeButton("Cancle", cancleListener);
		selectTableBuilder.create().show();
	}

	private ArrayList<String> getGeomtryTable() {
		try {
			ArrayList<String> tableName = new ArrayList<String>();
			String query = "SELECT DISTINCT f_table_name from geometry_columns";
			Stmt stmt = mDatabase.prepare(query);
			query = null;
			while (stmt.step()) {
				tableName.add(stmt.column_string(0));
			}
			stmt.close();
			return tableName;
		} catch (Exception e) {
			throwErrToMain(e.getMessage());
			return null;
		}
	}

	/**
	 * send the errors messages back to main activity and close this.
	 * 
	 * @param errmsg
	 */
	public void throwErrToMain(String errmsg) {
		Bundle errBundle = new Bundle();
		errBundle.putString("err", errmsg);
		mIntent.putExtras(errBundle);
		this.setResult(RESULT_OK, mIntent);
		this.finish();
	}

	public void setIsDynamicDrawing(boolean isDynamic) {
		mIsDynamicDrawing = isDynamic;
	}

	public class MyView extends View {
		/**
		 * 分别表示：SELECT选择属性 DRAG 拖动 ZOOM 放大缩小
		 */
		private static final int NONE = 0;
		private static final int ZOOM = 1;
		private static final int DRAG = 2;
		private static final int SELECT = 3;

		// store all the geometries to be shown and the spatial index of each
		// geometry.
		private STRtree m_geomsIndex;
		private Paint paint = new Paint();
		// indentify the mode while operating the map view.
		private int m_nMode = DRAG;
		// touch point to indentify move(drag)
		private double m_startX = 0;
		private double m_startY = 0;
		private double m_endX = 0;
		private double m_endY = 0;
		private double m_fOldDist = 1f;

		// the scale of fullscreen 全屏时比例
		private double rate;

		/**
		 * the scale of current screen. leavel = 1,full screen; leavel >
		 * 1,enlarged map leavel < 1,lessened map
		 */
		private double leavel = 1;
		//
		/**
		 * the distance of the origin map data(in database) while zooming and
		 * panning on the screen 相对于原数据坐标的平移位置dx,dy。
		 */
		private double dx = 0;
		private double dy = 0;

		// the width and height of the view
		int view_w;
		int view_h;

		// the picture of the canvas after redraw all the geometries on the
		// screen.
		private Bitmap m_CacheBitmap = null;
		boolean m_isDraging = false;

		MyView(Context context) {
			super(context);
		}

		Path path = new Path();

		@Override
		protected void onDraw(Canvas canvas) {
			view_w = getWidth();
			view_h = getHeight();
			if (!mIsDynamicDrawing && m_isDraging) {
				Log.w("Bitmap", "drag START");
				if (null == m_CacheBitmap) {
					Log.w("Bitmap", "drag bitmap is null");
					return;
				}
				if (m_CacheBitmap.isRecycled()) {
					Log.w("Bitmap", "drag bitmap had been recycled");
					return;
				}
				int offsetX = (int) (m_endX - m_startX);
				int offsetY = (int) (m_endY - m_startY);
				canvas.drawBitmap(m_CacheBitmap, offsetX, offsetY, null);
				Log.w("Bitmap", "drag END");
				return;
			}

			paint.setColor(Color.BLUE);
			paint.setStyle(Style.FILL);
			canvas.drawText("GeoInker", 10, 50, paint);
			if (null == m_CacheBitmap) {
				Log.w("Bitmap", "redraw bitmap is null");
				return;
			}
			Log.w("Bitmap", "redraw START");
			canvas.drawBitmap(m_CacheBitmap, 0, 0, null);
			Log.w("Bitmap", "redraw END");
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
				if (m_isDraging) {
					m_isDraging = false;
					drag2();
					DrawGeometryToBitmap();// redraw the geometry in this view
				}
				m_endX = m_startX = 0;
				m_endY = m_startY = 0;
				break;
			case MotionEvent.ACTION_POINTER_1_DOWN:
				/**
				 * change to zoom mode only when the moved distance of two
				 * fingers is big 10f 计算两点的距离，大于10f才认为是两根手指。
				 */
				m_fOldDist = spacing(event);
				if (m_fOldDist > 10f) {
					m_nMode = ZOOM;
				}
				break;
			case MotionEvent.ACTION_POINTER_1_UP:
				/**
				 * change to drag mode when a finger leave the screen and reset
				 * the touch point 中途其中一根手指离开屏幕，退出缩放模式.同时将开始标记点记为当前.
				 */
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
					if (mIsDynamicDrawing) {
						if (m_endX - m_startX > 10 || m_endX - m_startX < -10
								|| m_endY - m_startY > 10
								|| m_endY - m_startY < -10) {
							drag();
							m_endX = m_startX = event.getX();
							m_endY = m_startY = event.getY();
						}
					} else {
						if (!m_isDraging) {
							m_isDraging = true;
						}
					}
					postInvalidate();
				}
				break;
			}
			return true;
		}

		/*
		 * validate whether the distance of panning is out of the boundary of the mapview
		 */
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
		 * if the leavel is the minimal value ,don't zoom it anymore.
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
			DrawGeometryToBitmap();
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

		/**
		 * drag with dynamic drawing;
		 */
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

		/**
		 * drag with picture move
		 */
		private void drag2() {
			double tmp_dx = 0;// 直接赋值给dx会影响dy的判断
			tmp_dx = antTransData(m_endX, 0) - antTransData(m_startX, 0);
			dy += (antTransData(m_endY, 1) - antTransData(m_startY, 1));
			dx += tmp_dx;
			ValidateDragDistance();
		}

		private void query(double x, double y) {

		}

		/**
		 * change the coordinate from datasource to screen.
		 * 将数据坐标转换成屏幕坐标。
		 * 
		 * @param f
		 *            the value(x or y,decided by the param i) of the coordinate 坐标值
		 * @param i
		 *            0,trans x;1,trans y  .    0表示x，1表示y;
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
		 *  change the coordinate from screen to datasource.
		 * 将屏幕坐标转换成数据坐标。
		 * 
		 * @param f
		 *             the value(x or y,decided by the param i) of the coordinate 坐标值
		 * @param i
		 *            0,trans x;1,trans y  .    0表示x，1表示y;
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

		private double spacing(MotionEvent event) {
			double x = event.getX(0) - event.getX(1);
			double y = event.getY(0) - event.getY(1);
			return Math.sqrt(x * x + y * y);
		}

		/**
		 * query the geometries by an envelope while redraw them into screen
		 */
		private Envelope searchEnv = new Envelope();
		private void setCurrentEnvelop(double x1, double x2, double y1,
				double y2) {
			double xx1, xx2, yy1, yy2;
			xx1 = antTransData(x1, 0);
			xx2 = antTransData(x2, 0);
			yy1 = antTransData(y1, 1);
			yy2 = antTransData(y2, 1);
			searchEnv.init(xx1, xx2, yy1, yy2);
		}
		private void setCurrentEnvelop() {
			double xx1, xx2, yy1, yy2;
			xx1 = antTransData(0, 0);
			xx2 = antTransData(view_w, 0);
			yy1 = antTransData(view_h, 1);
			yy2 = antTransData(0, 1);
			searchEnv.init(xx1, xx2, yy1, yy2);
		}

		/**
		 * Draw the Geometrise to a bitmap call this function only when the
		 * change of the canvas is done
		 */
		private void DrawGeometryToBitmap() {
			m_CacheBitmap = Bitmap.createBitmap(view_w, view_h,
					Bitmap.Config.ARGB_8888);
			Canvas cn = new Canvas(m_CacheBitmap);
			if (null == m_geomsIndex) {
				return;
			}
			DrawGeomtry(cn);
			draw(cn);
			cn.save(Canvas.ALL_SAVE_FLAG);
			cn.restore();
			postInvalidate();
		}

		private void DrawGeomtry(Canvas canvas) {
			setCurrentEnvelop();
			List<Geometry> geoms = m_geomsIndex.query(searchEnv);
			int geoms_len = geoms.size();
			for (int i = 0; i < geoms_len; i++) {
				Geometry geometry = geoms.get(i);
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
						(float) transData(pt.getY(), 1), 2, paint);
			}

		}

		/**
		 * @param canvas
		 * @param mulLines
		 * 
		 */
		private void DrawLineStrings(Canvas canvas, Geometry geometry) {
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
			paint.setColor(Color.DKGRAY);
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
				Point ptStart = polygon.getPointN(i - 1);
				Point pt = polygon.getPointN(i);
				path.lineTo((float) transData(pt.getX(), 0),
						(float) transData(pt.getY(), 1));
				canvas.drawLine((float) transData(ptStart.getX(), 0),
						(float) transData(ptStart.getY(), 1),
						(float) transData(pt.getX(), 0),
						(float) transData(pt.getY(), 1), paint);
			}
			path.close();
			if (isInerRing) {
				paint.setColor(Color.BLACK);
			} else {
				paint.setColor(Color.BLUE);
			}
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
					// if there only one point 只有一个点的图层，原比例无缩放。
					if (geom_height < 1e-8 && geom_width < 1e-8) {
						rate = 1;
						mGeomEnvelope.init(view_w, 0, view_h, 0);
					} else {
						double rate1 = view_h / view_w;
						//the screen is narrower then the envelope of all geometries   屏幕比外接矩形窄
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
					m_geomsIndex = (STRtree) msg.obj;
					if (null == m_geomsIndex) {
						Log.w("database", "no date in the database……");
						mProgressDialog.dismiss();
						return;
					}
					mProgressDialog.dismiss();
					DrawGeometryToBitmap();
					break;
				case MessageType.SEND_ERR:
					MapCanvas.this.throwErrToMain(msg.obj.toString());
					break;
				default:
					break;
				}
			}
		};

		public void getData(ArrayList<String> tableNames) {
			for (int i = 0; i < tableNames.size(); i++) {
				QueryDataThread qdThread = new QueryDataThread(mDatabase,
						handlerGetData);
				qdThread.setTableName(tableNames.get(i));
				qdThread.start();
				qdThread = null;
			}

		}
	}
}
