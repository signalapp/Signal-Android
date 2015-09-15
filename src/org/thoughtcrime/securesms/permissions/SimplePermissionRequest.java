package org.thoughtcrime.securesms.permissions;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.widget.Toast;

import org.thoughtcrime.securesms.permissions.PermissionHandler.PermissionRequest;

import java.util.Map;

public abstract class SimplePermissionRequest extends PermissionRequest {
  private Context context;
  private String  errorText;

  public SimplePermissionRequest(@NonNull Context context,
                                 @NonNull String errorText,
                                 @NonNull String... permissions)
  {
    super(permissions);
    this.context   = context;
    this.errorText = errorText;
  }

  public SimplePermissionRequest(@NonNull Context context,
                                 @StringRes int errorTextRes,
                                 @NonNull String... permissions)
  {
    this(context, context.getString(errorTextRes), permissions);
  }

  @Override public void onResult(Map<String, PermissionResult> results) {
    if (isFullyGranted(results)) {
      onGranted();
    } else {
      Toast.makeText(context, errorText, Toast.LENGTH_SHORT).show();
    }
  }

  public abstract void onGranted();
}
