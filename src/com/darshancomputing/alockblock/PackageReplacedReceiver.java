/*
    Copyright (c) 2009-2015 Darshan-Josiah Barber

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
import android.preference.PreferenceManager;

public class PackageReplacedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences sp_store = context.getSharedPreferences("sp_store", 0);

        if (sp_store.getBoolean(ALockBlockService.KEY_DISABLE_LOCKING, false) ||
            settings.getBoolean(SettingsActivity.KEY_ALWAYS_SHOW_NOTIFICATION, false)) {
            ComponentName comp = new ComponentName(context.getPackageName(),
                                                   ALockBlockService.class.getName());
            context.startService(new Intent().setComponent(comp));
        }
    }
}
