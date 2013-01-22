package com.spatialite;

import java.util.ArrayList;

import jsqlite.Exception;
import jsqlite.Stmt;
import android.os.Message;
import android.util.Log;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryCollection;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKBReader;

public class QueryDataThread  extends Thread{
	
	private final jsqlite.Database mDatabase;
	private Message mMsg;
	public QueryDataThread(jsqlite.Database database,Message msg) {
		mDatabase = database;
		mMsg=msg;
	}
	@Override
	public void run()
	{
		try {
			// Create query
			//String query = "SELECT name, AsBinary(ST_Transform(geometry,4326)) from Towns where peoples > 350000";
			//String query = "SELECT name, AsBinary(Simplify(ST_Transform(geometry,4326),0.01)) from HighWays";
			String query = "SELECT name, AsBinary(ST_Transform(geometry,4326)) from Regions";
			Stmt stmt = mDatabase.prepare(query);

			ArrayList<Geometry> geoms = new ArrayList<Geometry>();
			String type="";
			//get the fist geometry type as the who geometry type;
			if (stmt.step()) {
				try {
					Geometry ge= new WKBReader().read(stmt.column_bytes(1));
					type =ge.getGeometryType();
					geoms.add(ge);
				} catch (ParseException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			Log.v("查询", "开始查询时间……");
			while(stmt.step()) {
				// Create JTS geometry from binary representation
				// returned from database
				try {
					Geometry ge= new WKBReader().read(stmt.column_bytes(1));
					type =ge.getGeometryType();
					geoms.add(new WKBReader().read(stmt.column_bytes(1)));
				} catch (ParseException e) {
					Log.e("ERR!!!", e.getMessage());
				}
			}
			Log.v("查询", "结束查询，开始转换……");
			GeometryFactory geomFactory = new GeometryFactory();
			GeometryCollection geomCollection=null;
			if (type.equals("Point")) {
				Point[] arrGeom = (Point[])geoms.toArray(new Point[geoms.size()]);//new Geometry[geoms.size()];			
				geomCollection= geomFactory.createMultiPoint(arrGeom);
			} else if (type.equals("LineString")) {
				LineString[] arrGeom= (LineString[])geoms.toArray(new LineString[geoms.size()]);
				geomCollection= geomFactory.createMultiLineString(arrGeom);
			} else if (type.equals("Polygon")) {
				Polygon[] arrGeom= (Polygon[])geoms.toArray(new Polygon[geoms.size()]);
				geomCollection= geomFactory.createMultiPolygon(arrGeom);
			} else if (type.equals("MultiPolygon")) {
			}
			stmt.close();
			mMsg.obj = geomCollection;
			mMsg.what=1;
			mMsg.sendToTarget();
		} catch (Exception e) {
			Log.e("ERR!!!", e.getMessage());
		}
	}
}
