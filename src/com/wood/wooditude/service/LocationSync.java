package com.wood.wooditude.service;

import org.json.JSONObject;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.wood.wooditude.Consts;

import android.app.IntentService;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.location.Location;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 * TODO: Customize class - update intent actions, extra parameters and static
 * helper methods.
 */
public class LocationSync extends IntentService implements
		GooglePlayServicesClient.ConnectionCallbacks,
		com.google.android.gms.location.LocationListener,
		OnConnectionFailedListener {

	private LocationClient locationClient;
	private LocationRequest locationRequester;
	private final IBinder sBinder = new LocalBinder();
	/* todo provider getter */
	public JSONObject locations = null;

	public class LocalBinder extends Binder {
		public LocationSync getService() {
			// Return this instance of LocalService so clients can call public
			// methods
			return LocationSync.this;
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return sBinder;
	}

	/** method for clients */
	public LatLng getLatLng() {
		if (locationClient.isConnected()) {
			Location loc = locationClient.getLastLocation(); 
			LatLng latLong = new LatLng(loc.getLatitude(),loc.getLongitude());
			return latLong;
		}
		return null;
	}

	@Override
	public void onCreate() {
		SharedPreferences preferences = PreferenceManager
				.getDefaultSharedPreferences(this);

		String syncInterval = preferences.getString("syncinterval", null);
		if (syncInterval != null)
			Consts.UPDATE_INTERVAL = Integer.parseInt(preferences.getString(
					"syncinterval", null));

		preferences
				.registerOnSharedPreferenceChangeListener(new OnSharedPreferenceChangeListener() {

					@Override
					public void onSharedPreferenceChanged(
							SharedPreferences sharedPreferences, String key) {
						if (key.equals("syncinterval")) {
							Consts.UPDATE_INTERVAL = sharedPreferences.getInt(
									"syncinterval", Consts.UPDATE_INTERVAL);
							locationRequester.setInterval(Consts.UPDATE_INTERVAL);
						}
					}
				});

		locationRequester = LocationRequest.create();
		locationRequester.setInterval(Consts.UPDATE_INTERVAL);
		locationRequester.setPriority(LocationRequest.PRIORITY_NO_POWER); // _BALANCED_POWER_ACCURACY);

		locationClient = new LocationClient(this, this, this);
		locationClient.connect();
	}

	@Override
	public void onConnected(Bundle connectionHint) {
		locationClient.requestLocationUpdates(locationRequester, this);
	}

	public LocationSync() {
		super("LocationSync");
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.i("locService", "starting on command");

		//new HttpTransfer(this).execute();
		return START_STICKY;
	}

	/**
	 * Handle action Foo in the provided background thread with the provided
	 * parameters.
	 */
	private void handleActionDownload() {
		// TODO: Handle action Foo
		throw new UnsupportedOperationException("Not yet implemented");
	}

	public void gotLocations (JSONObject locs) {
		locations = locs;
		
		Log.i(Consts.TAG, "the sync task was done");
	}
	/**
	 * Handle action Upload in the provided background thread with the provided
	 * parameters.
	 */
	private void handleActionUpload(String latLong) {
		// TODO: Handle action Upload
		Log.i("UPLOAD", latLong);
		new HttpTransfer(this).execute();
	}

	@Override
	public void onDestroy() {
		locationClient.disconnect();
		Log.i("locSync", "being destoryed");
	}

	@Override
	public void onLocationChanged(Location location) {
		/*
		 * currentLocation = location; LatLng latLong = new
		 * LatLng(currentLocation.getLatitude(),
		 * currentLocation.getLongitude()); mapMarkerMe = new
		 * MarkerOptions().position(latLong); mMap.clear();
		 * mMap.addMarker(mapMarkerMe);
		 * mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLong, 8));
		 */
		Log.i("LocSync", "location updated");
		String sLocation = Double.toString(location.getLatitude())+","+Double.toString(location.getLongitude());
		
		new HttpTransfer(this).execute(sLocation);
	}

	@Override
	public void onConnectionFailed(ConnectionResult result) {
		/* really don't care but have to implement method */
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		// TODO Auto-generated method stub
		if (intent != null) {
			final String action = intent.getAction();
			if (Consts.SYNC_ACTION_DOWNLOAD.equals(action)) {
				handleActionDownload();
			} else if (Consts.SYNC_ACTION_UPLOAD.equals(action)) {
				final String longLat = intent
						.getStringExtra(Consts.EXTRA_DATA_LATLONG);
				handleActionUpload(longLat);
			}
		}

	}

	@Override
	public void onDisconnected() {
		// TODO Auto-generated method stub

	}
}
