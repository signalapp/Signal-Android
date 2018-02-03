package org.thoughtcrime.securesms.permissions;


import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.ViewGroup;
import android.view.WindowManager;

import com.annimon.stream.Stream;
import com.annimon.stream.function.Consumer;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.LRUCache;
import org.thoughtcrime.securesms.util.ServiceUtil;

import java.lang.ref.WeakReference;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

public class Permissions {

  private static final Map<Integer, PermissionsRequest> OUTSTANDING = new LRUCache<>(2);

  public static PermissionsBuilder with(@NonNull Activity activity) {
    return new PermissionsBuilder(new ActivityPermissionObject(activity));
  }

  public static PermissionsBuilder with(@NonNull Fragment fragment) {
    return new PermissionsBuilder(new FragmentPermissionObject(fragment));
  }

  public static class PermissionsBuilder {

    private final PermissionObject permissionObject;

    private String[] requestedPermissions;

    private Runnable allGrantedListener;

    private Runnable anyDeniedListener;
    private Runnable anyPermanentlyDeniedListener;
    private Runnable anyResultListener;

    private Consumer<List<String>> someGrantedListener;
    private Consumer<List<String>> someDeniedListener;
    private Consumer<List<String>> somePermanentlyDeniedListener;

    private @DrawableRes int[]  rationalDialogHeader;
    private              String rationaleDialogMessage;

    private boolean ifNecesary;

    private boolean condition = true;

    PermissionsBuilder(PermissionObject permissionObject) {
      this.permissionObject = permissionObject;
    }

    public PermissionsBuilder request(String... requestedPermissions) {
      this.requestedPermissions = requestedPermissions;
      return this;
    }

    public PermissionsBuilder ifNecessary() {
      this.ifNecesary = true;
      return this;
    }

    public PermissionsBuilder ifNecessary(boolean condition) {
      this.ifNecesary = true;
      this.condition  = condition;
      return this;
    }

    public PermissionsBuilder withRationaleDialog(@NonNull String message, @NonNull @DrawableRes int... headers) {
      this.rationalDialogHeader   = headers;
      this.rationaleDialogMessage = message;
      return this;
    }

    public PermissionsBuilder withPermanentDenialDialog(@NonNull String message) {
      return onAnyPermanentlyDenied(new SettingsDialogListener(permissionObject.getContext(), message));
    }

    public PermissionsBuilder onAllGranted(Runnable allGrantedListener) {
      this.allGrantedListener = allGrantedListener;
      return this;
    }

    public PermissionsBuilder onAnyDenied(Runnable anyDeniedListener) {
      this.anyDeniedListener = anyDeniedListener;
      return this;
    }

    @SuppressWarnings("WeakerAccess")
    public PermissionsBuilder onAnyPermanentlyDenied(Runnable anyPermanentlyDeniedListener) {
      this.anyPermanentlyDeniedListener = anyPermanentlyDeniedListener;
      return this;
    }

    public PermissionsBuilder onAnyResult(Runnable anyResultListener) {
      this.anyResultListener = anyResultListener;
      return this;
    }

    public PermissionsBuilder onSomeGranted(Consumer<List<String>> someGrantedListener) {
      this.someGrantedListener = someGrantedListener;
      return this;
    }

    public PermissionsBuilder onSomeDenied(Consumer<List<String>> someDeniedListener) {
      this.someDeniedListener = someDeniedListener;
      return this;
    }

    public PermissionsBuilder onSomePermanentlyDenied(Consumer<List<String>> somePermanentlyDeniedListener) {
      this.somePermanentlyDeniedListener = somePermanentlyDeniedListener;
      return this;
    }

    public void execute() {
      PermissionsRequest request = new PermissionsRequest(allGrantedListener, anyDeniedListener, anyPermanentlyDeniedListener, anyResultListener,
                                                          someGrantedListener, someDeniedListener, somePermanentlyDeniedListener);

      if (ifNecesary && (permissionObject.hasAll(requestedPermissions) || !condition)) {
        executePreGrantedPermissionsRequest(request);
      } else if (rationaleDialogMessage != null && rationalDialogHeader != null) {
        executePermissionsRequestWithRationale(request);
      } else {
        executePermissionsRequest(request);
      }
    }

    private void executePreGrantedPermissionsRequest(PermissionsRequest request) {
      int[] grantResults = new int[requestedPermissions.length];
      for (int i=0;i<grantResults.length;i++) grantResults[i] = PackageManager.PERMISSION_GRANTED;

      request.onResult(requestedPermissions, grantResults, new boolean[requestedPermissions.length]);
    }

