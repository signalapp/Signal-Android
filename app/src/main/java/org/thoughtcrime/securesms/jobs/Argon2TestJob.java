package org.thoughtcrime.securesms.jobs;

import android.app.ActivityManager;

import androidx.annotation.NonNull;

import org.signal.argon2.Argon2;
import org.signal.argon2.Argon2Exception;
import org.signal.argon2.MemoryCost;
import org.signal.argon2.Type;
import org.signal.argon2.Version;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.Locale;

public final class Argon2TestJob extends BaseJob {

  public static final String KEY = "Argon2TestJob";

  private static final String TAG = Log.tag(Argon2TestJob.class);

  private Argon2TestJob(@NonNull Parameters parameters) {
    super(parameters);
  }

  public Argon2TestJob() {
    this(new Parameters.Builder()
            .setMaxAttempts(1)
            .build());
  }

  @Override
  protected void onRun() throws InterruptedException {
    if (TextSecurePreferences.isArgon2Tested(context)) {
      Log.w(TAG, "Skipping Argon2 test, it has been run before");
      return;
    }

    TextSecurePreferences.setArgon2Tested(context,true);

    Log.i(TAG, "Starting Argon2 test");

    Argon2.Builder argon2Builder = new Argon2.Builder(Version.V13)
                                             .type(Type.Argon2id)
                                             .parallelism(1);

    MemoryCost memoryCost = MemoryCost.MiB(4);
    try {
      checkRam(memoryCost);
    } catch (Exception e) {
      throw new Argon2NotEnoughRam4(e.getMessage());
    }
    test(argon2Builder.memoryCost(memoryCost)
                      .iterations(8)
                      .build(),
         "c0286e8dfd91b633f41d9dc13dc99b3705b62e23349dd399ff68be5fc7720c41",
         "$argon2id$v=19$m=4096,t=8,p=1$c29tZXNhbHQ$wChujf2RtjP0HZ3BPcmbNwW2LiM0ndOZ/2i+X8dyDEE");

    Thread.sleep(3000);

    memoryCost = MemoryCost.MiB(8);
    try {
      checkRam(memoryCost);
    } catch (Exception e) {
      throw new Argon2NotEnoughRam8(e.getMessage());
    }
    test(argon2Builder.memoryCost(memoryCost)
                      .iterations(3)
                      .build(),
         "c52fdf6c6dc5e4e82b826b00d795781540d6d50458a43f0ccc44a7d701830318",
         "$argon2id$v=19$m=8192,t=3,p=1$c29tZXNhbHQ$xS/fbG3F5OgrgmsA15V4FUDW1QRYpD8MzESn1wGDAxg");

    Thread.sleep(3000);

    memoryCost = MemoryCost.MiB(16);
    try {
      checkRam(memoryCost);
    } catch (Exception e) {
      throw new Argon2NotEnoughRam16(e.getMessage());
    }
    test(argon2Builder.memoryCost(memoryCost)
                      .iterations(3)
                      .build(),
         "d41922d454814e3dbf2828108bb43a5b6319b22fc9f169528c20d1a2846e06c6",
         "$argon2id$v=19$m=16384,t=3,p=1$c29tZXNhbHQ$1Bki1FSBTj2/KCgQi7Q6W2MZsi/J8WlSjCDRooRuBsY");

    Thread.sleep(3000);

    memoryCost = MemoryCost.MiB(32);
    try {
      checkRam(memoryCost);
    } catch (Exception e) {
      throw new Argon2NotEnoughRam32(e.getMessage());
    }
    test(argon2Builder.memoryCost(memoryCost)
                      .iterations(2)
                      .build(),
         "8365c0271b505e7fa397982790802de7b62f71f4e11e05f7a4e6b2ad4f75fec0",
         "$argon2id$v=19$m=32768,t=2,p=1$c29tZXNhbHQ$g2XAJxtQXn+jl5gnkIAt57YvcfThHgX3pOayrU91/sA");

    Log.i(TAG, "Argon2 test complete");
  }

