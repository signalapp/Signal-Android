package org.thoughtcrime.securesms.registration.fragments;

import android.content.Context;
import android.os.Build;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.util.Debouncer;

final class SignalStrengthPhoneStateListener extends PhoneStateListener
                                             implements DefaultLifecycleObserver
{
  private static final String TAG = Log.tag(SignalStrengthPhoneStateListener.class);

  private final Callback  callback;
  private final Debouncer debouncer = new Debouncer(1000);

  SignalStrengthPhoneStateListener(@NonNull LifecycleOwner lifecycleOwner, @NonNull Callback callback) {
    this.callback = callback;

    lifecycleOwner.getLifecycle().addObserver(this);
  }

  @Override
  public void onSignalStrengthsChanged(SignalStrength signalStrength) {
    if (signalStrength == null) return;

    if (isLowLevel(signalStrength)) {
      Log.w(TAG, "No cell signal detected");
      debouncer.publish(callback::onNoCellSignalPresent);
    } else {
      Log.i(TAG, "Cell signal detected");
      debouncer.clear();
      callback.onCellSignalPresent();
    }
  }

  private boolean isLowLevel(@NonNull SignalStrength signalStrength) {
    if (Build.VERSION.SDK_INT >= 23) {
      return signalStrength.getLevel() == 0;
    } else {
      //noinspection deprecation: False lint warning, deprecated by 29, but this else block is for < 23
      return signalStrength.getGsmSignalStrength() == 0;
    }
  }

  interface Callback {
    void onNoCellSignalPresent();

    void onCellSignalPresent();
  }

  @Override
  public void onResume(@NonNull LifecycleOwner owner) {
    TelephonyManager telephonyManager = (TelephonyManager) ApplicationDependencies.getApplication().getSystemService(Context.TELEPHONY_SERVICE);
    telephonyManager.listen(this, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
    Log.i(TAG, "Listening to cell phone signal strength changes");
  }

  @Override
  public void onPause(@NonNull LifecycleOwner owner) {
    TelephonyManager telephonyManager = (TelephonyManager) ApplicationDependencies.getApplication().getSystemService(Context.TELEPHONY_SERVICE);
    telephonyManager.listen(this, PhoneStateListener.LISTEN_NONE);
    Log.i(TAG, "Stopped listening to cell phone signal strength changes");
  }
}
