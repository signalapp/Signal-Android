package org.thoughtcrime.securesms.permissions;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;

import org.thoughtcrime.securesms.permissions.PermissionHandler.PermissionRequest.PermissionResult;
import org.thoughtcrime.securesms.util.Util;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


public class PermissionHandler {
  private Activity                             activity;
  private Map<Integer, PermissionRequestState> openRequests;
  private int                                  requestCodeCounter;

  public PermissionHandler(@NonNull Activity activity) {
    this.activity     = activity;
    this.openRequests = new HashMap<>();
  }

  public abstract static class PermissionRequest {
    private String[] permissions;

    public PermissionRequest(@NonNull String... permissions) {
      this.permissions = permissions;
    }

    public abstract void onResult(Map<String, PermissionResult> results);

    /**
     * Called if Android decides the user might want to see an explanation of a permission.
     *
     * @param permissionsNeedingRationale the list of permissions we need to explain
     * @param callback called when the rationale view is finished explaining
     * @return true if the handler should wait for the callback to be called, false otherwise
     */
    public boolean onRationaleRequested(List<String> permissionsNeedingRationale, RationaleCallback callback) {
      return false;
    }

    protected @NonNull static PermissionResult getSingleResult(@NonNull Map<String, PermissionResult> results) {
      return results.values().iterator().next();
    }

    protected static boolean isFullyGranted(@NonNull Map<String, PermissionResult> results) {
      for (PermissionResult result : results.values()) {
        if (!result.isGranted()) return false;
      }
      return true;
    }

    public enum PermissionResult {
      ALREADY_GRANTED, GRANTED, DENIED;

      public boolean isGranted() {
        return this == GRANTED || this == ALREADY_GRANTED;
      }

      public static PermissionResult fromAndroidResult(int result) {
        return result == PackageManager.PERMISSION_GRANTED ? GRANTED : DENIED;
      }
    }
  }

  private static class PermissionRequestState {
    public final PermissionRequest request;
    public final List<String>      granted        = new LinkedList<>();
    public final List<String>      needsRationale = new LinkedList<>();
    public final List<String>      needsApproval  = new LinkedList<>();

    public PermissionRequestState(PermissionRequest request) {
      this.request = request;
    }
  }

  public class RationaleCallback {
    private final PermissionRequestState state;

    RationaleCallback(PermissionRequestState state) {
      this.state = state;
    }

    public void onRationaleFinished() {
      requestPendingPermissions(state);
    }
  }

  public void request(final PermissionRequest request) {
    final PermissionRequestState state = new PermissionRequestState(request);
    for (final String permission : request.permissions) {
      if (Util.hasPermission(activity, permission)) {
        state.granted.add(permission);
      } else if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
        state.needsRationale.add(permission);
      } else {
        state.needsApproval.add(permission);
      }
    }

    if (state.needsApproval.isEmpty() &&
        state.needsRationale.isEmpty())
    {
      request.onResult(asImmediateResults(state.granted));
    } else {
      handlePendingPermissionsAndRationales(state);
    }
  }

  private void handlePendingPermissionsAndRationales(final PermissionRequestState state) {
    if (state.needsRationale.isEmpty() ||
        !state.request.onRationaleRequested(state.needsRationale, new RationaleCallback(state)))
    {
      requestPendingPermissions(state);
    }
  }

  private void requestPendingPermissions(final PermissionRequestState state) {
    final int          requestCode        = generateRequestCode();
    final List<String> pendingPermissions = new LinkedList<>(state.needsApproval);
    pendingPermissions.addAll(state.needsRationale);

    ActivityCompat.requestPermissions(activity,
                                      pendingPermissions.toArray(new String[pendingPermissions.size()]),
                                      requestCode);
    openRequests.put(requestCode, state);
  }

  private int generateRequestCode() {
    return (requestCodeCounter = (requestCodeCounter + 1) % 256);
  }

  private static Map<String, PermissionResult> asImmediateResults(List<String> permissions) {
    Map<String, PermissionResult> results = new HashMap<>();
    for (String permission : permissions) {
      results.put(permission, PermissionResult.ALREADY_GRANTED);
    }
    return results;
  }

  public void onRequestPermissionsResult(int requestCode,
                                         @NonNull String[] permissions,
                                         @NonNull int[] grantResults)
  {
    PermissionRequestState requestState = openRequests.get(requestCode);
    if (requestState == null) return;

    Map<String, PermissionResult> results = new HashMap<>(asImmediateResults(requestState.granted));
    for (int i = 0; i < permissions.length; i++) {
      results.put(permissions[i], PermissionResult.fromAndroidResult(grantResults[i]));
    }

    openRequests.remove(requestCode);
    requestState.request.onResult(results);
  }
}
