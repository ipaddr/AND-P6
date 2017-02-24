/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v7.widget.AppCompatDrawableManager;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class DigitalWatchFaceService extends CanvasWatchFaceService{

    private final String TAG = DigitalWatchFaceService.class.getSimpleName();

    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    /**
     * final date format
     */
    private static final SimpleDateFormat sdf = new SimpleDateFormat("EEE, MMM dd yyyy");

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<DigitalWatchFaceService.Engine> mWeakReference;

        public EngineHandler(DigitalWatchFaceService.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            DigitalWatchFaceService.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener,
            GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener{
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;

        // Time
        Paint mTextPaint;
        int alpha = 200;

        // Date
        Paint mTextPaintDate;
        int mDiffOfTextPaintDateSize = 3;
        int mDiffOfTextPaintDatePosX = 3;
        int mDiffOfTextPaintDatePosY = 8;

        // Devider
        Paint mLinePaint;
        int mDiffOfLinePaintPos = 36;

        // img
        private Bitmap mBackgroundBitmap;
        private float mScale = 1;
        private int mDiffOfTextPaintImgPos = 2;

        // high temp
        private String stringHighTemp = "100";
        private Paint mTextPaintHighTemp;
        int mDiffOfTextPaintHighTempSize = 2;
        int mDiffOfTextPaintHighTempPos = 14;

        // low temp
        private String stringLowTemp = "0";
        Paint mTextPaintLowTemp;
        int mDiffOfTextPaintLowTempSize = 2;
        int mDiffOfTextPaintLowTempPos = 14;


        boolean mAmbient;
        Calendar mCalendar;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        float mXOffset;
        float mYOffset;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;


        /**
         * Date receiver
         * @param holder
         */
        private BroadcastReceiver updateDateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                invalidate();
            }
        };

        private GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(DigitalWatchFaceService.this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Log.d(TAG, "onConnected");
            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);

            // if no first data found then request it from phone
            ExecutorService executorService = new ScheduledThreadPoolExecutor(1);
            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    SharedPreferences preferences = getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE);
                    int weatherId = preferences.getInt(Constant.EXTRA_WEATHERID, -1);
                    int high = preferences.getInt(Constant.EXTRA_HIGH, -1);
                    int low = preferences.getInt(Constant.EXTRA_LOW, 1);

                    if (weatherId == -1 || high == -1 || low == -1) {
                        Log.d(TAG, "empty");
                        requestDataFromPhone();
                    }
                    else {
                        Log.d(TAG, "not emtpy");
                        updateData(weatherId, high, low);
                    }
                }
            });
        }

        private void requestDataFromPhone() {
            Log.d(TAG, "requestDataFromPhone");
            PutDataMapRequest putDataMapReq = PutDataMapRequest.create(Constant.PATH_REQUEST_FROM_WEARABLE);
            putDataMapReq.getDataMap().putDouble(Constant.EXTRA_TIMESTAMP, System.currentTimeMillis());
            PutDataRequest putDataReq = putDataMapReq.asPutDataRequest();
            putDataReq.setUrgent();

            com.google.android.gms.common.api.PendingResult<DataApi.DataItemResult> pendingResult =
                    Wearable.DataApi.putDataItem(mGoogleApiClient, putDataReq);

            pendingResult.setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                @Override
                public void onResult(@NonNull DataApi.DataItemResult dataItemResult) {
                    if (!dataItemResult.getStatus().isSuccess()){
                        Log.d(TAG, "callback if");
                    } else {
                        Log.d(TAG, "callback if");
                    }
                }
            });
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
                    final DataItem item = event.getDataItem();
                    if (item.getUri().getPath().compareTo(Constant.PATH_COMMUNICATION) == 0) {;
                        DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                        final int weatherId = dataMap.getInt(Constant.EXTRA_WEATHERID);
                        final int high = (int)dataMap.getDouble(Constant.EXTRA_HIGH);
                        final int low = (int)dataMap.getDouble(Constant.EXTRA_LOW);
                        final int imgId = ImageHelper.getSmallArtResourceIdForWeatherCondition(weatherId);

                        Log.d(TAG, "weatherId : "+String.valueOf(weatherId));
                        Log.d(TAG, "high : "+String.valueOf(high));
                        Log.d(TAG, "low : "+String.valueOf(low));
                        Log.d(TAG, "imgId : "+String.valueOf(imgId));

                        SharedPreferences preferences = getSharedPreferences(Constant.PREF_NAME, Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putInt(Constant.EXTRA_WEATHERID, weatherId);
                        editor.putInt(Constant.EXTRA_HIGH, high);
                        editor.putInt(Constant.EXTRA_LOW, low);
                        editor.commit();

                        updateData(weatherId, high, low);
                    }
                }
            }
        }

        private void updateData(int weatherId, int high, int low){
            stringHighTemp = String.format(getString(R.string.high_temp), String.valueOf(high));
            stringLowTemp = String.format(getString(R.string.low_temp), String.valueOf(low));
            final int imgId = ImageHelper.getSmallArtResourceIdForWeatherCondition(weatherId);
            mBackgroundBitmap = getBitmapFromVectorDrawable(DigitalWatchFaceService.this, imgId);
            invalidate();
        }

        /**
         * default watch face
         * @param holder
         */

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(DigitalWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());

            Resources resources = DigitalWatchFaceService.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mTextPaint = new Paint();
            mTextPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mTextPaintDate = new Paint();
            mTextPaintDate = createTextPaint(resources.getColor(R.color.digital_text));
            mTextPaintDate.setAlpha(alpha);

            mLinePaint = new Paint();
            mLinePaint = createTextPaint(resources.getColor(R.color.digital_text));
            mLinePaint.setAlpha(alpha);
            mLinePaint.setTextSize(1);

            final int backgroundResId = R.drawable.art_light_clouds;
            mBackgroundBitmap = getBitmapFromVectorDrawable(DigitalWatchFaceService.this, backgroundResId);
            if (mBackgroundBitmap == null){
                Log.d(TAG, "null");
            } else {
                Log.d(TAG, "not null");
            }

            mTextPaintHighTemp = new Paint();
            mTextPaintHighTemp = createTextPaint(resources.getColor(R.color.digital_text));

            mTextPaintLowTemp = new Paint();
            mTextPaintLowTemp = createTextPaint(resources.getColor(R.color.digital_text));
            mTextPaintLowTemp.setAlpha(alpha);

            mCalendar = Calendar.getInstance();

            stringHighTemp = String.format(resources.getString(R.string.high_temp), stringHighTemp);
            stringLowTemp = String.format(resources.getString(R.string.low_temp), stringLowTemp);
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                mGoogleApiClient.connect();
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            DigitalWatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
            DigitalWatchFaceService.this.registerReceiver(updateDateReceiver, new IntentFilter(Intent.ACTION_DATE_CHANGED));
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            DigitalWatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
            DigitalWatchFaceService.this.unregisterReceiver(updateDateReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = DigitalWatchFaceService.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            mTextPaint.setTextSize(textSize);
            mTextPaintDate.setTextSize(textSize / mDiffOfTextPaintDateSize);
            mTextPaintHighTemp.setTextSize(textSize / mDiffOfTextPaintHighTempSize);
            mTextPaintLowTemp.setTextSize(textSize / mDiffOfTextPaintLowTempSize);

        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias(!inAmbientMode);
                    mTextPaintDate.setAntiAlias(!inAmbientMode);
                    mTextPaintHighTemp.setAntiAlias(!inAmbientMode);
                    mTextPaintLowTemp.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    // TODO: Add code to handle the tap gesture.
                    Toast.makeText(getApplicationContext(), R.string.message, Toast.LENGTH_SHORT)
                            .show();
                    break;
            }
            invalidate();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            if (mBackgroundBitmap == null)
                return;
            mScale = ((float) width) / (float) mBackgroundBitmap.getWidth() / 5;

            mBackgroundBitmap = Bitmap.createScaledBitmap(mBackgroundBitmap,
                    (int) (mBackgroundBitmap.getWidth() * mScale),
                    (int) (mBackgroundBitmap.getHeight() * mScale), true);
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            String text = mAmbient
                    ? String.format("%d:%02d", mCalendar.get(Calendar.HOUR),
                    mCalendar.get(Calendar.MINUTE))
                    : String.format("%d:%02d:%02d", mCalendar.get(Calendar.HOUR),
                    mCalendar.get(Calendar.MINUTE), mCalendar.get(Calendar.SECOND));

            canvas.drawText(text, mXOffset, mYOffset, mTextPaint);

            // date
            String textDate = sdf.format(new Date());
            float mXOffsetDate = mXOffset + (mTextPaint.measureText(text) - mTextPaintDate.measureText(textDate)) / 2;
            float mYOffsetDate = mYOffset + mTextPaintDate.getTextSize() + mDiffOfTextPaintDatePosY;
            float dateTextHeigh = mTextPaintDate.getTextSize();
            canvas.drawText(textDate
                    , mXOffsetDate
                    , mYOffsetDate
                    , mTextPaintDate);

            // devider
            float width = mTextPaint.measureText(text);
            canvas.drawLine(mXOffset
                    , mYOffsetDate + dateTextHeigh
                    , mXOffset + mTextPaint.measureText(text)
                    , mYOffsetDate + dateTextHeigh
                    , mLinePaint);


            if (mBackgroundBitmap == null)
                return;

            // calculate three position
            float withdBetween = 10;
            float imgLength = mBackgroundBitmap.getWidth();
            float HighLength = mTextPaintHighTemp.measureText(stringHighTemp);
            float lowLength = mTextPaintLowTemp.measureText(stringLowTemp);
            float totalLengh = imgLength + HighLength + lowLength + (withdBetween * 2);

            if (width > totalLengh) {

                // img
                float mXOffWeatherImg = mXOffset + (width - totalLengh) / 2; // mXOffsetDate;
                float mYOffWeatherImg = mYOffsetDate + dateTextHeigh * 2;
                canvas.drawBitmap(mBackgroundBitmap, mXOffWeatherImg, mYOffWeatherImg, mBackgroundPaint);

                // high temp
                float mXOffsethighTemp = mXOffWeatherImg + mBackgroundBitmap.getWidth() + withdBetween;
                canvas.drawText(stringHighTemp
                        , mXOffsethighTemp
                        , mYOffWeatherImg + mBackgroundBitmap.getHeight() / 2
                        , mTextPaintHighTemp);

                // low temp
                float mXOffsetLowTemp = mXOffWeatherImg + mBackgroundBitmap.getWidth() * 2 + withdBetween * 2;
                canvas.drawText(stringLowTemp
                        , mXOffsetLowTemp
                        , mYOffWeatherImg + mBackgroundBitmap.getHeight() / 2
                        , mTextPaintLowTemp);
            }
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }



    /**
     *
     * Getting bitmap value from vector image resource
     * @param context
     * @param drawableId
     * @return
     */
    public static Bitmap getBitmapFromVectorDrawable(Context context, int drawableId) {
        Drawable drawable = AppCompatDrawableManager.get().getDrawable(context, drawableId);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            drawable = (DrawableCompat.wrap(drawable)).mutate();
        }

        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return bitmap;
    }
}
