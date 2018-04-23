package com.epro.zlw;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

import com.epro.zlw.util.FixDexUtils;
import com.epro.zlw.util.MyConstants;
import com.epro.zlw.test.MyTestClass;
import com.epro.zlw.test.MyTestClass2;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
	private TextView tv;
	@TargetApi(Build.VERSION_CODES.M)
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		tv = findViewById(R.id.tv);
		if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
			requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
		}
	}

	public void test(View v){
		MyTestClass myTestClass = new MyTestClass();
		myTestClass.testFix(this);

		new MyTestClass2().test(tv);
	}
	
	public void fix(View v){
		fixBug();
	}

	private void fixBug() {
		//目录：/data/user/packageName/odex
		File fileDir = getDir(MyConstants.DEX_DIR,Context.MODE_PRIVATE);
		//往该目录下面放置我们修复好的dex文件。
		String name = "classes2.dex";
		String filePath = fileDir.getAbsolutePath()+File.separator+name;
		File file= new File(filePath);
		if(file.exists()){
			file.delete();
		}
		//搬家：把下载好的在SD卡里面的修复了的classes2.dex搬到应用目录filePath
		InputStream is = null;
		FileOutputStream os = null;
		try {
			is = new FileInputStream(Environment.getExternalStorageDirectory().getAbsolutePath()+File.separator+name);
			os = new FileOutputStream(filePath);
			int len = 0;
			byte[] buffer = new byte[1024];
			while ((len=is.read(buffer))!=-1){
				os.write(buffer,0,len);
			}

			File f = new File(filePath);
			if(f.exists()){
				Toast.makeText(this	,"dex 重写成功", Toast.LENGTH_SHORT).show();
			}
			//热修复
			FixDexUtils.loadFixedDex(this);

		} catch (Exception e) {
			e.printStackTrace();
		}


	}
}
