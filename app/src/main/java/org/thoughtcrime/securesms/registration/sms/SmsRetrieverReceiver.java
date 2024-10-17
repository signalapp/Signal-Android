/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.sms;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.gms.auth.api.phone.SmsRetriever;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.Status;

import org.greenrobot.eventbus.EventBus;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.service.VerificationCodeParser;

import java.util.Optional;

/**
 * Listen for SMS verification codes sent during registration or change number.
 */
public class SmsRetrieverReceiver extends BroadcastReceiver {

  private static final String TAG = Log.tag(SmsRetrieverReceiver.class);

  private final Context context;

  public SmsRetrieverReceiver(@NonNull Application context) {
    this.context = context;
  }

  public void registerReceiver() {
    Log.d(TAG, "Registering SMS retriever receiver");
    ContextCompat.registerReceiver(context, this, new IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION), ContextCompat.RECEIVER_EXPORTED);
  }

  public void unregisterReceiver() {
    Log.d(TAG, "Unregistering SMS retriever receiver");
    context.unregisterReceiver(this);
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    Log.i(TAG, "SmsRetrieverReceiver received a broadcast...");

    if (SmsRetriever.SMS_RETRIEVED_ACTION.equals(intent.getAction())) {
      Bundle extras = intent.getExtras();
      Status status = (Status) extras.get(SmsRetriever.EXTRA_STATUS);

      switch (status.getStatusCode()) {
        case CommonStatusCodes.SUCCESS:
          Optional<String> code = VerificationCodeParser.parse((String) extras.get(SmsRetriever.EXTRA_SMS_MESSAGE));
          if (code.isPresent()) {
            Log.i(TAG, "Received verification code.");
            EventBus.getDefault().post(new ReceivedSmsEvent(code.get()));
          } else {
            Log.w(TAG, "Could not parse verification code.");
          }
          break;
        case CommonStatusCodes.TIMEOUT:
          Log.w(TAG, "Hit a timeout waiting for the SMS to arrive.");
          break;
      }
    } else {
      Log.w(TAG, "SmsRetrieverReceiver received the wrong action?");
    }
  }
}
