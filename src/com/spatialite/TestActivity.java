package com.spatialite;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

public class TestActivity extends Activity {
	private String cacheFileName = "geoinker_db_dir.gik";
	private String currentDirString = "";
	private File currentFileParent;
	private File[] currentFiles;
	private ListView list;
	Context context;

	private void geoinkerInit() {
		currentFileParent = new File("/mnt/sdcard/");
		if (!currentFileParent.exists()) {
			AlertDialog.Builder exitApp = new Builder(this);
			exitApp.setTitle("No SDCard!");
			exitApp.setMessage("No SDCard are detected!");
			return;
		}
		File dir1 = new File("/mnt/sdcard/GeoInker");
		mkdatadir(dir1);
		File dir2 = new File("/mnt/sdcard/GeoInker/database");
		mkdatadir(dir2);
		File dir3 = new File("/mnt/sdcard/GeoInker/shp");
		mkdatadir(dir3);
		currentFileParent = dir1;
		currentFiles = currentFileParent.listFiles();
		inflateListView(currentFiles);
		currentDirString = readLastDir();
		if (null == currentDirString) {
			Button bt = (Button) findViewById(R.id.loadLast);
			bt.setEnabled(false);
		}

	}

	private void mkdatadir(File dir) {
		if (!dir.exists()) {
			dir.mkdir();
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		context = this;
		setContentView(R.layout.main);

		list = (ListView) findViewById(R.id.listView1);
		// binding the click listener of each file item in the listview
		list.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> adapterView, View view,
					int position, long id) {
				if (0 == position) {
					currentFileParent = currentFileParent.getParentFile();
					if (null == currentFileParent) {
						Toast.makeText(TestActivity.this,
								"This is the root directory!", Toast.LENGTH_LONG)
								.show();
						return;
					}
					currentFiles = currentFileParent.listFiles();
					inflateListView(currentFiles);
					return;
				}
				position = position - 1;
				if (currentFiles[position].isFile()) {
					openDataSource(currentFiles[position].getPath());
					return;
				}

				File[] tem = currentFiles[position].listFiles();
				if (tem == null || tem.length == 0) {

					Toast.makeText(TestActivity.this,
							"No File in this directory", Toast.LENGTH_LONG)
							.show();
				} else {
					currentFileParent = currentFiles[position];
					currentFiles = tem;
					inflateListView(currentFiles);
				}

			}
		});

		geoinkerInit();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		switch (resultCode)
		{
			//结果返回
		case RESULT_OK:
			//获取Bundle的数据
			Bundle bl= data.getExtras();
			String errString=bl.getString("err");
			AlertDialog.Builder errOpenDB = new Builder(context);
			errOpenDB.setTitle("Can't open the data!");
			errOpenDB.setMessage(errString);
			errOpenDB.setPositiveButton("OK", null);
			errOpenDB.create().show();
			break;
		default:
			break;
		}
	}
	
	private void openDataSource(String path){
		Intent intent = new Intent(TestActivity.this, MapCanvas.class);
		Bundle dataSource = new Bundle();
		dataSource.putString("datasource", path);
		intent.putExtras(dataSource);
		startActivityForResult(intent, 0);
	}
	public void onClick(View v) {
		if (v.getId() == R.id.loadLast) {

		}
	}

	/**
	 * @param files
	 *            the currend directory files. flate the list view with the
	 *            currend directory file
	 */
	private void inflateListView(File[] files) {
		List<Map<String, Object>> listItems = new ArrayList<Map<String, Object>>();
		Map<String, Object> listItemUp = new HashMap<String, Object>();
		listItemUp.put("icon", R.drawable.folder);
		listItemUp.put("filename", " ..");
		listItems.add(listItemUp);
		for (int i = 0; i < files.length; i++) {
			Map<String, Object> listItem = new HashMap<String, Object>();
			// choose the icon of file.
			if (files[i].isDirectory()) {
				listItem.put("icon", R.drawable.folder);
			} else {
				listItem.put("icon", R.drawable.file);
			}
			// set the file name into text list
			listItem.put("filename", files[i].getName());
			listItems.add(listItem);
		}
		SimpleAdapter mSchedule = new SimpleAdapter(context, listItems,
				R.layout.listview_item, new String[] { "icon", "filename" },
				new int[] { R.id.fileTypeIcon, R.id.ItemName });
		list.setAdapter(mSchedule);
	}



	private String readLastDir() {
		try {
			FileInputStream inputStream = this.openFileInput(cacheFileName);
			byte[] bytes = new byte[1024];
			ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream();
			while (inputStream.read(bytes) != -1) {
				arrayOutputStream.write(bytes, 0, bytes.length);
			}
			inputStream.close();
			arrayOutputStream.close();
			return new String(arrayOutputStream.toByteArray());

		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return null;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

}