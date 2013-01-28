package com.spatialite;

import java.util.ArrayList;

import jsqlite.Exception;
import jsqlite.Stmt;
import com.spatialite.MessageType;

import android.app.ProgressDialog;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKBReader;

public class QueryDataThread extends Thread {

	private final jsqlite.Database mDatabase;
	private Handler mHandler;

	public QueryDataThread(jsqlite.Database database, Handler handler) {
		mDatabase = database;
		mHandler = handler;
	}

	@Override
	public void run() {
		try {
			Message mMsg = mHandler.obtainMessage();
			// checking the Vector Layers
			String getExtendSQL = "SELECT row_count, ";
			getExtendSQL += "extent_min_x, extent_min_y, ";
			getExtendSQL += "extent_max_x, extent_max_y ";
			getExtendSQL += "FROM layer_statistics ";
			getExtendSQL += "WHERE table_name = 'Polygon'";

			Stmt extStmt = mDatabase.prepare(getExtendSQL);
			getExtendSQL = null;
			if (extStmt.step()) {
				int layer_length = extStmt.column_int(0);
				mMsg.obj = layer_length;
				mMsg.what = MessageType.SEND_LENGTH;
				mMsg.sendToTarget();
				double[] env = new double[4];
				env[0] = extStmt.column_double(1);
				env[1] = extStmt.column_double(2);
				env[2] = extStmt.column_double(3);
				env[3] = extStmt.column_double(4);
				mMsg = mHandler.obtainMessage();
				mMsg.obj = env;
				env = null;
				mMsg.what = MessageType.SEND_ENVELOPE;
				mMsg.sendToTarget();
			}

			// Create query
			String query = "SELECT name, AsBinary(geometry) from Polygon";
			// String query =
			// "SELECT name, AsBinary(geometry) from HighWays limit 100";
			// String query = "SELECT name, AsBinary(geometry) from Regions";
			Stmt stmt = mDatabase.prepare(query);
			query = null;
			ArrayList<Geometry> geoms = new ArrayList<Geometry>();
			// get the fist geometry type as the who geometry type;
			Log.w("查询", "开始查询时间……");
			int i = 0;
			WKBReader wkbReader = new WKBReader();
			while (stmt.step()) {
				// Create JTS geometry from binary representation
				// returned from database
				try {
					geoms.add(wkbReader.read(stmt.column_bytes(1)));
					mMsg = mHandler.obtainMessage();
					mMsg.obj = i++;
					mMsg.what = MessageType.SEND_PROGRESS;
					mMsg.sendToTarget();
				} catch (ParseException e) {
					Log.e("ERR!!!", e.getMessage());
				}
			}
			wkbReader = null;
			Log.w("查询", "结束查询，开始转换……");
			stmt.close();
			stmt = null;
			mMsg = mHandler.obtainMessage();
			mMsg.obj = geoms;
			mMsg.what = MessageType.SEND_GEOMETRIES;
			geoms = null;
			mMsg.sendToTarget();
		} catch (Exception e) {
			Log.e("ERR!!!", e.getMessage());
		}
	}
}
