package com.wood.wooditude.service;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Stack;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.location.Location;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.maps.model.LatLng;
import com.wood.wooditude.Consts;
import com.wood.wooditude.MainActivity;


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
	private ArrayList<Geofence> geofences;
	private String username = null;
	private Stack<Integer> notificationsDone;

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
		Consts.log("running saveGeofences..");
		saveGeofences();
		Consts.log("byebye client");
	}

	public void hello() {
		clientBound = true;
	}
	/* we can't pass circles around from the main thread
	 * they don't know where they are and need the main 
	 * thread to be able to look themselves up
	 */
	public void addGeofencePost (double latitude, double longitude, double d, String name) {
		Geofence geofencepost = new Geofence(latitude, longitude, d, name);
		geofences.add(geofencepost);
	}

	public void clearGeofencePosts () {
		geofences.clear();
		saveGeofences();
	}

	public void manualUpdate() {
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

		if (preferences.getBoolean(Consts.PREF_LOCATION_REPORTING, true) == false) {
			new HttpTransfer(this).execute();
			return;
		}

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
		Consts.log("service created");
		geofences = new ArrayList<Geofence>();
		notificationsDone  = new Stack<Integer> ();
		notificationsDone.setSize(10);

		SharedPreferences preferences = PreferenceManager
				.getDefaultSharedPreferences(this);

		String syncInterval = preferences.getString(Consts.PREF_SYNCINTERVAL,
				null);
		if (syncInterval != null)
			Consts.UPDATE_INTERVAL = Integer.parseInt(syncInterval);

		/* If we previously set some geofences then restore them */
		if (preferences.contains(Consts.PREF_GEOFENCES) &&
				geofences.isEmpty()) {
			String geofencesJSON = preferences.getString(Consts.PREF_GEOFENCES, "{}");
			try {
				JSONObject geofefencesStore = new JSONObject(geofencesJSON);
				loadGeofences (geofefencesStore);
			} catch (JSONException e) {	}
		}

		final LocationSync context = this;
		prefChangeListener = new OnSharedPreferenceChangeListener() {

			@Override
			public void onSharedPreferenceChanged(
					SharedPreferences sharedPreferences, String key) {
				if (key.equals(Consts.PREF_SYNCINTERVAL)) {
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
				} else if (key.equals(Consts.USERNAME_FIELD)) {
					username = sharedPreferences.getString(Consts.USERNAME_FIELD, "None");
				} else if (key.equals(Consts.PREF_LOCATION_REPORTING)) {
					Boolean locationReporting = sharedPreferences.getBoolean(Consts.PREF_LOCATION_REPORTING, true);
					if (locationReporting == false && locationClient != null)
						locationClient.disconnect();
					else
						locationClient.connect();
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

		Boolean locationReporting = preferences.getBoolean(Consts.PREF_LOCATION_REPORTING, true);
		locationClient = new LocationClient(this, this, this);
		if (locationReporting == true)
			locationClient.connect();

		/* Re-use the boot-receiver that starts the service on reboot to start the service in the case
		 * where the service might have died
		 */
		AlarmManager am = (AlarmManager) (this.getSystemService(Context.ALARM_SERVICE));
		Intent intent = new Intent(Consts.SERVICE_INTENT_FILTER).setClass(this, BootReceiver.class);
		PendingIntent activateServiceIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
		am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME, 0, AlarmManager.INTERVAL_HOUR , activateServiceIntent);

		username = preferences.getString(Consts.USERNAME_FIELD, "None");
	}

	@Override
	public void onConnected(Bundle connectionHint) {
		locationClient.requestLocationUpdates(locationRequester, this);
	}

	public LocationSync() {
		super("LocationSync");
	}

	public ArrayList<Geofence> getGeofences () {
		return geofences;
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
		
		if (!geofences.isEmpty()) {
			maybeGeofenceEntered (locations);
		}
	}

	/* Serialise and save geofences */
	private void saveGeofences () {
		Editor preferencesEdit = PreferenceManager
				.getDefaultSharedPreferences(this).edit();
		if(geofences.isEmpty()) {
			preferencesEdit.remove("geofences");
			preferencesEdit.apply();
			return;
		}

		JSONObject jObj = new JSONObject();
		JSONArray jArr = new JSONArray();

		try {
			jObj.put("geofences", jArr);
		} catch (JSONException e){}

		for (Geofence geofence: geofences) {
			jArr.put(geofence.toJSON());
		}
		preferencesEdit.putString("geofences", jObj.toString());
		preferencesEdit.apply();
	}


	private void loadGeofences (JSONObject geofencesJSON) {
		try {
			JSONArray geofencesJArr = geofencesJSON.getJSONArray ("geofences");
			for (int i=0; i < geofencesJArr.length(); i++) {
				JSONObject geofenceJObj = geofencesJArr.getJSONObject(i);
				Geofence geofence = new Geofence();
				if (geofence.loadFromJSON(geofenceJObj))
					geofences.add(geofence);
			}
		} catch (JSONException e) {}
	}

	private void maybeGeofenceEntered (JSONObject locations) {
		try {
			JSONArray array = locations.getJSONArray(Consts.LOCATIONS_FIELD);

			for (int i = 0; i < array.length(); i++) {
				LatLng latLng;
				String name, prettyName;
				JSONObject locationEntry = array.getJSONObject(i);

				if (!locationEntry.getString(Consts.LOCATION_FIELD).contains(","))
					continue;
				
				if (locationEntry.isNull(Consts.LOCATION_FIELD))
					continue;
				
				if (locationEntry.isNull(Consts.USERNAME_FIELD))
					continue;
				
				name = locationEntry.getString(Consts.USERNAME_FIELD);
				if (name.equals(username))
					continue;


				latLng = Consts.latLngFromString(locationEntry.getString(Consts.LOCATION_FIELD));
				Location personLocation = new Location ("wooditude");
				personLocation.setLatitude(latLng.latitude);
				personLocation.setLongitude(latLng.longitude);

				prettyName = name.substring(0, 1).toUpperCase(
						Locale.getDefault())
						+ name.substring(1);

				for (Geofence geofence: geofences) {
					geofence.location.distanceTo(personLocation);

					float distanceToGeofenceCentre;

					distanceToGeofenceCentre = personLocation.distanceTo(geofence.location); 
					/* Is the person in our 'geofenced' area? */
					if ((distanceToGeofenceCentre = personLocation.distanceTo(geofence.location)) <= geofence.radius) {
						String title = prettyName + " is in: "+geofence.name;
						String distanceStr = distanceToKmString(distanceToGeofenceCentre);
						String content = prettyName + " is "+distanceStr+" from the centre.";
						doNotification(title, content, prettyName);
					}
				}

				/* Do we have our personal geofence enabled if so check distance to others */
				SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
				float distanceToMe;
				if (preferences.getBoolean (Consts.PREF_PERSONAL_GEOFENCE, false)) {
					if (previousLocation != null &&
						(distanceToMe = previousLocation.distanceTo(personLocation)) <= 1000) {
						String title = prettyName + " is near you!";
						String distanceStr = distanceToKmString(distanceToMe);
						String content = prettyName + " is "+distanceStr+" from your position.";
						doNotification(title, content, prettyName);
					}
				}
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	private String distanceToKmString (float distance) {
		return String.format("%.2f", distance/1000)+"km";
	}

	private void doNotification (String title, String content, String prettyName) {
		NotificationManager mNotificationManager =
			    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

		int notificationId = title.hashCode();
		Intent intent = new Intent(this, MainActivity.class);
		intent.setData(Uri.parse("person://"+prettyName.toLowerCase()));
		PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_ONE_SHOT); 

		NotificationCompat.Builder mBuilder =
		        new NotificationCompat.Builder(this)
		        .setSmallIcon(android.R.drawable.ic_dialog_map)
		        .setContentTitle(title)
		        .setContentText(content)
		        .setContentIntent(pendingIntent);

		Notification notification = mBuilder.build();

		if (!notificationsDone.contains(notificationId))
			notification.defaults |= Notification.DEFAULT_SOUND;

		notificationsDone.add(notificationId);
		mNotificationManager.notify(notificationId, notification);
	}

	@Override
	public void onDestroy() {
		Consts.log("Service destroyed");
		locationClient.disconnect();
	}


	@Override
	public void onLocationChanged(Location location) {
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		/* It's unlikely we will get a onLocationChanged event if we're disconnected from
		 * the location client. But in case we do get a rouge one, return now.
		 */
		if (preferences.getBoolean(Consts.PREF_LOCATION_REPORTING, true) == false) {
			return;
		}

		/* If we haven't moved more than 10m and the UI is not running
		 * and we don't have any geofences to check then don't bother
		 * updating.
		 */

		Boolean personalGeofence = preferences.getBoolean(Consts.PREF_PERSONAL_GEOFENCE, false);

		if (previousLocation != null
				&& location.distanceTo(previousLocation) < 10
				&& clientBound == false
				&& geofences.isEmpty()
				&& personalGeofence == false) {
			Consts.log("Not updating because not moved significantly"
					+ " and UI is disconnected and I don't have any geofences");
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