/*
    Copyright (c) 2013 Darshan-Josiah Barber

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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class ALockBlockActivity extends Activity {
    public Context context;
    public Resources res;
    public Str str;
    public SharedPreferences settings;
    public SharedPreferences sp_store;

    private Intent biServiceIntent;
    private Messenger serviceMessenger;
    private final Messenger messenger = new Messenger(new MessageHandler());
    private ALockBlockService.RemoteConnection serviceConnection;
    private boolean serviceConnected;

    private Button toggle_lock_screen_b;
    private ImageView padlock;

    private static final String LOG_TAG = "A Lock Block - ALockBlockActivity";

    private final Handler mHandler = new Handler();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        context = getApplicationContext();
        res = getResources();
        str = new Str(res);
        loadSettingsFiles();

        if (settings.getBoolean(SettingsActivity.KEY_FIRST_RUN, true)) {
            // If you ever need a first-run dialog again, this is when you would show it
            SharedPreferences.Editor editor = sp_store.edit();
            editor.putBoolean(SettingsActivity.KEY_FIRST_RUN, false);
            editor.commit();
        }

        setContentView(R.layout.current_info);

        toggle_lock_screen_b = (Button) findViewById(R.id.toggle_lock_screen_b);
        padlock = (ImageView) findViewById(R.id.padlock);

        updateLockscreenButtons();
        bindButtons();

        serviceConnection = new ALockBlockService.RemoteConnection(messenger);

        biServiceIntent = new Intent(context, ALockBlockService.class);
        context.startService(biServiceIntent);
        bindService();
    }

    public void loadSettingsFiles() {
        settings = context.getSharedPreferences(SettingsActivity.SETTINGS_FILE, Context.MODE_MULTI_PROCESS);
        sp_store = context.getSharedPreferences(SettingsActivity.SP_STORE_FILE, Context.MODE_MULTI_PROCESS);
    }

    public void bindService() {
        if (! serviceConnected) {
            context.bindService(biServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
            serviceConnected = true;
        }
    }

    public class MessageHandler extends Handler {
        @Override
        public void handleMessage(Message incoming) {
            if (! serviceConnected) {
                Log.i(LOG_TAG, "serviceConected is false; ignoring message: " + incoming);
                return;
            }

            switch (incoming.what) {
            case ALockBlockService.RemoteConnection.CLIENT_SERVICE_CONNECTED:
                serviceMessenger = incoming.replyTo;
                sendServiceMessage(ALockBlockService.RemoteConnection.SERVICE_REGISTER_CLIENT);
                break;
            case ALockBlockService.RemoteConnection.CLIENT_KEYGUARD_UPDATED:
                updateLockscreenButtons();
                break;
            default:
                super.handleMessage(incoming);
            }
        }
    }

    private void sendServiceMessage(int what) {
        Message outgoing = Message.obtain();
        outgoing.what = what;
        outgoing.replyTo = messenger;
        try { if (serviceMessenger != null) serviceMessenger.send(outgoing); } catch (android.os.RemoteException e) {}
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (serviceConnected) {
            context.unbindService(serviceConnection);
            serviceConnected = false;
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (serviceMessenger != null)
            sendServiceMessage(ALockBlockService.RemoteConnection.SERVICE_REGISTER_CLIENT);
    }

    @Override
    public void onPause() {
        super.onPause();

        if (serviceMessenger != null)
            sendServiceMessage(ALockBlockService.RemoteConnection.SERVICE_UNREGISTER_CLIENT);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        return true;
    }

    private void toggleShowNotification() {
            SharedPreferences.Editor editor = sp_store.edit();
            editor.putBoolean(ALockBlockService.KEY_SHOW_NOTIFICATION,
                              ! sp_store.getBoolean(ALockBlockService.KEY_SHOW_NOTIFICATION, true));
            editor.commit();

            Message outgoing = Message.obtain();
            outgoing.what = ALockBlockService.RemoteConnection.SERVICE_CANCEL_NOTIFICATION_AND_RELOAD_SETTINGS;
            try { if (serviceMessenger != null) serviceMessenger.send(outgoing); } catch (android.os.RemoteException e) {}
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.menu_settings:
            mStartActivity(SettingsActivity.class);
            return true;
        case R.id.menu_help:
            mStartActivity(HelpActivity.class);
            return true;
        case R.id.menu_rate_and_review:
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                                         Uri.parse("market://details?id=com.darshancomputing.alockblock")));
            } catch (Exception e) {
                Toast.makeText(getApplicationContext(), "Sorry, can't launch Market!", Toast.LENGTH_SHORT).show();
            }
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    private void updateLockscreenButtons() {
        loadSettingsFiles();

        if (sp_store.getBoolean(ALockBlockService.KEY_DISABLE_LOCKING, false)) {
            toggle_lock_screen_b.setText(res.getString(R.string.reenable_lock_screen));
            padlock.setImageResource(R.drawable.padlock_unlocked);
        } else {
            toggle_lock_screen_b.setText(res.getString(R.string.disable_lock_screen));
            padlock.setImageResource(R.drawable.padlock_locked);
        }
    }

     private void setDisableLocking(boolean b) {
        SharedPreferences.Editor editor = sp_store.edit();
        editor.putBoolean(ALockBlockService.KEY_DISABLE_LOCKING, b);
        editor.commit();

        Message outgoing = Message.obtain();
        outgoing.what = ALockBlockService.RemoteConnection.SERVICE_RELOAD_SETTINGS;
        try { if (serviceMessenger != null) serviceMessenger.send(outgoing); } catch (android.os.RemoteException e) {}

        updateLockscreenButtons();

        if (settings.getBoolean(SettingsActivity.KEY_FINISH_AFTER_TOGGLE_LOCK, false)) finish();
    }

    /* Toggle Lock Screen */
    private final OnClickListener tlsButtonListener = new OnClickListener() {
        public void onClick(View v) {
            if (sp_store.getBoolean(ALockBlockService.KEY_DISABLE_LOCKING, false)) {
                setDisableLocking(false);
            } else {
                setDisableLocking(true);
            }
        }
    };

    private void mStartActivity(Class c) {
        ComponentName comp = new ComponentName(context.getPackageName(), c.getName());
        //startActivity(new Intent().setComponent(comp));
        startActivityForResult(new Intent().setComponent(comp), 1);
        //finish();
    }

    private void bindButtons() {
        toggle_lock_screen_b.setOnClickListener(tlsButtonListener);
        padlock.setOnClickListener(tlsButtonListener);
    }
}
