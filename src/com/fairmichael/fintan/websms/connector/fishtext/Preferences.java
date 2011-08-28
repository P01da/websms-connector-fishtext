/*
 * Copyright (C) 2010-2011 Felix Bechstein, Fintan Fairmichael
 * 
 * This file is part of WebSMS.
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; If not, see <http://www.gnu.org/licenses/>.
 */
package com.fairmichael.fintan.websms.connector.fishtext;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.widget.Toast;

/**
 * Preferences.
 * 
 * @author flx
 */
public final class Preferences extends PreferenceActivity implements OnSharedPreferenceChangeListener {
  /** Preference key: enabled. */
  static final String PREFS_ENABLED = "enable_fishtext";
  /** Preference's name: user's password. */
  static final String PREFS_PASSWORD = "password_fishtext";

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    this.addPreferencesFromResource(R.xml.connector_fishtext_prefs);
    SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
    sp.registerOnSharedPreferenceChangeListener(this);
  }

  private static final int MAX_PASSWORD_LENGTH = 12;

  @Override
  public void onSharedPreferenceChanged(final SharedPreferences sp, final String key) {
    if (key.equals(PREFS_PASSWORD)) {
      String value = sp.getString(PREFS_PASSWORD, null);
      if (value.length() > MAX_PASSWORD_LENGTH) {
        sp.edit().putString(PREFS_PASSWORD, value.substring(0, MAX_PASSWORD_LENGTH)).commit();
        Context context = this.getApplicationContext();
        Toast.makeText(context, R.string.warn_fishtext_password_length, Toast.LENGTH_LONG).show();
      }
    }
  }
}
