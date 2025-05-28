package com.cellsignalstrength;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";
    private static final String PREFS_NAME = "SIMSelection";
    private static final String AUTO_START_KEY = "AutoStart";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive: Received intent: " + intent.getAction());

        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            boolean isAutoStartEnabled = prefs.getBoolean(AUTO_START_KEY, false);

            Log.d(TAG, "onReceive: Auto start enabled: " + isAutoStartEnabled);

            if (isAutoStartEnabled) {
                Log.d(TAG, "onReceive: Auto start enabled, starting SignalStrengthService");
                Intent serviceIntent = new Intent(context, SignalStrengthService.class);

                try {
                    // For devices running Android O (API level 26) or higher, use startForegroundService
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent);
                    } else {
                        context.startService(serviceIntent);
                    }
                    Log.d(TAG, "onReceive: Service start attempted successfully");
                } catch (Exception e) {
                    Log.e(TAG, "onReceive: Failed to start service", e);
                }
            } else {
                Log.d(TAG, "onReceive: Auto start not enabled");
            }
        }
    }
}