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
import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Handler;
import android.os.Looper;
import android.provider.Telephony;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import android.telephony.TelephonyManager;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.StyleSpan;

import com.google.android.mms.pdu_alt.CharacterSets;
import com.google.android.mms.pdu_alt.EncodedStringValue;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.components.ComposeText;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.OutgoingLegacyMmsConnection;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.security.SecureRandom;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Util {
  private static final String TAG = Util.class.getSimpleName();

  private static volatile Handler handler;

  public static <T> List<T> asList(T... elements) {
    List<T> result = new LinkedList<>();
    Collections.addAll(result, elements);
    return result;
  }

  public static String join(String[] list, String delimiter) {
    return join(Arrays.asList(list), delimiter);
  }

  public static String join(Collection<String> list, String delimiter) {
    StringBuilder result = new StringBuilder();
    int i = 0;

    for (String item : list) {
      result.append(item);

      if (++i < list.size())
        result.append(delimiter);
    }

    return result.toString();
  }

  public static String join(long[] list, String delimeter) {
    StringBuilder sb = new StringBuilder();

    for (int j=0;j<list.length;j++) {
      if (j != 0) sb.append(delimeter);
      sb.append(list[j]);
    }

    return sb.toString();
  }

  public static ExecutorService newSingleThreadedLifoExecutor() {
    ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingLifoQueue<Runnable>());

    executor.execute(() -> {
//        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
      Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
    });

    return executor;
  }

  public static boolean isEmpty(EncodedStringValue[] value) {
    return value == null || value.length == 0;
  }

  public static boolean isEmpty(ComposeText value) {
    return value == null || value.getText() == null || TextUtils.isEmpty(value.getTextTrimmed());
  }

  public static boolean isEmpty(Collection collection) {
    return collection == null || collection.isEmpty();
  }

  public static <K, V> V getOrDefault(@NonNull Map<K, V> map, K key, V defaultValue) {
    return map.containsKey(key) ? map.get(key) : defaultValue;
  }

  public static String getFirstNonEmpty(String... values) {
    for (String value : values) {
      if (!TextUtils.isEmpty(value)) {
        return value;
      }
    }
    return "";
  }

  public static <E> List<List<E>> chunk(@NonNull List<E> list, int chunkSize) {
    List<List<E>> chunks = new ArrayList<>(list.size() / chunkSize);

    for (int i = 0; i < list.size(); i += chunkSize) {
      List<E> chunk = list.subList(i, Math.min(list.size(), i + chunkSize));
      chunks.add(chunk);
    }

    return chunks;
  }

  public static CharSequence getBoldedString(String value) {
    SpannableString spanned = new SpannableString(value);
    spanned.setSpan(new StyleSpan(Typeface.BOLD), 0,
                    spanned.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

    return spanned;
  }

  public static @NonNull String toIsoString(byte[] bytes) {
    try {
      return new String(bytes, CharacterSets.MIMENAME_ISO_8859_1);
    } catch (UnsupportedEncodingException e) {
      throw new AssertionError("ISO_8859_1 must be supported!");
    }
  }

  public static byte[] toIsoBytes(String isoString) {
    try {
      return isoString.getBytes(CharacterSets.MIMENAME_ISO_8859_1);
    } catch (UnsupportedEncodingException e) {
      throw new AssertionError("ISO_8859_1 must be supported!");
    }
  }

  public static byte[] toUtf8Bytes(String utf8String) {
    try {
      return utf8String.getBytes(CharacterSets.MIMENAME_UTF_8);
    } catch (UnsupportedEncodingException e) {
      throw new AssertionError("UTF_8 must be supported!");
    }
  }

  public static void wait(Object lock, long timeout) {
    try {
      lock.wait(timeout);
    } catch (InterruptedException ie) {
      throw new AssertionError(ie);
    }
  }

  public static void close(Closeable closeable) {
    try {
      closeable.close();
    } catch (IOException e) {
      Log.w(TAG, e);
    }
  }

  public static long getStreamLength(InputStream in) throws IOException {
    byte[] buffer    = new byte[4096];
    int    totalSize = 0;

    int read;

    while ((read = in.read(buffer)) != -1) {
      totalSize += read;
    }

    return totalSize;
  }

  public static boolean isOwnNumber(Context context, Address address) {
    if (address.isGroup()) return false;
    if (address.isEmail()) return false;

    return TextSecurePreferences.getLocalNumber(context).equals(address.toPhoneString());
  }

  public static void readFully(InputStream in, byte[] buffer) throws IOException {
    readFully(in, buffer, buffer.length);
  }

  public static void readFully(InputStream in, byte[] buffer, int len) throws IOException {
    int offset = 0;

    for (;;) {
      int read = in.read(buffer, offset, len - offset);
      if (read == -1) throw new EOFException("Stream ended early");

      if (read + offset < len) offset += read;
      else                		 return;
    }
  }

  public static byte[] readFully(InputStream in) throws IOException {
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    byte[] buffer              = new byte[4096];
    int read;

    while ((read = in.read(buffer)) != -1) {
      bout.write(buffer, 0, read);
    }

    in.close();

    return bout.toByteArray();
  }

  public static String readFullyAsString(InputStream in) throws IOException {
    return new String(readFully(in));
  }

  public static long copy(InputStream in, OutputStream out) throws IOException {
    byte[] buffer = new byte[8192];
    int read;
    long total = 0;

    while ((read = in.read(buffer)) != -1) {
      out.write(buffer, 0, read);
      total += read;
    }

    in.close();
    out.close();

    return total;
  }

  @RequiresPermission(anyOf = {
      android.Manifest.permission.READ_PHONE_STATE,
      android.Manifest.permission.READ_SMS,
      android.Manifest.permission.READ_PHONE_NUMBERS
  })
  @SuppressLint("MissingPermission")
  public static Optional<Phonenumber.PhoneNumber> getDeviceNumber(Context context) {
    try {
      final String           localNumber = ((TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE)).getLine1Number();
      final Optional<String> countryIso  = getSimCountryIso(context);

      if (TextUtils.isEmpty(localNumber)) return Optional.absent();
      if (!countryIso.isPresent())        return Optional.absent();

      return Optional.fromNullable(PhoneNumberUtil.getInstance().parse(localNumber, countryIso.get()));
    } catch (NumberParseException e) {
      Log.w(TAG, e);
      return Optional.absent();
    }
  }

  public static Optional<String> getSimCountryIso(Context context) {
    String simCountryIso = ((TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE)).getSimCountryIso();
    return Optional.fromNullable(simCountryIso != null ? simCountryIso.toUpperCase() : null);
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

  @SuppressLint("NewApi")
  public static boolean isDefaultSmsProvider(Context context){
    return context.getPackageName().equals(Telephony.Sms.getDefaultSmsPackage(context));
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
    return Base64.encodeBytes(secret);
  }

  public static byte[] getSecretBytes(int size) {
    byte[] secret = new byte[size];
    getSecureRandom().nextBytes(secret);
    return secret;
  }

  public static SecureRandom getSecureRandom() {
    return new SecureRandom();
  }

  public static int getDaysTillBuildExpiry() {
    int age = (int) TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - BuildConfig.BUILD_TIMESTAMP);
    return 90 - age;
  }

  @TargetApi(VERSION_CODES.LOLLIPOP)
  public static boolean isMmsCapable(Context context) {
    return (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) || OutgoingLegacyMmsConnection.isConnectionPossible(context);
  }

  public static boolean isMainThread() {
    return Looper.myLooper() == Looper.getMainLooper();
  }

  public static void assertMainThread() {
    if (!isMainThread()) {
      throw new AssertionError("Main-thread assertion failed.");
    }
  }

  public static void postToMain(final @NonNull Runnable runnable) {
    getHandler().post(runnable);
  }

  public static void runOnMain(final @NonNull Runnable runnable) {
    if (isMainThread()) runnable.run();
    else                getHandler().post(runnable);
  }

  public static void runOnMainDelayed(final @NonNull Runnable runnable, long delayMillis) {
    getHandler().postDelayed(runnable, delayMillis);
  }

  public static void cancelRunnableOnMain(@NonNull Runnable runnable) {
    getHandler().removeCallbacks(runnable);
  }

  public static void runOnMainSync(final @NonNull Runnable runnable) {
    if (isMainThread()) {
      runnable.run();
    } else {
      final CountDownLatch sync = new CountDownLatch(1);
      runOnMain(() -> {
        try {
          runnable.run();
        } finally {
          sync.countDown();
        }
      });
      try {
        sync.await();
      } catch (InterruptedException ie) {
        throw new AssertionError(ie);
      }
    }
  }

  public static <T> T getRandomElement(T[] elements) {
    return elements[new SecureRandom().nextInt(elements.length)];
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

  @TargetApi(VERSION_CODES.KITKAT)
  public static boolean isLowMemory(Context context) {
    ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

    return (VERSION.SDK_INT >= VERSION_CODES.KITKAT && activityManager.isLowRamDevice()) ||
           activityManager.getLargeMemoryClass() <= 64;
  }

  public static int clamp(int value, int min, int max) {
    return Math.min(Math.max(value, min), max);
  }

  public static float clamp(float value, float min, float max) {
    return Math.min(Math.max(value, min), max);
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
    {
      ClipboardManager clipboardManager = (ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);
      clipboardManager.setPrimaryClip(ClipData.newPlainText("Safety numbers", text));
    }
  }

  public static int toIntExact(long value) {
    if ((int)value != value) {
      throw new ArithmeticException("integer overflow");
    }
    return (int)value;
  }

  public static boolean isStringEquals(String first, String second) {
    if (first == null) return second == null;
    return first.equals(second);
  }

  public static boolean isEquals(@Nullable Long first, long second) {
    return first != null && first == second;
  }

  public static String getPrettyFileSize(long sizeBytes) {
    if (sizeBytes <= 0) return "0";

    String[] units       = new String[]{"B", "kB", "MB", "GB", "TB"};
    int      digitGroups = (int) (Math.log10(sizeBytes) / Math.log10(1024));

    return new DecimalFormat("#,##0.#").format(sizeBytes/Math.pow(1024, digitGroups)) + " " + units[digitGroups];
  }

  private static Handler getHandler() {
    if (handler == null) {
      synchronized (Util.class) {
        if (handler == null) {
          handler = new Handler(Looper.getMainLooper());
        }
      }
    }
    return handler;
  }
}
