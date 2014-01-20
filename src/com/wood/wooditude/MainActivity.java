package com.wood.wooditude;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.DrawerLayout;
import android.text.format.DateUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.wood.wooditude.service.LocationSync;
import com.wood.wooditude.service.LocationSync.LocalBinder;

public class MainActivity extends FragmentActivity {
	protected static final TimeUnit SECONDS = null;
	public GoogleMap mMap;
	private LocationSync locationSyncService;
	private boolean sBound = false;
	private float[] markerColours;
	private JSONObject locations;
	private List<Person> people;
	private DrawerLayout mDrawerLayout;
	private ListView mDrawerList;
	private ActionBarDrawerToggle mDrawerToggle;
	private MenuItem manualSyncItem;
	private ArrayAdapter<Person> mDrawerListAdapter;

	/** Defines callbacks for service binding, passed to bindService() */
	private ServiceConnection mConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			// We've bound to LocalService, cast the IBinder and get
			// LocalService instance
			LocalBinder binder = (LocalBinder) service;
			locationSyncService = binder.getService();
			locationSyncService.hello();
			sBound = true;
			runSpinner(true);
			locationSyncService.manualUpdate();
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			sBound = false;
		}
	};

	private BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			Consts.log("Got request from service to update map");
			updateMap();
			runSpinner(false);
		}
	};

	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		// Sync the toggle state after onRestoreInstanceState has occurred.
		mDrawerToggle.syncState();
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		// Pass any configuration change to the drawer toggls
		mDrawerToggle.onConfigurationChanged(newConfig);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.option_menu, menu);

		inflater.inflate(R.menu.actionbar_menu, menu);
		manualSyncItem = menu.findItem(R.id.manual_sync);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (mDrawerToggle.onOptionsItemSelected(item)) {
			return true;
		}
		switch (item.getItemId()) {

		case R.id.settings:
			Intent settings = new Intent(this, SettingsActivity.class);
			startActivity(settings);
			return true;
		case R.id.manual_sync:
			if (sBound) {
				Consts.log("Start manual sync");
				runSpinner (true);
				locationSyncService.manualUpdate();
			} else {
				runSpinner(true);
			}
			return true;
		case R.id.about:
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle (R.string.about_label);
			String version = "";
			try {
				version = this.getPackageManager().getPackageInfo("com.wood.wooditude", 0).versionName;
			} catch (NameNotFoundException e) {
				e.printStackTrace();
			}

			builder.setMessage(getString(R.string.app_name) +" "+ getString (R.string.version_label) +" "+ version);
			builder.show();
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		setUpMapIfNeeded();

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

		mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
		mDrawerList = (ListView) findViewById(R.id.left_drawer);

		mDrawerLayout.setFocusableInTouchMode(true);
		mDrawerLayout.setFocusable(true);

		mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
		mDrawerList = (ListView) findViewById(R.id.left_drawer);

		mDrawerList.setOnItemClickListener(new DrawerItemClickListener());

		// enable ActionBar app icon to behave as action to toggle nav drawer
		getActionBar().setDisplayHomeAsUpEnabled(true);

		// ActionBarDrawerToggle ties together the the proper interactions
		// between the sliding drawer and the action bar app icon
		mDrawerToggle = new ActionBarDrawerToggle(this, /* host Activity */
		mDrawerLayout, /* DrawerLayout object */
		R.drawable.ic_drawer, /* nav drawer image to replace 'Up' caret */
		R.string.app_name, /* "open drawer" description for accessibility */
		R.string.app_name /* "close drawer" description for accessibility */
		) {
			public void onDrawerClosed(View view) {
				invalidateOptionsMenu(); // creates call to
											// onPrepareOptionsMenu()
			}

			public void onDrawerOpened(View drawerView) {
				invalidateOptionsMenu(); // creates call to
											// onPrepareOptionsMenu()
			}
		};
		mDrawerLayout.setDrawerListener(mDrawerToggle);
		people = new ArrayList<Person>();
		mDrawerListAdapter = new PersonListAdapater(this, R.id.draweritem, people);
		mDrawerList.setAdapter(mDrawerListAdapter);
	}

	private void runSpinner (boolean start) {
		if (manualSyncItem != null) {
			if (start)
				manualSyncItem.setActionView(R.layout.actionbar_spinner);
			else
				manualSyncItem.setActionView(null);
		}
	}

	private void zoomToPerson(Person person) {
		LatLng latLng = person.getLocation();
		if (latLng != null) {
			mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 13));
		}
	}

	private LatLng latLngFromString (String stringLatLng) {
		LatLng latLng;
		if (!stringLatLng.contains(","))
			return new LatLng (0,0);

		String strLatLong[] = stringLatLng.split(",");
		latLng = new LatLng(Double.parseDouble(strLatLong[0]),
				Double.parseDouble(strLatLong[1]));
		return latLng;
	}

	private class DrawerItemClickListener implements
			ListView.OnItemClickListener {
		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position,
				long id) {
			zoomToPerson(people.get(position));
			mDrawerLayout.closeDrawers();
			mDrawerList.clearChoices();
			mDrawerList.requestLayout();
		}
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

		if (latLong != null) {
			MarkerOptions mapMarkerMe = new MarkerOptions().position(latLong)
					.title("It's You!");
			mMap.addMarker(mapMarkerMe);
		}

		locations = locationSyncService.getLocations();
		if (locations == null)
			return;

		people.clear();

		try {
			JSONArray array = locations.getJSONArray(Consts.LOCATIONS_FIELD);

			for (int i = 0; i < array.length(); i++) {
				MarkerOptions marker;
				LatLng latLng;
				String name,date,dateDiff, prettyName;
				SimpleDateFormat dateFormat;
				JSONObject locationEntry = array.getJSONObject(i);

				if (locationEntry.isNull(Consts.USERNAME_FIELD))
					continue;

				name = locationEntry.getString(Consts.USERNAME_FIELD);
				/*
				 * Copy user names into a standard string array for drawer list
				 * also make names with capitals
				 */
				prettyName = name.substring(0, 1).toUpperCase(
								Locale.getDefault())
								+ name.substring(1);
				/* if user is me don't add it from the server source */
				if (name.equals(user))
					continue;

				if (locationEntry.isNull(Consts.LOCATION_FIELD)
						|| locationEntry.isNull(Consts.DATE_FIELD))
					continue;

				date = locationEntry.getString(Consts.DATE_FIELD);
				dateFormat = new SimpleDateFormat ("dd MMM yyyy hh:mm:ss", Locale.getDefault());
				try {
					Date personDate = dateFormat.parse(date);
					dateDiff = (String) DateUtils.getRelativeTimeSpanString(personDate.getTime(), new Date().getTime (), 1000);
				} catch (ParseException e) {
					e.printStackTrace();
					dateDiff = date;
				}

				if (!locationEntry.getString(Consts.LOCATION_FIELD).contains(
						","))
					continue;

				latLng = latLngFromString(locationEntry.getString(Consts.LOCATION_FIELD));

				mDrawerListAdapter.add(new Person (prettyName, latLng, dateDiff));

				marker = new MarkerOptions()
						.position(latLng)
						.title(prettyName)
						.snippet(dateDiff)
						.icon(BitmapDescriptorFactory
						.defaultMarker(markerColours[i % 10]));

				mMap.addMarker(marker);
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
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
			locationSyncService.byebye();
			unbindService(mConnection);
			sBound = false;
		}
		LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
	}

	@Override
	protected void onPause() {
		if (sBound) {
			locationSyncService.byebye();
			unbindService(mConnection);
			sBound = false;
		}
		super.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();
		setUpMapIfNeeded();
		LocalBroadcastManager.getInstance(this).registerReceiver(receiver,
				new IntentFilter(Consts.NOTIFICATION));
		updateMap();
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
		mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(53.383611,
				-1.466944), 6));
		mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
		mMap.setMyLocationEnabled(true);
	}
}