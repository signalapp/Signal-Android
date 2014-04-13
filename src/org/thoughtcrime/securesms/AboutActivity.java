/*
 * Copyright (C) 2014 Open Whisper Systems and the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.thoughtcrime.securesms;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.content.Context;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockPreferenceActivity;
import com.actionbarsherlock.view.MenuItem;

import org.thoughtcrime.securesms.ApplicationListener;
import org.thoughtcrime.securesms.Constants;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicTheme;

public final class AboutActivity extends SherlockPreferenceActivity
{
  private final DynamicTheme    dynamicTheme    = new DynamicTheme   ();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private static final String KEY_ABOUT_VERSION = "about_version";
  private static final String KEY_ABOUT_CHANGES = "about_changes";
  private static final String KEY_ABOUT_SOURCE = "about_source_code";
  private static final String KEY_ABOUT_SUPPORT = "about_support";
  private static final String KEY_ABOUT_BUGREPORTS = "about_bugreports";
  private static final String KEY_ABOUT_LICENSE = "about_license";

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		dynamicTheme.onCreate(this);
		dynamicLanguage.onCreate(this);
		super.onCreate(savedInstanceState);

		addPreferencesFromResource(R.xml.about);

		final ActionBar actionBar = getSupportActionBar();
		actionBar.setDisplayHomeAsUpEnabled(true);

                Context context = AboutActivity.this;
                findPreference(KEY_ABOUT_VERSION).setSummary(Util.getAppAndVersionName(context));
                findPreference(KEY_ABOUT_CHANGES).setSummary(Constants.CHANGES_URL);
                findPreference(KEY_ABOUT_SOURCE).setSummary(Constants.SOURCE_URL);
                findPreference(KEY_ABOUT_SUPPORT).setSummary(Constants.SUPPORT_URL);
                findPreference(KEY_ABOUT_BUGREPORTS).setSummary(Constants.BUGREPORTS_URL);
                findPreference(KEY_ABOUT_LICENSE).setSummary(Constants.LICENSE_URL);

	}


	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
		switch (item.getItemId())
		{
			case android.R.id.home:
				finish();
				return true;
                }

                return super.onOptionsItemSelected(item);
        }


	@Override
	public boolean onPreferenceTreeClick(final PreferenceScreen preferenceScreen, final Preference preference)
	{
		final String key = preference.getKey();
		if (KEY_ABOUT_CHANGES.equals(key))
		{
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.CHANGES_URL)));
		}
                else if (KEY_ABOUT_SOURCE.equals(key))
                {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.SOURCE_URL)));
                }
                else if (KEY_ABOUT_SUPPORT.equals(key))
                {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.SUPPORT_URL)));
                }
                else if (KEY_ABOUT_BUGREPORTS.equals(key))
                {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.BUGREPORTS_URL)));
                }
                else if (KEY_ABOUT_LICENSE.equals(key))
                {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.LICENSE_URL)));
                }

		return false;
	}
}
