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
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;
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

import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;

public class ALockBlockFragment extends Fragment {
    private static ALockBlockActivity activity;
    private Intent biServiceIntent;
    private Messenger serviceMessenger;
    private final Messenger messenger = new Messenger(new MessageHandler());
    private ALockBlockService.RemoteConnection serviceConnection;
    private boolean serviceConnected;

    private View view;
    private Button toggle_lock_screen_b;
    private ImageView padlock;

    private static final String LOG_TAG = "A Lock Block";

    private final Handler mHandler = new Handler();

    public void bindService() {
        if (! serviceConnected) {
            activity.context.bindService(biServiceIntent, serviceConnection, 0);
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
    public View onCreateView (LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.current_info, container, false);

        toggle_lock_screen_b = (Button) view.findViewById(R.id.toggle_lock_screen_b);
        padlock = (ImageView) view.findViewById(R.id.padlock);

        updateLockscreenButton();
        bindButtons();

        return view;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        activity = (ALockBlockActivity) getActivity();

        setHasOptionsMenu(true);
        //setRetainInstance(true); // TODO: Sort out a clean way to do this?

        if (activity.settings.getBoolean(SettingsActivity.KEY_FIRST_RUN, true)) {
            // If you ever need a first-run dialog again, this is when you would show it
            SharedPreferences.Editor editor = activity.sp_store.edit();
            editor.putBoolean(SettingsActivity.KEY_FIRST_RUN, false);
            editor.commit();
        }

        // TODO: everything after here could happen in another thread?
        //   They tend to take about 70ms on the myTouch
        SharedPreferences.Editor editor = activity.sp_store.edit();
        editor.putBoolean(ALockBlockService.KEY_SERVICE_DESIRED, true);
        editor.commit();

        serviceConnection = new ALockBlockService.RemoteConnection(messenger);

        biServiceIntent = new Intent(activity.context, ALockBlockService.class);
        activity.context.startService(biServiceIntent);
        bindService();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (serviceConnected) {
            activity.context.unbindService(serviceConnection);
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
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        inflater.inflate(R.menu.main, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        MenuItem snItem = menu.findItem(R.id.menu_show_notification);

        if (activity.sp_store.getBoolean(ALockBlockService.KEY_SHOW_NOTIFICATION, true)) {
            snItem.setIcon(R.drawable.ic_menu_stop);
            snItem.setTitle(R.string.menu_hide_notification);
        } else {
            snItem.setIcon(R.drawable.ic_menu_notifications);
            snItem.setTitle(R.string.menu_show_notification);
        }
    }

    private void toggleShowNotification() {
            SharedPreferences.Editor editor = activity.sp_store.edit();
            editor.putBoolean(ALockBlockService.KEY_SHOW_NOTIFICATION,
                              ! activity.sp_store.getBoolean(ALockBlockService.KEY_SHOW_NOTIFICATION, true));
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
        case R.id.menu_close:
            DialogFragment df = new ConfirmCloseDialogFragment();
            df.show(getFragmentManager(), "TODO: What is this string for?2");
            return true;
        case R.id.menu_help:
            mStartActivity(HelpActivity.class);
            return true;
        case R.id.menu_show_notification:
            toggleShowNotification();
            return true;
        case R.id.menu_rate_and_review:
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                                         Uri.parse("market://details?id=com.darshancomputing.alockblock")));
            } catch (Exception e) {
                Toast.makeText(activity.getApplicationContext(), "Sorry, can't launch Market!", Toast.LENGTH_SHORT).show();
            }
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    public static class ConfirmCloseDialogFragment extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(activity)
                .setTitle(activity.res.getString(R.string.confirm_close))
                .setMessage(activity.res.getString(R.string.confirm_close_hint))
                .setPositiveButton(activity.res.getString(R.string.yes),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface di, int id) {
                            ((ALockBlockActivity) activity).aLockBlockFragment.closeApp();
                            di.cancel();
                        }
                    })
                .setNegativeButton(activity.res.getString(R.string.cancel),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface di, int id) {
                            di.cancel();
                        }
                    })
                .create();
        }
    }

    public void closeApp() {
        SharedPreferences.Editor editor = activity.sp_store.edit();
        editor.putBoolean(ALockBlockService.KEY_SERVICE_DESIRED, false);
        editor.commit();

        activity.finishActivity(1);

        if (serviceConnected) {
            activity.context.unbindService(serviceConnection);
            activity.context.stopService(biServiceIntent);
            serviceConnected = false;
        }

        activity.finish();
    }

    private void updateLockscreenButton() {
        activity.loadSettingsFiles();

        if (activity.sp_store.getBoolean(ALockBlockService.KEY_DISABLE_LOCKING, false)) {
            toggle_lock_screen_b.setText(activity.res.getString(R.string.reenable_lock_screen));
            padlock.setImageResource(R.drawable.padlock_unlocked);
        } else {
            toggle_lock_screen_b.setText(activity.res.getString(R.string.disable_lock_screen));
            padlock.setImageResource(R.drawable.padlock_locked);
        }
    }

    private void setDisableLocking(boolean b) {
        SharedPreferences.Editor editor = activity.sp_store.edit();
        editor.putBoolean(ALockBlockService.KEY_DISABLE_LOCKING, b);
        editor.commit();

        Message outgoing = Message.obtain();
        outgoing.what = ALockBlockService.RemoteConnection.SERVICE_RELOAD_SETTINGS;
        try { if (serviceMessenger != null) serviceMessenger.send(outgoing); } catch (android.os.RemoteException e) {}

        updateLockscreenButton();

        if (activity.settings.getBoolean(SettingsActivity.KEY_FINISH_AFTER_TOGGLE_LOCK, false)) activity.finish();
    }

    /* Toggle Lock Screen */
    private final OnClickListener tlsButtonListener = new OnClickListener() {
        public void onClick(View v) {
            if (activity.sp_store.getBoolean(ALockBlockService.KEY_DISABLE_LOCKING, false)) {
                setDisableLocking(false);
            } else {
                setDisableLocking(true);
            }
        }
    };

    private void mStartActivity(Class c) {
        ComponentName comp = new ComponentName(activity.context.getPackageName(), c.getName());
        //startActivity(new Intent().setComponent(comp));
        startActivityForResult(new Intent().setComponent(comp), 1);
        //activity.finish();
    }

    private void bindButtons() {
        toggle_lock_screen_b.setOnClickListener(tlsButtonListener);
        padlock.setOnClickListener(tlsButtonListener);
    }
}
