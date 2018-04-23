---
title: Dex分包进行热修复 
tags: Dex,热修复
grammar_cjkRuby: true
---

- 项目需要继承谷歌`MultiDex`框架
	- 在项目的`build.gradle`中添加仓库
	``` gradle?linenums 
	buildscript {
		ext.kotlin_version = '1.2.40'
		repositories {
			jcenter()
			maven {
				url 'https://maven.google.com/'
				name 'Google'
			}
		}
		dependencies {
			classpath 'com.android.tools.build:gradle:3.1.1'
			classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version"
		}
	}

	allprojects {
		repositories {
			jcenter()
			maven {
				url 'https://maven.google.com/'
				name 'Google'
			}
		}
	}
	```

	- 然后在module的`build.gradle`中添加依赖。
		``` gradle?linenums
		dependencies {
			implementation 'com.android.support:multidex:1.0.3'
			implementation "org.jetbrains.kotlin:kotlin-stdlib:$kotlin_version"
		}
		
		//同时在android标签下添加如下代码：
		buildTypes {
        release {
            multiDexKeepFile file('dex.keep')
            def myFile = file('dex.keep')
            println("isFileExists:"+myFile.exists())
            println "dex keep"
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.txt'
        }
    }
		```
- 建立`Application的子类`,然后重写如下的方法。
``` java?linenums
@Override
	protected void attachBaseContext(Context base) {
		// TODO Auto-generated method stub
		MultiDex.install(base);
		FixDexUtils.INSTANCE.loadFixedDex(base);
		super.attachBaseContext(base);

	}
```

- 先将从服务器下载好的classes.dex文件放置在手机外置的sd卡的根目录下，然后再将该`dex`文件拷贝至`/data/user/packageName/odex`目录下。
	> 其中的oder的文件夹名字可以任意取。经过测试发现，系统会在`oder`的名字前面拼接`app`,变成`app_odex`.
	``` java?linenums
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
	````
	其中的FixDexUtils的代码如下：
	``` kotlin?linenums
			package com.epro.zlw.util

	import java.io.File
	import java.lang.reflect.Array
	import java.util.HashSet


	import android.content.Context
	import dalvik.system.DexClassLoader
	import dalvik.system.PathClassLoader

	object FixDexUtils {
		private val loadedDex = HashSet<File>()

		init {
			loadedDex.clear()
		}

		@JvmStatic
		fun loadFixedDex(context: Context?) {
			if (context == //String optimizedDirectory,
					null) {
				return
			}
			//遍历所有的修复的dex /data/user/0/com.dex.main/app_odex/classes2.dex
			val fileDir = context.getDir(MyConstants.DEX_DIR, Context.MODE_PRIVATE)
			val listFiles = fileDir.listFiles()
			for (file in listFiles) {
				if (file.name.startsWith("classes") && file.name.endsWith(".dex")) {
					loadedDex.add(file)//存入集合
				}
			}
			//dex合并之前的dex
			doDexInject(context, fileDir, loadedDex)
		}

		@Throws(Exception::class)
		private fun setField(obj: Any, cl: Class<*>, field: String, value: Any) {
			val localField = cl.getDeclaredField(field)
			localField.isAccessible = true
			localField.set(obj, value)
		}

		private fun doDexInject(appContext: Context, filesDir: File, loadedDex: HashSet<File>) {
			val optimizeDir = filesDir.absolutePath + File.separator + "opt_dex"
			val fopt = File(optimizeDir)
			if (!fopt.exists()) {
				fopt.mkdirs()
			}
			//1.加载应用程序的dex
			try {
				val pathLoader = appContext.classLoader as PathClassLoader

				for (dex in loadedDex) {
					//2.加载指定的修复的dex文件。
					val classLoader = DexClassLoader(
							dex.absolutePath, //String dexPath,
							fopt.absolutePath, null, //String libraryPath,
							pathLoader//ClassLoader parent
					)
					//3.合并
					//得到BaseDexClassLoader 中的pathList字段
					val dexObj = getPathList(classLoader)
					//得到应用程序本来的pathLIst字段
					val pathObj = getPathList(pathLoader)

					val mDexElementsList = getDexElements(dexObj)
					val pathDexElementsList = getDexElements(pathObj)
					//合并完成
					val dexElements = combineArray(mDexElementsList, pathDexElementsList)
					//重写给PathList里面的lement[] dexElements;赋值
					val pathList = getPathList(pathLoader)
					setField(pathList, pathList.javaClass, "dexElements", dexElements)
				}
			} catch (e: Exception) {
				e.printStackTrace()
			}

		}

		@Throws(NoSuchFieldException::class, IllegalArgumentException::class, IllegalAccessException::class)
		private fun getField(obj: Any, cl: Class<*>, field: String): Any {
			val localField = cl.getDeclaredField(field)
			localField.isAccessible = true
			return localField.get(obj)
		}

		@Throws(Exception::class)
		private fun getPathList(baseDexClassLoader: Any): Any {
			return getField(baseDexClassLoader, Class.forName("dalvik.system.BaseDexClassLoader"), "pathList")
		}

		@Throws(Exception::class)
		private fun getDexElements(obj: Any): Any {
			return getField(obj, obj.javaClass, "dexElements")
		}

		/**
		 * 两个数组合并
		 * @param arrayLhs  包含修复的类
		 * @param arrayRhs  包含有问题的类
		 * @return
		 */
		private fun combineArray(arrayLhs: Any, arrayRhs: Any): Any {
			val localClass = arrayLhs.javaClass.componentType
			val i = Array.getLength(arrayLhs)
			val j = i + Array.getLength(arrayRhs)
			val result = Array.newInstance(localClass, j)
			for (k in 0 until j) {
				if (k < i) {
					Array.set(result, k, Array.get(arrayLhs, k))
				} else {
					Array.set(result, k, Array.get(arrayRhs, k - i))
				}
			}
			return result
		}
	}

	```
- 编写我们要测试的类
	``` java?linenums
	public class MyTestClass {
		public  void testFix(Context context){
			int i = 10;
			int a = 0;
			Toast.makeText(context, "shit:"+i/a, Toast.LENGTH_SHORT).show();
		}
	}
	```
	> 注意上面的a的值为0，很显然现在会出现崩溃.
	之后运行一遍，在如下的路径下找到我们要修复的class文件
	`app\build\intermediates\classes\debug\com\epro\zlw\test`
	接着我们将`a = 0` 改为`a = 1`,然后再次运行一遍，再到上面的目录下找到这个`class文件`
	
- 把上面找到的`MyTestClass.class文件（注意要包括包名的全路径）`拷贝至桌面，如图所示：
![enter description here](./images/1524499326124.jpg)

	然后将`Android SDK中的build-tools路径设置为全局路径`，接下来打开`cmd`运行如下命令:
	`dx --dex --output=C:\Users\ZLW\Desktop\dex\Classes2.dex C:\Users\ZLW\Desktop\dex`
	> 其中 `--output=`后面的路径为生成的Classes2.dex要放置的目录，最后面的参数为要指定在哪个目录下找class文件

- 可以看到在与`com`同一级目录下生成了`Classes2.dex`文件，我们将其拷贝至SD卡根目录下。

- 接着运行我们的老项目（就是我们之前有问题的项目），点击修复
![enter description here](./images/1524499601492.jpg)
	
	接着再点击test，发现bug被修复了。
	