  private void test(Argon2 argon2, String expectedHashHex, String expectedEncoded) {
    long startTime = System.currentTimeMillis();

    Argon2.Result hash;
    try {
      hash = argon2.hash("signal".getBytes(StandardCharsets.UTF_8),
                         "somesalt".getBytes(StandardCharsets.UTF_8));
    } catch (Argon2Exception e) {
      throw new Argon2TestJobRuntimeException(e);
    }

    long endTime = System.currentTimeMillis();

    Log.i(TAG, String.format(Locale.US, "Argon2 hash complete:%n%s%.3f seconds", hash, (endTime - startTime) / 1000f));

    if (!hash.getHashHex().equals(expectedHashHex)) {
      throw new Argon2HashIncorrectRuntimeException("Hash was not correct");
    }

    if (!hash.getEncoded().equals(expectedEncoded)) {
      throw new Argon2EncodedHashIncorrectRuntimeException("Encoded Hash was not correct");
    }
  }

  private void checkRam(MemoryCost memoryCost) throws Exception {
    NumberFormat               numberFormat    = NumberFormat.getInstance(Locale.US);
    ActivityManager            activityManager = ServiceUtil.getActivityManager(context);
    ActivityManager.MemoryInfo memoryInfo      = new ActivityManager.MemoryInfo();

    activityManager.getMemoryInfo(memoryInfo);

    long remainingRam = memoryInfo.availMem - memoryInfo.threshold - memoryCost.toBytes();

    if (remainingRam <= 0) {
      throw new Exception(String.format("Not enough RAM available without taking the system into a low memory state.%n" +
                                        "Available: %s%n" +
                                        "Low memory threshold: %s%n" +
                                        "Requested: %s%n" +
                                        "Shortfall: %s",
        numberFormat.format(memoryInfo.availMem),
        numberFormat.format(memoryInfo.threshold),
        numberFormat.format(memoryCost.toBytes()),
        numberFormat.format(-remainingRam)
      ));
    } else {
      Log.i(TAG, String.format("Enough RAM available without taking the system into a low memory state.%n" +
                               "Available: %s%n" +
                               "Low memory threshold: %s%n" +
                               "Requested: %s%n" +
                               "Surplus: %s",
        numberFormat.format(memoryInfo.availMem),
        numberFormat.format(memoryInfo.threshold),
        numberFormat.format(memoryCost.toBytes()),
        numberFormat.format(remainingRam)
      ));
    }
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return false;
  }

  @Override
  public @NonNull Data serialize() {
    return Data.EMPTY;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onFailure() {}

  private static abstract class Argon2RuntimeException extends RuntimeException {
    private Argon2RuntimeException(String message) {
      super(message);
    }

    private Argon2RuntimeException(Throwable inner) {
      super(inner);
    }
  }

  private static class Argon2HashIncorrectRuntimeException extends Argon2RuntimeException {
    private Argon2HashIncorrectRuntimeException(String message) {
      super(message);
    }
  }

  private static class Argon2EncodedHashIncorrectRuntimeException extends Argon2RuntimeException {
    private Argon2EncodedHashIncorrectRuntimeException(String message) {
      super(message);
    }
  }

  private static class Argon2TestJobRuntimeException extends Argon2RuntimeException {
    private Argon2TestJobRuntimeException(Throwable t) {
      super(t);
    }
  }

  private static class Argon2NotEnoughRam4 extends Argon2RuntimeException {
    private Argon2NotEnoughRam4(String message) {
      super(message);
    }
  }

  private static class Argon2NotEnoughRam8 extends Argon2RuntimeException {
    private Argon2NotEnoughRam8(String message) {
      super(message);
    }
  }

  private static class Argon2NotEnoughRam16 extends Argon2RuntimeException {
    private Argon2NotEnoughRam16(String message) {
      super(message);
    }
  }

  private static class Argon2NotEnoughRam32 extends Argon2RuntimeException {
    private Argon2NotEnoughRam32(String message) {
      super(message);
    }
  }

  public static final class Factory implements Job.Factory<Argon2TestJob> {
    @Override
    public @NonNull Argon2TestJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new Argon2TestJob(parameters);
    }
  }
}
