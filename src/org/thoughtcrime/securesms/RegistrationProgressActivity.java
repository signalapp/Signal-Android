package org.thoughtcrime.securesms;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockActivity;
import org.thoughtcrime.securesms.service.RegistrationService;
import org.thoughtcrime.securesms.util.PhoneNumberFormatter;

import static org.thoughtcrime.securesms.service.RegistrationService.RegistrationState;

public class RegistrationProgressActivity extends SherlockActivity {

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
  private ProgressBar gcmRegistrationProgress;

  private ImageView   connectingCheck;
  private ImageView   verificationCheck;
  private ImageView   gcmRegistrationCheck;

  private TextView    connectingText;
  private TextView    verificationText;
  private TextView    registrationTimerText;
  private TextView    gcmRegistrationText;

  private Button      editButton;
  private Button      verificationFailureButton;
  private Button      connectivityFailureButton;

  private volatile boolean visible;

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);
    this.getSupportActionBar().setTitle("Verifying number");
    setContentView(R.layout.registration_progress_activity);

    initializeResources();
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
    this.registrationLayout        = (LinearLayout)findViewById(R.id.registering_layout);
    this.verificationFailureLayout = (LinearLayout)findViewById(R.id.verification_failure_layout);
    this.connectivityFailureLayout = (LinearLayout)findViewById(R.id.connectivity_failure_layout);
    this.registrationProgress      = (ProgressBar) findViewById(R.id.registration_progress);
    this.connectingProgress        = (ProgressBar) findViewById(R.id.connecting_progress);
    this.verificationProgress      = (ProgressBar) findViewById(R.id.verification_progress);
    this.gcmRegistrationProgress   = (ProgressBar) findViewById(R.id.gcm_registering_progress);
    this.connectingCheck           = (ImageView)   findViewById(R.id.connecting_complete);
    this.verificationCheck         = (ImageView)   findViewById(R.id.verification_complete);
    this.gcmRegistrationCheck      = (ImageView)   findViewById(R.id.gcm_registering_complete);
    this.connectingText            = (TextView)    findViewById(R.id.connecting_text);
    this.verificationText          = (TextView)    findViewById(R.id.verification_text);
    this.registrationTimerText     = (TextView)    findViewById(R.id.registration_timer);
    this.gcmRegistrationText       = (TextView)    findViewById(R.id.gcm_registering_text);
    this.editButton                = (Button)      findViewById(R.id.edit_button);
    this.verificationFailureButton = (Button)      findViewById(R.id.verification_failure_edit_button);
    this.connectivityFailureButton = (Button)      findViewById(R.id.connectivity_failure_edit_button);
    this.timeoutProgressLayout     = (RelativeLayout) findViewById(R.id.timer_progress_layout);

    this.editButton.setOnClickListener(new EditButtonListener());
    this.verificationFailureButton.setOnClickListener(new EditButtonListener());
    this.connectivityFailureButton.setOnClickListener(new EditButtonListener());
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
      intent.putExtra("e164number", getNumberDirective());
      startService(intent);
    } else {
      startActivity(new Intent(this, RegistrationActivity.class));
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
    this.gcmRegistrationProgress.setVisibility(View.INVISIBLE);
    this.gcmRegistrationCheck.setVisibility(View.INVISIBLE);
    this.connectingText.setTextColor(FOCUSED_COLOR);
    this.verificationText.setTextColor(UNFOCUSED_COLOR);
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
    this.gcmRegistrationProgress.setVisibility(View.INVISIBLE);
    this.gcmRegistrationCheck.setVisibility(View.INVISIBLE);
    this.connectingText.setTextColor(UNFOCUSED_COLOR);
    this.verificationText.setTextColor(FOCUSED_COLOR);
    this.gcmRegistrationText.setTextColor(UNFOCUSED_COLOR);
    this.registrationProgress.setVisibility(View.VISIBLE);
    this.timeoutProgressLayout.setVisibility(View.VISIBLE);
  }

  private void handleStateGcmRegistering() {
    this.registrationLayout.setVisibility(View.VISIBLE);
    this.verificationFailureLayout.setVisibility(View.GONE);
    this.connectivityFailureLayout.setVisibility(View.GONE);
    this.connectingProgress.setVisibility(View.INVISIBLE);
    this.connectingCheck.setVisibility(View.VISIBLE);
    this.verificationProgress.setVisibility(View.INVISIBLE);
    this.verificationCheck.setVisibility(View.VISIBLE);
    this.gcmRegistrationProgress.setVisibility(View.VISIBLE);
    this.gcmRegistrationCheck.setVisibility(View.INVISIBLE);
    this.connectingText.setTextColor(UNFOCUSED_COLOR);
    this.verificationText.setTextColor(UNFOCUSED_COLOR);
    this.gcmRegistrationText.setTextColor(FOCUSED_COLOR);
    this.registrationProgress.setVisibility(View.INVISIBLE);
    this.timeoutProgressLayout.setVisibility(View.INVISIBLE);
  }

  private void handleGcmTimeout(String number) {
    handleConnectivityError(number);
  }

  private void handleVerificationTimeout(String number) {
    this.registrationLayout.setVisibility(View.GONE);
    this.connectivityFailureLayout.setVisibility(View.GONE);
    this.verificationFailureLayout.setVisibility(View.VISIBLE);
    this.verificationFailureButton.setText(String.format("Edit %s",
                                                         PhoneNumberFormatter.formatNumberInternational(number)));
  }

  private void handleConnectivityError(String number) {
    this.registrationLayout.setVisibility(View.GONE);
    this.verificationFailureLayout.setVisibility(View.GONE);
    this.connectivityFailureLayout.setVisibility(View.VISIBLE);
    this.connectivityFailureButton.setText(String.format("Edit %s",
                                                         PhoneNumberFormatter.formatNumberInternational(number)));
  }

  private void handleVerificationComplete() {
    if (visible) {
      Toast.makeText(this, "Registration complete", Toast.LENGTH_LONG).show();
    }

    shutdownService();
    startActivity(new Intent(this, RoutingActivity.class));
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
    return getIntent().getStringExtra("e164number") != null;
  }

  private String getNumberDirective() {
    return getIntent().getStringExtra("e164number");
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
      registrationStateHandler.obtainMessage(state.state, state.number).sendToTarget();

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
      switch (message.what) {
        case RegistrationState.STATE_IDLE:              handleStateIdle();                              break;
        case RegistrationState.STATE_CONNECTING:        handleStateConnecting();                        break;
        case RegistrationState.STATE_VERIFYING:         handleStateVerifying();                         break;
        case RegistrationState.STATE_TIMER:             handleTimerUpdate();                            break;
        case RegistrationState.STATE_GCM_REGISTERING:   handleStateGcmRegistering();                    break;
        case RegistrationState.STATE_TIMEOUT:           handleVerificationTimeout((String)message.obj); break;
        case RegistrationState.STATE_COMPLETE:          handleVerificationComplete();                   break;
        case RegistrationState.STATE_GCM_TIMEOUT:       handleGcmTimeout((String)message.obj);          break;
        case RegistrationState.STATE_NETWORK_ERROR:     handleConnectivityError((String)message.obj);   break;
      }
    }
  }

  private class EditButtonListener implements View.OnClickListener {
    @Override
    public void onClick(View v) {
      shutdownService();

      Intent activityIntent = new Intent(RegistrationProgressActivity.this, RegistrationActivity.class);
      startActivity(activityIntent);
      finish();
    }
  }

  private class RegistrationReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      abortBroadcast();
    }
  }
}
