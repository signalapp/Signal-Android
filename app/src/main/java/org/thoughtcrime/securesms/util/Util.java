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

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.telephony.TelephonyManager;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.StyleSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;

import com.annimon.stream.Stream;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import org.signal.core.util.Base64;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.ComposeText;
import org.thoughtcrime.securesms.keyvalue.SignalStore;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class Util {
  private static final String TAG = Log.tag(Util.class);

  private static final long BUILD_LIFESPAN = TimeUnit.DAYS.toMillis(90);

  public static final String COPY_LABEL = "text\u00AD";

  public static <T> List<T> asList(T... elements) {
    List<T> result = new LinkedList<>();
    Collections.addAll(result, elements);
    return result;
  }

  public static String join(String[] list, String delimiter) {
    return join(Arrays.asList(list), delimiter);
  }

  public static <T> String join(Collection<T> list, String delimiter) {
    StringBuilder result = new StringBuilder();
    int i = 0;

    for (T item : list) {
      result.append(item);

      if (++i < list.size())
        result.append(delimiter);
    }

    return result.toString();
  }

  public static String join(long[] list, String delimeter) {
    List<Long> boxed = new ArrayList<>(list.length);

    for (int i = 0; i < list.length; i++) {
      boxed.add(list[i]);
    }

    return join(boxed, delimeter);
  }

  @SafeVarargs
  public static @NonNull <E> List<E> join(@NonNull List<E>... lists) {
    int     totalSize = Stream.of(lists).reduce(0, (sum, list) -> sum + list.size());
    List<E> joined    = new ArrayList<>(totalSize);

    for (List<E> list : lists) {
      joined.addAll(list);
    }

    return joined;
  }

  public static String join(List<Long> list, String delimeter) {
    StringBuilder sb = new StringBuilder();

    for (int j = 0; j < list.size(); j++) {
      if (j != 0) sb.append(delimeter);
      sb.append(list.get(j));
    }

    return sb.toString();
  }

  public static String rightPad(String value, int length) {
    if (value.length() >= length) {
      return value;
    }

    StringBuilder out = new StringBuilder(value);
    while (out.length() < length) {
      out.append(" ");
    }

    return out.toString();
  }

  public static boolean isEmpty(ComposeText value) {
    return value == null || value.getText() == null || TextUtils.isEmpty(value.getTextTrimmed());
  }

  public static boolean isEmpty(Collection<?> collection) {
    return collection == null || collection.isEmpty();
  }

  public static boolean isEmpty(@Nullable CharSequence charSequence) {
    return charSequence == null || charSequence.length() == 0;
  }

  public static boolean hasItems(@Nullable Collection<?> collection) {
    return collection != null && !collection.isEmpty();
  }

  public static <K, V> boolean hasItems(@Nullable Map<K, V> map) {
    return map != null && !map.isEmpty();
  }

  public static <K, V> V getOrDefault(@NonNull Map<K, V> map, K key, V defaultValue) {
    return map.containsKey(key) ? map.get(key) : defaultValue;
  }

  public static String getFirstNonEmpty(String... values) {
    for (String value : values) {
      if (!Util.isEmpty(value)) {
        return value;
      }
    }
    return "";
  }

  public static @NonNull String emptyIfNull(@Nullable String value) {
    return value != null ? value : "";
  }

  public static @NonNull CharSequence emptyIfNull(@Nullable CharSequence value) {
    return value != null ? value : "";
  }

  public static CharSequence getBoldedString(String value) {
    SpannableString spanned = new SpannableString(value);
    spanned.setSpan(new StyleSpan(Typeface.BOLD), 0,
                    spanned.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

    return spanned;
  }

  public static byte[] toIsoBytes(String isoString) {
    return isoString.getBytes(StandardCharsets.ISO_8859_1);
  }

  public static void wait(Object lock, long timeout) {
    try {
      lock.wait(timeout);
    } catch (InterruptedException ie) {
      throw new AssertionError(ie);
    }
  }

  @RequiresPermission(anyOf = {
      android.Manifest.permission.READ_PHONE_STATE,
      android.Manifest.permission.READ_PHONE_NUMBERS
  })
  @SuppressLint("MissingPermission")
  public static Optional<Phonenumber.PhoneNumber> getDeviceNumber(Context context) {
    try {
      final String           localNumber = ((TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE)).getLine1Number();
      final Optional<String> countryIso  = getSimCountryIso(context);

      if (TextUtils.isEmpty(localNumber)) return Optional.empty();
      if (!countryIso.isPresent())        return Optional.empty();

      return Optional.ofNullable(PhoneNumberUtil.getInstance().parse(localNumber, countryIso.get()));
    } catch (NumberParseException e) {
      Log.w(TAG, e);
      return Optional.empty();
    }
  }

  public static Optional<String> getSimCountryIso(Context context) {
    String simCountryIso = ((TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE)).getSimCountryIso();
    return Optional.ofNullable(simCountryIso != null ? simCountryIso.toUpperCase() : null);
  }

  public static @Nullable String getNetworkCountryIso(Context context) {
    String networkCountryIso = ((TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE)).getNetworkCountryIso();
    return networkCountryIso == null ? null : networkCountryIso.toUpperCase();
  }

  public static @NonNull <T> T firstNonNull(@Nullable T optional, @NonNull T fallback) {
    return optional != null ? optional : fallback;
  }

  @SafeVarargs
  public static @NonNull <T> T firstNonNull(T ... ts) {
    for (T t : ts) {
      if (t != null) {
        return t;
      }
    }

    throw new IllegalStateException("All choices were null.");
  }

  public static <T> List<List<T>> partition(List<T> list, int partitionSize) {
    List<List<T>> results = new LinkedList<>();

    for (int index=0;index<list.size();index+=partitionSize) {
      int subListSize = Math.min(partitionSize, list.size() - index);

      results.add(list.subList(index, index + subListSize));
    }

    return results;
  }

  public static List<String> split(String source, String delimiter) {
    List<String> results = new LinkedList<>();

    if (TextUtils.isEmpty(source)) {
      return results;
    }

    String[] elements = source.split(delimiter);
    Collections.addAll(results, elements);

    return results;
  }

  public static byte[][] split(byte[] input, int firstLength, int secondLength) {
    byte[][] parts = new byte[2][];

    parts[0] = new byte[firstLength];
    System.arraycopy(input, 0, parts[0], 0, firstLength);

    parts[1] = new byte[secondLength];
    System.arraycopy(input, firstLength, parts[1], 0, secondLength);

    return parts;
  }

  public static byte[] combine(byte[]... elements) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();

      for (byte[] element : elements) {
        baos.write(element);
      }

      return baos.toByteArray();
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  public static byte[] trim(byte[] input, int length) {
    byte[] result = new byte[length];
    System.arraycopy(input, 0, result, 0, result.length);

    return result;
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

  public static String getSecret(int size) {
    byte[] secret = getSecretBytes(size);
    return Base64.encodeWithPadding(secret);
  }

  public static byte[] getSecretBytes(int size) {
    return getSecretBytes(new SecureRandom(), size);
  }

  public static byte[] getSecretBytes(@NonNull SecureRandom secureRandom, int size) {
    byte[] secret = new byte[size];
    secureRandom.nextBytes(secret);
    return secret;
  }

  /**
   * @return The amount of time (in ms) until this build of Signal will be considered 'expired'.
   *         Takes into account both the build age as well as any remote deprecation values.
   */
  public static long getTimeUntilBuildExpiry(long currentTime) {
    if (SignalStore.misc().isClientDeprecated()) {
      return 0;
    }

    long buildAge                   = currentTime - BuildConfig.BUILD_TIMESTAMP;
    long timeUntilBuildDeprecation  = BUILD_LIFESPAN - buildAge;
    long timeUntilRemoteDeprecation = RemoteDeprecation.getTimeUntilDeprecation(currentTime);

    if (timeUntilRemoteDeprecation != -1) {
      long timeUntilDeprecation = Math.min(timeUntilBuildDeprecation, timeUntilRemoteDeprecation);
      return Math.max(timeUntilDeprecation, 0);
    } else {
      return Math.max(timeUntilBuildDeprecation, 0);
    }
  }

  public static <T> T getRandomElement(List<T> elements) {
    return elements.get(new SecureRandom().nextInt(elements.size()));
  }

  public static boolean equals(@Nullable Object a, @Nullable Object b) {
    return a == b || (a != null && a.equals(b));
  }

  public static int hashCode(@Nullable Object... objects) {
    return Arrays.hashCode(objects);
  }

  public static @Nullable Uri uri(@Nullable String uri) {
    if (uri == null) return null;
    else             return Uri.parse(uri);
  }

  public static boolean isLowMemory(Context context) {
    ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

    return activityManager.isLowRamDevice() || activityManager.getLargeMemoryClass() <= 64;
  }

  public static int clamp(int value, int min, int max) {
    return Math.min(Math.max(value, min), max);
  }

  public static long clamp(long value, long min, long max) {
    return Math.min(Math.max(value, min), max);
  }

  public static float clamp(float value, float min, float max) {
    return Math.min(Math.max(value, min), max);
  }

  /**
   * Returns half of the difference between the given length, and the length when scaled by the
   * given scale.
   */
  public static float halfOffsetFromScale(int length, float scale) {
    float scaledLength = length * scale;
    return (length - scaledLength) / 2;
  }

  public static @Nullable String readTextFromClipboard(@NonNull Context context) {
    {
      ClipboardManager clipboardManager = (ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);

      if (clipboardManager.hasPrimaryClip() && clipboardManager.getPrimaryClip().getItemCount() > 0) {
        return clipboardManager.getPrimaryClip().getItemAt(0).getText().toString();
      } else {
        return null;
      }
    }
  }

  public static void writeTextToClipboard(@NonNull Context context, @NonNull String text) {
    writeTextToClipboard(context, context.getString(R.string.app_name), text);
  }

  public static void writeTextToClipboard(@NonNull Context context, @NonNull String label, @NonNull String text) {
    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
    ClipData clip = ClipData.newPlainText(label, text);
    clipboard.setPrimaryClip(clip);
  }

  public static int toIntExact(long value) {
    if ((int)value != value) {
      throw new ArithmeticException("integer overflow");
    }
    return (int)value;
  }

  public static void copyToClipboard(@NonNull Context context, @NonNull CharSequence text) {
    ServiceUtil.getClipboardManager(context).setPrimaryClip(ClipData.newPlainText(COPY_LABEL, text));
  }

  public static int parseInt(String integer, int defaultValue) {
    try {
      return Integer.parseInt(integer);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }
}
