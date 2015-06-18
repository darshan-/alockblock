/*
    Copyright (c) 2009-2013 Darshan-Josiah Barber

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.
*/

package com.darshancomputing.alockblock;

import android.app.AlarmManager;
import android.app.KeyguardManager;
import android.app.KeyguardManager.KeyguardLock;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import java.util.Date;
import java.util.HashSet;

public class ALockBlockService extends Service {
    private final IntentFilter batteryChanged = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    private final IntentFilter userPresent    = new IntentFilter(Intent.ACTION_USER_PRESENT);
    private PendingIntent mainWindowPendingIntent;
    private PendingIntent updatePredictorPendingIntent;
    private Intent alarmsIntent;

    private NotificationManager mNotificationManager;
    private AlarmManager alarmManager;
    private static SharedPreferences settings;
    private static SharedPreferences sp_store;
    private static SharedPreferences.Editor sps_editor;

    private KeyguardLock kl;
    private KeyguardManager km;
    private android.os.Vibrator mVibrator;
    private android.media.AudioManager mAudioManager;

    private Notification kgUnlockedNotification;

    private Context context;
    private Resources res;
    private Str str;
    private long now;
    private boolean updated_lasts;
    private static java.util.HashSet<Messenger> clientMessengers;
    private static Messenger messenger;

    private static HashSet<Integer> widgetIds = new HashSet<Integer>();
    private static AppWidgetManager widgetManager;

    private static final String LOG_TAG = "com.darshancomputing.alockblock - ALockBlockService";

    private static final int NOTIFICATION_PRIMARY      = 1;
    private static final int NOTIFICATION_KG_UNLOCKED  = 2;

    public static final String KEY_DISABLE_LOCKING = "disable_lock_screen";
    public static final String KEY_SERVICE_DESIRED = "serviceDesired";
    public static final String KEY_SHOW_NOTIFICATION = "show_notification";
    public static final String LAST_SDK_API = "last_sdk_api";


    private static final Object[] EMPTY_OBJECT_ARRAY = {};
    private static final  Class[]  EMPTY_CLASS_ARRAY = {};

    /* Global variables for these Notification Runnables */
    private Notification mainNotification;
    private String mainNotificationTopLine, mainNotificationBottomLine;
    private RemoteViews notificationRV;

    private final Handler mHandler = new Handler();

    private final Runnable mNotify = new Runnable() {
        public void run() {
            startForeground(NOTIFICATION_PRIMARY, mainNotification);
            mHandler.removeCallbacks(mNotify);
        }
    };

    private final Runnable runDisableKeyguard = new Runnable() {
        public void run() {
            kl = km.newKeyguardLock(getPackageName());
            kl.disableKeyguard();
            updateKeyguardNotification();
        }
    };

    @Override
    public void onCreate() {
        res = getResources();
        str = new Str(res);
        context = getApplicationContext();

        messenger = new Messenger(new MessageHandler());
        clientMessengers = new java.util.HashSet<Messenger>();

        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        loadSettingsFiles(context);
        sdkVersioning();

        Intent mainWindowIntent = new Intent(context, ALockBlockActivity.class);
        mainWindowPendingIntent = PendingIntent.getActivity(context, 0, mainWindowIntent, 0);

        kgUnlockedNotification = new Notification(R.drawable.kg_unlocked, null, 0);
        kgUnlockedNotification.flags |= Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;

        kgUnlockedNotification.setLatestEventInfo(context, "Lock Screen Disabled",
                                                  "Press to re-enable", mainWindowPendingIntent);

        km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);

