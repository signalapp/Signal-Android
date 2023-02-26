package org.thoughtcrime.securesms.verify;

import android.animation.TypeEvaluator;
import android.animation.ValueAnimator;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.text.Html;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.view.ContextMenu;
import android.view.LayoutInflater;
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
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.signal.core.util.ThreadUtil;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.fingerprint.Fingerprint;
import org.signal.libsignal.protocol.fingerprint.FingerprintVersionMismatchException;
import org.signal.libsignal.protocol.fingerprint.NumericFingerprintGenerator;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.crypto.IdentityKeyParcelable;
import org.thoughtcrime.securesms.crypto.ReentrantSessionLock;
import org.thoughtcrime.securesms.database.IdentityTable;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.MultiDeviceVerifiedUpdateJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.qr.QrCode;
import org.thoughtcrime.securesms.recipients.LiveRecipient;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.storage.StorageSyncHelper;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.IdentityUtil;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.signal.core.util.concurrent.SimpleTask;
import org.whispersystems.signalservice.api.SignalSessionLock;

import java.nio.charset.Charset;
import java.util.Locale;

/**
 * Fragment to display a user's identity key.
 */
public class VerifyDisplayFragment extends Fragment implements ViewTreeObserver.OnScrollChangedListener {

  private static final String TAG = Log.tag(VerifyDisplayFragment.class);

  private static final String RECIPIENT_ID    = "recipient_id";
  private static final String REMOTE_IDENTITY = "remote_identity";
  private static final String LOCAL_IDENTITY  = "local_identity";
  private static final String LOCAL_NUMBER    = "local_number";
  private static final String VERIFIED_STATE  = "verified_state";

  private LiveRecipient recipient;
  private IdentityKey   localIdentity;
  private IdentityKey   remoteIdentity;
  private Fingerprint   fingerprint;

  private Toolbar      toolbar;
  private ScrollView   scrollView;
  private View         numbersContainer;
  private View         loading;
  private View         qrCodeContainer;
  private ImageView    qrCode;
  private ImageView    qrVerified;
  private TextSwitcher tapLabel;
  private TextView     description;
  private Callback     callback;
  private Button       verifyButton;
  private View         toolbarShadow;
  private View         bottomShadow;

  private TextView[] codes                = new TextView[12];
  private boolean    animateSuccessOnDraw = false;
  private boolean    animateFailureOnDraw = false;
  private boolean    currentVerifiedState = false;

