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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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

import android.support.v4.app.NotificationCompat;

public class ALockBlockService extends Service {
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
    private boolean holding_lock = false;
    private android.os.Vibrator mVibrator;
    private android.media.AudioManager mAudioManager;

    private Bitmap largeIconU;
    private Bitmap largeIconL;

    private Context context;
    private Resources res;
    private Str str;
    private long now;
    private boolean updated_lasts;
    private static java.util.HashSet<Messenger> clientMessengers;
    private static Messenger messenger;

    private static HashSet<Integer> widgetIds = new HashSet<Integer>();
    private static AppWidgetManager widgetManager;

    private static final String LOG_TAG = "A Lock Block - ALockBlockService";

    private static final int NOTIFICATION_KG_UNLOCKED  = 2;

    public static final String KEY_DISABLE_LOCKING = "disable_lock_screen";
    public static final String LAST_SDK_API = "last_sdk_api";

    public static final String EXTRA_ACTION = "com.darshancomputing.alockblock.action";
    public static final String ACTION_REENABLE = "re-enable";
    public static final String ACTION_DISABLE = "disable";


    private static final Object[] EMPTY_OBJECT_ARRAY = {};
    private static final  Class[]  EMPTY_CLASS_ARRAY = {};

    private final Handler mHandler = new Handler();

    private final Runnable runDisableKeyguard = new Runnable() {
        public void run() {
            kl = km.newKeyguardLock(getPackageName());
            kl.disableKeyguard();
            holding_lock = true;
            maximize();
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

        // Version 11+ is needed to get large icon dimensions.  Versions prior to 11 ignore large icon anyway.
        //   Recent versions scale for us, but older (11+) versions don't scale, so we need to.
        if (android.os.Build.VERSION.SDK_INT >= 11) {
            int liw = getResources().getDimensionPixelSize(android.R.dimen.notification_large_icon_wid‌​th);
            int lih = getResources().getDimensionPixelSize(android.R.dimen.notification_large_icon_hei‌​ght);

            Bitmap largeIconUo = BitmapFactory.decodeResource(getResources(), R.drawable.padlock_unlocked_small);
            Bitmap largeIconLo = BitmapFactory.decodeResource(getResources(), R.drawable.padlock_locked_small);

            largeIconU = Bitmap.createScaledBitmap(largeIconUo, liw, lih, false);
            largeIconL = Bitmap.createScaledBitmap(largeIconLo, liw, lih, false);

            largeIconUo.recycle();
            largeIconLo.recycle();
        }

        km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);

        if (sp_store.getBoolean(KEY_DISABLE_LOCKING, false))
            setEnablednessOfKeyguard(false);
    }

