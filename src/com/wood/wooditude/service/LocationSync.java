package com.wood.wooditude.service;

import org.json.JSONObject;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.location.Location;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.maps.model.LatLng;
import com.wood.wooditude.Consts;


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

		String syncInterval = preferences.getString(Consts.PREF_SYNCINTERVAL,
				null);
		if (syncInterval != null)
			Consts.UPDATE_INTERVAL = Integer.parseInt(syncInterval);

		final LocationSync context = this;
		prefChangeListener = new OnSharedPreferenceChangeListener() {

			@Override
			public void onSharedPreferenceChanged(
					SharedPreferences sharedPreferences, String key) {
				if (key.equals("syncinterval")) {
					String syncInterval = sharedPreferences.getString(
							Consts.PREF_SYNCINTERVAL, null);
					if (syncInterval != null) {
						Consts.UPDATE_INTERVAL = Integer.parseInt(syncInterval);
						Consts.log("onChanged Setting update interval to:"
								+ Consts.UPDATE_INTERVAL);
						context.locationRequester = LocationRequest.create();
						context.locationRequester
								.setInterval(Consts.UPDATE_INTERVAL);
						context.locationRequester
								.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
						context.locationClient.requestLocationUpdates(
								locationRequester, context);
					}
				}
			}
		};
		preferences
				.registerOnSharedPreferenceChangeListener(prefChangeListener);

		locationRequester = LocationRequest.create();
		Consts.log("onCreate sSetting update interval to:"
				+ Consts.UPDATE_INTERVAL);
		locationRequester.setInterval(Consts.UPDATE_INTERVAL);
		locationRequester
				.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);

		locationClient = new LocationClient(this, this, this);
		locationClient.connect();

		/* Re-use the boot-receiver that starts the service on reboot to start the service in the case
		 * where the service might have died
		 */
		AlarmManager am = (AlarmManager) (this.getSystemService(Context.ALARM_SERVICE));
		Intent intent = new Intent(Consts.SERVICE_INTENT_FILTER).setClass(this, BootReceiver.class);
		PendingIntent activateServiceIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
		am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME, 0, AlarmManager.INTERVAL_HOUR , activateServiceIntent);
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
		return START_STICKY;
	}

	public void httpTransferFinished(JSONObject locationResults) {
		locations = locationResults;
		Consts.log("httpTransferFinished broadcasting...");
		LocalBroadcastManager.getInstance(this).sendBroadcast(
				new Intent(Consts.NOTIFICATION));
	}

	@Override
	public void onDestroy() {
		Consts.log("Service destroyed");
		locationClient.disconnect();
	}

	@Override
	public void onLocationChanged(Location location) {
		/*
		 * If we haven't moved more than 10m and the UI is not running
		 * bound/using the service then just return.
		 */
		Consts.log("clientBound ==" + clientBound);
		if (previousLocation != null
				&& location.distanceTo(previousLocation) < 10
				&& clientBound == false) {
			Consts.log("Not updating because not moved significantly and UI is disconnected");
			return;
		}
		Consts.log("Location changed running HttpTransfer");
		String sLocation = Double.toString(location.getLatitude()) + ","
				+ Double.toString(location.getLongitude());
		new HttpTransfer(this).execute(sLocation);
		previousLocation = location;
	}

	public boolean getClientBound () {
		return this.clientBound;
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