  static VerifyDisplayFragment create(@NonNull RecipientId recipientId,
                                      @NonNull IdentityKeyParcelable remoteIdentity,
                                      @NonNull IdentityKeyParcelable localIdentity,
                                      @NonNull String localNumber,
                                      boolean verifiedState)
  {
    Bundle extras = new Bundle();
    extras.putParcelable(RECIPIENT_ID, recipientId);
    extras.putParcelable(REMOTE_IDENTITY, remoteIdentity);
    extras.putParcelable(LOCAL_IDENTITY, localIdentity);
    extras.putString(LOCAL_NUMBER, localNumber);
    extras.putBoolean(VERIFIED_STATE, verifiedState);

    VerifyDisplayFragment fragment = new VerifyDisplayFragment();
    fragment.setArguments(extras);

    return fragment;
  }

  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);

    if (context instanceof Callback) {
      callback = (Callback) context;
    } else if (getParentFragment() instanceof Callback) {
      callback = (Callback) getParentFragment();
    } else {
      throw new ClassCastException("Cannot find ScanListener in parent component");
    }
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup viewGroup, Bundle bundle) {
    return ViewUtil.inflate(inflater, viewGroup, R.layout.verify_display_fragment);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    this.toolbar          = view.findViewById(R.id.toolbar);
    this.scrollView       = view.findViewById(R.id.scroll_view);
    this.numbersContainer = view.findViewById(R.id.number_table);
    this.loading          = view.findViewById(R.id.loading);
    this.qrCodeContainer  = view.findViewById(R.id.qr_code_container);
    this.qrCode           = view.findViewById(R.id.qr_code);
    this.verifyButton     = view.findViewById(R.id.verify_button);
    this.qrVerified       = view.findViewById(R.id.qr_verified);
    this.description      = view.findViewById(R.id.description);
    this.tapLabel         = view.findViewById(R.id.tap_label);
    this.toolbarShadow    = view.findViewById(R.id.toolbar_shadow);
    this.bottomShadow     = view.findViewById(R.id.verify_identity_bottom_shadow);
    this.codes[0]         = view.findViewById(R.id.code_first);
    this.codes[1]         = view.findViewById(R.id.code_second);
    this.codes[2]         = view.findViewById(R.id.code_third);
    this.codes[3]         = view.findViewById(R.id.code_fourth);
    this.codes[4]         = view.findViewById(R.id.code_fifth);
    this.codes[5]         = view.findViewById(R.id.code_sixth);
    this.codes[6]         = view.findViewById(R.id.code_seventh);
    this.codes[7]         = view.findViewById(R.id.code_eighth);
    this.codes[8]         = view.findViewById(R.id.code_ninth);
    this.codes[9]         = view.findViewById(R.id.code_tenth);
    this.codes[10]        = view.findViewById(R.id.code_eleventh);
    this.codes[11]        = view.findViewById(R.id.code_twelth);

    this.qrCodeContainer.setOnClickListener(v -> callback.onQrCodeContainerClicked());
    this.registerForContextMenu(numbersContainer);

    updateVerifyButton(getArguments().getBoolean(VERIFIED_STATE, false), false);
    this.verifyButton.setOnClickListener((button -> updateVerifyButton(!currentVerifiedState, true)));

    this.scrollView.getViewTreeObserver().addOnScrollChangedListener(this);

    toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());
    toolbar.setOnMenuItemClickListener(this::onToolbarOptionsItemSelected);
    toolbar.setTitle(R.string.AndroidManifest__verify_safety_number);
  }

  @Override
  public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
    super.onViewStateRestored(savedInstanceState);
    initializeFingerprint();
  }

  @Override
  public void onDestroyView() {
    this.scrollView.getViewTreeObserver().removeOnScrollChangedListener(this);
    super.onDestroyView();
  }

  private void initializeFingerprint() {
    RecipientId           recipientId              = getArguments().getParcelable(RECIPIENT_ID);
    IdentityKeyParcelable localIdentityParcelable  = getArguments().getParcelable(LOCAL_IDENTITY);
    IdentityKeyParcelable remoteIdentityParcelable = getArguments().getParcelable(REMOTE_IDENTITY);

    this.localIdentity  = localIdentityParcelable.get();
    this.recipient      = Recipient.live(recipientId);
    this.remoteIdentity = remoteIdentityParcelable.get();

    int    version;
    byte[] localId;
    byte[] remoteId;

    //noinspection WrongThread
    Recipient resolved = recipient.resolve();

    if (FeatureFlags.verifyV2() && resolved.getServiceId().isPresent()) {
      Log.i(TAG, "Using UUID (version 2).");
      version  = 2;
      localId  = SignalStore.account().requireAci().toByteArray();
      remoteId = resolved.requireServiceId().toByteArray();
    } else if (!FeatureFlags.verifyV2() && resolved.getE164().isPresent()) {
      Log.i(TAG, "Using E164 (version 1).");
      version  = 1;
      localId  = Recipient.self().requireE164().getBytes();
      remoteId = resolved.requireE164().getBytes();
    } else {
      Log.w(TAG, String.format(Locale.ENGLISH, "Could not show proper verification! verifyV2: %s, hasUuid: %s, hasE164: %s", FeatureFlags.verifyV2(), resolved.getServiceId().isPresent(), resolved.getE164().isPresent()));
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

    SimpleTask.run(() -> new NumericFingerprintGenerator(5200).createFor(version,
                                                                         localId, localIdentity,
                                                                         remoteId, remoteIdentity),
                   fingerprint -> {
                     if (getActivity() == null) return;
                     VerifyDisplayFragment.this.fingerprint = fingerprint;
                     setFingerprintViews(fingerprint, true);
                     initializeOptionsMenu();
                   });
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
                                  ContextMenu.ContextMenuInfo menuInfo)
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

    if (item.getItemId() ==  R.id.menu_copy) {
      handleCopyToClipboard(fingerprint, codes.length);
      return true;
    } else if (item.getItemId() == R.id.menu_compare) {
      handleCompareWithClipboard(fingerprint);
      return true;
    } else {
      return super.onContextItemSelected(item);
    }
  }

  private void initializeOptionsMenu() {
    if (fingerprint != null) {
      requireActivity().getMenuInflater().inflate(R.menu.verify_identity, toolbar.getMenu());
    }
  }

  public boolean onToolbarOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == R.id.verify_identity__share) {
      handleShare(fingerprint, codes.length);
      return true;
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

  private @NonNull String getFormattedSafetyNumbers(@NonNull Fingerprint fingerprint, int segmentCount) {
    String[]      segments = getSegments(fingerprint, segmentCount);
    StringBuilder result   = new StringBuilder();

    for (int i = 0; i < segments.length; i++) {
      result.append(segments[i]);

      if (i != segments.length - 1) {
        if (((i + 1) % 4) == 0) result.append('\n');
        else result.append(' ');
      }
    }

    return result.toString();
  }

  private void handleCopyToClipboard(Fingerprint fingerprint, int segmentCount) {
    Util.writeTextToClipboard(requireContext(), "Safety numbers", getFormattedSafetyNumbers(fingerprint, segmentCount));
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
    String escapedDisplayName = Html.escapeHtml(recipient.getDisplayName(getContext()));

    description.setText(Html.fromHtml(String.format(getActivity().getString(R.string.verify_display_fragment__to_verify_the_security_of_your_end_to_end_encryption_with_s), escapedDisplayName)));
    description.setMovementMethod(LinkMovementMethod.getInstance());
  }

  private void setFingerprintViews(Fingerprint fingerprint, boolean animate) {
    String[] segments = getSegments(fingerprint, codes.length);

    for (int i = 0; i < codes.length; i++) {
      if (animate) setCodeSegment(codes[i], segments[i]);
      else codes[i].setText(segments[i]);
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

    valueAnimator.addUpdateListener(animation -> {
      int value = (int) animation.getAnimatedValue();
      codeView.setText(String.format(Locale.getDefault(), "%05d", value));
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

    for (int i = 0; i < segmentCount; i++) {
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
    Bitmap qrBitmap  = ((BitmapDrawable) qrCode.getDrawable()).getBitmap();
    Bitmap qrSuccess = createVerifiedBitmap(qrBitmap.getWidth(), qrBitmap.getHeight(), R.drawable.ic_check_white_48dp);

    qrVerified.setImageBitmap(qrSuccess);
    qrVerified.getBackground().setColorFilter(getResources().getColor(R.color.green_500), PorterDuff.Mode.MULTIPLY);

    tapLabel.setText(getString(R.string.verify_display_fragment__successful_match));

    animateVerified();
  }

  private void animateVerifiedFailure() {
    Bitmap qrBitmap  = ((BitmapDrawable) qrCode.getDrawable()).getBitmap();
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
        qrVerified.postDelayed(() -> {
          ScaleAnimation scaleAnimation1 = new ScaleAnimation(1, 0, 1, 0,
                                                              ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
                                                              ScaleAnimation.RELATIVE_TO_SELF, 0.5f);

          scaleAnimation1.setInterpolator(new AnticipateInterpolator());
          scaleAnimation1.setDuration(500);
          ViewUtil.animateOut(qrVerified, scaleAnimation1, View.GONE);
          ViewUtil.fadeIn(qrCode, 800);
          qrCodeContainer.setEnabled(true);
          tapLabel.setText(getString(R.string.verify_display_fragment__tap_to_scan));
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
      final Context     context     = requireContext().getApplicationContext();

      SignalExecutors.BOUNDED.execute(() -> {
        try (SignalSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
          if (verified) {
            Log.i(TAG, "Saving identity: " + recipientId);
            ApplicationDependencies.getProtocolStore().aci().identities()
                                   .saveIdentityWithoutSideEffects(recipientId,
                                                                   remoteIdentity,
                                                                   IdentityTable.VerifiedStatus.VERIFIED,
                                                                   false,
                                                                   System.currentTimeMillis(),
                                                                   true);
          } else {
            ApplicationDependencies.getProtocolStore().aci().identities().setVerified(recipientId, remoteIdentity, IdentityTable.VerifiedStatus.DEFAULT);
          }

          ApplicationDependencies.getJobManager()
                                 .add(new MultiDeviceVerifiedUpdateJob(recipientId,
                                                                       remoteIdentity,
                                                                       verified ? IdentityTable.VerifiedStatus.VERIFIED
                                                                                : IdentityTable.VerifiedStatus.DEFAULT));
          StorageSyncHelper.scheduleSyncForDataChange();

          IdentityUtil.markIdentityVerified(context, recipient.get(), verified, false);
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

  interface Callback {
    void onQrCodeContainerClicked();
  }
}
