package com.byteshaft.neon;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import com.byteshaft.ezflashlight.CameraInitializationListener;
import com.byteshaft.ezflashlight.Flashlight;
import com.byteshaft.ezflashlight.FlashlightGlobals;

public class FlashlightService extends Service implements CameraInitializationListener {

    private static FlashlightService sFlashlightService = null;
    private ScreenStateListener mScreenStateListener = null;
    private Notification mNotification = null;
    private SystemManager mSystemManager = null;
    private RemoteUpdateUiHelpers mRemoteUi = null;
    private com.byteshaft.ezflashlight.Flashlight mFlashlight = null;
    private boolean AUTOSTART = true;

    static FlashlightService getInstance() {
        return sFlashlightService;
    }

    static boolean isRunning() {
        return sFlashlightService != null;
    }

    private static void setServiceInstance(FlashlightService flashlightService) {
        sFlashlightService = flashlightService;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        setServiceInstance(this);
        mScreenStateListener = new ScreenStateListener(this);
        mRemoteUi = new RemoteUpdateUiHelpers(this);
        mSystemManager = new SystemManager(this);
        mNotification = new Notification(this);
        mFlashlight = new Flashlight(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String starter = intent.getStringExtra("STARTER");
        Log.i(AppGlobals.LOG_TAG, String.format("Service started from %s", starter));
        AUTOSTART = intent.getBooleanExtra("AUTOSTART", true);
        mFlashlight.setOnCameraStateChangeListener(this);
        mFlashlight.initializeCamera();
        return START_NOT_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        if (!AppGlobals.isWidgetTapped()) {
            stopSelf();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (FlashlightGlobals.isFlashlightOn()) {
            stopTorch();
        }
        mScreenStateListener.unregister();
        mSystemManager.releaseWakeLock();
        mFlashlight.releaseAllResources();
        setServiceInstance(null);
        Log.i(AppGlobals.LOG_TAG, "Service down.");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    synchronized void lightenTorch() {
        Log.i(AppGlobals.LOG_TAG, "Turning on");
        mFlashlight.turnOn();
        /* Make is foreground service by showing a Notification,
        this ensures the service does not get killed on low resources.
        Unless the situation is really really bad.
        786 is just a random ID for the notification.
        */
        startForeground(786, mNotification.getNotification());
    }

    synchronized void stopTorch() {
        Log.i(AppGlobals.LOG_TAG, "Turning off");
        mRemoteUi.setUiButtonsOn(false);
        mFlashlight.turnOff();
        stopForeground(true);
        AppGlobals.setIsWidgetTapped(false);
    }

    @Override
    public void onCameraOpened() {

    }

    @Override
    public void onCameraViewSetup() {
        if (AUTOSTART) {
            new Handler().post(new Runnable() {
                @Override
                public void run() {
                    lightenTorch();
                }
            });
        } else {
            mRemoteUi.setUiButtonsOn(false);
        }
        mScreenStateListener.register();
        mSystemManager.setWakeLock();
    }

    @Override
    public void onCameraBusy() {
        if (AppGlobals.isWidgetTapped()) {
            mRemoteUi.setUiButtonsOn(false);
            Helpers.showFlashlightBusyToast(getApplicationContext());
            AppGlobals.setIsWidgetTapped(false);
        } else {
            Helpers.showFlashlightBusyDialog(MainActivity.getInstance());
        }
        stopSelf();
    }
}
