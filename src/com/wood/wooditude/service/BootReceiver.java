package com.wood.wooditude.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context arg0, Intent arg1) {
        Intent serviceIntent = new Intent(arg0, LocationSync.class);
        arg0.startService(serviceIntent);
    }
}