        if (sp_store.getBoolean(KEY_DISABLE_LOCKING, false))
            setEnablednessOfKeyguard(false);
    }

    @Override
    public void onDestroy() {
        setEnablednessOfKeyguard(true);
        mHandler.removeCallbacks(mNotify);
        mNotificationManager.cancelAll();
        stopForeground(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return Service.START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return messenger.getBinder();
    }

    public class MessageHandler extends Handler {
        @Override
        public void handleMessage(Message incoming) {
            switch (incoming.what) {
            case RemoteConnection.SERVICE_CLIENT_CONNECTED:
                sendClientMessage(incoming.replyTo, RemoteConnection.CLIENT_SERVICE_CONNECTED);
                break;
            case RemoteConnection.SERVICE_REGISTER_CLIENT:
                clientMessengers.add(incoming.replyTo);
                break;
            case RemoteConnection.SERVICE_UNREGISTER_CLIENT:
                clientMessengers.remove(incoming.replyTo);
                break;
            case RemoteConnection.SERVICE_RELOAD_SETTINGS:
                reloadSettings(false);
                break;
            case RemoteConnection.SERVICE_CANCEL_NOTIFICATION_AND_RELOAD_SETTINGS:
                reloadSettings(true);
                break;
            default:
                super.handleMessage(incoming);
            }
        }
    }

    private static void sendClientMessage(Messenger clientMessenger, int what) {
        sendClientMessage(clientMessenger, what, null);
    }

    private static void sendClientMessage(Messenger clientMessenger, int what, Bundle data) {
        Message outgoing = Message.obtain();
        outgoing.what = what;
        outgoing.replyTo = messenger;
        outgoing.setData(data);
        try { clientMessenger.send(outgoing); } catch (android.os.RemoteException e) {}
    }

    public static class RemoteConnection implements ServiceConnection {
        // Messages clients send to the service
        public static final int SERVICE_CLIENT_CONNECTED = 0;
        public static final int SERVICE_REGISTER_CLIENT = 1;
        public static final int SERVICE_UNREGISTER_CLIENT = 2;
        public static final int SERVICE_RELOAD_SETTINGS = 3;
        public static final int SERVICE_CANCEL_NOTIFICATION_AND_RELOAD_SETTINGS = 4;

        // Messages the service sends to clients
        public static final int CLIENT_SERVICE_CONNECTED = 0;

        public Messenger serviceMessenger;
        private Messenger clientMessenger;

        public RemoteConnection(Messenger m) {
            clientMessenger = m;
        }

        public void onServiceConnected(ComponentName name, IBinder iBinder) {
            serviceMessenger = new Messenger(iBinder);

            Message outgoing = Message.obtain();
            outgoing.what = SERVICE_CLIENT_CONNECTED;
            outgoing.replyTo = clientMessenger;
            try { serviceMessenger.send(outgoing); } catch (android.os.RemoteException e) {}
        }

        public void onServiceDisconnected(ComponentName name) {
            serviceMessenger = null;
        }
    }

    private static void loadSettingsFiles(Context context) {
        settings = context.getSharedPreferences(SettingsActivity.SETTINGS_FILE, Context.MODE_MULTI_PROCESS);
        sp_store = context.getSharedPreferences(SettingsActivity.SP_STORE_FILE, Context.MODE_MULTI_PROCESS);
    }

    private void reloadSettings(boolean cancelFirst) {
        loadSettingsFiles(context);

        str = new Str(res); // Language override may have changed

        if (cancelFirst) stopForeground(true);

        if (sp_store.getBoolean(KEY_DISABLE_LOCKING, false))
            setEnablednessOfKeyguard(false);
        else
            setEnablednessOfKeyguard(true);
    }

    // Does anything needed when SDK API level increases and sets LAST_SDK_API
    private void sdkVersioning(){
        SharedPreferences.Editor sps_editor = sp_store.edit();
        SharedPreferences.Editor settings_editor = settings.edit();

        if (sp_store.getInt(LAST_SDK_API, 0) < 21 && android.os.Build.VERSION.SDK_INT >= 21) {
            settings_editor.putBoolean(SettingsActivity.KEY_USE_SYSTEM_NOTIFICATION_LAYOUT, true);
        }

        sps_editor.putInt(LAST_SDK_API, android.os.Build.VERSION.SDK_INT);

        sps_editor.commit();
        settings_editor.commit();
    }

    private void doNotify() {
        mHandler.post(mNotify);
    }

    private void setEnablednessOfKeyguard(boolean enabled) {
        if (enabled) {
            if (kl != null) {
                unregisterReceiver(mUserPresentReceiver);
                mHandler.removeCallbacks(runDisableKeyguard);
                kl.reenableKeyguard();
                kl = null;
            }
        } else {
            if (km.inKeyguardRestrictedInputMode()) {
                registerReceiver(mUserPresentReceiver, userPresent);
            } else {
                if (kl != null)
                    kl.reenableKeyguard();
                else
                    registerReceiver(mUserPresentReceiver, userPresent);

                mHandler.postDelayed(runDisableKeyguard, 300);
            }
        }

        updateKeyguardNotification();
    }

    private void updateKeyguardNotification() {
        mNotificationManager.notify(NOTIFICATION_KG_UNLOCKED, kgUnlockedNotification);
    }

    private final BroadcastReceiver mUserPresentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_USER_PRESENT.equals(intent.getAction())){
                if (sp_store.getBoolean(KEY_DISABLE_LOCKING, false))
                    setEnablednessOfKeyguard(false);
            }
        }
    };

    private String formatTime(Date d) {
        String format = android.provider.Settings.System.getString(getContentResolver(),
                                                                   android.provider.Settings.System.TIME_12_24);
        if (format == null || format.equals("12")) {
            return java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT,
                                                        java.util.Locale.getDefault()).format(d);
        } else {
            return (new java.text.SimpleDateFormat("HH:mm")).format(d);
        }
    }
}
