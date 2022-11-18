/*
 * Copyright (C) 2016-2017 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms;

import android.animation.TypeEvaluator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnticipateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.ScaleAnimation;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import org.signal.core.util.ThreadUtil;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.components.camera.CameraView;
import org.thoughtcrime.securesms.crypto.ReentrantSessionLock;
import org.thoughtcrime.securesms.crypto.IdentityKeyParcelable;
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.IdentityDatabase.VerifiedStatus;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.MultiDeviceVerifiedUpdateJob;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.qr.QrCode;
import org.thoughtcrime.securesms.qr.ScanListener;
import org.thoughtcrime.securesms.qr.ScanningThread;
import org.thoughtcrime.securesms.recipients.LiveRecipient;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.storage.StorageSyncHelper;
import org.thoughtcrime.securesms.util.DynamicDarkActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.IdentityUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.fingerprint.Fingerprint;
import org.whispersystems.libsignal.fingerprint.NumericFingerprintGenerator;
import org.whispersystems.signalservice.api.SignalSessionLock;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.nio.charset.Charset;
import java.util.Locale;


/**
 * Activity for verifying identity keys.
 *
 * @author Moxie Marlinspike
 */
@SuppressLint("StaticFieldLeak")
public class VerifyIdentityActivity extends PassphraseRequiredActivity {

  private static final String TAG = Log.tag(VerifyIdentityActivity.class);

  private static final String RECIPIENT_EXTRA = "recipient_id";
  private static final String IDENTITY_EXTRA  = "recipient_identity";
  private static final String VERIFIED_EXTRA  = "verified_state";

  private final DynamicTheme dynamicTheme = new DynamicDarkActionBarTheme();

  private final VerifyDisplayFragment displayFragment = new VerifyDisplayFragment();

  public static Intent newIntent(@NonNull Context context,
                                 @NonNull IdentityDatabase.IdentityRecord identityRecord)
  {
    return newIntent(context,
                     identityRecord.getRecipientId(),
                     identityRecord.getIdentityKey(),
                     identityRecord.getVerifiedStatus() == IdentityDatabase.VerifiedStatus.VERIFIED);
  }

  public static Intent newIntent(@NonNull Context context,
                                 @NonNull IdentityDatabase.IdentityRecord identityRecord,
                                 boolean verified)
  {
    return newIntent(context,
                     identityRecord.getRecipientId(),
                     identityRecord.getIdentityKey(),
                     verified);
  }

  public static Intent newIntent(@NonNull Context context,
                                 @NonNull RecipientId recipientId,
                                 @NonNull IdentityKey identityKey,
                                 boolean verified)
  {
    Intent intent = new Intent(context, VerifyIdentityActivity.class);

    intent.putExtra(RECIPIENT_EXTRA, recipientId);
    intent.putExtra(IDENTITY_EXTRA, new IdentityKeyParcelable(identityKey));
    intent.putExtra(VERIFIED_EXTRA, verified);

    return intent;
  }

