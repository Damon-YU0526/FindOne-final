package com.way.util;

import android.util.Log;

/**
 * Log utils
 * 
 * @author way
 * 
 */
public class L {
	public static boolean isDebug = true;
	private static final String TAG = "way";

	public static void i(String msg) {
		if (isDebug)
			Log.i(TAG, msg);
	}

	public static void d(String msg) {
		if (isDebug)
			Log.d(TAG, msg);
	}

	public static void e(String msg) {
		if (isDebug)
			Log.e(TAG, msg);
	}

	public static void v(String msg) {
		if (isDebug)
			Log.v(TAG, msg);
	}
	//The following is to pass in the class name and print log
	public static void i(Class<?> _class,String msg){
		if (isDebug)
			Log.i(_class.getName(), msg);
	}
	public static void d(Class<?> _class,String msg){
		if (isDebug)
			Log.i(_class.getName(), msg);
	}
	public static void e(Class<?> _class,String msg){
		if (isDebug)
			Log.i(_class.getName(), msg);
	}
	public static void v(Class<?> _class,String msg){
		if (isDebug)
			Log.i(_class.getName(), msg);
	}
	// Here is the function passed in the custom tag
	public static void i(String tag, String msg) {
		if (isDebug)
			Log.i(tag, msg);
	}

	public static void d(String tag, String msg) {
		if (isDebug)
			Log.i(tag, msg);
	}

	public static void e(String tag, String msg) {
		if (isDebug)
			Log.i(tag, msg);
	}

	public static void v(String tag, String msg) {
		if (isDebug)
			Log.i(tag, msg);
	}
}
