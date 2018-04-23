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
    //	[12345] [9876]
    //	[9876  12345]

}
