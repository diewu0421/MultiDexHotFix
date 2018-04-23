package com.epro.zlw;

import com.epro.zlw.util.FixDexUtils;

import android.app.Application;
import android.content.Context;
import android.support.multidex.MultiDex;

public class MyApplication extends Application{
	@Override
	public void onCreate() {
		// TODO Auto-generated method stub
		super.onCreate();
	}
	@Override
	protected void attachBaseContext(Context base) {
		// TODO Auto-generated method stub
		MultiDex.install(base);
		FixDexUtils.INSTANCE.loadFixedDex(base);
		super.attachBaseContext(base);

	}
}
