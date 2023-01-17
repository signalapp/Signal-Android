package org.thoughtcrime.securesms.devicetransfer.olddevice;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

public class OldDeviceExitActivity extends AppCompatActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    finishAll(this);
  }

  public static void exit(@NonNull Activity activity) {
    Intent intent = new Intent(activity, OldDeviceExitActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
    activity.startActivity(intent);
    finishAll(activity);
  }

  private static void finishAll(@NonNull Activity activity) {
    activity.finishAndRemoveTask();
  }
}