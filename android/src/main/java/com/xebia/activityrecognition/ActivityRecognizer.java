package com.xebia.activityrecognition;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.content.WakefulBroadcastReceiver;

import java.io.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.bridge.ReactApplicationContext;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.ActivityRecognitionClient;
import com.google.android.gms.location.DetectedActivity;

import java.util.List;
import android.arch.persistence.room.Room;
import java.util.Comparator;
import java.util.Collections;
import java.util.Date;

public class ActivityRecognizer implements OnConnectionFailedListener {
    protected static final String TAG = ActivityRecognizer.class.getSimpleName();
    protected ActivityDetectionBroadcastReceiver mBroadcastReceiver;
    private Context mContext;
    private ReactContext mReactContext;
    private ActivityRecognitionClient mArclient;
    private PendingIntent pIntent;
    private GoogleApiAvailability mGoogleApiAvailability;
    private ActivityCache.AppDatabase mAppDatabase;
    private Timer mockTimer;

    public static boolean isServiceRunning = false;

    public ActivityRecognizer(ReactApplicationContext reactContext) {
        mContext = reactContext.getApplicationContext();
        init(reactContext.getApplicationContext());
    }

    public ActivityRecognizer(Context context) {
        init(context);
    }

    private void init(Context context) {
        mContext = context;
        mGoogleApiAvailability = GoogleApiAvailability.getInstance();
        mAppDatabase = Room.databaseBuilder(mContext, ActivityCache.AppDatabase.class, "activity_db").build();

        if (checkPlayServices()) {
            mBroadcastReceiver = new ActivityDetectionBroadcastReceiver();
            mArclient = ActivityRecognition.getClient(mContext);
        }
    }

    public void start(long detectionIntervalMillis) {
        Log.i(TAG, "Start");

        if (!ActivityRecognizer.isServiceRunning) {
            Log.i(TAG, "Start foreground service ");

            Intent intent = new Intent(mContext, ActivityService.class);
            intent.setAction(ActivityService.ACTION_START_FOREGROUND_SERVICE);
            intent.putExtra(ActivityService.DETECTION_INTERVAL, detectionIntervalMillis);
            mContext.startService(intent);
            ActivityRecognizer.isServiceRunning = true;
        }
    }

    public void attachReceiver(long detectionIntervalMillis) {
        Log.i(TAG, "Attach receiver");
        if (mArclient == null) {
            throw new Error("No Google API client. Your device likely doesn't have Google Play Services.");
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(DetectionService.BROADCAST_ACTION);
        mContext.registerReceiver(mBroadcastReceiver, filter);

        Log.i(TAG, "Adding intent to broadcast");
        Intent intent = new Intent(mContext, DetectionService.class);
        pIntent = PendingIntent.getService(mContext, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        mArclient.requestActivityUpdates(detectionIntervalMillis, pIntent);
    }

    // Subscribe to mock activity updates.
    public void startMocked(long detectionIntervalMillis, final int mockActivityType) {
        mockTimer = new Timer();
        mockTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                final ArrayList<DetectedActivity> detectedActivities = new ArrayList<>();
                DetectedActivity detectedActivity = new DetectedActivity(mockActivityType, 100);
                detectedActivities.add(detectedActivity);
                onUpdate(detectedActivities);
            }
        }, 0, detectionIntervalMillis);

    }

    // Unsubscribe from mock activity updates.
    public void stopMocked() {
        mockTimer.cancel();
    }

    // Unsubscribe from activity updates and disconnect from Google Play Services.
    // Also called when connection failed.
    public void stop() {
        Log.i(TAG, "Stop foreground service");
        Intent intent = new Intent(mContext, ActivityService.class);
        intent.setAction(ActivityService.ACTION_STOP_FOREGROUND_SERVICE);
        mContext.startService(intent);
    }

    public void removeReceiver() {
        Log.i(TAG, "Remove receiver");
        if (mArclient == null) {
            throw new Error("No Google API client. Your device likely doesn't have Google Play Services.");
        }

        mArclient.removeActivityUpdates(pIntent);
        mContext.unregisterReceiver(mBroadcastReceiver);
    }

    // Verify Google Play Services availability
    public boolean checkPlayServices() {
        int resultCode = mGoogleApiAvailability.isGooglePlayServicesAvailable(mContext);
        if (resultCode != ConnectionResult.SUCCESS) {
            String errorString = mGoogleApiAvailability.getErrorString(resultCode);
            if (mGoogleApiAvailability.isUserResolvableError(resultCode)) {
                Log.w(TAG, errorString);
            } else {
                Log.e(TAG, "This device is not supported. " + errorString);
            }
            return false;
        }
        return true;
    }

    // Implement GoogleApiClient.ConnectionCallbacks
    public void onConnectionSuspended(int cause) {
        Log.i(TAG, "GoogleApiClient connection suspended, reconnecting...");
    }

    // Implement GoogleApiClient.OnConnectionFailedListener
    public void onConnectionFailed(ConnectionResult result) {
        Log.e(TAG, "GoogleApiClient connection failed: " + result.getErrorCode());
    }

    // Create key-value map with activity recognition result
    public void onUpdate(ArrayList<DetectedActivity> detectedActivities) {
        WritableMap params = Arguments.createMap();
        for (DetectedActivity activity : detectedActivities) {
            params.putInt(DetectionService.getActivityString(activity.getType()), activity.getConfidence());
        }
        // cacheResult(detectedActivities);
        if (mReactContext != null) { // only if in react context
            sendEvent("DetectedActivity", params);
        }
    }

    public void getHistory(Date fromDateTime, Date toDateTime, ActivityCache.GetHistoryAsyncResponse response) {
        Log.d(TAG, "Getting history");
        ActivityCache.GetHistory(mAppDatabase, fromDateTime, toDateTime, response);
    }

    public void clearHistory() {
        Log.e(TAG, "Clearing history");
        ActivityCache.DeleteAll(mAppDatabase);
    }

    // Send result back to JavaScript land
    private void sendEvent(String eventName, @Nullable WritableMap params) {
        try {
            mReactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(eventName, params);
        } catch (RuntimeException e) {
            Log.e(TAG, "java.lang.RuntimeException: Trying to invoke JS before CatalystInstance has been set!", e);
        }
    }

    public class ActivityDetectionBroadcastReceiver extends BroadcastReceiver {
        protected static final String TAG = "ActivityDetectionBroadcastReceiver";

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Received activity update");
            ArrayList<DetectedActivity> updatedActivities = intent
                    .getParcelableArrayListExtra(DetectionService.ACTIVITY_EXTRA);
            onUpdate(updatedActivities);
        }
    }
}
