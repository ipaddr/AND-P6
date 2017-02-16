package com.example.android.sunshine;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.WearableListenerService;

public class MyService extends WearableListenerService {
    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
        Log.d("MainActivity", "Data from Phone in services");
        for (DataEvent event : dataEventBuffer) {
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                Log.d("MainActivity", "received data from phone");
            }
        }
    }
}
