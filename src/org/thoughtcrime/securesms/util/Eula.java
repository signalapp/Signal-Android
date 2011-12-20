package org.thoughtcrime.securesms.util;


/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.InputStreamReader;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.SecureSMS;

/**
 * Displays an EULA ("End User License Agreement") that the user has to accept before
 * using the application. Your application should call {@link Eula#showEula(android.app.Activity)}
 * in the onCreate() method of the first activity. If the user accepts the EULA, it will never
 * be shown again. If the user refuses, {@link android.app.Activity#finish()} is invoked
 * on your activity.
 */
public class Eula {
    private static final String PREFERENCE_EULA_ACCEPTED = "eula.accepted";
    private static final String PREFERENCES_EULA = "eula";
    
    private static final String PREFERENCE_DISCLAIMER_ACCEPTED = "disclaimer.accepted";

    public static boolean seenDisclaimer(final Activity activity) {
    	return activity.getSharedPreferences(PREFERENCES_EULA, Activity.MODE_PRIVATE).getBoolean(PREFERENCE_DISCLAIMER_ACCEPTED, false);
    }
    
    /**
     * Displays the EULA if necessary. This method should be called from the onCreate()
     * method of your main Activity.
     *
     * @param activity The Activity to finish if the user rejects the EULA.
     */    
    public static void showEula(final SecureSMS activity) {
        final SharedPreferences preferences = activity.getSharedPreferences(PREFERENCES_EULA,
                Activity.MODE_PRIVATE);
        if (!preferences.getBoolean(PREFERENCE_EULA_ACCEPTED, false)) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle("End User License Agreement");
            builder.setCancelable(true);
            builder.setPositiveButton("Accept", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    accept(activity);
                }
            });
            builder.setNegativeButton("Refuse", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    refuse(activity);
                }
            });
            builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                public void onCancel(DialogInterface dialog) {
                    refuse(activity);
                }
            });
            builder.setMessage(readFile(activity, R.raw.eula));
            builder.create().show();
        } else {
        	activity.eulaComplete();
        }
    }

    private static void acceptDisclaimer(SecureSMS activity) {
    	activity.getSharedPreferences(PREFERENCES_EULA, Activity.MODE_PRIVATE).edit().putBoolean(PREFERENCE_DISCLAIMER_ACCEPTED, true).commit();
    	activity.eulaComplete();
    }
    
    private static void accept(SecureSMS activity) {
        activity.getSharedPreferences(PREFERENCES_EULA, Activity.MODE_PRIVATE).edit().putBoolean(PREFERENCE_EULA_ACCEPTED, true).commit();
        showDisclaimer(activity);
    }

    private static void refuse(Activity activity) {
    	Intent intent = new Intent(Intent.ACTION_DELETE, Uri.parse("package:org.thoughtcrime.securesms"));
    	activity.startActivity(intent);
        activity.finish();
    }

    public static void showDisclaimer(final SecureSMS activity) {
    	if (!seenDisclaimer(activity)) {
	        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
	        builder.setMessage(readFile(activity, R.raw.disclaimer));
	        builder.setCancelable(true);
	        builder.setTitle("Please Note");
	        builder.setPositiveButton("I understand", new DialogInterface.OnClickListener() {				
				public void onClick(DialogInterface dialog, int which) {
					acceptDisclaimer(activity);
				}
			});
	        builder.create().show();
    	} else {
    		activity.eulaComplete();
    	}
    }

    private static CharSequence readFile(Activity activity, int id) {
        BufferedReader in = null;
        try {
            in = new BufferedReader(new InputStreamReader(
                    activity.getResources().openRawResource(id)));
            String line;
            StringBuilder buffer = new StringBuilder();
            while ((line = in.readLine()) != null) buffer.append(line).append('\n');
            return buffer;
        } catch (IOException e) {
            return "";
        } finally {
            closeStream(in);
        }
    }

    /**
     * Closes the specified stream.
     *
     * @param stream The stream to close.
     */
    private static void closeStream(Closeable stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }
}

