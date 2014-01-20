package com.wood.wooditude;

import java.util.List;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class PersonListAdapater extends ArrayAdapter<Person> {

	Context context;
	List<Person> people;

	public PersonListAdapater(Context context, int textViewResourceId, List<Person> people) {
		super(context, textViewResourceId, people);
		this.people = people;
		this.context = context;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View row=inflater.inflate(R.layout.drawer_list_item, parent, false);
		TextView name =(TextView)row.findViewById(R.id.draweritem_name);
		TextView lastCheckin = (TextView)row.findViewById(R.id.draweritem_lastcheckin);

		name.setLongClickable(false);
		name.setClickable(false);
		name.setOnClickListener(null);
		
		lastCheckin.setLongClickable(false);
		lastCheckin.setClickable(false);
		lastCheckin.setOnClickListener(null);
		
		if (people.size() > position) {
			Person mPerson = people.get(position);
			name.setText(mPerson.getName());
			lastCheckin.setText (mPerson.getLastCheckIn());
			Consts.log ("Adding item to view "+mPerson.getName());
		}
		return row;
	}
}
