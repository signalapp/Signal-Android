package org.thoughtcrime.securesms;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.push.AccountManagerFactory;
import org.thoughtcrime.securesms.service.RegistrationService;
import org.thoughtcrime.securesms.util.Dialogs;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.push.exceptions.ExpectationFailedException;
import org.whispersystems.signalservice.api.push.exceptions.RateLimitException;
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter;

import java.io.IOException;

import static org.thoughtcrime.securesms.service.RegistrationService.RegistrationState;

public class RegistrationProgressActivity extends BaseActionBarActivity {

  private static final String TAG = RegistrationProgressActivity.class.getSimpleName();

  public static final String NUMBER_EXTRA        = "e164number";
  public static final String MASTER_SECRET_EXTRA = "master_secret";
  public static final String GCM_SUPPORTED_EXTRA = "gcm_supported";

  private static final int FOCUSED_COLOR   = Color.parseColor("#ff333333");
  private static final int UNFOCUSED_COLOR = Color.parseColor("#ff808080");

  private ServiceConnection    serviceConnection        = new RegistrationServiceConnection();
  private Handler              registrationStateHandler = new RegistrationStateHandler();
  private RegistrationReceiver registrationReceiver     = new RegistrationReceiver();

  private RegistrationService registrationService;

  private LinearLayout registrationLayout;
  private LinearLayout verificationFailureLayout;
  private LinearLayout connectivityFailureLayout;
  private RelativeLayout timeoutProgressLayout;

  private ProgressBar registrationProgress;
  private ProgressBar connectingProgress;
  private ProgressBar verificationProgress;
  private ProgressBar generatingKeysProgress;
  private ProgressBar gcmRegistrationProgress;


  private ImageView   connectingCheck;
  private ImageView   verificationCheck;
  private ImageView   generatingKeysCheck;
  private ImageView   gcmRegistrationCheck;

  private TextView    connectingText;
  private TextView    verificationText;
  private TextView    registrationTimerText;
  private TextView    generatingKeysText;
  private TextView    gcmRegistrationText;

  private Button      verificationFailureButton;
  private Button      connectivityFailureButton;
  private Button      callButton;
  private Button      verifyButton;

  private EditText    codeEditText;

