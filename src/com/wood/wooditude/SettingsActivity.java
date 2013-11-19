package com.wood.wooditude;

import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;


public class SettingsActivity extends PreferenceActivity {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Display the fragment as the main content.
		getFragmentManager().beginTransaction()
				.replace(android.R.id.content, new SettingsFragment()).commit();
	}
	public static class SettingsFragment extends PreferenceFragment {
		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);

			// Load the preferences from an XML resource
			addPreferencesFromResource(R.xml.preferences);
		}
	}

}
