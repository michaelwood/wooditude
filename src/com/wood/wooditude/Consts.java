package com.wood.wooditude;

import android.util.Log;

public class Consts {
	public static final String SYNC_ACTION_UPLOAD = "com.wood.wooditude.action.UPLOAD";
	public static final String SYNC_ACTION_DOWNLOAD = "com.wood.wooditude.action.DOWNLOAD";
	public static final String EXTRA_DATA_LATLONG = "com.wood.wooditude.extra.LATLONG";
	public static final String TAG = "WOOD";
	/* 5 minutes */
	public static int UPDATE_INTERVAL = 5 * 60 * 1000;

	public static final String USERNAME_FIELD = "username";
	public static final String LOCATION_FIELD = "locationd";
	public static final String DATE_FIELD = "date";
	public static final String LOCATIONS_FIELD = "locations";

	public static final String PREF_SYNCINTERVAL = "syncinterval";

	public static final String NOTIFICATION = "com.wood.wooditude.service.notify";
	public static final String SERVICE_INTENT_FILTER = "com.wood.wooditude.service";

	private static final boolean debugEnabled = false;

	private Consts () {} 
	public static void log (String in) { if (debugEnabled) { Log.i(Consts.TAG, in); } }
}