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

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFace extends CanvasWatchFaceService {
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

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchFace.Engine> mWeakReference;

        public EngineHandler(SunshineWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }


    private class Engine extends CanvasWatchFaceService.Engine implements  GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener, DataApi.DataListener
    {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mTextPaint;
        Paint mDatePaint;
        Paint separatorLinePaint;

        Paint mhighTemperatureTextPaint;
        Paint mlowTemperatureTextPaint;

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
        float mYOffsetDate;

        final int dateYOffset = 10;

        final int lineXOffset = 50;

        final int lineYOffset = 25;

        final int tempYOffset = lineYOffset + 70;

        final int tempDiffOffset = 5;

        final int weatherIconXOffset = 25;

        /*final int weatherIconYOffsetRound = 47;

        final int weatherIconYOffsetSquare = 40;*/

        int weahtherIconYOffset  = 0;

        final int lowTempYOffset = 10;

        Bitmap mWeatherIcon;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;


        final String WEATHER_LOCATION = "/weatherlocation";
        final String WEATHER_CONDITION = "weather_condition";
        final String HIGH_TEMP = "high_temp";
        final String LOW_TEMP = "low_temp";



        String highTemperature;
        String lowTemperature;


        private GoogleApiClient mGoogleApiClient;
        private Resources resources;

        final String TAG = "SUNSHINEWEAR";

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());


            mGoogleApiClient = new GoogleApiClient.Builder(SunshineWatchFace.this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(Wearable.API)
                    .build();

            resources = SunshineWatchFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);
            mYOffsetDate = resources.getDimension(R.dimen.digital_y_offset_date);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.backgroundcolor));

            mTextPaint = new Paint();
            mTextPaint = createTextPaint(resources.getColor(R.color.time_text_color));

            mDatePaint = new Paint();
            mDatePaint = createTextPaint(resources.getColor(R.color.date_text_color));

            separatorLinePaint = new Paint();
            separatorLinePaint.setColor(resources.getColor(R.color.line_color));
            separatorLinePaint.setStrokeWidth(2);

            mhighTemperatureTextPaint = createTextPaint(resources.getColor(R.color.time_text_color));
            mlowTemperatureTextPaint = createTextPaint(resources.getColor(R.color.date_text_color));

            mCalendar = Calendar.getInstance();
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
                registerReceiver();
                Log.d(TAG, "onVisibilityChanged");
                mGoogleApiClient.connect();
                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();

                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
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
            SunshineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_time_text_size_round : R.dimen.digital_time_text_size);

            float dateTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_date_text_size_round : R.dimen.digital_date_text_size);

            weahtherIconYOffset = isRound?45:40;

            mTextPaint.setTextSize(textSize);
            mDatePaint.setTextSize(dateTextSize);

            mhighTemperatureTextPaint.setTextSize(textSize);
            mlowTemperatureTextPaint.setTextSize(textSize);

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
                    mDatePaint.setAntiAlias(!inAmbientMode);
                    mhighTemperatureTextPaint.setAntiAlias(!inAmbientMode);
                    mlowTemperatureTextPaint.setAntiAlias(!inAmbientMode);

                    separatorLinePaint.setColor(inAmbientMode ? getResources().getColor(R.color.line_color_ambient) : getResources().getColor(R.color.line_color));

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


            //draw time
            String text = mAmbient
                    ? String.format("%d:%02d", mCalendar.get(Calendar.HOUR),
                    mCalendar.get(Calendar.MINUTE))
                    : String.format("%d:%02d:%02d", mCalendar.get(Calendar.HOUR),
                    mCalendar.get(Calendar.MINUTE), mCalendar.get(Calendar.SECOND));


            float textWidth = mTextPaint.measureText(text);
            float halfDateTextWidth = textWidth / 2;
            float xOffset = bounds.centerX() - halfDateTextWidth;
            canvas.drawText(text, xOffset, bounds.centerY() - mYOffset, mTextPaint);


            //draw date
            SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, MMM dd yyyy", Locale.US);
            String date = dateFormat.format(mCalendar.getTime()).toUpperCase(Locale.US);

            float dateTextWidth = mDatePaint.measureText(date);
            float halfTimeTextWidth = dateTextWidth / 2;
            float xOffsetDate = bounds.centerX() - halfTimeTextWidth;
            canvas.drawText(date, xOffsetDate, bounds.centerY() + dateYOffset, mDatePaint);


            //draw line
            canvas.drawLine(bounds.centerX() - lineXOffset, bounds.centerY() + lineYOffset, bounds.centerX()+ lineXOffset, bounds.centerY()+ lineYOffset, separatorLinePaint);


            float xOffsetHighTemp = 0;
            float halfLowTempTextWidth = 0;
            if(highTemperature!=null) {
                //draw high temperature
                float highTempTextWidth = mhighTemperatureTextPaint.measureText(highTemperature);
                float halfHighTempTextWidth = highTempTextWidth / 2;
                xOffsetHighTemp = bounds.centerX() - halfHighTempTextWidth;
                canvas.drawText(highTemperature, xOffsetHighTemp, bounds.centerY() + tempYOffset, mhighTemperatureTextPaint);
            }

            if(lowTemperature!=null) {
                //draw low temperature
                float lowTempTextWidth = mlowTemperatureTextPaint.measureText(lowTemperature);
                halfLowTempTextWidth = lowTempTextWidth / 2;
                float xOffsetLowTemp = bounds.centerX() - halfLowTempTextWidth;
                canvas.drawText(lowTemperature, bounds.centerX() + halfLowTempTextWidth+ lowTempYOffset, bounds.centerY() + tempYOffset, mlowTemperatureTextPaint);
            }

            if(mWeatherIcon!=null) {
                if (!mAmbient) {
                    canvas.drawBitmap(mWeatherIcon, xOffsetHighTemp - halfLowTempTextWidth - lowTempYOffset - weatherIconXOffset, bounds.centerY() + weahtherIconYOffset, null);
                }
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


        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Wearable.DataApi.addListener(mGoogleApiClient, this);
        }

        @Override
        public void onConnectionSuspended(int i) {

        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            Log.d(TAG, connectionResult.getErrorMessage());
        }

        @Override
        public void onDataChanged(DataEventBuffer dataEvents) {
            Log.d(TAG, "onDataChanged");
            for (DataEvent dataEvent : dataEvents) {
                if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                    DataItem dataItem = dataEvent.getDataItem();
                    if (dataItem.getUri().getPath().compareTo(WEATHER_LOCATION) == 0) {
                        DataMap dataMap = DataMapItem.fromDataItem(dataItem).getDataMap();
                        updateWeatherInfo(dataMap.getString(HIGH_TEMP),
                                dataMap.getString(LOW_TEMP), dataMap.getInt(WEATHER_CONDITION));
                        invalidate();
                    }
                }
            }
        }

        private void updateWeatherInfo(String highTemperature, String lowTemperature, int weatherCondition) {
            this.highTemperature = highTemperature;
            this.lowTemperature = lowTemperature;
            this.mWeatherIcon = BitmapFactory.decodeResource(resources, WeatherUtility.getWeatherConditionResource(weatherCondition));
        }
    }
}
