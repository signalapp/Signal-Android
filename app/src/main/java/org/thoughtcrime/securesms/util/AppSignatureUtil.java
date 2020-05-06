package org.thoughtcrime.securesms.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.logging.Log;
import org.whispersystems.libsignal.util.guava.Optional;

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
  public static Optional<String> getAppSignature(@NonNull Context context) {
    try {
      String         packageName    = context.getPackageName();
      PackageManager packageManager = context.getPackageManager();
      PackageInfo    packageInfo    = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
      Signature[]    signatures     = packageInfo.signatures;

      if (signatures.length > 0) {
        String hash = hash(packageName, signatures[0].toCharsString());
        return Optional.fromNullable(hash);
      }
    } catch (PackageManager.NameNotFoundException e) {
      Log.w(TAG, e);
    }

    return Optional.absent();
  }

  private static String hash(String packageName, String signature) {
    String appInfo = packageName + " " + signature;
    try {
      MessageDigest messageDigest = MessageDigest.getInstance(HASH_TYPE);
      messageDigest.update(appInfo.getBytes(StandardCharsets.UTF_8));

      byte[] hashSignature = messageDigest.digest();
      hashSignature = Arrays.copyOfRange(hashSignature, 0, HASH_LENGTH_BYTES);

      String base64Hash = Base64.encodeBytes(hashSignature);
      base64Hash = base64Hash.substring(0, HASH_LENGTH_CHARS);

      return base64Hash;
    } catch (NoSuchAlgorithmException e) {
      Log.w(TAG, e);
    }

    return null;
  }
}
