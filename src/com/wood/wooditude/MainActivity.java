package com.wood.wooditude;

import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.wood.wooditude.R;
import com.wood.wooditude.service.LocationSync;
import com.wood.wooditude.service.LocationSync.LocalBinder;

public class MainActivity extends FragmentActivity {
	protected static final TimeUnit SECONDS = null;
	public GoogleMap mMap;
	private LocationSync locationSyncService;
	private boolean sBound = false;
	private Runnable mapUpdater;
	private Runnable initialMapUpdater;
	private Handler updateHandler = new Handler();
	private float[] markerColours;

	/** Defines callbacks for service binding, passed to bindService() */
	private ServiceConnection mConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			// We've bound to LocalService, cast the IBinder and get
			// LocalService instance
			LocalBinder binder = (LocalBinder) service;
			locationSyncService = binder.getService();
			sBound = true;
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			sBound = false;
		}
	};

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.option_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.settings:
			Intent settings = new Intent(this, SettingsActivity.class);
			startActivity(settings);
		case R.id.manual_sync:
			updateHandler.postDelayed(initialMapUpdater, 4000);
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		setUpMapIfNeeded();
		setUpMapUpdaterIfNeeded();

		markerColours = new float[10];
		markerColours[0] = BitmapDescriptorFactory.HUE_AZURE;
		markerColours[1] = BitmapDescriptorFactory.HUE_BLUE;
		markerColours[2] = BitmapDescriptorFactory.HUE_CYAN;
		markerColours[3] = BitmapDescriptorFactory.HUE_GREEN;
		markerColours[4] = BitmapDescriptorFactory.HUE_MAGENTA;
		markerColours[5] = BitmapDescriptorFactory.HUE_ORANGE;
		markerColours[6] = BitmapDescriptorFactory.HUE_RED;
		markerColours[7] = BitmapDescriptorFactory.HUE_ROSE;
		markerColours[8] = BitmapDescriptorFactory.HUE_VIOLET;
		markerColours[9] = BitmapDescriptorFactory.HUE_YELLOW;
	}

	private void updateMap() {
		if (!sBound)
			return;
		String user;
		LatLng latLong = locationSyncService.getLatLng();
		SharedPreferences preferences = PreferenceManager
				.getDefaultSharedPreferences(this);
		user = preferences.getString("username", "");

		mMap.clear();

		JSONObject locations = locationSyncService.locations;
		if (locations == null)
			return;

		try {
			JSONArray array = locations.getJSONArray("locations");
			for (int i = 0; i < array.length(); i++) {
				MarkerOptions marker;
				JSONObject locationEntry = array.getJSONObject(i);
				
				if (locationEntry.isNull("username"))
					continue;
				
				String name = locationEntry.getString("username");
				/* if user is me don't add it from the server source */
				if (name.equals(user))
					continue;
				
				
				if (locationEntry.isNull("locationd") || locationEntry.isNull("date"))
					continue;
				
				String date = locationEntry.getString("date");
				String strLatLong[] = locationEntry.getString("locationd").split(",");
				Log.i(Consts.TAG, name);

				LatLng latLng = new LatLng(Double.parseDouble(strLatLong[0]),
						Double.parseDouble(strLatLong[1]));

				marker = new MarkerOptions()
						.position(latLng)
						.title(name)
						.snippet(date)
						.icon(BitmapDescriptorFactory
								.defaultMarker(markerColours[i % 10]));
				

				mMap.addMarker(marker);
			}
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		Log.i(Consts.TAG, latLong.toString());
		MarkerOptions mapMarkerMe = new MarkerOptions().position(latLong)
				.title("It's You!");
		mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLong, 7));
		mMap.addMarker(mapMarkerMe);
	}

	private void setUpMapUpdaterIfNeeded() {

		if (initialMapUpdater == null) {
			initialMapUpdater = new Runnable() {
				@Override
				public void run() {
					Log.i(Consts.TAG, "running initial map updater");
					android.os.Process
							.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
					updateMap();
				}
			};
		}
		initialMapUpdater.run();
		
		if (mapUpdater == null) {

			updateHandler.postDelayed(initialMapUpdater, 4000);

			mapUpdater = new Runnable() {
				@Override
				public void run() {
					Log.i(Consts.TAG, "running map updater");
					android.os.Process
							.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
					updateMap();
					updateHandler.postDelayed(this, Consts.UPDATE_INTERVAL);
				}
			};
		}
		
		updateHandler.postDelayed(mapUpdater, Consts.UPDATE_INTERVAL);
	}

	@Override
	protected void onStart() {
		super.onStart();
		/* starts the intent for syncing if it's not already started */
		Intent intent = new Intent(MainActivity.this, LocationSync.class);
		startService(intent);
		bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
	}

	@Override
	protected void onStop() {
		super.onStop();

		if (sBound) {
			unbindService(mConnection);
			sBound = false;
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		setUpMapIfNeeded();
		setUpMapUpdaterIfNeeded();
	}

	private void setUpMapIfNeeded() {
		if (mMap != null) {
			return;
		}
		mMap = ((SupportMapFragment) getSupportFragmentManager()
				.findFragmentById(R.id.map)).getMap();
		if (mMap == null) {
			return;
		}
		mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
		mMap.setMyLocationEnabled(true);
	}
}