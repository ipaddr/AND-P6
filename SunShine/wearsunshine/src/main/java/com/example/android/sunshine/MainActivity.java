package com.example.android.sunshine;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.SharedPreferencesCompat;
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
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity implements
        DataApi.DataListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = MainActivity.class.getSimpleName();

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

    private BroadcastReceiver updateDateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mDate.setText(DateFormat.getDateInstance().format(new Date()));
        }
    };

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
                        Log.d(TAG, "onClick");
                    }
                });
                mTime.setFormat12Hour(null);
                mTime.setFormat24Hour("H:mm");

                SimpleDateFormat sdf = new SimpleDateFormat("EEE, MMM dd yyyy");
                String text = sdf.format(new Date());
                mDate = (TextView) stub.findViewById(R.id.date);
                mDate.setText(text);

                mHighTemp = (TextView) stub.findViewById(R.id.high_temp);
                mLowTemp = (TextView) stub.findViewById(R.id.low_temp);
                mWeatherIcon = (ImageView) stub.findViewById(R.id.weather_icon);
            }
        });
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.d(TAG, "onConnected");
        Wearable.DataApi.addListener(mGoogleApiClient, this);

        // if no first data found then request it from phone
        boolean isFirstDataExist = getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)
                .getBoolean(Constant.NO_FIRST_DATA, false);
        if (!isFirstDataExist)
            requestDataFromPhone();
    }

    private void requestDataFromPhone() {
        Toast.makeText(MainActivity.this, "requestDataFromPhone", Toast.LENGTH_SHORT).show();
        PutDataMapRequest putDataMapReq = PutDataMapRequest.create(Constant.PATH_REQUEST_FROM_WEARABLE);
        putDataMapReq.getDataMap().putDouble(Constant.EXTRA_TIMESTAMP, System.currentTimeMillis());
        PutDataRequest putDataReq = putDataMapReq.asPutDataRequest();

        com.google.android.gms.common.api.PendingResult<DataApi.DataItemResult> pendingResult =
                Wearable.DataApi.putDataItem(mGoogleApiClient, putDataReq);

        pendingResult.setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
            @Override
            public void onResult(@NonNull DataApi.DataItemResult dataItemResult) {
                if (!dataItemResult.getStatus().isSuccess()){
                    Toast.makeText(MainActivity.this, "callback if", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "callback else", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(updateDateReceiver, new IntentFilter(Intent.ACTION_DATE_CHANGED));
        mGoogleApiClient.connect();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(updateDateReceiver);
        Wearable.DataApi.removeListener(mGoogleApiClient, this);
        mGoogleApiClient.disconnect();
    }

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
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "onConnectionSuspended");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.d(TAG, "onConnectionFailed");
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
        Log.d(TAG, "Data from Phone in activity");
        for (DataEvent event : dataEventBuffer) {
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                // DataItem changed
                DataItem item = event.getDataItem();
                if (item.getUri().getPath().compareTo(Constant.PATH_COMMUNICATION) == 0) {
                    DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                    int weatherId = dataMap.getInt(Constant.EXTRA_WEATHERID);
                    int high = (int)dataMap.getDouble(Constant.EXTRA_HIGH);
                    int low = (int)dataMap.getDouble(Constant.EXTRA_LOW);
                    Log.d(TAG, "weatherId : "+String.valueOf(weatherId));
                    Log.d(TAG, "high : "+String.valueOf(high));
                    Log.d(TAG, "low : "+String.valueOf(low));
                    mHighTemp.setText(String.format(getString(R.string.high_temp), String.valueOf(high)));
                    mLowTemp.setText(String.format(getString(R.string.low_temp), String.valueOf(low)));
                    final Asset profileAsset = dataMap.getAsset(Constant.EXTRA_IMG);
                    if (profileAsset == null) {
                        Log.d(TAG, "profileAsset is null");
                        return;
                    }
                    ExecutorService executorService = new ScheduledThreadPoolExecutor(1);
                    executorService.submit(new Runnable() {
                        @Override
                        public void run() {
                            Log.d(TAG, "profileAsset is not null");
                            final Bitmap bitmap = loadBitmapFromAsset(profileAsset);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mWeatherIcon.setImageBitmap(bitmap);
                                }
                            });
                        }
                    });
                    boolean isFirstDataExist = getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE)
                            .edit().putBoolean(Constant.NO_FIRST_DATA, true).commit();
                    if (!isFirstDataExist)
                        requestDataFromPhone();
                }
            }
        }
    }

    public Bitmap loadBitmapFromAsset(Asset asset) {
        if (asset == null) {
            throw new IllegalArgumentException("Asset must be non-null");
        }
        ConnectionResult result =
                mGoogleApiClient.blockingConnect(0, TimeUnit.MILLISECONDS);
        if (!result.isSuccess()) {
            return null;
        }
        // convert asset into a file descriptor and block until it's ready
        InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                mGoogleApiClient, asset).await().getInputStream();
        mGoogleApiClient.disconnect();

        if (assetInputStream == null) {
            Log.w(TAG, "Requested an unknown Asset.");
            return null;
        }
        // decode the stream into a bitmap
        return BitmapFactory.decodeStream(assetInputStream);
    }
}
