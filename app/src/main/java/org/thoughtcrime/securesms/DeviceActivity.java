package org.thoughtcrime.securesms;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.TextUtils;
import android.transition.TransitionInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;

import org.signal.core.util.ThreadUtil;
import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.IdentityKeyPair;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.ecc.Curve;
import org.signal.libsignal.protocol.ecc.ECPublicKey;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.signal.qr.kitkat.ScanListener;
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.jobs.LinkedDeviceInactiveCheckJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.signal.core.util.Base64;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.task.ProgressDialogAsyncTask;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.push.exceptions.NotFoundException;
import org.whispersystems.signalservice.internal.push.DeviceLimitExceededException;

import java.io.IOException;

public class DeviceActivity extends PassphraseRequiredActivity
    implements Button.OnClickListener, ScanListener, DeviceLinkFragment.LinkClickedListener
{

  private static final String TAG = Log.tag(DeviceActivity.class);

  private static final String EXTRA_DIRECT_TO_SCANNER = "add";

  private final DynamicTheme    dynamicTheme    = new DynamicNoActionBarTheme();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private DeviceAddFragment  deviceAddFragment;
  private DeviceListFragment deviceListFragment;
  private DeviceLinkFragment deviceLinkFragment;
  private MenuItem           cameraSwitchItem = null;


  public static Intent getIntentForScanner(Context context) {
    Intent intent = new Intent(context, DeviceActivity.class);
    intent.putExtra(EXTRA_DIRECT_TO_SCANNER, true);
    return intent;
  }

  @Override
  public void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }

  @Override
  public void onCreate(Bundle bundle, boolean ready) {
    setContentView(R.layout.device_activity);

    Toolbar toolbar = findViewById(R.id.toolbar);

    setSupportActionBar(toolbar);
    requireSupportActionBar().setDisplayHomeAsUpEnabled(true);
    requireSupportActionBar().setTitle(R.string.AndroidManifest__linked_devices);

    this.deviceAddFragment  = new DeviceAddFragment();
    this.deviceListFragment = new DeviceListFragment();
    this.deviceLinkFragment = new DeviceLinkFragment();

    this.deviceListFragment.setAddDeviceButtonListener(this);
    this.deviceAddFragment.setScanListener(this);

    if (getIntent().getBooleanExtra(EXTRA_DIRECT_TO_SCANNER, false)) {
      initFragment(R.id.fragment_container, deviceAddFragment, dynamicLanguage.getCurrentLocale());
    } else {
      initFragment(R.id.fragment_container, deviceListFragment, dynamicLanguage.getCurrentLocale());
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      finish();
      return true;
    }

    return false;
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.device_add, menu);
    cameraSwitchItem = menu.findItem(R.id.device_add_camera_switch);
    cameraSwitchItem.setVisible(false);
    return super.onCreateOptionsMenu(menu);
  }

  public MenuItem getCameraSwitchItem() {
    return cameraSwitchItem;
  }

  @Override
  public void onClick(View v) {
    Permissions.with(this)
               .request(Manifest.permission.CAMERA)
               .ifNecessary()
               .withRationaleDialog(getString(R.string.CameraXFragment_allow_access_camera), getString(R.string.CameraXFragment_to_scan_qr_code_allow_camera), R.drawable.symbol_camera_24)
               .withPermanentDenialDialog(getString(R.string.DeviceActivity_signal_needs_the_camera_permission_in_order_to_scan_a_qr_code), null, R.string.CameraXFragment_allow_access_camera, R.string.CameraXFragment_to_scan_qr_codes, getSupportFragmentManager())
               .onAllGranted(() -> {
                 getSupportFragmentManager().beginTransaction()
                                            .replace(R.id.fragment_container, deviceAddFragment)
                                            .addToBackStack(null)
                                            .commitAllowingStateLoss();
               })
               .onAnyDenied(() -> Toast.makeText(this, R.string.CameraXFragment_signal_needs_camera_access_scan_qr_code, Toast.LENGTH_LONG).show())
               .execute();
  }

  @Override
  public void onQrDataFound(@NonNull final String data) {
    ThreadUtil.runOnMain(() -> {
      ((Vibrator)getSystemService(Context.VIBRATOR_SERVICE)).vibrate(50);
      Uri uri = Uri.parse(data);
      deviceLinkFragment.setLinkClickedListener(uri, DeviceActivity.this);

      deviceAddFragment.setSharedElementReturnTransition(TransitionInflater.from(DeviceActivity.this).inflateTransition(R.transition.fragment_shared));
      deviceAddFragment.setExitTransition(TransitionInflater.from(DeviceActivity.this).inflateTransition(android.R.transition.fade));

      deviceLinkFragment.setSharedElementEnterTransition(TransitionInflater.from(DeviceActivity.this).inflateTransition(R.transition.fragment_shared));
      deviceLinkFragment.setEnterTransition(TransitionInflater.from(DeviceActivity.this).inflateTransition(android.R.transition.fade));

      getSupportFragmentManager().beginTransaction()
                                 .addToBackStack(null)
                                 .addSharedElement(deviceAddFragment.getDevicesImage(), "devices")
                                 .replace(R.id.fragment_container, deviceLinkFragment)
                                 .commit();

    });
  }

  @SuppressLint("MissingSuperCall")
  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

  @SuppressLint("StaticFieldLeak")
  @Override
  public void onLink(final Uri uri) {
    new ProgressDialogAsyncTask<Void, Void, Integer>(this,
                                                     R.string.DeviceProvisioningActivity_content_progress_title,
                                                     R.string.DeviceProvisioningActivity_content_progress_content)
    {
      private static final int SUCCESS        = 0;
      private static final int NO_DEVICE      = 1;
      private static final int NETWORK_ERROR  = 2;
      private static final int KEY_ERROR      = 3;
      private static final int LIMIT_EXCEEDED = 4;
      private static final int BAD_CODE       = 5;

      @Override
      protected Integer doInBackground(Void... params) {
        boolean isMultiDevice = TextSecurePreferences.isMultiDevice(DeviceActivity.this);

        try {
          Context                     context          = DeviceActivity.this;
          SignalServiceAccountManager accountManager   = AppDependencies.getSignalServiceAccountManager();
          String                      verificationCode = accountManager.getNewDeviceVerificationCode();
          String                      ephemeralId      = uri.getQueryParameter("uuid");
          String                      publicKeyEncoded = uri.getQueryParameter("pub_key");

          if (TextUtils.isEmpty(ephemeralId) || TextUtils.isEmpty(publicKeyEncoded)) {
            Log.w(TAG, "UUID or Key is empty!");
            return BAD_CODE;
          }

          ECPublicKey     publicKey          = Curve.decodePoint(Base64.decode(publicKeyEncoded), 0);
          IdentityKeyPair aciIdentityKeyPair = SignalStore.account().getAciIdentityKey();
          IdentityKeyPair pniIdentityKeyPair = SignalStore.account().getPniIdentityKey();
          ProfileKey      profileKey         = ProfileKeyUtil.getSelfProfileKey();

          TextSecurePreferences.setMultiDevice(DeviceActivity.this, true);
          accountManager.addDevice(ephemeralId, publicKey, aciIdentityKeyPair, pniIdentityKeyPair, profileKey, SignalStore.svr().getOrCreateMasterKey(), verificationCode);

          return SUCCESS;
        } catch (NotFoundException e) {
          Log.w(TAG, e);
          TextSecurePreferences.setMultiDevice(DeviceActivity.this, isMultiDevice);
          return NO_DEVICE;
        } catch (DeviceLimitExceededException e) {
          Log.w(TAG, e);
          TextSecurePreferences.setMultiDevice(DeviceActivity.this, isMultiDevice);
          return LIMIT_EXCEEDED;
        } catch (IOException e) {
          Log.w(TAG, e);
          TextSecurePreferences.setMultiDevice(DeviceActivity.this, isMultiDevice);
          return NETWORK_ERROR;
        } catch (InvalidKeyException e) {
          Log.w(TAG, e);
          TextSecurePreferences.setMultiDevice(DeviceActivity.this, isMultiDevice);
          return KEY_ERROR;
        }
      }

      @Override
      protected void onPostExecute(Integer result) {
        super.onPostExecute(result);

        LinkedDeviceInactiveCheckJob.enqueue();

        Context context = DeviceActivity.this;

        switch (result) {
          case SUCCESS:
            Toast.makeText(context, R.string.DeviceProvisioningActivity_content_progress_success, Toast.LENGTH_SHORT).show();
            finish();
            return;
          case NO_DEVICE:
            Toast.makeText(context, R.string.DeviceProvisioningActivity_content_progress_no_device, Toast.LENGTH_LONG).show();
            break;
          case NETWORK_ERROR:
            Toast.makeText(context, R.string.DeviceProvisioningActivity_content_progress_network_error, Toast.LENGTH_LONG).show();
            break;
          case KEY_ERROR:
            Toast.makeText(context, R.string.DeviceProvisioningActivity_content_progress_key_error, Toast.LENGTH_LONG).show();
            break;
          case LIMIT_EXCEEDED:
            Toast.makeText(context, R.string.DeviceProvisioningActivity_sorry_you_have_too_many_devices_linked_already, Toast.LENGTH_LONG).show();
            break;
          case BAD_CODE:
            Toast.makeText(context, R.string.DeviceActivity_sorry_this_is_not_a_valid_device_link_qr_code, Toast.LENGTH_LONG).show();
            break;
        }

        getSupportFragmentManager().popBackStackImmediate();
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }
}
