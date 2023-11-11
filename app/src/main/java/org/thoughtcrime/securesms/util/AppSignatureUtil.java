package org.thoughtcrime.securesms.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;

import androidx.annotation.NonNull;

import org.signal.core.util.Base64;
import org.signal.core.util.logging.Log;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public final class AppSignatureUtil {

  private static final String TAG = Log.tag(AppSignatureUtil.class);

  private static final String HASH_TYPE         = "SHA-256";
  private static final int    HASH_LENGTH_BYTES = 9;
  private static final int    HASH_LENGTH_CHARS = 11;

  private AppSignatureUtil() {}

  /**
   * Only intended to be used for logging.
   */
  @SuppressLint("PackageManagerGetSignatures")
  public static @NonNull String getAppSignature(@NonNull Context context) {
    String hash = null;
    try {
      String         packageName    = context.getPackageName();
      PackageManager packageManager = context.getPackageManager();
      PackageInfo    packageInfo    = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
      Signature[]    signatures     = packageInfo.signatures;

      if (signatures.length > 0) {
        hash = hash(packageName, signatures[0].toCharsString());
      }
    } catch (PackageManager.NameNotFoundException e) {
      Log.w(TAG, e);
    }

    return hash != null ? hash : "Unknown";
  }

  private static String hash(String packageName, String signature) {
    String appInfo = packageName + " " + signature;
    try {
      MessageDigest messageDigest = MessageDigest.getInstance(HASH_TYPE);
      messageDigest.update(appInfo.getBytes(StandardCharsets.UTF_8));

      byte[] hashSignature = messageDigest.digest();
      hashSignature = Arrays.copyOfRange(hashSignature, 0, HASH_LENGTH_BYTES);

      String base64Hash = Base64.encodeWithPadding(hashSignature);
      base64Hash = base64Hash.substring(0, HASH_LENGTH_CHARS);

      return base64Hash;
    } catch (NoSuchAlgorithmException e) {
      Log.w(TAG, e);
    }

    return null;
  }
}
