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

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.PagerTabStrip;
import android.support.v4.view.ViewPager;

public class ALockBlockActivity extends FragmentActivity {
    private ALockBlockPagerAdapter pagerAdapter;
    private ViewPager viewPager;
    public static ALockBlockFragment aLockBlockFragment;

    public Context context;
    public Resources res;
    public Str str;
    public SharedPreferences settings;
    public SharedPreferences sp_store;

    private static final String LOG_TAG = "A Lock Block";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        context = getApplicationContext();
        res = getResources();
        str = new Str(res);
        loadSettingsFiles();

        if (android.os.Build.VERSION.SDK_INT >= 14) {
            getActionBar().setHomeButtonEnabled(true);
            getActionBar().setDisplayShowHomeEnabled(true);
        }

        super.onCreate(savedInstanceState); // Recreates Fragments, so only call after doing necessary setup

        setContentView(R.layout.battery_info);

        pagerAdapter = new ALockBlockPagerAdapter(getSupportFragmentManager());
        viewPager = (ViewPager) findViewById(R.id.pager);
        viewPager.setAdapter(pagerAdapter);

        PagerTabStrip tabStrip = (PagerTabStrip) findViewById(R.id.pager_tab_strip);
        tabStrip.setTabIndicatorColor(0x33b5e5);

        viewPager.setCurrentItem(1);
    }

    public void loadSettingsFiles() {
        settings = context.getSharedPreferences(SettingsActivity.SETTINGS_FILE, Context.MODE_MULTI_PROCESS);
        sp_store = context.getSharedPreferences(SettingsActivity.SP_STORE_FILE, Context.MODE_MULTI_PROCESS);
    }

    public class ALockBlockPagerAdapter extends FragmentPagerAdapter {
        public ALockBlockPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public int getCount() {
            return 1; // TODO
        }

        // TODO: Put Fragment types and page titles in Arrays or Map or something.
        @Override
        public Fragment getItem(int position) {
            if (aLockBlockFragment == null)
                aLockBlockFragment = new ALockBlockFragment();
            return aLockBlockFragment;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            if (position == 1) return res.getString(R.string.tab_current_info).toUpperCase();
            else               return res.getString(R.string.tab_history).toUpperCase();
        }
    }
}
