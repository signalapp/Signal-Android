/**
 * Copyright (C) 2016 Open Whisper Systems
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

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.text.Html;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnticipateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.ScaleAnimation;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.thoughtcrime.securesms.color.MaterialColor;
import org.thoughtcrime.securesms.components.camera.CameraView;
import org.thoughtcrime.securesms.crypto.IdentityKeyParcelable;
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.qr.QrCode;
import org.thoughtcrime.securesms.qr.ScanListener;
import org.thoughtcrime.securesms.qr.ScanningThread;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.fingerprint.Fingerprint;
import org.whispersystems.libsignal.fingerprint.FingerprintParsingException;
import org.whispersystems.libsignal.fingerprint.FingerprintVersionMismatchException;
import org.whispersystems.libsignal.fingerprint.NumericFingerprintGenerator;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

/**
 * Activity for verifying identity keys.
 *
 * @author Moxie Marlinspike
 */
public class VerifyIdentityActivity extends PassphraseRequiredActionBarActivity implements Recipient.RecipientModifiedListener, ScanListener, View.OnClickListener {

  private static final String TAG = VerifyIdentityActivity.class.getSimpleName();

  public static final String RECIPIENT_ID       = "recipient_id";
  public static final String RECIPIENT_IDENTITY = "recipient_identity";

  private final DynamicTheme    dynamicTheme    = new DynamicTheme();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private VerifyDisplayFragment displayFragment = new VerifyDisplayFragment();
  private VerifyScanFragment    scanFragment    = new VerifyScanFragment();

