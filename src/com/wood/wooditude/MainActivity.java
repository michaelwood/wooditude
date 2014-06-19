package com.wood.wooditude;

import java.io.IOException;
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
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.DrawerLayout;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnMapClickListener;
import com.google.android.gms.maps.GoogleMap.OnMapLongClickListener;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.wood.wooditude.service.Geofence;
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
	private boolean geofenceSetInProgress = false;
	private ArrayList<CircleAndName> allGeofences;
	private ArrayList<CircleAndName> currentAddGeofences;
	MenuItem acceptAddGeofence, cancelAddGeofence;
	private AsyncTask<Void, Void, String> geocoderTask;
	private String zoomToPersonIntent = null;

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

			/* If we have some geofences already we must have already received them
			 * from the service earlier
			 */
			if (!allGeofences.isEmpty())
				return;

			Consts.log("Restoring geofences");
			for (Geofence geofence: locationSyncService.getGeofences()) {
				Circle circle;
				CircleOptions circleOpts;
				circleOpts = makeGeofenceCircleOpts(geofence.location, geofence.radius);
				circle = mMap.addCircle(circleOpts);
				allGeofences.add(new CircleAndName(circle, geofence.name));
			}
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
	
	private void toggleGeofenceMenuItems() {
		acceptAddGeofence.setVisible(!acceptAddGeofence.isVisible());
		cancelAddGeofence.setVisible(!cancelAddGeofence.isVisible());
	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		// Sync the toggle state after onRestoreInstanceState has occurred.
		mDrawerToggle.syncState();
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		// Pass any configuration change to the drawer toggles
		mDrawerToggle.onConfigurationChanged(newConfig);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.option_menu, menu);

		inflater.inflate(R.menu.actionbar_menu, menu);
		manualSyncItem = menu.findItem(R.id.manual_sync);

		acceptAddGeofence = menu.findItem(R.id.geofence_add_done);
		cancelAddGeofence = menu.findItem(R.id.geofence_add_cancel);

		acceptAddGeofence.setOnMenuItemClickListener(new OnMenuItemClickListener() {

			@Override
			public boolean onMenuItemClick(MenuItem item) {
				geofenceSetInProgress = false;
				toggleGeofenceMenuItems();
					if (sBound) {

						for (CircleAndName mCircle: currentAddGeofences) {
							locationSyncService.addGeofencePost(mCircle.circle.getCenter().latitude,
									mCircle.circle.getCenter().longitude, mCircle.circle.getRadius(),
									mCircle.name);
						}

						allGeofences.addAll(currentAddGeofences);
						currentAddGeofences.clear();
					} else {
						connectToService();
						Consts.log("Error tried to add geofence to service but I am not connected to it");
					}
				return false;
			}
		});

		cancelAddGeofence.setOnMenuItemClickListener(new OnMenuItemClickListener() {

			@Override
			public boolean onMenuItemClick(MenuItem item) {
				// FIXME also clear from the service
				for (CircleAndName mCircle: currentAddGeofences) {
					mCircle.circle.remove();
				}
				geofenceSetInProgress = false;
				toggleGeofenceMenuItems();
				return false;
			}
		});

		return true;
	}

	private void clearGeofences () {
		for (CircleAndName mCircle: allGeofences) {
			mCircle.circle.remove();
		}
		allGeofences.clear();
		if (sBound) {
			locationSyncService.clearGeofencePosts();
		}
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
			return true;
		case R.id.add_geofence:
			toggleGeofenceMenuItems();
			geofenceSetInProgress = true;
			return true;
		case R.id.clear_geofences:
			clearGeofences ();
			return true;
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
		allGeofences = new ArrayList<CircleAndName>();
		currentAddGeofences = new ArrayList<CircleAndName>();
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
		Person zoomToPersonRequest = null;
		LatLng latLong = locationSyncService.getLatLng();
		SharedPreferences preferences = PreferenceManager
				.getDefaultSharedPreferences(this);
		user = preferences.getString("username", "");

		locations = locationSyncService.getLocations();
		if (locations == null)
			return;

		/* Remove markers from map (needs to be done in main thread)*/
		for (Person person: people) {
			person.removeMarker();
		}
		/* Clear the list of people we're about to re-populate */
		people.clear();


		if (latLong != null) {
			MarkerOptions mapMarkerMeOpts = new MarkerOptions().position(latLong)
					.title("It's You!");
			Marker meMarker = mMap.addMarker(mapMarkerMeOpts);
			Person me = new Person ("Me", latLong, "", meMarker);
			people.add(me);
		}

		try {
			JSONArray array = locations.getJSONArray(Consts.LOCATIONS_FIELD);

			for (int i = 0; i < array.length(); i++) {
				MarkerOptions markerOpts;
				LatLng latLng;
				String name,date,dateDiff, prettyName;
				SimpleDateFormat dateFormat;
				JSONObject locationEntry = array.getJSONObject(i);
				Marker marker;

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

				latLng = Consts.latLngFromString(locationEntry.getString(Consts.LOCATION_FIELD));

				//mDrawerListAdapter.add(new Person (prettyName, latLng, dateDiff));

				markerOpts = new MarkerOptions()
						.position(latLng)
						.title(prettyName)
						.snippet(dateDiff)
						.icon(BitmapDescriptorFactory
						.defaultMarker(markerColours[i % 10]));

				marker = mMap.addMarker(markerOpts);
				Person person = new Person (prettyName, latLng, dateDiff, marker); 
				people.add(person);

				/* Ages ago we may have had a request to zoom to a person from the intent
				 * which started the main activity. Only now that we have all the persons 
				 * can we honour that request.
				 */
				if (zoomToPersonIntent !=null)
					Consts.log("checking "+zoomToPersonIntent +" against "+name);
				if (zoomToPersonIntent != null && 
					name.equals (zoomToPersonIntent)) {
					zoomToPersonRequest = person;
				}
			}

			/* Notify the adapter that we've changed it's model - "people" */
			mDrawerListAdapter.notifyDataSetChanged();
		} catch (JSONException e) {
			e.printStackTrace();
		}
		if (zoomToPersonRequest != null) {
			zoomToPerson(zoomToPersonRequest);
			zoomToPersonIntent = null;
		}
	}


	private CircleOptions makeGeofenceCircleOpts (LatLng latLng, double radius) {
		CircleOptions circleOpts = new CircleOptions();
		circleOpts.fillColor(Color.argb(100, 0, 0, 0));
		circleOpts.strokeWidth(4);
		circleOpts.strokeColor(Color.rgb(160, 255, 58));
		circleOpts.center(latLng);
		circleOpts.radius(radius);

		return circleOpts;
	}

	private CircleOptions makeGeofenceCircleOpts (Location location, double radius) {
		LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
		return makeGeofenceCircleOpts(latLng, radius);
	}

	private void addGeofence (LatLng latLng) {
		Circle circle;
		CircleOptions circleOpts;
		/* Make the radius inversely proportional to the zoom level i.e. the more
		 * you zoom in the smaller the radius
		 */	
		float radius = (float) (Math.pow(mMap.getCameraPosition().zoom ,-2)*1000000-1000);
		circleOpts = makeGeofenceCircleOpts (latLng, radius);
		circle = mMap.addCircle(circleOpts);
		openAddGeofenceDialog(circle);
	}

	private class CircleAndName {
		public Circle circle;
		public String name;

		public CircleAndName (Circle circle, String name) {
			this.circle = circle;
			this.name = name;
		}
	}

	private AsyncTask<Void, Void, String> geocode (final double lat, final double lng, final EditText editText) {
		final Context context = this;
		AsyncTask<Void, Void, String> task = new AsyncTask<Void, Void, String> () {

			@Override
			protected void onPostExecute(String result) {
				if (result != null && editText != null &&
						editText.getText().length() == 0)
					editText.setText(result);
			}

			@Override
			protected String doInBackground(Void... params) {
				String resolvedLocation = null;
				if (!Geocoder.isPresent()) {
					Consts.log("Geocoder not available - no location based auto collections are possible");
					return null;
				}

				Geocoder geocoder = new Geocoder(context, Locale.getDefault());
				List<Address> addresses;
				try {
					/* check to make sure user hasn't added text already */
					if (editText.getText().length() != 0) {
						return null;
					}

					addresses = geocoder.getFromLocation(lat, lng, 1);
					if(addresses != null && addresses.size() > 0)
						resolvedLocation = addresses.get(0).getLocality();
					} catch (IOException e) {
						e.printStackTrace();
					}

				return resolvedLocation;
			}
		};
		task.execute();

		return task;
	}

	private void openAddGeofenceDialog (final Circle circle) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		
		builder.setTitle(R.string.add_geofence);
		builder.setMessage(R.string.geofence_name);
		View addGeofenceDialogView = this.getLayoutInflater().inflate(R.layout.geofence_name_dialog,
				null);
		builder.setView(addGeofenceDialogView);

		builder.setPositiveButton(android.R.string.ok,
				new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface mDialog, int id) {
				Dialog dialog = (Dialog)mDialog;
				String geofenceName = ((EditText)dialog.findViewById(R.id.geofence_name_edit)).getText().toString();

				ArrayList<CircleAndName> tmpGeofences = new ArrayList<CircleAndName>();
				tmpGeofences.addAll(allGeofences);
				tmpGeofences.addAll(currentAddGeofences);

				/* Avoid duplicate names */
				for (CircleAndName mCircle : tmpGeofences) {
					if (mCircle.name.contains(geofenceName)) {

						for (int i=1; i<9999; i++) {
							/* Should match anything here(1) */
							if (geofenceName.matches("[\\w|\\s]*\\(\\d\\){1}")) {
								Consts.log("Got match for a name that already has a wefwefw(n)");
								int firstBracket = geofenceName.indexOf("(");
								String currentd = geofenceName.substring(firstBracket+1, geofenceName.length()-1);

								try {
								i = Integer.valueOf (currentd)+1;
								} catch (NumberFormatException e) {
									Consts.log("Couldn't parse number string");
								}
								geofenceName = geofenceName.replaceAll("\\(\\d\\)", "");
							}
							String testName = geofenceName + String.format("(%d)", i);
							/* If it doesn't contain the test name we found a good one!*/
							if (!mCircle.name.contains(testName)) {
								geofenceName = testName;
								Consts.log ("fount new name because of dup"+geofenceName);
								break;
							}
						}
					}
				}
				CircleAndName mCircle = new CircleAndName(circle, geofenceName);

				currentAddGeofences.add(mCircle);
				dialog.dismiss();
			}
		});
		builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				if (geocoderTask != null)
					geocoderTask.cancel(true);

				dialog.dismiss();
				circle.remove();
			}
		});

		AlertDialog dialog = builder.create();
		dialog.show();

		EditText editText = ((EditText)dialog.findViewById(R.id.geofence_name_edit));
		geocoderTask = geocode(circle.getCenter().latitude, circle.getCenter().longitude,
				editText);
	}

	private void connectToService() {
		Intent intent = new Intent(MainActivity.this, LocationSync.class);
		startService(intent);
		bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
	}
	@Override
	protected void onStart() {
		super.onStart();
		/* starts the intent for syncing if it's not already started */
		connectToService();
		Intent intent = this.getIntent();
		if (intent != null &&
			intent.getData() != null) {
			zoomToPersonIntent = intent.getData().toString().substring("person://".length());
			/* Clear the intent */
			intent = null;
			Consts.log("zoom to person intent:"+zoomToPersonIntent);
		}
	}

	@Override
	protected void onStop() {
		super.onStop();

		if (sBound) {
			locationSyncService.byebye();
			unbindService(mConnection);
			sBound = false;
		}
		zoomToPersonIntent = null;
		LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (sBound) {
			locationSyncService.byebye();
			unbindService(mConnection);
			sBound = false;
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		Consts.log("resume");
		connectToService();
		setUpMapIfNeeded();
		LocalBroadcastManager.getInstance(this).registerReceiver(receiver,
				new IntentFilter(Consts.NOTIFICATION));
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

		mMap.setOnMapClickListener(new OnMapClickListener() {

			@Override
			public void onMapClick(LatLng latLng) {
				if (geofenceSetInProgress) {
					addGeofence(latLng);
				}
			}
			
		});

		mMap.setOnMapLongClickListener(new OnMapLongClickListener() {
			@Override
			public void onMapLongClick(LatLng latLng) {
				addGeofence(latLng);
			}
		});
	}
}