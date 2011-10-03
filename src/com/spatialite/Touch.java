package com.spatialite;

import android.app.Activity;  
import android.os.Bundle;  
import android.view.Window;  
import android.view.WindowManager;  
   
public class Touch extends Activity {  
    @Override  
    public void onCreate(Bundle savedInstanceState) {  
        super.onCreate(savedInstanceState);  
        //隐藏标题栏  
        requestWindowFeature(Window.FEATURE_NO_TITLE);  
        //设置成全屏  
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,  
                WindowManager.LayoutParams.FLAG_FULLSCREEN);  
        //设置为上面的MTView  
        setContentView(new MultiTouch(this));  
    }  
} 
