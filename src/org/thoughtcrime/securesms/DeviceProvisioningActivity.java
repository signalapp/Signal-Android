package org.thoughtcrime.securesms;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.push.TextSecureCommunicationFactory;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.ProgressDialogAsyncTask;
import org.whispersystems.libaxolotl.IdentityKeyPair;
import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.ecc.Curve;
import org.whispersystems.libaxolotl.ecc.ECPublicKey;
import org.whispersystems.textsecure.api.TextSecureAccountManager;
import org.whispersystems.textsecure.api.push.exceptions.NotFoundException;

import java.io.IOException;

public class DeviceProvisioningActivity extends PassphraseRequiredActionBarActivity {

  private static final String TAG = DeviceProvisioningActivity.class.getSimpleName();

  private Button       continueButton;
  private Button       cancelButton;
  private Uri          uri;
  private MasterSecret masterSecret;

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);
    setContentView(R.layout.device_provisioning_activity);

    initializeResources();
  }

  @Override
  public void onNewMasterSecret(MasterSecret masterSecret) {
    this.masterSecret = masterSecret;
  }

  private void initializeResources() {
    this.continueButton = (Button)findViewById(R.id.continue_button);
    this.cancelButton   = (Button)findViewById(R.id.cancel_button);
    this.uri            = getIntent().getData();

    this.continueButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        handleProvisioning();
      }
    });

    this.cancelButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        finish();
      }
    });
  }

  private void handleProvisioning() {
    new ProgressDialogAsyncTask<Void, Void, Integer>(this, "Adding device...", "Adding new device...") {
      private static final int SUCCESS       = 0;
      private static final int NO_DEVICE     = 1;
      private static final int NETWORK_ERROR = 2;
      private static final int KEY_ERROR     = 3;

      @Override
      protected Integer doInBackground(Void... params) {
        try {
          Context                  context          = DeviceProvisioningActivity.this;
          TextSecureAccountManager accountManager   = TextSecureCommunicationFactory.createManager(context);
          String                   verificationCode = accountManager.getNewDeviceVerificationCode();
          String                   ephemeralId      = uri.getQueryParameter("uuid");
          String                   publicKeyEncoded = uri.getQueryParameter("pub_key");
          ECPublicKey              publicKey        = Curve.decodePoint(Base64.decode(publicKeyEncoded), 0);
          IdentityKeyPair          identityKeyPair  = IdentityKeyUtil.getIdentityKeyPair(context, masterSecret);

          accountManager.addDevice(ephemeralId, publicKey, identityKeyPair, verificationCode);
          return SUCCESS;

        } catch (NotFoundException e) {
          Log.w(TAG, e);
          return NO_DEVICE;
        } catch (IOException e) {
          Log.w(TAG, e);
          return NETWORK_ERROR;
        } catch (InvalidKeyException e) {
          Log.w(TAG, e);
          return KEY_ERROR;
        }
      }

      @Override
      protected void onPostExecute(Integer result) {
        super.onPostExecute(result);

        Context context = DeviceProvisioningActivity.this;

        switch (result) {
          case SUCCESS:
            Toast.makeText(context, "Device added!", Toast.LENGTH_SHORT).show();
            finish();
            break;
          case NO_DEVICE:
            Toast.makeText(context, "No device found!", Toast.LENGTH_LONG).show();
            break;
          case NETWORK_ERROR:
            Toast.makeText(context, "Network error!", Toast.LENGTH_LONG).show();
            break;
          case KEY_ERROR:
            Toast.makeText(context, "Invalid QR code!", Toast.LENGTH_LONG).show();
            break;
        }
      }
    }.execute();
  }
}