  @Override
  public void onPreCreate() {
//    dynamicTheme.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle state, boolean ready) {

    LiveRecipient recipient = Recipient.live(getIntent().getParcelableExtra(RECIPIENT_EXTRA));

    Bundle extras = new Bundle();
    extras.putParcelable(VerifyDisplayFragment.RECIPIENT_ID, getIntent().getParcelableExtra(RECIPIENT_EXTRA));
    extras.putParcelable(VerifyDisplayFragment.REMOTE_IDENTITY, getIntent().getParcelableExtra(IDENTITY_EXTRA));
    extras.putParcelable(VerifyDisplayFragment.LOCAL_IDENTITY, new IdentityKeyParcelable(IdentityKeyUtil.getIdentityKey(this)));
    extras.putString(VerifyDisplayFragment.LOCAL_NUMBER, TextSecurePreferences.getLocalNumber(this));
    extras.putBoolean(VerifyDisplayFragment.VERIFIED_STATE, getIntent().getBooleanExtra(VERIFIED_EXTRA, false));

    initFragment(android.R.id.content, displayFragment, Locale.getDefault(), extras);
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

  public static class VerifyDisplayFragment extends Fragment implements CompoundButton.OnCheckedChangeListener {

    public static final String RECIPIENT_ID    = "recipient_id";
    public static final String REMOTE_NUMBER   = "remote_number";
    public static final String REMOTE_IDENTITY = "remote_identity";
    public static final String LOCAL_IDENTITY  = "local_identity";
    public static final String LOCAL_NUMBER    = "local_number";
    public static final String VERIFIED_STATE  = "verified_state";

    private LiveRecipient recipient;
    private IdentityKey   localIdentity;
    private IdentityKey   remoteIdentity;
    private Fingerprint   fingerprint;

    private View                 container;
    private ImageView            qrCode;
    private ImageView            qrVerified;
    private TextView             description;
    private SwitchCompat         verified;

    private TextView[] codes                = new TextView[12];
    private boolean    animateSuccessOnDraw = false;
    private boolean    animateFailureOnDraw = false;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup viewGroup, Bundle bundle) {
      this.container        = ViewUtil.inflate(inflater, viewGroup, R.layout.verify_display_fragment);
      this.qrCode           = container.findViewById(R.id.qr_code);
      this.verified         = container.findViewById(R.id.verified_switch);
      this.qrVerified       = container.findViewById(R.id.qr_verified);
      this.description      = container.findViewById(R.id.description);
      this.codes[0]         = container.findViewById(R.id.code_first);
      this.codes[1]         = container.findViewById(R.id.code_second);
      this.codes[2]         = container.findViewById(R.id.code_third);
      this.codes[3]         = container.findViewById(R.id.code_fourth);
      this.codes[4]         = container.findViewById(R.id.code_fifth);
      this.codes[5]         = container.findViewById(R.id.code_sixth);
      this.codes[6]         = container.findViewById(R.id.code_seventh);
      this.codes[7]         = container.findViewById(R.id.code_eighth);
      this.codes[8]         = container.findViewById(R.id.code_ninth);
      this.codes[9]         = container.findViewById(R.id.code_tenth);
      this.codes[10]        = container.findViewById(R.id.code_eleventh);
      this.codes[11]        = container.findViewById(R.id.code_twelth);

      this.verified.setChecked(getArguments().getBoolean(VERIFIED_STATE, false));
      this.verified.setOnCheckedChangeListener(this);

      return container;
    }

    @Override
    public void onCreate(Bundle bundle) {
      super.onCreate(bundle);

      RecipientId           recipientId              = getArguments().getParcelable(RECIPIENT_ID);
      IdentityKeyParcelable localIdentityParcelable  = getArguments().getParcelable(LOCAL_IDENTITY);
      IdentityKeyParcelable remoteIdentityParcelable = getArguments().getParcelable(REMOTE_IDENTITY);

      if (recipientId == null)              throw new AssertionError("RecipientId required");
      if (localIdentityParcelable == null)  throw new AssertionError("local identity required");
      if (remoteIdentityParcelable == null) throw new AssertionError("remote identity required");

      this.localIdentity  = localIdentityParcelable.get();
      this.recipient      = Recipient.live(recipientId);
      this.remoteIdentity = remoteIdentityParcelable.get();

      int    version;
      byte[] localId;
      byte[] remoteId;

      //noinspection WrongThread
      Recipient resolved = recipient.resolve();

      if (FeatureFlags.verifyV2() && resolved.getUuid().isPresent()) {
        Log.i(TAG, "Using UUID (version 2).");
        version  = 2;
        localId  = UuidUtil.toByteArray(TextSecurePreferences.getLocalUuid(requireContext()));
        remoteId = UuidUtil.toByteArray(resolved.getUuid().get());
      } else if (!FeatureFlags.verifyV2() && resolved.getE164().isPresent()) {
        Log.i(TAG, "Using E164 (version 1).");
        version  = 1;
        localId  = TextSecurePreferences.getLocalNumber(requireContext()).getBytes();
        remoteId = resolved.requireE164().getBytes();
      } else {
        Log.w(TAG, String.format(Locale.ENGLISH, "Could not show proper verification! verifyV2: %s, hasUuid: %s, hasE164: %s", FeatureFlags.verifyV2(), resolved.getUuid().isPresent(), resolved.getE164().isPresent()));
        new AlertDialog.Builder(requireContext())
                       .setMessage(getString(R.string.VerifyIdentityActivity_you_must_first_exchange_messages_in_order_to_view, resolved.getDisplayName(requireContext())))
                       .setPositiveButton(android.R.string.ok, (dialog, which) -> requireActivity().finish())
                       .setOnDismissListener(dialog -> requireActivity().finish())
                       .show();
        return;
      }

      this.recipient.observe(this, this::setRecipientText);

      new AsyncTask<Void, Void, Fingerprint>() {
        @Override
        protected Fingerprint doInBackground(Void... params) {
          return new NumericFingerprintGenerator(5200).createFor(version,
                                                                 localId, localIdentity,
                                                                 remoteId, remoteIdentity);
        }

        @Override
        protected void onPostExecute(Fingerprint fingerprint) {
          VerifyDisplayFragment.this.fingerprint = fingerprint;
          setFingerprintViews(fingerprint, true);
          getActivity().supportInvalidateOptionsMenu();
        }
      }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public void onResume() {
      super.onResume();

      setRecipientText(recipient.get());

      if (fingerprint != null) {
        setFingerprintViews(fingerprint, false);
      }

      if (animateSuccessOnDraw) {
        animateSuccessOnDraw = false;
        animateVerifiedSuccess();
      } else if (animateFailureOnDraw) {
        animateFailureOnDraw = false;
        animateVerifiedFailure();
      }
    }

    private void setRecipientText(Recipient recipient) {
      description.setText(Html.fromHtml(String.format(getActivity().getString(R.string.verify_display_fragment__if_you_wish_to_verify_the_security_of_your_end_to_end_encryption_with_s), recipient.getDisplayName(getContext()))));
      description.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void setFingerprintViews(Fingerprint fingerprint, boolean animate) {
      String[] segments = getSegments(fingerprint, codes.length);

      for (int i=0;i<codes.length;i++) {
        if (animate) setCodeSegment(codes[i], segments[i]);
        else         codes[i].setText(segments[i]);
      }

      byte[] qrCodeData   = fingerprint.getScannableFingerprint().getSerialized();
      String qrCodeString = new String(qrCodeData, Charset.forName("ISO-8859-1"));
      Bitmap qrCodeBitmap = QrCode.create(qrCodeString);

      qrCode.setImageBitmap(qrCodeBitmap);

      if (animate) {
        ViewUtil.fadeIn(qrCode, 1000);
      } else {
        qrCode.setVisibility(View.VISIBLE);
      }
    }

    private void setCodeSegment(final TextView codeView, String segment) {
      ValueAnimator valueAnimator = new ValueAnimator();
      valueAnimator.setObjectValues(0, Integer.parseInt(segment));

      valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
          int value = (int) animation.getAnimatedValue();
          codeView.setText(String.format(Locale.getDefault(), "%05d", value));
        }
      });

      valueAnimator.setEvaluator(new TypeEvaluator<Integer>() {
        public Integer evaluate(float fraction, Integer startValue, Integer endValue) {
          return Math.round(startValue + (endValue - startValue) * fraction);
        }
      });

      valueAnimator.setDuration(1000);
      valueAnimator.start();
    }

