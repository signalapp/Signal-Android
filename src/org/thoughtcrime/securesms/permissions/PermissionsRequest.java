package org.thoughtcrime.securesms.permissions;


import android.content.pm.PackageManager;
import android.support.annotation.Nullable;

import com.annimon.stream.function.Consumer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class PermissionsRequest {

  private final Map<String, Boolean> PRE_REQUEST_MAPPING = new HashMap<>();

  private final @Nullable Runnable allGrantedListener;

  private final @Nullable Runnable anyDeniedListener;
  private final @Nullable Runnable anyPermanentlyDeniedListener;
  private final @Nullable Runnable anyResultListener;

  private final @Nullable Consumer<List<String>> someGrantedListener;
  private final @Nullable Consumer<List<String>> someDeniedListener;
  private final @Nullable Consumer<List<String>> somePermanentlyDeniedListener;

  PermissionsRequest(@Nullable Runnable allGrantedListener,
                     @Nullable Runnable anyDeniedListener,
                     @Nullable Runnable anyPermanentlyDeniedListener,
                     @Nullable Runnable anyResultListener,
                     @Nullable Consumer<List<String>> someGrantedListener,
                     @Nullable Consumer<List<String>> someDeniedListener,
                     @Nullable Consumer<List<String>> somePermanentlyDeniedListener)
  {
    this.allGrantedListener            = allGrantedListener;

    this.anyDeniedListener             = anyDeniedListener;
    this.anyPermanentlyDeniedListener  = anyPermanentlyDeniedListener;
    this.anyResultListener             = anyResultListener;

    this.someGrantedListener           = someGrantedListener;
    this.someDeniedListener            = someDeniedListener;
    this.somePermanentlyDeniedListener = somePermanentlyDeniedListener;
  }

  void onResult(String[] permissions, int[] grantResults, boolean[] shouldShowRationaleDialog) {
    List<String> granted           = new ArrayList<>(permissions.length);
    List<String> denied            = new ArrayList<>(permissions.length);
    List<String> permanentlyDenied = new ArrayList<>(permissions.length);

    for (int i = 0; i < permissions.length; i++) {
      if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
        granted.add(permissions[i]);
      } else {
        boolean preRequestShouldShowRationaleDialog = PRE_REQUEST_MAPPING.get(permissions[i]);

        if ((somePermanentlyDeniedListener != null || anyPermanentlyDeniedListener != null) &&
            !preRequestShouldShowRationaleDialog && !shouldShowRationaleDialog[i])
        {
          permanentlyDenied.add(permissions[i]);
        } else {
          denied.add(permissions[i]);
        }
      }
    }

    if (allGrantedListener != null && granted.size() > 0 && (denied.size() == 0 && permanentlyDenied.size() == 0)) {
      allGrantedListener.run();
    } else if (someGrantedListener != null && granted.size() > 0) {
      someGrantedListener.accept(granted);
    }

    if (denied.size() > 0) {
      if (anyDeniedListener != null)  anyDeniedListener.run();
      if (someDeniedListener != null) someDeniedListener.accept(denied);
    }

    if (permanentlyDenied.size() > 0) {
      if (anyPermanentlyDeniedListener != null)  anyPermanentlyDeniedListener.run();
      if (somePermanentlyDeniedListener != null) somePermanentlyDeniedListener.accept(permanentlyDenied);
    }

    if (anyResultListener != null) {
      anyResultListener.run();
    }
  }

  void addMapping(String permission, boolean shouldShowRationaleDialog) {
    PRE_REQUEST_MAPPING.put(permission, shouldShowRationaleDialog);
  }
}