  @Override
  public void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle state, @NonNull MasterSecret masterSecret) {
    try {
      getSupportActionBar().setDisplayHomeAsUpEnabled(true);
      getSupportActionBar().setTitle(R.string.AndroidManifest__verify_safety_number);

      Recipient recipient = RecipientFactory.getRecipientForId(this, getIntent().getLongExtra(RECIPIENT_ID, -1), true);
      recipient.addListener(this);

      setActionBarNotificationBarColor(recipient.getColor());

      Bundle extras = new Bundle();
      extras.putParcelable(VerifyDisplayFragment.REMOTE_IDENTITY, getIntent().getParcelableExtra(RECIPIENT_IDENTITY));
      extras.putString(VerifyDisplayFragment.REMOTE_NUMBER, Util.canonicalizeNumber(this, recipient.getNumber()));
      extras.putParcelable(VerifyDisplayFragment.LOCAL_IDENTITY, new IdentityKeyParcelable(IdentityKeyUtil.getIdentityKey(this)));
      extras.putString(VerifyDisplayFragment.LOCAL_NUMBER, TextSecurePreferences.getLocalNumber(this));

      scanFragment.setScanListener(this);
      displayFragment.setClickListener(this);

      initFragment(android.R.id.content, displayFragment, masterSecret, dynamicLanguage.getCurrentLocale(), extras);
    } catch (InvalidNumberException e) {
      Log.w(TAG, e);
      finish();
    }
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    super.onPrepareOptionsMenu(menu);

    menu.clear();
    MenuInflater inflater = this.getMenuInflater();
    inflater.inflate(R.menu.verify_identity, menu);

    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.verify_identity__share: handleShare();  return true;
      case android.R.id.home:           finish();       return true;
    }

    return false;
  }

  @Override
  public void onModified(final Recipient recipient) {
    Util.runOnMain(new Runnable() {
      @Override
      public void run() {
        setActionBarNotificationBarColor(recipient.getColor());
      }
    });
  }

  @Override
  public void onQrDataFound(final String data) {
    Util.runOnMain(new Runnable() {
      @Override
      public void run() {
        ((Vibrator)getSystemService(Context.VIBRATOR_SERVICE)).vibrate(50);

        getSupportFragmentManager().popBackStack();
        displayFragment.setScannedFingerprint(data);
      }
    });
  }

  @Override
  public void onClick(View v) {
    FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
    transaction.setCustomAnimations(R.anim.slide_from_top, R.anim.slide_to_bottom,
                                    R.anim.slide_from_bottom, R.anim.slide_to_top);

    transaction.replace(android.R.id.content, scanFragment)
               .addToBackStack(null)
               .commit();
  }

  private void setActionBarNotificationBarColor(MaterialColor color) {
    getSupportActionBar().setBackgroundDrawable(new ColorDrawable(color.toActionBarColor(this)));

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      getWindow().setStatusBarColor(color.toStatusBarColor(this));
    }
  }

  private void handleShare() {
    String shareString =
        getString(R.string.VerifyIdentityActivity_our_signal_safety_number) + "\n" +
        displayFragment.getFormattedSafetyNumbers() + "\n";

    Intent intent = new Intent();
    intent.setAction(Intent.ACTION_SEND);
    intent.putExtra(Intent.EXTRA_TEXT, shareString);
    intent.setType("text/plain");

    try {
      startActivity(Intent.createChooser(intent, getString(R.string.VerifyIdentityActivity_share_safety_number_via)));
    } catch (ActivityNotFoundException e) {
      Toast.makeText(VerifyIdentityActivity.this, R.string.VerifyIdentityActivity_no_app_to_share_to, Toast.LENGTH_LONG).show();
    }
  }


  public static class VerifyDisplayFragment extends Fragment implements Recipients.RecipientsModifiedListener {

    public static final String REMOTE_NUMBER   = "remote_number";
    public static final String REMOTE_IDENTITY = "remote_identity";
    public static final String LOCAL_IDENTITY  = "local_identity";
    public static final String LOCAL_NUMBER    = "local_number";

    private Recipients recipient;
    private String     localNumber;
    private String     remoteNumber;

    private IdentityKey localIdentity;
    private IdentityKey remoteIdentity;

    private Fingerprint fingerprint;

    private View                 container;
    private View                 numbersContainer;
    private ImageView            qrCode;
    private ImageView            qrVerified;
    private TextView             description;
    private View.OnClickListener clickListener;

    private TextView[] codes                = new TextView[12];
    private boolean    animateSuccessOnDraw = false;
    private boolean    animateFailureOnDraw = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup viewGroup, Bundle bundle) {
      this.container        = ViewUtil.inflate(inflater, viewGroup, R.layout.verify_display_fragment);
      this.numbersContainer = ViewUtil.findById(container, R.id.number_table);
      this.qrCode           = ViewUtil.findById(container, R.id.qr_code);
      this.qrVerified       = ViewUtil.findById(container, R.id.qr_verified);
      this.description      = ViewUtil.findById(container, R.id.description);
      this.codes[0]         = ViewUtil.findById(container, R.id.code_first);
      this.codes[1]         = ViewUtil.findById(container, R.id.code_second);
      this.codes[2]         = ViewUtil.findById(container, R.id.code_third);
      this.codes[3]         = ViewUtil.findById(container, R.id.code_fourth);
      this.codes[4]         = ViewUtil.findById(container, R.id.code_fifth);
      this.codes[5]         = ViewUtil.findById(container, R.id.code_sixth);
      this.codes[6]         = ViewUtil.findById(container, R.id.code_seventh);
      this.codes[7]         = ViewUtil.findById(container, R.id.code_eighth);
      this.codes[8]         = ViewUtil.findById(container, R.id.code_ninth);
      this.codes[9]         = ViewUtil.findById(container, R.id.code_tenth);
      this.codes[10]        = ViewUtil.findById(container, R.id.code_eleventh);
      this.codes[11]        = ViewUtil.findById(container, R.id.code_twelth);

      this.qrCode.setOnClickListener(clickListener);
      this.registerForContextMenu(numbersContainer);

      return container;
    }

    @Override
    public void onCreate(Bundle bundle) {
      super.onCreate(bundle);

      this.localNumber    = getArguments().getString(LOCAL_NUMBER);
      this.localIdentity  = ((IdentityKeyParcelable)getArguments().getParcelable(LOCAL_IDENTITY)).get();
      this.remoteNumber   = getArguments().getString(REMOTE_NUMBER);
      this.recipient      = RecipientFactory.getRecipientsFromString(getActivity(), this.remoteNumber, true);
      this.remoteIdentity = ((IdentityKeyParcelable)getArguments().getParcelable(REMOTE_IDENTITY)).get();
      this.fingerprint    = new NumericFingerprintGenerator(5200).createFor(localNumber, localIdentity,
                                                                            remoteNumber, remoteIdentity);

      this.recipient.addListener(this);
    }

    @Override
    public void onModified(Recipients recipients) {
      Util.runOnMain(new Runnable() {
        @Override
        public void run() {
          setFingerprintViews(fingerprint);
        }
      });
    }

    @Override
    public void onResume() {
      super.onResume();

      setFingerprintViews(fingerprint);

      if (animateSuccessOnDraw) {
        animateSuccessOnDraw = false;
        animateVerifiedSuccess();
      } else if (animateFailureOnDraw) {
        animateFailureOnDraw = false;
        animateVerifiedFailure();;
      }
    }

    @Override
    public void onDestroy() {
      super.onDestroy();
      recipient.removeListener(this);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view,
                                    ContextMenuInfo menuInfo)
    {
      super.onCreateContextMenu(menu, view, menuInfo);

      MenuInflater inflater = getActivity().getMenuInflater();
      inflater.inflate(R.menu.verify_display_fragment_context_menu, menu);

    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
      switch (item.getItemId()) {
        case R.id.menu_copy:    handleCopyToClipboard();      return true;
        case R.id.menu_compare: handleCompareWithClipboard(); return true;
        default:                return super.onContextItemSelected(item);
      }
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
      } catch (FingerprintParsingException e) {
        Log.w(TAG, e);
        Toast.makeText(getActivity(), R.string.VerifyIdentityActivity_the_scanned_qr_code_is_not_a_correctly_formatted_safety_number, Toast.LENGTH_LONG).show();
      } catch (UnsupportedEncodingException e) {
        throw new AssertionError(e);
      }
    }

    public void setClickListener(View.OnClickListener listener) {
      this.clickListener = listener;
    }

    public String getFormattedSafetyNumbers() {
      StringBuilder result = new StringBuilder();

      for (int i = 0; i < codes.length; i++) {
        result.append(codes[i].getText());

        if (i != codes.length - 1) {
          if (((i+1) % 4) == 0) result.append('\n');
          else                  result.append(' ');
        }
      }

      return result.toString();
    }

    private void handleCopyToClipboard() {
      Util.writeTextToClipboard(getActivity(), getFormattedSafetyNumbers());
    }

    private void handleCompareWithClipboard() {
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

    private void setFingerprintViews(Fingerprint fingerprint) {
      String digits   = fingerprint.getDisplayableFingerprint().getDisplayText();
      int    partSize = digits.length() / codes.length;

      for (int i=0;i<codes.length;i++) {
        codes[i].setText(digits.substring(i * partSize, (i * partSize) + partSize));
      }

      byte[] qrCodeData   = fingerprint.getScannableFingerprint().getSerialized();
      String qrCodeString = new String(qrCodeData, Charset.forName("ISO-8859-1"));
      Bitmap qrCodeBitmap = QrCode.create(qrCodeString);

      qrCode.setImageBitmap(qrCodeBitmap);
      description.setText(Html.fromHtml(String.format(getActivity().getString(R.string.verify_display_fragment__if_you_wish_to_verify_the_security_of_your_end_to_end_encryption_with_s), recipient.toShortString())));
      description.setMovementMethod(LinkMovementMethod.getInstance());
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
  }

  public static class VerifyScanFragment extends Fragment {

    private View           container;
    private CameraView     cameraView;
    private ScanningThread scanningThread;
    private ScanListener   scanListener;

    public View onCreateView(LayoutInflater inflater, ViewGroup viewGroup, Bundle bundle) {
      this.container  = ViewUtil.inflate(inflater, viewGroup, R.layout.verify_scan_fragment);
      this.cameraView = ViewUtil.findById(container, R.id.scanner);

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
