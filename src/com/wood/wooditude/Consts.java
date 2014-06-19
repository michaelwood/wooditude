package com.wood.wooditude;

import com.google.android.gms.maps.model.LatLng;

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
	public static final String PREF_GEOFENCES = "geofences";
	public static final String INTENT_EXTRA_ZOOM_TO_PERSON = "ZoomToPerson";
	public static final String PREF_LOCATION_REPORTING = "locationreporting";
	public static final String PREF_PERSONAL_GEOFENCE = "personalgeofence";

	public static final String NOTIFICATION = "com.wood.wooditude.service.notify";
	public static final String SERVICE_INTENT_FILTER = "com.wood.wooditude.service";

	private static final boolean debugEnabled = false;

	private Consts () {} 
	public static void log (String in) { if (debugEnabled) { Log.i(Consts.TAG, in); } }
	
	public static LatLng latLngFromString (String stringLatLng) {
		LatLng latLng;
		if (!stringLatLng.contains(","))
			return new LatLng (0,0);

		String strLatLong[] = stringLatLng.split(",");
		latLng = new LatLng(Double.parseDouble(strLatLong[0]),
				Double.parseDouble(strLatLong[1]));
		return latLng;
	}
}