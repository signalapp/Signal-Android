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

import android.Manifest;
import android.animation.TypeEvaluator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
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
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.Animation;
import android.view.animation.AnticipateInterpolator;
import android.view.animation.ScaleAnimation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextSwitcher;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.OneShotPreDrawListener;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.signal.core.util.ThreadUtil;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.components.ShapeScrim;
import org.thoughtcrime.securesms.components.camera.CameraView;
import org.thoughtcrime.securesms.crypto.ReentrantSessionLock;
import org.thoughtcrime.securesms.crypto.IdentityKeyParcelable;
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.IdentityDatabase.VerifiedStatus;
import org.thoughtcrime.securesms.database.model.IdentityRecord;
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
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.IdentityUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.fingerprint.Fingerprint;
import org.whispersystems.libsignal.fingerprint.FingerprintVersionMismatchException;
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
public class VerifyIdentityActivity extends PassphraseRequiredActivity implements ScanListener, View.OnClickListener {

  private static final String TAG = Log.tag(VerifyIdentityActivity.class);

  private static final String RECIPIENT_EXTRA = "recipient_id";
  private static final String IDENTITY_EXTRA  = "recipient_identity";
  private static final String VERIFIED_EXTRA  = "verified_state";

  private final DynamicTheme dynamicTheme = new DynamicNoActionBarTheme();

  private final VerifyDisplayFragment displayFragment = new VerifyDisplayFragment();
  private final VerifyScanFragment    scanFragment    = new VerifyScanFragment();

  public static Intent newIntent(@NonNull Context context,
                                 @NonNull IdentityRecord identityRecord)
  {
    return newIntent(context,
                     identityRecord.getRecipientId(),
                     identityRecord.getIdentityKey(),
                     identityRecord.getVerifiedStatus() == IdentityDatabase.VerifiedStatus.VERIFIED);
  }

  public static Intent newIntent(@NonNull Context context,
                                 @NonNull IdentityRecord identityRecord,
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
    dynamicTheme.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle state, boolean ready) {
    Bundle extras = new Bundle();
    extras.putParcelable(VerifyDisplayFragment.RECIPIENT_ID, getIntent().getParcelableExtra(RECIPIENT_EXTRA));
    extras.putParcelable(VerifyDisplayFragment.REMOTE_IDENTITY, getIntent().getParcelableExtra(IDENTITY_EXTRA));
    extras.putParcelable(VerifyDisplayFragment.LOCAL_IDENTITY, new IdentityKeyParcelable(IdentityKeyUtil.getIdentityKey(this)));
    extras.putString(VerifyDisplayFragment.LOCAL_NUMBER, Recipient.self().requireE164());
    extras.putBoolean(VerifyDisplayFragment.VERIFIED_STATE, getIntent().getBooleanExtra(VERIFIED_EXTRA, false));

    scanFragment.setScanListener(this);
    displayFragment.setClickListener(this);

    initFragment(android.R.id.content, displayFragment, Locale.getDefault(), extras);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case android.R.id.home: finish(); return true;
    }

