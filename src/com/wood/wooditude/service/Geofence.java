package com.wood.wooditude.service;

import org.json.JSONException;
import org.json.JSONObject;

import android.location.Location;

public class Geofence {
	public Location location;
	public double radius;
	public String name;

	public Geofence (double latitude, double longitude, double radius, String name) {
		this.radius = radius;
		this.name = name;
		this.location = new Location("wooditude");
		this.location.setLatitude(latitude);
		this.location.setLongitude(longitude);
	}
	public Geofence () {
	}

	public JSONObject toJSON () {
		JSONObject out = new JSONObject ();
		try {
		out.put("name", name);
		out.put("radius", radius);
		out.put("latitude", location.getLatitude());
		out.put("longitude", location.getLongitude());
		} catch (JSONException e) {}
		return out;
	}
	
	public boolean loadFromJSON (JSONObject obj) {
		double latitude, longitude;
		try {
		name = obj.getString("name");
		radius = obj.getDouble("radius");
		latitude = obj.getDouble("latitude");
		longitude = obj.getDouble("longitude");

		location = new Location("wooditude");
		location.setLatitude(latitude);
		location.setLongitude(longitude);
		} catch (JSONException e) { 
			return false;
		}
		return true;
	}
}
