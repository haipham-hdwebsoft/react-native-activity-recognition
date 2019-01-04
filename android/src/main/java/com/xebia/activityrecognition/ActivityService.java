package com.xebia.activityrecognition;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;
import android.support.v4.app.NotificationCompat;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.google.android.gms.location.DetectedActivity;
import java.util.ArrayList;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.ReactNativeHost;
import com.facebook.react.ReactInstanceManager;
import com.facebook.react.ReactApplication;
import com.facebook.react.bridge.ReactApplicationContext;

public class ActivityService extends Service {

    private static final String TAG = ActivityService.class.getSimpleName();
    private static final String PACKAGE_NAME = DetectionService.class.getPackage().getName();

    public static final String ACTION_START_FOREGROUND_SERVICE = PACKAGE_NAME + ".ACTION_START_FOREGROUND_SERVICE";
    public static final String ACTION_STOP_FOREGROUND_SERVICE = PACKAGE_NAME + ".ACTION_STOP_FOREGROUND_SERVICE";
    public static final String DETECTION_INTERVAL = PACKAGE_NAME + ".DETECTION_INTERVAL";
    public static final String REACT_NATIVE_CONTEXT = PACKAGE_NAME + ".REACT_NATIVE_CONTEXT";

    private ActivityRecognizer activityRecognizer;
    private ReactContext mReactContext;
    private long interval;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Received start id " + startId + ": " + intent);
        if (intent.getAction() == ACTION_START_FOREGROUND_SERVICE) {
            interval = intent.getLongExtra(ActivityService.DETECTION_INTERVAL, 10000);
            ReactApplication application = (ReactApplication) this.getApplication();
            ReactNativeHost reactNativeHost = application.getReactNativeHost();
            ReactInstanceManager reactInstanceManager = reactNativeHost.getReactInstanceManager();
            mReactContext = (ReactApplicationContext) reactInstanceManager.getCurrentReactContext();
            startForegroundService();
            Log.d(TAG, "Foreground service is started");
        } else if (intent.getAction() == ACTION_STOP_FOREGROUND_SERVICE) {
            stopForegroundService();
            Log.d(TAG, "Foreground service is stopped");
        }
        return START_REDELIVER_INTENT;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "ActivityService foreground service onCreate()");

    }

    private void startForegroundService() {
        Log.d(TAG, "Start foreground service");

        if (mReactContext != null) {
            Log.d(TAG, "React context found");
            activityRecognizer = new ActivityRecognizer(mReactContext);
        }
        activityRecognizer = new ActivityRecognizer(this);
        activityRecognizer.attachReceiver(interval);

        // Build the notification.
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        Notification notification = builder.build();

        // Start foreground service.
        startForeground(1, notification);
    }

    private void stopForegroundService() {
        Log.d(TAG, "Stop foreground service");

        activityRecognizer.removeReceiver();

        ActivityRecognizer.isServiceRunning = false;

        // Stop foreground service and remove the notification.
        stopForeground(true);

        // Stop the foreground service.
        stopSelf();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        if (ActivityRecognizer.isServiceRunning) {
            Log.d(TAG, "onDestroy removing receiver");
            activityRecognizer.removeReceiver();
            ActivityRecognizer.isServiceRunning = false;
        }
    }
}