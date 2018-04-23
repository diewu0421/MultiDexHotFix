package com.epro.zlw.test;


import android.content.Context;
import android.widget.Toast;

public class MyTestClass {
	public  void testFix(Context context){
		int i = 10;
		int a = 1;
		Toast.makeText(context, "shit:"+i/a, Toast.LENGTH_SHORT).show();
	}
}
