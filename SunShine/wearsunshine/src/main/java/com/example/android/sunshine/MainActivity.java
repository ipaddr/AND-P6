package com.example.android.sunshine;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.Wearable;

import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends Activity implements
        DataApi.DataListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    // variable to hold the value of time
    private TextClock mTime;
    // variable to hold the value of date
    private TextView mDate;
    // variable to hold the value of high temp
    private TextView mHighTemp;
    // variable to hold the value of low temp
    private TextView mLowTemp;
    // variable to hold the value of image of weather
    private ImageView mWeatherIcon;

    private GoogleApiClient mGoogleApiClient;
    private void googleAPI(){
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d("MainActivity", "onCreate");
        super.onCreate(savedInstanceState);
        googleAPI();
        setContentView(R.layout.activity_main);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mTime = (TextClock) stub.findViewById(R.id.time);
                mTime.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Toast.makeText(MainActivity.this, "ok", Toast.LENGTH_SHORT).show();
                        Log.d("MainActivity", "onClick");
                    }
                });
                mTime.setFormat12Hour(null);
                mTime.setFormat24Hour("H:mm");
//                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
//                String text = sdf.format(new Date());
//                mTime.setText(text);
                mDate = (TextView) stub.findViewById(R.id.date);
                mHighTemp = (TextView) stub.findViewById(R.id.high_temp);
                mLowTemp = (TextView) stub.findViewById(R.id.low_temp);
                mWeatherIcon = (ImageView) stub.findViewById(R.id.weather_icon);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.d("MainActivity", "onConnected");
        Wearable.DataApi.addListener(mGoogleApiClient, this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Toast.makeText(MainActivity.this, "onPause", Toast.LENGTH_SHORT).show();
        Wearable.DataApi.removeListener(mGoogleApiClient, this);
        mGoogleApiClient.disconnect();
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d("MainActivity", "onConnectionSuspended");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.d("MainActivity", "onConnectionFailed");
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
        Log.d("MainActivity", "Data from Phone in activity");
        int i = 0;
        for (DataEvent event : dataEventBuffer) {
            Log.d("MainActivity", "Data from Phone in activity at position - "+String.valueOf(i));
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                Log.d("MainActivity", "Data from Phone in activity was changed");
                mTime.setText("Data from Phone");
            } else {
                Log.d("MainActivity", "Data from Phone in activity was deleted");
            }
            i++;
        }
    }
}
