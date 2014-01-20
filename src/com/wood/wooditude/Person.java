package com.wood.wooditude;

import com.google.android.gms.maps.model.LatLng;

public class Person {
	private LatLng location;
	private String lastCheckIn;
	private String name;

	public Person () { } 
	
	public Person (String name, LatLng location, String lastCheckIn) {
		this.location = location;
		this.lastCheckIn = lastCheckIn;
		this.name = name;
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
}