    return false;
  }

  @Override
  public void onQrDataFound(final String data) {
    ThreadUtil.runOnMain(() -> {
      ((Vibrator)getSystemService(Context.VIBRATOR_SERVICE)).vibrate(50);

      getSupportFragmentManager().popBackStack();
      displayFragment.setScannedFingerprint(data);
    });
  }

  @Override
  public void onClick(View v) {
    Permissions.with(this)
               .request(Manifest.permission.CAMERA)
               .ifNecessary()
               .withPermanentDenialDialog(getString(R.string.VerifyIdentityActivity_signal_needs_the_camera_permission_in_order_to_scan_a_qr_code_but_it_has_been_permanently_denied))
               .onAllGranted(() -> {
                 FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                 transaction.setCustomAnimations(R.anim.slide_from_top, R.anim.slide_to_bottom,
                                                 R.anim.slide_from_bottom, R.anim.slide_to_top);

                 transaction.replace(android.R.id.content, scanFragment)
                            .addToBackStack(null)
                            .commitAllowingStateLoss();
               })
               .onAnyDenied(() -> Toast.makeText(this, R.string.VerifyIdentityActivity_unable_to_scan_qr_code_without_camera_permission, Toast.LENGTH_LONG).show())
               .execute();
  }

  @SuppressLint("MissingSuperCall")
  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

  public static class VerifyDisplayFragment extends Fragment implements ViewTreeObserver.OnScrollChangedListener {

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

    private Toolbar              toolbar;
    private ScrollView           scrollView;
    private View                 container;
    private View                 numbersContainer;
    private View                 loading;
    private View                 qrCodeContainer;
    private ImageView            qrCode;
    private ImageView            qrVerified;
    private TextSwitcher         tapLabel;
    private TextView             description;
    private View.OnClickListener clickListener;
    private Button               verifyButton;
    private View                 toolbarShadow;
    private View                 bottomShadow;

    private TextView[] codes                = new TextView[12];
    private boolean    animateSuccessOnDraw = false;
    private boolean    animateFailureOnDraw = false;
    private boolean    currentVerifiedState = false;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup viewGroup, Bundle bundle) {
      this.container        = ViewUtil.inflate(inflater, viewGroup, R.layout.verify_display_fragment);
      this.toolbar          = container.findViewById(R.id.toolbar);
      this.scrollView       = container.findViewById(R.id.scroll_view);
      this.numbersContainer = container.findViewById(R.id.number_table);
      this.loading          = container.findViewById(R.id.loading);
      this.qrCodeContainer  = container.findViewById(R.id.qr_code_container);
      this.qrCode           = container.findViewById(R.id.qr_code);
      this.verifyButton     = container.findViewById(R.id.verify_button);
      this.qrVerified       = container.findViewById(R.id.qr_verified);
      this.description      = container.findViewById(R.id.description);
      this.tapLabel         = container.findViewById(R.id.tap_label);
      this.toolbarShadow    = container.findViewById(R.id.toolbar_shadow);
      this.bottomShadow     = container.findViewById(R.id.verify_identity_bottom_shadow);
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

      this.qrCodeContainer.setOnClickListener(clickListener);
      this.registerForContextMenu(numbersContainer);

      updateVerifyButton(getArguments().getBoolean(VERIFIED_STATE, false), false);
      this.verifyButton.setOnClickListener((button -> updateVerifyButton(!currentVerifiedState, true)));

      this.scrollView.getViewTreeObserver().addOnScrollChangedListener(this);

      ((AppCompatActivity)requireActivity()).setSupportActionBar(toolbar);
      ((AppCompatActivity)requireActivity()).setTitle(R.string.AndroidManifest__verify_safety_number);

      return container;
    }

    @Override public void onDestroyView() {
      this.scrollView.getViewTreeObserver().removeOnScrollChangedListener(this);
      super.onDestroyView();
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

      if (FeatureFlags.verifyV2() && resolved.getAci().isPresent()) {
        Log.i(TAG, "Using UUID (version 2).");
        version  = 2;
        localId  = Recipient.self().requireAci().toByteArray();
        remoteId = resolved.requireAci().toByteArray();
      } else if (!FeatureFlags.verifyV2() && resolved.getE164().isPresent()) {
        Log.i(TAG, "Using E164 (version 1).");
        version  = 1;
        localId  = Recipient.self().requireE164().getBytes();
        remoteId = resolved.requireE164().getBytes();
      } else {
        Log.w(TAG, String.format(Locale.ENGLISH, "Could not show proper verification! verifyV2: %s, hasUuid: %s, hasE164: %s", FeatureFlags.verifyV2(), resolved.getAci().isPresent(), resolved.getE164().isPresent()));
        new MaterialAlertDialogBuilder(requireContext())
            .setMessage(getString(R.string.VerifyIdentityActivity_you_must_first_exchange_messages_in_order_to_view, resolved.getDisplayName(requireContext())))
            .setPositiveButton(android.R.string.ok, (dialog, which) -> requireActivity().finish())
            .setOnDismissListener(dialog -> {
              requireActivity().finish();
              dialog.dismiss();
            })
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
          if (getActivity() == null) return;
          VerifyDisplayFragment.this.fingerprint = fingerprint;
          setFingerprintViews(fingerprint, true);
          getActivity().supportInvalidateOptionsMenu();
        }
      }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

      setHasOptionsMenu(true);
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

      ThreadUtil.postToMain(this::onScrollChanged);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view,
                                    ContextMenuInfo menuInfo)
    {
      super.onCreateContextMenu(menu, view, menuInfo);

      if (fingerprint != null) {
        MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(R.menu.verify_display_fragment_context_menu, menu);
      }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
      if (fingerprint == null) return super.onContextItemSelected(item);

      switch (item.getItemId()) {
        case R.id.menu_copy:    handleCopyToClipboard(fingerprint, codes.length); return true;
        case R.id.menu_compare: handleCompareWithClipboard(fingerprint);          return true;
        default:                return super.onContextItemSelected(item);
      }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
      super.onCreateOptionsMenu(menu, inflater);

      if (fingerprint != null) {
        inflater.inflate(R.menu.verify_identity, menu);
      }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
      switch (item.getItemId()) {
        case R.id.verify_identity__share: handleShare(fingerprint, codes.length);  return true;
      }

      return false;
    }

    public void setScannedFingerprint(String scanned) {
      try {
        if (fingerprint.getScannableFingerprint().compareTo(scanned.getBytes("ISO-8859-1"))) {
          this.animateSuccessOnDraw = true;
        } else {
          this.animateFailureOnDraw = true;
        }
      } catch (FingerprintVersionMismatchException e) {
        Log.w(TAG, e);
        if (e.getOurVersion() < e.getTheirVersion()) {
          Toast.makeText(getActivity(), R.string.VerifyIdentityActivity_your_contact_is_running_a_newer_version_of_Signal, Toast.LENGTH_LONG).show();
        } else {
          Toast.makeText(getActivity(), R.string.VerifyIdentityActivity_your_contact_is_running_an_old_version_of_signal, Toast.LENGTH_LONG).show();
        }
        this.animateFailureOnDraw = true;
      } catch (Exception e) {
        Log.w(TAG, e);
        Toast.makeText(getActivity(), R.string.VerifyIdentityActivity_the_scanned_qr_code_is_not_a_correctly_formatted_safety_number, Toast.LENGTH_LONG).show();
        this.animateFailureOnDraw = true;
      }
    }

    public void setClickListener(View.OnClickListener listener) {
      this.clickListener = listener;
    }

    private @NonNull String getFormattedSafetyNumbers(@NonNull Fingerprint fingerprint, int segmentCount) {
      String[]      segments = getSegments(fingerprint, segmentCount);
      StringBuilder result   = new StringBuilder();

      for (int i = 0; i < segments.length; i++) {
        result.append(segments[i]);

        if (i != segments.length - 1) {
          if (((i+1) % 4) == 0) result.append('\n');
          else                  result.append(' ');
        }
      }

      return result.toString();
    }

    private void handleCopyToClipboard(Fingerprint fingerprint, int segmentCount) {
      Util.writeTextToClipboard(getActivity(), getFormattedSafetyNumbers(fingerprint, segmentCount));
    }

    private void handleCompareWithClipboard(Fingerprint fingerprint) {
      String clipboardData = Util.readTextFromClipboard(getActivity());

      if (clipboardData == null) {
        Toast.makeText(getActivity(), R.string.VerifyIdentityActivity_no_safety_number_to_compare_was_found_in_the_clipboard, Toast.LENGTH_LONG).show();
        return;
      }

      String numericClipboardData = clipboardData.replaceAll("\\D", "");

      if (TextUtils.isEmpty(numericClipboardData) || numericClipboardData.length() != 60) {
        Toast.makeText(getActivity(), R.string.VerifyIdentityActivity_no_safety_number_to_compare_was_found_in_the_clipboard, Toast.LENGTH_LONG).show();
        return;
      }

      if (fingerprint.getDisplayableFingerprint().getDisplayText().equals(numericClipboardData)) {
        animateVerifiedSuccess();
      } else {
        animateVerifiedFailure();
      }
    }

    private void handleShare(@NonNull Fingerprint fingerprint, int segmentCount) {
      String shareString =
          getString(R.string.VerifyIdentityActivity_our_signal_safety_number) + "\n" +
              getFormattedSafetyNumbers(fingerprint, segmentCount) + "\n";

      Intent intent = new Intent();
      intent.setAction(Intent.ACTION_SEND);
      intent.putExtra(Intent.EXTRA_TEXT, shareString);
      intent.setType("text/plain");

      try {
        startActivity(Intent.createChooser(intent, getString(R.string.VerifyIdentityActivity_share_safety_number_via)));
      } catch (ActivityNotFoundException e) {
        Toast.makeText(getActivity(), R.string.VerifyIdentityActivity_no_app_to_share_to, Toast.LENGTH_LONG).show();
      }
    }

    private void setRecipientText(Recipient recipient) {
      description.setText(Html.fromHtml(String.format(getActivity().getString(R.string.verify_display_fragment__to_verify_the_security_of_your_end_to_end_encryption_with_s), recipient.getDisplayName(getContext()))));
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
        ViewUtil.fadeIn(tapLabel, 1000);
        ViewUtil.fadeOut(loading, 300, View.GONE);
      } else {
        qrCode.setVisibility(View.VISIBLE);
        tapLabel.setVisibility(View.VISIBLE);
        loading.setVisibility(View.GONE);
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

      tapLabel.setText(getString(R.string.verify_display_fragment__successful_match));

      animateVerified();
    }

    private void animateVerifiedFailure() {
      Bitmap qrBitmap  = ((BitmapDrawable)qrCode.getDrawable()).getBitmap();
      Bitmap qrSuccess = createVerifiedBitmap(qrBitmap.getWidth(), qrBitmap.getHeight(), R.drawable.ic_close_white_48dp);

      qrVerified.setImageBitmap(qrSuccess);
      qrVerified.getBackground().setColorFilter(getResources().getColor(R.color.red_500), PorterDuff.Mode.MULTIPLY);

      tapLabel.setText(getString(R.string.verify_display_fragment__failed_to_verify_safety_number));

      animateVerified();
    }

    private void animateVerified() {
      ScaleAnimation scaleAnimation = new ScaleAnimation(0, 1, 0, 1,
                                                         ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
                                                         ScaleAnimation.RELATIVE_TO_SELF, 0.5f);
      scaleAnimation.setInterpolator(new FastOutSlowInInterpolator());
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
              ViewUtil.fadeIn(qrCode, 800);
              qrCodeContainer.setEnabled(true);
              tapLabel.setText(getString(R.string.verify_display_fragment__tap_to_scan));
            }
          }, 2000);
        }

        @Override
        public void onAnimationRepeat(Animation animation) {}
      });

      ViewUtil.fadeOut(qrCode, 200, View.INVISIBLE);
      ViewUtil.animateIn(qrVerified, scaleAnimation);
      qrCodeContainer.setEnabled(false);
    }

    private void updateVerifyButton(boolean verified, boolean update) {
      currentVerifiedState = verified;

      if (verified) {
        verifyButton.setText(R.string.verify_display_fragment__clear_verification);
      } else {
        verifyButton.setText(R.string.verify_display_fragment__mark_as_verified);
      }

      if (update) {
        final RecipientId recipientId = recipient.getId();

        SignalExecutors.BOUNDED.execute(() -> {
          try (SignalSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
            if (verified) {
              Log.i(TAG, "Saving identity: " + recipientId);
              ApplicationDependencies.getIdentityStore()
                                     .saveIdentityWithoutSideEffects(recipientId,
                                                                     remoteIdentity,
                                                                     VerifiedStatus.VERIFIED,
                                                                     false,
                                                                     System.currentTimeMillis(),
                                                                     true);
            } else {
              ApplicationDependencies.getIdentityStore().setVerified(recipientId, remoteIdentity, VerifiedStatus.DEFAULT);
            }

            ApplicationDependencies.getJobManager()
                                   .add(new MultiDeviceVerifiedUpdateJob(recipientId,
                                                                         remoteIdentity,
                                                                         verified ? VerifiedStatus.VERIFIED
                                                                                   : VerifiedStatus.DEFAULT));
            StorageSyncHelper.scheduleSyncForDataChange();

            IdentityUtil.markIdentityVerified(getActivity(), recipient.get(), verified, false);
          }
        });
      }
    }


    @Override public void onScrollChanged() {
      if (scrollView.canScrollVertically(-1)) {
        if (toolbarShadow.getVisibility() != View.VISIBLE) {
          ViewUtil.fadeIn(toolbarShadow, 250);
        }
      } else {
        if (toolbarShadow.getVisibility() != View.GONE) {
          ViewUtil.fadeOut(toolbarShadow, 250);
        }
      }

      if (scrollView.canScrollVertically(1)) {
        if (bottomShadow.getVisibility() != View.VISIBLE) {
          ViewUtil.fadeIn(bottomShadow, 250);
        }
      } else {
        ViewUtil.fadeOut(bottomShadow, 250);
      }
    }
  }

  public static class VerifyScanFragment extends Fragment {

    private View           container;
    private CameraView     cameraView;
    private ShapeScrim     cameraScrim;
    private ImageView      cameraMarks;
    private ScanningThread scanningThread;
    private ScanListener   scanListener;

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup viewGroup, Bundle bundle) {
      this.container   = ViewUtil.inflate(inflater, viewGroup, R.layout.verify_scan_fragment);
      this.cameraView  = container.findViewById(R.id.scanner);
      this.cameraScrim = container.findViewById(R.id.camera_scrim);
      this.cameraMarks = container.findViewById(R.id.camera_marks);

      OneShotPreDrawListener.add(cameraScrim, () -> {
        int width  = cameraScrim.getScrimWidth();
        int height = cameraScrim.getScrimHeight();

        ViewUtil.updateLayoutParams(cameraMarks, width, height);
      });

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
