package org.thoughtcrime.securesms;


import android.Manifest;
import android.animation.Animator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.dd.CircularProgressButton;

import org.thoughtcrime.securesms.avatar.AvatarSelection;
import org.thoughtcrime.securesms.components.InputAwareLayout;
import org.thoughtcrime.securesms.components.LabeledEditText;
import org.thoughtcrime.securesms.components.emoji.EmojiKeyboardProvider;
import org.thoughtcrime.securesms.components.emoji.EmojiToggle;
import org.thoughtcrime.securesms.components.emoji.MediaKeyboard;
import org.thoughtcrime.securesms.contacts.avatars.ResourceContactPhoto;
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.jobs.MultiDeviceProfileKeyUpdateJob;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.profiles.ProfileMediaConstraints;
import org.thoughtcrime.securesms.profiles.SystemProfileUtil;
import org.thoughtcrime.securesms.util.BitmapDecodingException;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicRegistrationTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.concurrent.ListenableFuture;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.crypto.ProfileCipher;
import org.whispersystems.signalservice.api.util.StreamDetails;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.concurrent.ExecutionException;

import javax.inject.Inject;

@SuppressLint("StaticFieldLeak")
public class CreateProfileActivity extends BaseActionBarActivity implements InjectableType {

  private static final String TAG = CreateProfileActivity.class.getSimpleName();

  public static final String NEXT_INTENT    = "next_intent";
  public static final String EXCLUDE_SYSTEM = "exclude_system";

  private final DynamicTheme    dynamicTheme    = new DynamicRegistrationTheme();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  @Inject SignalServiceAccountManager accountManager;

  private InputAwareLayout       container;
  private ImageView              avatar;
  private CircularProgressButton finishButton;
  private LabeledEditText        name;
  private EmojiToggle            emojiToggle;
  private MediaKeyboard          mediaKeyboard;
  private View                   reveal;