  private MasterSecret masterSecret;
  private boolean      gcmSupported;
  private volatile boolean visible;

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);
    getSupportActionBar().setTitle(getString(R.string.RegistrationProgressActivity_verifying_number));
    setContentView(R.layout.registration_progress_activity);

    initializeResources();
    initializeLinks();
    initializeServiceBinding();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    shutdownServiceBinding();
  }

  @Override
  public void onResume() {
    super.onResume();
    handleActivityVisible();
  }

  @Override
  public void onPause() {
    super.onPause();
    handleActivityNotVisible();
  }

  @Override
  public void onBackPressed() {

  }

  private void initializeServiceBinding() {
    Intent intent = new Intent(this, RegistrationService.class);
    bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
  }

  private void initializeResources() {
    this.masterSecret              = getIntent().getParcelableExtra(MASTER_SECRET_EXTRA);
    this.gcmSupported              = getIntent().getBooleanExtra(GCM_SUPPORTED_EXTRA, true);
    this.registrationLayout        = (LinearLayout)findViewById(R.id.registering_layout);
    this.verificationFailureLayout = (LinearLayout)findViewById(R.id.verification_failure_layout);
    this.connectivityFailureLayout = (LinearLayout)findViewById(R.id.connectivity_failure_layout);
    this.registrationProgress      = (ProgressBar) findViewById(R.id.registration_progress);
    this.connectingProgress        = (ProgressBar) findViewById(R.id.connecting_progress);
    this.verificationProgress      = (ProgressBar) findViewById(R.id.verification_progress);
    this.generatingKeysProgress    = (ProgressBar) findViewById(R.id.generating_keys_progress);
    this.gcmRegistrationProgress   = (ProgressBar) findViewById(R.id.gcm_registering_progress);
    this.connectingCheck           = (ImageView)   findViewById(R.id.connecting_complete);
    this.verificationCheck         = (ImageView)   findViewById(R.id.verification_complete);
    this.generatingKeysCheck       = (ImageView)   findViewById(R.id.generating_keys_complete);
    this.gcmRegistrationCheck      = (ImageView)   findViewById(R.id.gcm_registering_complete);
    this.connectingText            = (TextView)    findViewById(R.id.connecting_text);
    this.verificationText          = (TextView)    findViewById(R.id.verification_text);
    this.registrationTimerText     = (TextView)    findViewById(R.id.registration_timer);
    this.generatingKeysText        = (TextView)    findViewById(R.id.generating_keys_text);
    this.gcmRegistrationText       = (TextView)    findViewById(R.id.gcm_registering_text);
    this.verificationFailureButton = (Button)      findViewById(R.id.verification_failure_edit_button);
    this.connectivityFailureButton = (Button)      findViewById(R.id.connectivity_failure_edit_button);
    this.callButton                = (Button)      findViewById(R.id.call_button);
    this.verifyButton              = (Button)      findViewById(R.id.verify_button);
    this.codeEditText              = (EditText)    findViewById(R.id.telephone_code);
    this.timeoutProgressLayout     = (RelativeLayout) findViewById(R.id.timer_progress_layout);
    Button editButton              = (Button)      findViewById(R.id.edit_button);

    editButton.setOnClickListener(new EditButtonListener());
    this.verificationFailureButton.setOnClickListener(new EditButtonListener());
    this.connectivityFailureButton.setOnClickListener(new EditButtonListener());
  }

  private void initializeLinks() {
    TextView        failureText     = (TextView) findViewById(R.id.sms_failed_text);
    String          pretext         = getString(R.string.registration_progress_activity__signal_timed_out_while_waiting_for_a_verification_sms_message);
    String          link            = getString(R.string.RegistrationProblemsActivity_possible_problems);
    SpannableString spannableString = new SpannableString(pretext + " " + link);

    spannableString.setSpan(new ClickableSpan() {
      @Override
      public void onClick(View widget) {
        new AlertDialog.Builder(RegistrationProgressActivity.this)
                .setTitle(R.string.RegistrationProblemsActivity_possible_problems)
                .setView(R.layout.registration_problems)
                .setNeutralButton(android.R.string.ok, null)
                .show();
      }
    }, pretext.length() + 1, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    failureText.setText(spannableString);
    failureText.setMovementMethod(LinkMovementMethod.getInstance());
  }

  private void handleActivityVisible() {
    IntentFilter filter = new IntentFilter(RegistrationService.REGISTRATION_EVENT);
    filter.setPriority(1000);
    registerReceiver(registrationReceiver, filter);
    visible = true;
  }

  private void handleActivityNotVisible() {
    unregisterReceiver(registrationReceiver);
    visible = false;
  }

  private void handleStateIdle() {
    if (hasNumberDirective()) {
      Intent intent = new Intent(this, RegistrationService.class);
      intent.setAction(RegistrationService.REGISTER_NUMBER_ACTION);
      intent.putExtra(RegistrationService.NUMBER_EXTRA, getNumberDirective());
      intent.putExtra(RegistrationService.MASTER_SECRET_EXTRA, masterSecret);
      intent.putExtra(RegistrationService.GCM_SUPPORTED_EXTRA, gcmSupported);
      startService(intent);
    } else {
      Intent intent = new Intent(this, RegistrationActivity.class);
      intent.putExtra("master_secret", masterSecret);
      startActivity(intent);
      finish();
    }
  }

  private void handleStateConnecting() {
    this.registrationLayout.setVisibility(View.VISIBLE);
    this.verificationFailureLayout.setVisibility(View.GONE);
    this.connectivityFailureLayout.setVisibility(View.GONE);
    this.connectingProgress.setVisibility(View.VISIBLE);
    this.connectingCheck.setVisibility(View.INVISIBLE);
    this.verificationProgress.setVisibility(View.INVISIBLE);
    this.verificationCheck.setVisibility(View.INVISIBLE);
    this.generatingKeysProgress.setVisibility(View.INVISIBLE);
    this.generatingKeysCheck.setVisibility(View.INVISIBLE);
    this.gcmRegistrationProgress.setVisibility(View.INVISIBLE);
    this.gcmRegistrationCheck.setVisibility(View.INVISIBLE);
    this.connectingText.setTextColor(FOCUSED_COLOR);
    this.verificationText.setTextColor(UNFOCUSED_COLOR);
    this.generatingKeysText.setTextColor(UNFOCUSED_COLOR);
    this.gcmRegistrationText.setTextColor(UNFOCUSED_COLOR);
    this.timeoutProgressLayout.setVisibility(View.VISIBLE);
  }

  private void handleStateVerifying() {
    this.registrationLayout.setVisibility(View.VISIBLE);
    this.verificationFailureLayout.setVisibility(View.GONE);
    this.connectivityFailureLayout.setVisibility(View.GONE);
    this.connectingProgress.setVisibility(View.INVISIBLE);
    this.connectingCheck.setVisibility(View.VISIBLE);
    this.verificationProgress.setVisibility(View.VISIBLE);
    this.verificationCheck.setVisibility(View.INVISIBLE);
    this.generatingKeysProgress.setVisibility(View.INVISIBLE);
    this.generatingKeysCheck.setVisibility(View.INVISIBLE);
    this.gcmRegistrationProgress.setVisibility(View.INVISIBLE);
    this.gcmRegistrationCheck.setVisibility(View.INVISIBLE);
    this.connectingText.setTextColor(UNFOCUSED_COLOR);
    this.verificationText.setTextColor(FOCUSED_COLOR);
    this.generatingKeysText.setTextColor(UNFOCUSED_COLOR);
    this.gcmRegistrationText.setTextColor(UNFOCUSED_COLOR);
    this.registrationProgress.setVisibility(View.VISIBLE);
    this.timeoutProgressLayout.setVisibility(View.VISIBLE);
  }

  private void handleStateGeneratingKeys() {
    this.registrationLayout.setVisibility(View.VISIBLE);
    this.verificationFailureLayout.setVisibility(View.GONE);
    this.connectivityFailureLayout.setVisibility(View.GONE);
    this.connectingProgress.setVisibility(View.INVISIBLE);
    this.connectingCheck.setVisibility(View.VISIBLE);
    this.verificationProgress.setVisibility(View.INVISIBLE);
    this.verificationCheck.setVisibility(View.VISIBLE);
    this.generatingKeysProgress.setVisibility(View.VISIBLE);
    this.generatingKeysCheck.setVisibility(View.INVISIBLE);
    this.gcmRegistrationProgress.setVisibility(View.INVISIBLE);
    this.gcmRegistrationCheck.setVisibility(View.INVISIBLE);
    this.connectingText.setTextColor(UNFOCUSED_COLOR);
    this.verificationText.setTextColor(UNFOCUSED_COLOR);
    this.generatingKeysText.setTextColor(FOCUSED_COLOR);
    this.gcmRegistrationText.setTextColor(UNFOCUSED_COLOR);
    this.registrationProgress.setVisibility(View.INVISIBLE);
    this.timeoutProgressLayout.setVisibility(View.INVISIBLE);
  }

  private void handleStateGcmRegistering() {
    this.registrationLayout.setVisibility(View.VISIBLE);
    this.verificationFailureLayout.setVisibility(View.GONE);
    this.connectivityFailureLayout.setVisibility(View.GONE);
    this.connectingProgress.setVisibility(View.INVISIBLE);
    this.connectingCheck.setVisibility(View.VISIBLE);
    this.verificationProgress.setVisibility(View.INVISIBLE);
    this.verificationCheck.setVisibility(View.VISIBLE);
    this.generatingKeysProgress.setVisibility(View.INVISIBLE);
    this.generatingKeysCheck.setVisibility(View.VISIBLE);
    this.gcmRegistrationProgress.setVisibility(View.VISIBLE);
    this.gcmRegistrationCheck.setVisibility(View.INVISIBLE);
    this.connectingText.setTextColor(UNFOCUSED_COLOR);
    this.verificationText.setTextColor(UNFOCUSED_COLOR);
    this.generatingKeysText.setTextColor(UNFOCUSED_COLOR);
    this.gcmRegistrationText.setTextColor(FOCUSED_COLOR);
    this.registrationProgress.setVisibility(View.INVISIBLE);
    this.timeoutProgressLayout.setVisibility(View.INVISIBLE);
  }

  private void handleGcmTimeout(RegistrationState state) {
    handleConnectivityError(state);
  }

  private void handleVerificationRequestedVoice(RegistrationState state) {
    handleVerificationTimeout(state);
    verifyButton.setOnClickListener(new VerifyClickListener(state.number, state.password, gcmSupported));
    verifyButton.setEnabled(true);
    codeEditText.setEnabled(true);
  }

  private void handleVerificationTimeout(RegistrationState state) {
    this.callButton.setOnClickListener(new CallClickListener(state.number));
    this.verifyButton.setEnabled(false);
    this.codeEditText.setEnabled(false);
    this.registrationLayout.setVisibility(View.GONE);
    this.connectivityFailureLayout.setVisibility(View.GONE);
    this.verificationFailureLayout.setVisibility(View.VISIBLE);
    this.verificationFailureButton.setText(String.format(getString(R.string.RegistrationProgressActivity_edit_s),
                                                         PhoneNumberFormatter.formatNumberInternational(state.number)));
  }

  private void handleConnectivityError(RegistrationState state) {
    this.registrationLayout.setVisibility(View.GONE);
    this.verificationFailureLayout.setVisibility(View.GONE);
    this.connectivityFailureLayout.setVisibility(View.VISIBLE);
    this.connectivityFailureButton.setText(String.format(getString(R.string.RegistrationProgressActivity_edit_s),
                                                         PhoneNumberFormatter.formatNumberInternational(state.number)));
  }

  private void handleMultiRegistrationError(RegistrationState state) {
    handleVerificationTimeout(state);
    Dialogs.showAlertDialog(this,                         getString(R.string.RegistrationProgressActivity_registration_conflict),
                         getString(R.string.RegistrationProgressActivity_this_number_is_already_registered_on_a_different));
  }

  private void handleVerificationComplete() {
    if (visible) {
      Toast.makeText(this,
                     R.string.RegistrationProgressActivity_registration_complete,
                     Toast.LENGTH_LONG).show();
    }

    shutdownService();
    startActivity(new Intent(this, ConversationListActivity.class));
    finish();
  }

  private void handleTimerUpdate() {
    if (registrationService == null)
      return;

    int totalSecondsRemaining = registrationService.getSecondsRemaining();
    int minutesRemaining      = totalSecondsRemaining / 60;
    int secondsRemaining      = totalSecondsRemaining - (minutesRemaining * 60);
    double percentageComplete = (double)((60 * 2) - totalSecondsRemaining) / (double)(60 * 2);
    int progress              = (int)Math.round(((double)registrationProgress.getMax()) * percentageComplete);

    this.registrationProgress.setProgress(progress);
    this.registrationTimerText.setText(String.format("%02d:%02d", minutesRemaining, secondsRemaining));

    registrationStateHandler.sendEmptyMessageDelayed(RegistrationState.STATE_TIMER, 1000);
  }

  private boolean hasNumberDirective() {
    return getIntent().getStringExtra(NUMBER_EXTRA) != null;
  }

  private String getNumberDirective() {
    return getIntent().getStringExtra(NUMBER_EXTRA);
  }

  private void shutdownServiceBinding() {
    if (serviceConnection != null) {
      unbindService(serviceConnection);
      serviceConnection = null;
    }
  }

  private void shutdownService() {
    if (registrationService != null) {
      registrationService.shutdown();
      registrationService = null;
    }

    shutdownServiceBinding();

    Intent serviceIntent = new Intent(RegistrationProgressActivity.this, RegistrationService.class);
    stopService(serviceIntent);
  }

  private class RegistrationServiceConnection implements ServiceConnection {
    @Override
    public void onServiceConnected(ComponentName className, IBinder service) {
      registrationService  = ((RegistrationService.RegistrationServiceBinder)service).getService();
      registrationService.setRegistrationStateHandler(registrationStateHandler);

      RegistrationState state = registrationService.getRegistrationState();
      registrationStateHandler.obtainMessage(state.state, state).sendToTarget();

      handleTimerUpdate();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
      registrationService.setRegistrationStateHandler(null);
    }
  }

  private class RegistrationStateHandler extends Handler {
    @Override
    public void handleMessage(Message message) {
      RegistrationState state = (RegistrationState)message.obj;

      switch (message.what) {
      case RegistrationState.STATE_IDLE:                 handleStateIdle();                       break;
      case RegistrationState.STATE_CONNECTING:           handleStateConnecting();                 break;
      case RegistrationState.STATE_VERIFYING:            handleStateVerifying();                  break;
      case RegistrationState.STATE_TIMER:                handleTimerUpdate();                     break;
      case RegistrationState.STATE_GENERATING_KEYS:      handleStateGeneratingKeys();             break;
      case RegistrationState.STATE_GCM_REGISTERING:      handleStateGcmRegistering();             break;
      case RegistrationState.STATE_TIMEOUT:              handleVerificationTimeout(state);        break;
      case RegistrationState.STATE_COMPLETE:             handleVerificationComplete();            break;
      case RegistrationState.STATE_GCM_TIMEOUT:          handleGcmTimeout(state);                 break;
      case RegistrationState.STATE_NETWORK_ERROR:        handleConnectivityError(state);          break;
      case RegistrationState.STATE_MULTI_REGISTERED:     handleMultiRegistrationError(state);     break;
      case RegistrationState.STATE_VOICE_REQUESTED:      handleVerificationRequestedVoice(state); break;
      }
    }
  }

  private class EditButtonListener implements View.OnClickListener {
    @Override
    public void onClick(View v) {
      shutdownService();

      Intent activityIntent = new Intent(RegistrationProgressActivity.this, RegistrationActivity.class);
      activityIntent.putExtra(RegistrationProgressActivity.MASTER_SECRET_EXTRA, masterSecret);
      activityIntent.putExtra(RegistrationProgressActivity.GCM_SUPPORTED_EXTRA, gcmSupported);
      startActivity(activityIntent);
      finish();
    }
  }

  private static class RegistrationReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      abortBroadcast();
    }
  }

  private class VerifyClickListener implements View.OnClickListener {

    private static final int SUCCESS                  = 0;
    private static final int NETWORK_ERROR            = 1;
    private static final int RATE_LIMIT_ERROR         = 2;
    private static final int VERIFICATION_ERROR       = 3;
    private static final int MULTI_REGISTRATION_ERROR = 4;

    private final String  e164number;
    private final String  password;
    private final String  signalingKey;
    private final boolean gcmSupported;
    private final Context context;

    private ProgressDialog progressDialog;

    public VerifyClickListener(String e164number, String password, boolean gcmSupported) {
      this.e164number   = e164number;
      this.password     = password;
      this.signalingKey = Util.getSecret(52);
      this.gcmSupported = gcmSupported;
      this.context      = RegistrationProgressActivity.this;
    }

    @Override
    public void onClick(View v) {
      final String code = codeEditText.getText().toString();

      if (TextUtils.isEmpty(code)) {
        Toast.makeText(context,
                       getString(R.string.RegistrationProgressActivity_you_must_enter_the_code_you_received_first),
                       Toast.LENGTH_LONG).show();
        return;
      }

      new AsyncTask<Void, Void, Integer>() {

        @Override
        protected void onPreExecute() {
          progressDialog = ProgressDialog.show(context,
                                               getString(R.string.RegistrationProgressActivity_connecting),
                                               getString(R.string.RegistrationProgressActivity_connecting_for_verification),
                                               true, false);
        }

        @Override
        protected void onPostExecute(Integer result) {
          if (progressDialog != null) progressDialog.dismiss();

          switch (result) {
            case SUCCESS:
              Intent intent = new Intent(context, RegistrationService.class);
              intent.setAction(RegistrationService.VOICE_REGISTER_ACTION);
              intent.putExtra(RegistrationService.NUMBER_EXTRA, e164number);
              intent.putExtra(RegistrationService.PASSWORD_EXTRA, password);
              intent.putExtra(RegistrationService.SIGNALING_KEY_EXTRA, signalingKey);
              intent.putExtra(RegistrationService.MASTER_SECRET_EXTRA, masterSecret);
              intent.putExtra(RegistrationService.GCM_SUPPORTED_EXTRA, gcmSupported);
              startService(intent);
              break;
            case NETWORK_ERROR:
              Dialogs.showAlertDialog(context, getString(R.string.RegistrationProgressActivity_network_error),
                                   getString(R.string.RegistrationProgressActivity_unable_to_connect));
              break;
            case VERIFICATION_ERROR:
              Dialogs.showAlertDialog(context, getString(R.string.RegistrationProgressActivity_verification_failed),
                                   getString(R.string.RegistrationProgressActivity_the_verification_code_you_submitted_is_incorrect));
              break;
            case RATE_LIMIT_ERROR:
              Dialogs.showAlertDialog(context, getString(R.string.RegistrationProgressActivity_too_many_attempts),
                                   getString(R.string.RegistrationProgressActivity_youve_submitted_an_incorrect_verification_code_too_many_times));
              break;
            case MULTI_REGISTRATION_ERROR:
              Dialogs.showAlertDialog(context, getString(R.string.RegistrationProgressActivity_registration_conflict),
                                   getString(R.string.RegistrationProgressActivity_this_number_is_already_registered_on_a_different));
              break;
          }
        }

        @Override
        protected Integer doInBackground(Void... params) {
          try {
            SignalServiceAccountManager accountManager = AccountManagerFactory.createManager(context, e164number, password);
            int                         registrationId = TextSecurePreferences.getLocalRegistrationId(context);
            boolean                     video          = TextSecurePreferences.isWebrtcCallingEnabled(context);

            accountManager.verifyAccountWithCode(code, signalingKey, registrationId, true, video, !gcmSupported);

            return SUCCESS;
          } catch (ExpectationFailedException e) {
            Log.w(TAG, e);
            return MULTI_REGISTRATION_ERROR;
          } catch (RateLimitException e) {
            Log.w(TAG, e);
            return RATE_LIMIT_ERROR;
          } catch (IOException e) {
            Log.w(TAG, e);
            return NETWORK_ERROR;
          }
        }
      }.execute();
    }
  }

  private class CallClickListener implements View.OnClickListener {

    private static final int SUCCESS             = 0;
    private static final int NETWORK_ERROR       = 1;
    private static final int RATE_LIMIT_EXCEEDED = 2;
    private static final int CREATE_ERROR        = 3;

    private final String  e164number;
    private final String password;
    private final Context context;

    public CallClickListener(String e164number) {
      this.e164number   = e164number;
      this.password     = Util.getSecret(18);
      this.context      = RegistrationProgressActivity.this;
    }

    @Override
    public void onClick(View v) {
      new AsyncTask<Void, Void, Integer>() {
        private ProgressDialog progressDialog;

        @Override
        protected void onPreExecute() {
          progressDialog = ProgressDialog.show(context,
                                               getString(R.string.RegistrationProgressActivity_requesting_call),
                                               getString(R.string.RegistrationProgressActivity_requesting_incoming_call),
                                               true, false);
        }

        @Override
        protected void onPostExecute(Integer result) {
          if (progressDialog != null) progressDialog.dismiss();

          switch (result) {
            case SUCCESS:
              Intent intent = new Intent(context, RegistrationService.class);
              intent.setAction(RegistrationService.VOICE_REQUESTED_ACTION);
              intent.putExtra(RegistrationService.NUMBER_EXTRA, e164number);
              intent.putExtra(RegistrationService.PASSWORD_EXTRA, password);
              intent.putExtra(RegistrationService.MASTER_SECRET_EXTRA, masterSecret);
              intent.putExtra(RegistrationService.GCM_SUPPORTED_EXTRA, gcmSupported);
              startService(intent);

              callButton.setEnabled(false);
              new Handler().postDelayed(new Runnable(){
                @Override
                public void run() {
                  callButton.setEnabled(true);
                }
              }, 15000);
              break;
            case NETWORK_ERROR:
              Dialogs.showAlertDialog(context,
                                   getString(R.string.RegistrationProgressActivity_network_error),
                                   getString(R.string.RegistrationProgressActivity_unable_to_connect));
              break;
            case CREATE_ERROR:
              Dialogs.showAlertDialog(context,
                                   getString(R.string.RegistrationProgressActivity_server_error),
                                   getString(R.string.RegistrationProgressActivity_the_server_encountered_an_error));
              break;
            case RATE_LIMIT_EXCEEDED:
              Dialogs.showAlertDialog(context,
                                   getString(R.string.RegistrationProgressActivity_too_many_requests),
                                   getString(R.string.RegistrationProgressActivity_youve_already_requested_a_voice_call));
              break;
          }
        }

        @Override
        protected Integer doInBackground(Void... params) {
          try {
            SignalServiceAccountManager accountManager = AccountManagerFactory.createManager(context, e164number, password);
            accountManager.requestVoiceVerificationCode();

            return SUCCESS;
          } catch (RateLimitException e) {
            Log.w(TAG, e);
            return RATE_LIMIT_EXCEEDED;
          } catch (IOException e) {
            Log.w(TAG, e);
            return NETWORK_ERROR;
          }
        }
      }.execute();
    }

  }
}