    @Override
    public void onDestroy() {
        setEnablednessOfKeyguard(true);
        stopForeground(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getStringExtra(EXTRA_ACTION);

        if (ACTION_REENABLE.equals(action)) {
            SharedPreferences.Editor editor = sp_store.edit();
            editor.putBoolean(ALockBlockService.KEY_DISABLE_LOCKING, false);
            editor.commit();
        } else if (ACTION_DISABLE.equals(action)) {
            SharedPreferences.Editor editor = sp_store.edit();
            editor.putBoolean(ALockBlockService.KEY_DISABLE_LOCKING, true);
            editor.commit();
        }

        reloadSettings(false);

        return Service.START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return messenger.getBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (! (sp_store.getBoolean(KEY_DISABLE_LOCKING, false)))
            stopSelf();

        return true;
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
        public static final int CLIENT_KEYGUARD_UPDATED = 1;

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

    private void setEnablednessOfKeyguard(boolean enabled) {
        if (enabled) {
            minimize();

            if (! holding_lock) return;

            if (kl != null) {
                unregisterReceiver(mUserPresentReceiver);
                mHandler.removeCallbacks(runDisableKeyguard);
                kl.reenableKeyguard();
                kl = null;
            }

            holding_lock = false;

            if (clientMessengers.size() == 0) stopSelf();
        } else {
            if (km.inKeyguardRestrictedInputMode()) {
                notifyNeedUnlock();
                registerReceiver(mUserPresentReceiver, userPresent);
            } else {
                if (kl != null) {
                    kl.reenableKeyguard();
                    holding_lock = false;
                } else {
                    // This is just a bit of a hack to make it safe to unregister later without restriction.
                    //   The other option is to have a boolean keep track of whether it is registered
                    registerReceiver(mUserPresentReceiver, userPresent);
                }

                mHandler.removeCallbacks(runDisableKeyguard);
                mHandler.postDelayed(runDisableKeyguard, 300);
            }
        }

        updateClientKeyguardStatus();
    }

    private void maximize() {
        mNotificationManager.cancelAll();
        stopForeground(true);

        Intent mainWindowIntent = new Intent(context, ALockBlockActivity.class);
        mainWindowPendingIntent = PendingIntent.getActivity(context, 0, mainWindowIntent, 0);

        ComponentName comp = new ComponentName(getPackageName(), ALockBlockService.class.getName());
        Intent reEnableIntent = new Intent().setComponent(comp).putExtra(EXTRA_ACTION, ACTION_REENABLE);
        PendingIntent reEnablePendingIntent = PendingIntent.getService(this, 0, reEnableIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        NotificationCompat.Builder kgunb = new NotificationCompat.Builder(this)
            .setSmallIcon(R.drawable.kg_unlocked)
            .setLargeIcon(largeIconU)
            .setContentTitle("Lock Screen Disabled")
            .setContentText("A Lock Block")
            .setContentIntent(mainWindowPendingIntent)
            .setShowWhen(false)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX);

        if (settings.getBoolean(SettingsActivity.KEY_REENABLE_FROM_NOTIFICATION, false))
            kgunb.addAction(R.drawable.ic_menu_login, "Re-enable", reEnablePendingIntent);

        startForeground(NOTIFICATION_KG_UNLOCKED, kgunb.build());
    }

    private void minimize() {
        mNotificationManager.cancelAll();
        stopForeground(true);

        if (!settings.getBoolean(SettingsActivity.KEY_ALWAYS_SHOW_NOTIFICATION, false))
            return;

        Intent mainWindowIntent = new Intent(context, ALockBlockActivity.class);
        mainWindowPendingIntent = PendingIntent.getActivity(context, 0, mainWindowIntent, 0);

        ComponentName comp = new ComponentName(getPackageName(), ALockBlockService.class.getName());
        Intent disableIntent = new Intent().setComponent(comp).putExtra(EXTRA_ACTION, ACTION_DISABLE);
        PendingIntent disablePendingIntent = PendingIntent.getService(this, 0, disableIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        NotificationCompat.Builder kgunb = new NotificationCompat.Builder(this)
            .setSmallIcon(R.drawable.kg_unlocked)
            .setLargeIcon(largeIconL)
            .setContentTitle("Lock Screen Enabled")
            .setContentText("A Lock Block")
            .setContentIntent(mainWindowPendingIntent)
            .setShowWhen(false)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN);

        if (settings.getBoolean(SettingsActivity.KEY_REENABLE_FROM_NOTIFICATION, false))
            kgunb.addAction(R.drawable.ic_menu_login, "Disable", disablePendingIntent);

        mNotificationManager.notify(NOTIFICATION_KG_UNLOCKED, kgunb.build());
    }

    private void notifyNeedUnlock() {
        mNotificationManager.cancelAll();
        stopForeground(true);

        Intent mainWindowIntent = new Intent(context, ALockBlockActivity.class);
        mainWindowPendingIntent = PendingIntent.getActivity(context, 0, mainWindowIntent, 0);

        NotificationCompat.Builder kgunb = new NotificationCompat.Builder(this)
            .setSmallIcon(R.drawable.kg_unlocked)
            .setLargeIcon(largeIconU)
            .setContentTitle("Please Unlock the Screen")
            .setContentText("A Lock Block")
            .setContentIntent(mainWindowPendingIntent)
            .setShowWhen(false)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX);

        mNotificationManager.notify(NOTIFICATION_KG_UNLOCKED, kgunb.build());
    }

    private void updateClientKeyguardStatus() {
        for (Messenger messenger : clientMessengers) {
            sendClientMessage(messenger, RemoteConnection.CLIENT_KEYGUARD_UPDATED);
        }
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
}
