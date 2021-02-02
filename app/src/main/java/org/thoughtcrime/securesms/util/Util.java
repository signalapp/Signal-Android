/*
 * Copyright (C) 2011 Whisper Systems
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
package org.thoughtcrime.securesms.util;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import android.text.TextUtils;

import org.session.libsession.utilities.TextSecurePreferences;
import org.thoughtcrime.securesms.components.ComposeText;
import org.session.libsession.messaging.threads.Address;
import org.thoughtcrime.securesms.mms.OutgoingLegacyMmsConnection;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import network.loki.messenger.BuildConfig;

public class Util {

  public static boolean isEmpty(ComposeText value) {
    return value == null || value.getText() == null || TextUtils.isEmpty(value.getTextTrimmed());
  }

  /**
   * The app version.
   * <p>
   * This code should be used in all places that compare app versions rather than
   * {@link #getManifestApkVersion(Context)} or {@link BuildConfig#VERSION_CODE}.
   */
  public static int getCanonicalVersionCode() {
    return BuildConfig.CANONICAL_VERSION_CODE;
  }

  /**
   * {@link BuildConfig#VERSION_CODE} may not be the actual version due to ABI split code adding a
   * postfix after BuildConfig is generated.
   * <p>
   * However, in most cases you want to use {@link BuildConfig#CANONICAL_VERSION_CODE} via
   * {@link #getCanonicalVersionCode()}
   */
  public static int getManifestApkVersion(Context context) {
    try {
      return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
    } catch (PackageManager.NameNotFoundException e) {
      throw new AssertionError(e);
    }
  }

  @TargetApi(VERSION_CODES.LOLLIPOP)
  public static boolean isMmsCapable(Context context) {
    return (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) || OutgoingLegacyMmsConnection.isConnectionPossible(context);
  }
}
