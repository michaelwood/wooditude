package com.wood.wooditude.service;

import java.util.prefs.PreferenceChangeListener;

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
import android.os.Parcel;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
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
	private JSONObject locations = null;
	private Location previousLocation = null;
	private boolean clientBound = false;
	private OnSharedPreferenceChangeListener prefChangeListener;

	public class LocalBinder extends Binder {
		public LocationSync getService() {
			// Return this instance of LocalService so clients can call public
			// methods
			return LocationSync.this;
		}
	}

	/* methods for clients */
	public LatLng getLatLng() {
		if (locationClient != null && locationClient.isConnected()) {
			Location loc = locationClient.getLastLocation();
			if (loc != null) {
				LatLng latLong = new LatLng(loc.getLatitude(),
						loc.getLongitude());
				return latLong;
			}
		}
		return null;
	}

	public JSONObject getLocations() {
		return locations;
	}

	/* we know we only have one client ever so no need to keep count */
	public void byebye() {
		clientBound = false;
	}

	public void hello() {
		clientBound = true;
	}

	public void manualUpdate() {
		Location location = null;
		String sLocation = null;

		/* Get last from the client */
		if (locationClient != null && locationClient.isConnected())
			location = locationClient.getLastLocation();

		/* That failed so try our previousLocation */
		if (location == null && previousLocation != null)
			location = previousLocation;

		if (location != null)
			sLocation = Double.toString(location.getLatitude()) + ","
					+ Double.toString(location.getLongitude());

		/* We might not have got a location but that's OK
		 * passing a null value will just result in a download only
		 */
		new HttpTransfer(this).execute(sLocation);
	}

	@Override
	public boolean onUnbind(Intent intent) {
		clientBound = false;
		Consts.log("loc sync unBound  to activit");
		return super.onUnbind(intent);
	}

	@Override
	public void onRebind(Intent intent) {
		clientBound = true;
		Consts.log("loc sync reBound  to activit");
		super.onRebind(intent);
	}

	@Override
	public IBinder onBind(Intent intent) {
		clientBound = true;
		Consts.log("loc sync Bound  to activit");
		return sBinder;
	}

	@Override
	public void onCreate() {
		SharedPreferences preferences = PreferenceManager
				.getDefaultSharedPreferences(this);

		String syncInterval = preferences.getString(Consts.PREF_SYNCINTERVAL, null);
		if (syncInterval != null)
			Consts.UPDATE_INTERVAL = Integer.parseInt(syncInterval);

		prefChangeListener = new OnSharedPreferenceChangeListener() {

			@Override
			public void onSharedPreferenceChanged(
					SharedPreferences sharedPreferences, String key) {
				Log.i (Consts.TAG, "pref changed "+key);
				if (key.equals("syncinterval")) {
					String syncInterval = sharedPreferences.getString(Consts.PREF_SYNCINTERVAL, null);
					if (syncInterval != null) {
						Consts.UPDATE_INTERVAL = Integer.parseInt(syncInterval);
					Log.i(Consts.TAG, "setting syncinterval");
					locationRequester.setInterval(Consts.UPDATE_INTERVAL);}
				}
			}
		};
		preferences.registerOnSharedPreferenceChangeListener(prefChangeListener);

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
		clientBound = true;

		// new HttpTransfer(this).execute();
		return START_STICKY;
	}

	public void httpTransferFinished (JSONObject locationResults) {
		Log.i(Consts.TAG, "httpTransfer task was done");
		locations = locationResults;
		LocalBroadcastManager.getInstance(this).sendBroadcast (new Intent (Consts.NOTIFICATION));
	}

	@Override
	public void onDestroy() {
		locationClient.disconnect();
		Log.i("locSync", "being destroyed");
	}

	@Override
	public void onLocationChanged(Location location) {
		Log.i("LocSync", "location updated");
		Log.i(Consts.TAG, "current interval is "+locationRequester.getInterval());

		/* If we haven't moved more than 10m and the UI is not running
		 * bound/using the service then just return.
		 */
		if (previousLocation != null &&
			location.distanceTo(previousLocation) < 10 &&
			clientBound == false) {
			Log.i(Consts.TAG, "not updating because not moved significantly and UI is disconnected");
			return;
		}
		String sLocation = Double.toString(location.getLatitude()) + ","
				+ Double.toString(location.getLongitude());
		new HttpTransfer(this).execute(sLocation);
		previousLocation = location;
	}

	@Override
	public void onConnectionFailed(ConnectionResult result) {
		/* really don't care but have to implement method */
	}

	@Override
	protected void onHandleIntent(Intent intent) {

	}

	@Override
	public void onDisconnected() {
		// TODO Auto-generated method stub

	}
}