    @SuppressWarnings("ConstantConditions")
    private void executePermissionsRequestWithRationale(PermissionsRequest request) {
      RationaleDialog.createFor(permissionObject.getContext(), rationaleDialogMessage, rationalDialogHeader)
                     .setPositiveButton(R.string.Permissions_continue, (dialog, which) -> executePermissionsRequest(request))
                     .setNegativeButton(R.string.Permissions_not_now, null)
                     .show()
                     .getWindow()
                     .setLayout((int)(permissionObject.getWindowWidth() * .75), ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void executePermissionsRequest(PermissionsRequest request) {
      int requestCode = new SecureRandom().nextInt(65434) + 100;

      synchronized (OUTSTANDING) {
        OUTSTANDING.put(requestCode, request);
      }

      for (String permission : requestedPermissions) {
        request.addMapping(permission, permissionObject.shouldShouldPermissionRationale(permission));
      }

      permissionObject.requestPermissions(requestCode, requestedPermissions);
    }

  }

  private static void requestPermissions(@NonNull Activity activity, int requestCode, String... permissions) {
    ActivityCompat.requestPermissions(activity, filterNotGranted(activity, permissions), requestCode);
  }

  private static void requestPermissions(@NonNull Fragment fragment, int requestCode, String... permissions) {
    fragment.requestPermissions(filterNotGranted(fragment.getContext(), permissions), requestCode);
  }

  private static String[] filterNotGranted(@NonNull Context context, String... permissions) {
    return Stream.of(permissions)
                 .filter(permission -> ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED)
                 .toList()
                 .toArray(new String[0]);
  }

  public static boolean hasAny(@NonNull Context context, String... permissions) {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
        Stream.of(permissions).anyMatch(permission -> ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED);

  }

  public static boolean hasAll(@NonNull Context context, String... permissions) {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
        Stream.of(permissions).allMatch(permission -> ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED);

  }

  public static void onRequestPermissionsResult(Fragment fragment, int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    onRequestPermissionsResult(new FragmentPermissionObject(fragment), requestCode, permissions, grantResults);
  }

  public static void onRequestPermissionsResult(Activity activity, int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    onRequestPermissionsResult(new ActivityPermissionObject(activity), requestCode, permissions, grantResults);
  }

  private static void onRequestPermissionsResult(@NonNull PermissionObject context, int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    PermissionsRequest resultListener;

    synchronized (OUTSTANDING) {
      resultListener = OUTSTANDING.remove(requestCode);
    }

    if (resultListener == null) return;

    boolean[] shouldShowRationaleDialog = new boolean[permissions.length];

    for (int i=0;i<permissions.length;i++) {
      if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
        shouldShowRationaleDialog[i] = context.shouldShouldPermissionRationale(permissions[i]);
      }
    }

    resultListener.onResult(permissions, grantResults, shouldShowRationaleDialog);
  }

  private static Intent getApplicationSettingsIntent(@NonNull Context context) {
    Intent intent = new Intent();
    intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
    Uri uri = Uri.fromParts("package", context.getPackageName(), null);
    intent.setData(uri);

    return intent;
  }

  private abstract static class PermissionObject {

    abstract Context getContext();
    abstract boolean shouldShouldPermissionRationale(String permission);
    abstract boolean hasAll(String... permissions);
    abstract void requestPermissions(int requestCode, String... permissions);

    int getWindowWidth() {
      WindowManager  windowManager = ServiceUtil.getWindowManager(getContext());
      Display        display       = windowManager.getDefaultDisplay();
      DisplayMetrics metrics       = new DisplayMetrics();
      display.getMetrics(metrics);

      return metrics.widthPixels;
    }
  }

  private static class ActivityPermissionObject extends PermissionObject {

    private Activity activity;

    ActivityPermissionObject(@NonNull Activity activity) {
      this.activity = activity;
    }

    @Override
    public Context getContext() {
      return activity;
    }

    @Override
    public boolean shouldShouldPermissionRationale(String permission) {
      return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission);
    }

    @Override
    public boolean hasAll(String... permissions) {
      return Permissions.hasAll(activity, permissions);
    }

    @Override
    public void requestPermissions(int requestCode, String... permissions) {
      Permissions.requestPermissions(activity, requestCode, permissions);
    }
  }

  private static class FragmentPermissionObject extends PermissionObject {

    private Fragment fragment;

    FragmentPermissionObject(@NonNull Fragment fragment) {
      this.fragment = fragment;
    }

    @Override
    public Context getContext() {
      return fragment.getContext();
    }

    @Override
    public boolean shouldShouldPermissionRationale(String permission) {
      return fragment.shouldShowRequestPermissionRationale(permission);
    }

    @Override
    public boolean hasAll(String... permissions) {
      return Permissions.hasAll(fragment.getContext(), permissions);
    }

    @Override
    public void requestPermissions(int requestCode, String... permissions) {
      Permissions.requestPermissions(fragment, requestCode, permissions);
    }
  }

  private static class SettingsDialogListener implements Runnable {

    private final WeakReference<Context> context;
    private final String                 message;

    SettingsDialogListener(Context context, String message) {
      this.message = message;
      this.context = new WeakReference<>(context);
    }

    @Override
    public void run() {
      Context context = this.context.get();

      if (context != null) {
        new AlertDialog.Builder(context)
            .setTitle(R.string.Permissions_permission_required)
            .setMessage(message)
            .setPositiveButton(R.string.Permissions_continue, (dialog, which) -> context.startActivity(getApplicationSettingsIntent(context)))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
      }
    }
  }
}
