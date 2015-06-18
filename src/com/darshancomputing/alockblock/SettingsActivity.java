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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.WindowManager;
import java.util.Locale;

public class SettingsActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {
    public static final String SETTINGS_FILE = "com.darshancomputing.alockblock_preferences";
    public static final String SP_STORE_FILE = "sp_store";

    public static final String KEY_CONFIRM_DISABLE_LOCKING = "confirm_disable_lock_screen";
    public static final String KEY_FINISH_AFTER_TOGGLE_LOCK = "finish_after_toggle_lock";
    public static final String KEY_AUTO_DISABLE_LOCKING = "auto_disable_lock_screen";
    public static final String KEY_DISALLOW_DISABLE_LOCK_SCREEN = "disallow_disable_lock_screen";
    public static final String KEY_MAIN_NOTIFICATION_PRIORITY = "main_notification_priority";
    public static final String KEY_AUTOSTART = "autostart";
    public static final String KEY_USE_SYSTEM_NOTIFICATION_LAYOUT = "use_system_notification_layout";
    public static final String KEY_FIRST_RUN = "first_run";

    private static final String[] PARENTS    = {};
    private static final String[] DEPENDENTS = {};

    private static final String[] LIST_PREFS = {KEY_AUTOSTART, KEY_MAIN_NOTIFICATION_PRIORITY};

    private static final String[] RESET_SERVICE = {KEY_AUTO_DISABLE_LOCKING};

    private static final String[] RESET_SERVICE_WITH_CANCEL_NOTIFICATION = {KEY_MAIN_NOTIFICATION_PRIORITY,
                                                                            KEY_USE_SYSTEM_NOTIFICATION_LAYOUT
    };

    private Intent biServiceIntent;
    private Messenger serviceMessenger;
    private final Messenger messenger = new Messenger(new MessageHandler());
    private final ALockBlockService.RemoteConnection serviceConnection = new ALockBlockService.RemoteConnection(messenger);

    private Resources res;
    private PreferenceScreen mPreferenceScreen;
    private SharedPreferences mSharedPreferences;

    private String pref_screen;

    private int menu_res = R.menu.settings;

    private static final Object[] EMPTY_OBJECT_ARRAY = {};
    private static final  Class[]  EMPTY_CLASS_ARRAY = {};

    public class MessageHandler extends Handler {
        @Override
        public void handleMessage(Message incoming) {
            switch (incoming.what) {
            case ALockBlockService.RemoteConnection.CLIENT_SERVICE_CONNECTED:
                serviceMessenger = incoming.replyTo;
                break;
            default:
                super.handleMessage(incoming);
            }
        }
    }

    private final Handler mHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        res = getResources();

        // Stranglely disabled by default for API level 14+
        if (android.os.Build.VERSION.SDK_INT >= 14) {
            getActionBar().setHomeButtonEnabled(true);
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }

        PreferenceManager pm = getPreferenceManager();
        pm.setSharedPreferencesName(SETTINGS_FILE);
        pm.setSharedPreferencesMode(Context.MODE_MULTI_PROCESS);
        mSharedPreferences = pm.getSharedPreferences();

        setPrefScreen(R.xml.main_pref_screen);
        setWindowSubtitle(res.getString(R.string.settings_activity_subtitle));

        for (int i=0; i < PARENTS.length; i++)
            setEnablednessOfDeps(i);

        for (int i=0; i < LIST_PREFS.length; i++)
            updateListPrefSummary(LIST_PREFS[i]);