  private Intent nextIntent;
  private byte[] avatarBytes;
  private File   captureFile;

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);

    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);

    setContentView(R.layout.profile_create_activity);

    getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

    initializeResources();
    initializeEmojiInput();
    initializeProfileName(getIntent().getBooleanExtra(EXCLUDE_SYSTEM, false));
    initializeProfileAvatar(getIntent().getBooleanExtra(EXCLUDE_SYSTEM, false));

    ApplicationContext.getInstance(this).injectDependencies(this);
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);
  }

  @Override
  public void onBackPressed() {
    if (container.isInputOpen()) container.hideCurrentInput(name.getInput());
    else                         super.onBackPressed();
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);

    if (container.getCurrentInput() == mediaKeyboard) {
      container.hideAttachedInput(true);
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    switch (requestCode) {
      case AvatarSelection.REQUEST_CODE_AVATAR:
        if (resultCode == Activity.RESULT_OK) {
          Uri outputFile = Uri.fromFile(new File(getCacheDir(), "cropped"));
          Uri inputFile  = (data != null ? data.getData() : null);

          if (inputFile == null && captureFile != null) {
            inputFile = Uri.fromFile(captureFile);
          }

          if (data != null && data.getBooleanExtra("delete", false)) {
            avatarBytes = null;
            avatar.setImageDrawable(new ResourceContactPhoto(R.drawable.ic_camera_alt_white_24dp).asDrawable(this, getResources().getColor(R.color.grey_400)));
          } else {
            AvatarSelection.circularCropImage(this, inputFile, outputFile, R.string.CropImageActivity_profile_avatar);
          }
        }

        break;
      case AvatarSelection.REQUEST_CODE_CROP_IMAGE:
        if (resultCode == Activity.RESULT_OK) {
          new AsyncTask<Void, Void, byte[]>() {
            @Override
            protected byte[] doInBackground(Void... params) {
              try {
                BitmapUtil.ScaleResult result = BitmapUtil.createScaledBytes(CreateProfileActivity.this, AvatarSelection.getResultUri(data), new ProfileMediaConstraints());
                return result.getBitmap();
              } catch (BitmapDecodingException e) {
                Log.w(TAG, e);
                return null;
              }
            }

            @Override
            protected void onPostExecute(byte[] result) {
              if (result != null) {
                avatarBytes = result;
                GlideApp.with(CreateProfileActivity.this)
                        .load(avatarBytes)
                        .skipMemoryCache(true)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .circleCrop()
                        .into(avatar);
              } else {
                Toast.makeText(CreateProfileActivity.this, R.string.CreateProfileActivity_error_setting_profile_photo, Toast.LENGTH_LONG).show();
              }
            }
          }.execute();
        }
        break;
    }
  }

  private void initializeResources() {
    TextView skipButton       = ViewUtil.findById(this, R.id.skip_button);

    this.avatar       = ViewUtil.findById(this, R.id.avatar);
    this.name         = ViewUtil.findById(this, R.id.name);
    this.emojiToggle  = ViewUtil.findById(this, R.id.emoji_toggle);
    this.mediaKeyboard = ViewUtil.findById(this, R.id.emoji_drawer);
    this.container    = ViewUtil.findById(this, R.id.container);
    this.finishButton = ViewUtil.findById(this, R.id.finish_button);
    this.reveal       = ViewUtil.findById(this, R.id.reveal);
    this.nextIntent   = getIntent().getParcelableExtra(NEXT_INTENT);

    this.avatar.setOnClickListener(view -> Permissions.with(this)
                                                      .request(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                                      .ifNecessary()
                                                      .onAnyResult(this::startAvatarSelection)
                                                      .execute());

    this.name.getInput().addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {}
      @Override
      public void afterTextChanged(Editable s) {
        if (s.toString().getBytes().length > ProfileCipher.NAME_PADDED_LENGTH) {
          name.getInput().setError(getString(R.string.CreateProfileActivity_too_long));
          finishButton.setEnabled(false);
        } else if (name.getInput().getError() != null || !finishButton.isEnabled()) {
          name.getInput().setError(null);
          finishButton.setEnabled(true);
        }
      }
    });

    this.finishButton.setOnClickListener(view -> {
      this.finishButton.setIndeterminateProgressMode(true);
      this.finishButton.setProgress(50);
      handleUpload();
    });

    skipButton.setOnClickListener(view -> {
      if (nextIntent != null) startActivity(nextIntent);
      finish();
    });
  }

  private void initializeProfileName(boolean excludeSystem) {
    if (!TextUtils.isEmpty(TextSecurePreferences.getProfileName(this))) {
      String profileName = TextSecurePreferences.getProfileName(this);

      name.setText(profileName);
      name.getInput().setSelection(profileName.length(), profileName.length());
    } else if (!excludeSystem) {
      SystemProfileUtil.getSystemProfileName(this).addListener(new ListenableFuture.Listener<String>() {
        @Override
        public void onSuccess(String result) {
          if (!TextUtils.isEmpty(result)) {
            name.setText(result);
            name.getInput().setSelection(result.length(), result.length());
          }
        }

        @Override
        public void onFailure(ExecutionException e) {
          Log.w(TAG, e);
        }
      });
    }
  }

  private void initializeProfileAvatar(boolean excludeSystem) {
    Address ourAddress = Address.fromSerialized(TextSecurePreferences.getLocalNumber(this));

    if (AvatarHelper.getAvatarFile(this, ourAddress).exists() && AvatarHelper.getAvatarFile(this, ourAddress).length() > 0) {
      new AsyncTask<Void, Void, byte[]>() {
        @Override
        protected byte[] doInBackground(Void... params) {
          try {
            return Util.readFully(AvatarHelper.getInputStreamFor(CreateProfileActivity.this, ourAddress));
          } catch (IOException e) {
            Log.w(TAG, e);
            return null;
          }
        }

        @Override
        protected void onPostExecute(byte[] result) {
          if (result != null) {
            avatarBytes = result;
            GlideApp.with(CreateProfileActivity.this)
                    .load(result)
                    .circleCrop()
                    .into(avatar);
          }
        }
      }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    } else if (!excludeSystem) {
      SystemProfileUtil.getSystemProfileAvatar(this, new ProfileMediaConstraints()).addListener(new ListenableFuture.Listener<byte[]>() {
        @Override
        public void onSuccess(byte[] result) {
          if (result != null) {
            avatarBytes = result;
            GlideApp.with(CreateProfileActivity.this)
                    .load(result)
                    .circleCrop()
                    .into(avatar);
          }
        }

        @Override
        public void onFailure(ExecutionException e) {
          Log.w(TAG, e);
        }
      });
    }
  }

  private void initializeEmojiInput() {
    this.emojiToggle.attach(mediaKeyboard);

    this.emojiToggle.setOnClickListener(v -> {
      if (container.getCurrentInput() == mediaKeyboard) {
        container.showSoftkey(name.getInput());
      } else {
        container.show(name.getInput(), mediaKeyboard);
      }
    });

    this.mediaKeyboard.setProviders(0, new EmojiKeyboardProvider(this, new EmojiKeyboardProvider.EmojiEventListener() {
      @Override
      public void onKeyEvent(KeyEvent keyEvent) {
        name.dispatchKeyEvent(keyEvent);
      }

      @Override
      public void onEmojiSelected(String emoji) {
        final int start = name.getInput().getSelectionStart();
        final int end   = name.getInput().getSelectionEnd();

        name.getText().replace(Math.min(start, end), Math.max(start, end), emoji);
        name.getInput().setSelection(start + emoji.length());
      }
    }));

    this.container.addOnKeyboardShownListener(() -> emojiToggle.setToMedia());
    this.name.setOnClickListener(v -> container.showSoftkey(name.getInput()));
  }

  private void startAvatarSelection() {
    captureFile = AvatarSelection.startAvatarSelection(this, avatarBytes != null, true);
  }

  private void handleUpload() {
    final String        name;
    final StreamDetails avatar;

    if (TextUtils.isEmpty(this.name.getText().toString())) name = null;
    else                                                   name = this.name.getText().toString();

    if (avatarBytes == null || avatarBytes.length == 0) avatar = null;
    else                                                avatar = new StreamDetails(new ByteArrayInputStream(avatarBytes),
                                                                                   "image/jpeg", avatarBytes.length);

    new AsyncTask<Void, Void, Boolean>() {
      @Override
      protected Boolean doInBackground(Void... params) {
        Context context    = CreateProfileActivity.this;
        byte[]  profileKey = ProfileKeyUtil.getProfileKey(CreateProfileActivity.this);

        try {
          accountManager.setProfileName(profileKey, name);
          TextSecurePreferences.setProfileName(context, name);
        } catch (IOException e) {
          Log.w(TAG, e);
          return false;
        }

        try {
          accountManager.setProfileAvatar(profileKey, avatar);
          AvatarHelper.setAvatar(CreateProfileActivity.this, Address.fromSerialized(TextSecurePreferences.getLocalNumber(context)), avatarBytes);
          TextSecurePreferences.setProfileAvatarId(CreateProfileActivity.this, new SecureRandom().nextInt());
        } catch (IOException e) {
          Log.w(TAG, e);
          return false;
        }

        ApplicationContext.getInstance(context).getJobManager().add(new MultiDeviceProfileKeyUpdateJob());

        return true;
      }

      @Override
      public void onPostExecute(Boolean result) {
        super.onPostExecute(result);

        if (result) {
          if (captureFile != null) captureFile.delete();
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) handleFinishedLollipop();
          else                                                       handleFinishedLegacy();
        } else        {
          Toast.makeText(CreateProfileActivity.this, R.string.CreateProfileActivity_problem_setting_profile, Toast.LENGTH_LONG).show();
        }
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  private void handleFinishedLegacy() {
    finishButton.setProgress(0);
    if (nextIntent != null) startActivity(nextIntent);
    finish();
  }

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  private void handleFinishedLollipop() {
    int[] finishButtonLocation = new int[2];
    int[] revealLocation       = new int[2];

    finishButton.getLocationInWindow(finishButtonLocation);
    reveal.getLocationInWindow(revealLocation);

    int finishX = finishButtonLocation[0] - revealLocation[0];
    int finishY = finishButtonLocation[1] - revealLocation[1];

    finishX += finishButton.getWidth() / 2;
    finishY += finishButton.getHeight() / 2;

    Animator animation = ViewAnimationUtils.createCircularReveal(reveal, finishX, finishY, 0f, (float) Math.max(reveal.getWidth(), reveal.getHeight()));
    animation.setDuration(500);
    animation.addListener(new Animator.AnimatorListener() {
      @Override
      public void onAnimationStart(Animator animation) {}

      @Override
      public void onAnimationEnd(Animator animation) {
        finishButton.setProgress(0);
        if (nextIntent != null)  startActivity(nextIntent);
        finish();
      }

      @Override
      public void onAnimationCancel(Animator animation) {}
      @Override
      public void onAnimationRepeat(Animator animation) {}
    });

    reveal.setVisibility(View.VISIBLE);
    animation.start();
  }
}
