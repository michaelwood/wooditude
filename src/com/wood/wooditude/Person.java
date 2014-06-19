package com.wood.wooditude;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;

public class Person {
	private LatLng location;
	private String lastCheckIn;
	private String name;
	private Marker marker;

	public Person () { } 
	
	public Person (String name, LatLng location, String lastCheckIn, Marker marker) {
		this.location = location;
		this.lastCheckIn = lastCheckIn;
		this.name = name;
		this.setMarker(marker);
	}

	public LatLng getLocation() {
		return location;
	}
	public void setLocation(LatLng location) {
		this.location = location;
	}
	public String getLastCheckIn() {
		return lastCheckIn;
	}
	public void setLastCheckIn(String lastCheckIn) {
		this.lastCheckIn = lastCheckIn;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}

	public Marker getMarker() {
		return marker;
	}

	public void setMarker(Marker marker) {
		this.marker = marker;
	}

	public void removeMarker () {
		marker.remove();
	}
}
