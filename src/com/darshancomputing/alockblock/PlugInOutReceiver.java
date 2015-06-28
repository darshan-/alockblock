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
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;

public class PlugInOutReceiver extends BroadcastReceiver {
    private static final String LOG_TAG = "A Lock Block - PlugInOutReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences settings = context.getSharedPreferences(SettingsActivity.SETTINGS_FILE,
                                                                  Context.MODE_MULTI_PROCESS);
        SharedPreferences sp_store = context.getSharedPreferences(SettingsActivity.SP_STORE_FILE,
                                                                  Context.MODE_MULTI_PROCESS);
        SharedPreferences.Editor editor = sp_store.edit();

        if (settings.getBoolean(SettingsActivity.KEY_AUTO_DISABLE_LOCKING, false)) {
            if (Intent.ACTION_POWER_CONNECTED.equals(intent.getAction())) {
                if (sp_store.getBoolean(ALockBlockService.KEY_DISABLE_LOCKING, false))
                    return;

                editor.putBoolean(ALockBlockService.KEY_DISABLE_LOCKING, true);
            } else if (Intent.ACTION_POWER_DISCONNECTED.equals(intent.getAction())) {
                if (! sp_store.getBoolean(ALockBlockService.KEY_DISABLE_LOCKING, false))
                    return;

                editor.putBoolean(ALockBlockService.KEY_DISABLE_LOCKING, false);

                /* If the screen was on, "inside" the keyguard, when the keyguard was disabled, then we're
                   still inside it now, even if the screen is off.  So we aquire a wakelock that forces the
                   screen to turn on, then release it.  If the screen is on now, this has no effect, but
                   if it's off, then either the user will press the power button or the screen will turn
                   itself off after the normal timeout.  Either way, when the screen goes off, the keyguard
                   will now be enabled properly. */
                PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK |
                                                          PowerManager.ACQUIRE_CAUSES_WAKEUP |
                                                          PowerManager.ON_AFTER_RELEASE,
                                                          context.getPackageName());
                wl.acquire();
                wl.release();
            }

            editor.commit();

            context.startService(new Intent(context, ALockBlockService.class));
        }
    }
}
