package com.spatialite;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import jsqlite.Callback;
import jsqlite.Exception;
import jsqlite.Stmt;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

public class TestActivity  extends Activity {

	private static final String TAG = TestActivity.class.getName();
	private Context context;
	private ArrayList<HashMap<String, Object>> listItem = new ArrayList<HashMap<String, Object>>();
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		context=this;
		setContentView(R.layout.main);
	}
	private void bindList(){
		ListView list = (ListView) findViewById(R.id.listView1);    
		SimpleAdapter mSchedule = new SimpleAdapter(context,
				listItem,
				R.layout.listview_item,
				new String[] {"ItemName", "ItemText"},
				new int[] {R.id.ItemName,R.id.ItemText});  
		//ListAdapter adapter= new ListAdapter();
		list.setAdapter(mSchedule);
	}
	public void onShowUI(View v) {
		Intent intent = new Intent(TestActivity.this,MapCanvas.class);
		startActivityForResult(intent,0);
	}
	public void onShowTestCanvas(View v){
		//Intent intent = new Intent(TestActivity.this,TestCanvas.class);
		Intent intent = new Intent(TestActivity.this,Touch.class);
		startActivityForResult(intent,0);
	}
	public void onShowMultitouch(View v){
		Intent intent = new Intent(TestActivity.this,MultitouchVisible.class);
		startActivityForResult(intent,0);
	}
	public void onClick(View v) {
		if(v.getId() == R.id.button1){
			getData();
			bindList();
		}
	}
	private void getData(){
		try {
			Class.forName("SQLite.JDBCDriver").newInstance();
			jsqlite.Database db = new jsqlite.Database();

	        // 检测/创建数据库的文件夹  
	        File dir = new File("/mnt/sdcard/");  
	        if (!dir.exists()) {  
	            dir.mkdir();  
	        }  
	        // 如果文件夹已经存在了  
	        else {  
	            // 检查文件是否存在  
	            dir = new File("/mnt/sdcard/", "test-2.3.sqlite");  
	            if (!dir.exists()){
	            	Log.v(TAG,"不存在地理数据库！");
	            	Toast.makeText(getApplicationContext(), "不存在数据库",
	            		     Toast.LENGTH_SHORT).show();
	                return; 
	            } 
	        }  
			//db.open(Environment.getExternalStorageDirectory() + "/download/test-2.3.sqlite",jsqlite.Constants.SQLITE_OPEN_READONLY);
	        Toast.makeText(getApplicationContext(), "正在加载数据库……",
				     Toast.LENGTH_SHORT).show();
			db.open("/mnt/sdcard/test-2.3.sqlite",jsqlite.Constants.SQLITE_OPEN_READONLY);
			listItem.clear();
			Callback cb = new Callback() {
				@Override
				public void columns(String[] coldata) {
					Log.v(TAG, "Columns: " + Arrays.toString(coldata));
					
				}

				@Override
				public void types(String[] types) {
					Log.v(TAG, "Types: " + Arrays.toString(types));
				}

				@Override
				public boolean newrow(String[] rowdata) {
					Log.v(TAG, "Row: " + Arrays.toString(rowdata));
					HashMap<String, Object> map = new HashMap<String, Object>();  
			        map.put("ItemText", rowdata[2]);
			        map.put("ItemName", rowdata[0]);
			        listItem.add(map);  
					// Careful (from parent javadoc):
					// "If true is returned the running SQLite query is aborted."
					return false;
				}
			};
			
			String query = "SELECT name, peoples, AsText(Geometry) from Towns where peoples > 350000";
			Stmt st = db.prepare(query);
			
			//db.exec("select Distance(PointFromText('point(-77.35368 39.04106)', 4326), PointFromText('point(-77.35581 39.01725)', 4326));", cb);
			db.exec("SELECT name, peoples, AsText(Geometry), GeometryType(Geometry), NumPoints(Geometry), SRID(Geometry), IsValid(Geometry) from Towns where peoples > 350000;", cb);
			//db.exec("SELECT Distance( Transform(MakePoint(4.430174797, 51.01047063, 4326), 32631), Transform(MakePoint(4.43001276, 51.01041585, 4326),32631));", cb);
			
			
			/*
			Class.forName("SQLite.JDBCDriver").newInstance();
			Connection conn = DriverManager.getConnection("jdbc:sqlite:/mnt/sdcard/download/test-2.3.sqlite");
			
			String query = "SELECT name, peoples, ST_GeometryType(Geometry) from Towns where peoples > 350000";
			try
		    {
		      Statement st = conn.createStatement();
		      ResultSet rs = st.executeQuery(query);
		      while (rs.next())
		      {
		    	  Log.v(TAG, "Name: " + rs.getString("name"));
		      }
		    }
		    catch (SQLException ex)
		    {
		    	ex.printStackTrace();
		    }
		    */

		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		/*} catch (SQLException e) {
			e.printStackTrace();*/
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}