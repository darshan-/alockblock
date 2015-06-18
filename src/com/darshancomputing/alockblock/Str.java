/*
    Copyright (c) 2010-2013 Darshan-Josiah Barber

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

import android.content.res.Resources;
import android.view.WindowManager;

/* TODO?: have a public instance in the service and grab the server's instance from all other classes? */
public class Str {
    private Resources res;

    public String yes;
    public String cancel;
    public String okay;

    public String currently_set_to;

    public Str(Resources r) {
        res = r;

        yes                = res.getString(R.string.yes);
        cancel             = res.getString(R.string.cancel);
        okay               = res.getString(R.string.okay);

        currently_set_to    = res.getString(R.string.currently_set_to);
    }

    public static int indexOf(String[] a, String key) {
        for (int i=0, size=a.length; i < size; i++)
            if (key.equals(a[i])) return i;

        return -1;
    }
}