    private String[] getSegments(Fingerprint fingerprint, int segmentCount) {
      String[] segments = new String[segmentCount];
      String   digits   = fingerprint.getDisplayableFingerprint().getDisplayText();
      int      partSize = digits.length() / segmentCount;

      for (int i=0;i<segmentCount;i++) {
        segments[i] = digits.substring(i * partSize, (i * partSize) + partSize);
      }

      return segments;
    }

    private Bitmap createVerifiedBitmap(int width, int height, @DrawableRes int id) {
      Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
      Canvas canvas = new Canvas(bitmap);
      Bitmap check  = BitmapFactory.decodeResource(getResources(), id);
      float  offset = (width - check.getWidth()) / 2;

      canvas.drawBitmap(check, offset, offset, null);

      return bitmap;
    }

    private void animateVerifiedSuccess() {
      Bitmap qrBitmap  = ((BitmapDrawable)qrCode.getDrawable()).getBitmap();
      Bitmap qrSuccess = createVerifiedBitmap(qrBitmap.getWidth(), qrBitmap.getHeight(), R.drawable.ic_check_white_48dp);

      qrVerified.setImageBitmap(qrSuccess);
      qrVerified.getBackground().setColorFilter(getResources().getColor(R.color.green_500), PorterDuff.Mode.MULTIPLY);

      animateVerified();
    }

