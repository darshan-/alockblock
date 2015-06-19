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

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.preference.PreferenceManager;
import android.util.Log;

public class PlugInOutReceiver extends BroadcastReceiver {
    private static final String LOG_TAG = "A Lock Block - PlugInOutReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences sp_store = context.getSharedPreferences("sp_store", 0);
        SharedPreferences.Editor editor = sp_store.edit();

        if (settings.getBoolean(SettingsActivity.KEY_AUTO_DISABLE_LOCKING, false)) {
            if (Intent.ACTION_POWER_CONNECTED.equals(intent.getAction()))
                editor.putBoolean(ALockBlockService.KEY_DISABLE_LOCKING, true);
            else if (Intent.ACTION_POWER_DISCONNECTED.equals(intent.getAction()))
                editor.putBoolean(ALockBlockService.KEY_DISABLE_LOCKING, false);

            editor.commit();

            context.startService(new Intent(context, ALockBlockService.class));
        }
    }
}