        biServiceIntent = new Intent(this, ALockBlockService.class);
        bindService(biServiceIntent, serviceConnection, 0);
    }

    private void setWindowSubtitle(String subtitle) {
        if (res.getBoolean(R.bool.long_activity_names))
            setTitle(res.getString(R.string.app_full_name) + " - " + subtitle);
        else
            setTitle(subtitle);
    }

    private void setPrefScreen(int resource) {
        addPreferencesFromResource(resource);

        mPreferenceScreen  = getPreferenceScreen();
    }

    private void restartThisScreen() {
        ComponentName comp = new ComponentName(getPackageName(), SettingsActivity.class.getName());
        Intent intent = new Intent().setComponent(comp);
        startActivity(intent);
        finish();
    }

    private void resetService() {
        resetService(false);
    }

    private void resetService(boolean cancelFirst) {
        mSharedPreferences.edit().commit(); // Force file to be saved

        Message outgoing = Message.obtain();

        if (cancelFirst)
            outgoing.what = ALockBlockService.RemoteConnection.SERVICE_CANCEL_NOTIFICATION_AND_RELOAD_SETTINGS;
        else
            outgoing.what = ALockBlockService.RemoteConnection.SERVICE_RELOAD_SETTINGS;

        try {
            serviceMessenger.send(outgoing);
        } catch (Exception e) {
            startService(new Intent(this, ALockBlockService.class));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (serviceConnection != null) unbindService(serviceConnection);
    }

    @Override
    protected void onResume() {
        super.onResume();

        mSharedPreferences.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

        mSharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(menu_res, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.menu_help:
            ComponentName comp = new ComponentName(getPackageName(), SettingsHelpActivity.class.getName());
            Intent intent = new Intent().setComponent(comp);

            startActivity(intent);

            return true;
        case android.R.id.home:
            finish();
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        Dialog dialog;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        Str str = new Str(getResources());

        switch (id) {
        default:
            dialog = null;
        }

        return dialog;
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        mSharedPreferences.unregisterOnSharedPreferenceChangeListener(this);

        for (int i=0; i < PARENTS.length; i++) {
            if (key.equals(PARENTS[i])) {
                setEnablednessOfDeps(i);
                if (i == 0) setEnablednessOfDeps(1); /* Doubled charge key */
                if (i == 2) setEnablednessOfDeps(3); /* Doubled charge key */
                break;
            }
        }

        for (int i=0; i < LIST_PREFS.length; i++) {
            if (key.equals(LIST_PREFS[i])) {
                updateListPrefSummary(LIST_PREFS[i]);
                break;
            }
        }

        for (int i=0; i < RESET_SERVICE.length; i++) {
            if (key.equals(RESET_SERVICE[i])) {
                resetService();
                break;
            }
        }

        for (int i=0; i < RESET_SERVICE_WITH_CANCEL_NOTIFICATION.length; i++) {
            if (key.equals(RESET_SERVICE_WITH_CANCEL_NOTIFICATION[i])) {
                resetService(true);
                break;
            }
        }

        mSharedPreferences.registerOnSharedPreferenceChangeListener(this);
    }

    private void setEnablednessOfDeps(int index) {
        Preference dependent = mPreferenceScreen.findPreference(DEPENDENTS[index]);
        if (dependent == null) return;

        if (mSharedPreferences.getBoolean(PARENTS[index], false))
            dependent.setEnabled(true);
        else
            dependent.setEnabled(false);

        updateListPrefSummary(DEPENDENTS[index]);
    }

    private void setEnablednessOfMutuallyExclusive(String key1, String key2) {
        Preference pref1 = mPreferenceScreen.findPreference(key1);
        Preference pref2 = mPreferenceScreen.findPreference(key2);

        if (pref1 == null) return;

        if (mSharedPreferences.getBoolean(key1, false))
            pref2.setEnabled(false);
        else if (mSharedPreferences.getBoolean(key2, false))
            pref1.setEnabled(false);
        else {
            pref1.setEnabled(true);
            pref2.setEnabled(true);
        }
    }

    private void updateListPrefSummary(String key) {
        ListPreference pref;
        try { /* Code is simplest elsewhere if we call this on all dependents, but some aren't ListPreferences. */
            pref = (ListPreference) mPreferenceScreen.findPreference(key);
        } catch (java.lang.ClassCastException e) {
            return;
        }

        if (pref == null) return;

        if (pref.isEnabled()) {
            pref.setSummary(res.getString(R.string.currently_set_to) + pref.getEntry());
        } else {
            pref.setSummary(res.getString(R.string.currently_disabled));
        }
    }
}