    private void animateVerifiedFailure() {
      Bitmap qrBitmap  = ((BitmapDrawable)qrCode.getDrawable()).getBitmap();
      Bitmap qrSuccess = createVerifiedBitmap(qrBitmap.getWidth(), qrBitmap.getHeight(), R.drawable.ic_close_white_48dp);

      qrVerified.setImageBitmap(qrSuccess);
      qrVerified.getBackground().setColorFilter(getResources().getColor(R.color.red_500), PorterDuff.Mode.MULTIPLY);

      animateVerified();
    }

    private void animateVerified() {
      ScaleAnimation scaleAnimation = new ScaleAnimation(0, 1, 0, 1,
                                                         ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
                                                         ScaleAnimation.RELATIVE_TO_SELF, 0.5f);
      scaleAnimation.setInterpolator(new OvershootInterpolator());
      scaleAnimation.setDuration(800);
      scaleAnimation.setAnimationListener(new Animation.AnimationListener() {
        @Override
        public void onAnimationStart(Animation animation) {}

        @Override
        public void onAnimationEnd(Animation animation) {
          qrVerified.postDelayed(new Runnable() {
            @Override
            public void run() {
              ScaleAnimation scaleAnimation = new ScaleAnimation(1, 0, 1, 0,
                                                                 ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
                                                                 ScaleAnimation.RELATIVE_TO_SELF, 0.5f);

              scaleAnimation.setInterpolator(new AnticipateInterpolator());
              scaleAnimation.setDuration(500);
              ViewUtil.animateOut(qrVerified, scaleAnimation, View.GONE);
            }
          }, 2000);
        }

        @Override
        public void onAnimationRepeat(Animation animation) {}
      });

      ViewUtil.animateIn(qrVerified, scaleAnimation);
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, final boolean isChecked) {
      final Recipient   recipient   = this.recipient.get();
      final RecipientId recipientId = recipient.getId();

      SignalExecutors.BOUNDED.execute(() -> {
        try (SignalSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
          if (isChecked) {
            Log.i(TAG, "Saving identity: " + recipientId);
            DatabaseFactory.getIdentityDatabase(getActivity())
                    .saveIdentity(recipientId,
                            remoteIdentity,
                            VerifiedStatus.VERIFIED, false,
                            System.currentTimeMillis(), true);
          } else {
            DatabaseFactory.getIdentityDatabase(getActivity())
                    .setVerified(recipientId,
                            remoteIdentity,
                            VerifiedStatus.DEFAULT);
          }

          ApplicationDependencies.getJobManager()
                  .add(new MultiDeviceVerifiedUpdateJob(recipientId,
                          remoteIdentity,
                          isChecked ? VerifiedStatus.VERIFIED
                                  : VerifiedStatus.DEFAULT));
          StorageSyncHelper.scheduleSyncForDataChange();

          IdentityUtil.markIdentityVerified(getActivity(), recipient, isChecked, false);
        }
      });
    }
  }

  public static class VerifyScanFragment extends Fragment {

    private View           container;
    private CameraView     cameraView;
    private ScanningThread scanningThread;
    private ScanListener scanListener;

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup viewGroup, Bundle bundle) {
      this.container  = ViewUtil.inflate(inflater, viewGroup, R.layout.verify_scan_fragment);
      this.cameraView = container.findViewById(R.id.scanner);

      return container;
    }

    @Override
    public void onResume() {
      super.onResume();
      this.scanningThread = new ScanningThread();
      this.scanningThread.setScanListener(scanListener);
      this.scanningThread.setCharacterSet("ISO-8859-1");
      this.cameraView.onResume();
      this.cameraView.setPreviewCallback(scanningThread);
      this.scanningThread.start();
    }

    @Override
    public void onPause() {
      super.onPause();
      this.cameraView.onPause();
      this.scanningThread.stopScanning();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfiguration) {
      super.onConfigurationChanged(newConfiguration);
      this.cameraView.onPause();
      this.cameraView.onResume();
      this.cameraView.setPreviewCallback(scanningThread);
    }

    public void setScanListener(ScanListener listener) {
      if (this.scanningThread != null) scanningThread.setScanListener(listener);
      this.scanListener = listener;
    }

  }

}
