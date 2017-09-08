package org.thoughtcrime.securesms;


import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.soundcloud.android.crop.Crop;

import org.thoughtcrime.securesms.components.InputAwareLayout;
import org.thoughtcrime.securesms.components.emoji.EmojiDrawer;
import org.thoughtcrime.securesms.components.emoji.EmojiToggle;
import org.thoughtcrime.securesms.contacts.avatars.ContactPhoto;
import org.thoughtcrime.securesms.contacts.avatars.ContactPhotoFactory;
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.jobs.MultiDeviceProfileKeyUpdateJob;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.profiles.ProfileMediaConstraints;
import org.thoughtcrime.securesms.profiles.SystemProfileUtil;
import org.thoughtcrime.securesms.util.BitmapDecodingException;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.IntentUtils;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.concurrent.ListenableFuture;
import org.thoughtcrime.securesms.util.task.ProgressDialogAsyncTask;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.crypto.ProfileCipher;
import org.whispersystems.signalservice.api.util.StreamDetails;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.inject.Inject;

import static android.provider.MediaStore.EXTRA_OUTPUT;

public class CreateProfileActivity extends BaseActionBarActivity implements InjectableType {

  private static final String TAG = CreateProfileActivity.class.getSimpleName();

  public static final String NEXT_INTENT    = "next_intent";
  public static final String EXCLUDE_SYSTEM = "exclude_system";

  private static final int REQUEST_CODE_AVATAR = 1;

  @Inject SignalServiceAccountManager accountManager;

  private InputAwareLayout container;
  private ImageView        avatar;
  private Button           finishButton;
  private EditText         name;
  private EmojiToggle      emojiToggle;
  private EmojiDrawer      emojiDrawer;

  private Intent nextIntent;
  private byte[] avatarBytes;
  private File   captureFile;

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);

    setContentView(R.layout.profile_create_activity);

    getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    getSupportActionBar().setTitle(R.string.CreateProfileActivity_your_profile_info);

    initializeResources();
    initializeEmojiInput();
    initializeProfileName(getIntent().getBooleanExtra(EXCLUDE_SYSTEM, false));
    initializeProfileAvatar(getIntent().getBooleanExtra(EXCLUDE_SYSTEM, false));

    ApplicationContext.getInstance(this).injectDependencies(this);
  }

  @Override
  public void onBackPressed() {
    if (container.isInputOpen()) container.hideCurrentInput(name);
    else                         super.onBackPressed();
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);

    if (container.getCurrentInput() == emojiDrawer) {
      container.hideAttachedInput(true);
    }
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    switch (requestCode) {
      case REQUEST_CODE_AVATAR:
        if (resultCode == Activity.RESULT_OK) {
          Uri outputFile = Uri.fromFile(new File(getCacheDir(), "cropped"));
          Uri inputFile  = (data != null ? data.getData() : null);

          if (inputFile == null && captureFile != null) {
            inputFile = Uri.fromFile(captureFile);
          }

          if (data != null && data.getBooleanExtra("delete", false)) {
            avatarBytes = null;
            avatar.setImageDrawable(ContactPhotoFactory.getResourceContactPhoto(R.drawable.ic_camera_alt_white_24dp)
                                                       .asDrawable(this, getResources().getColor(R.color.grey_400)));
          } else {
            new Crop(inputFile).output(outputFile).asSquare().start(this);
          }
        }

        break;
      case Crop.REQUEST_CROP:
        if (resultCode == Activity.RESULT_OK) {
          try {
            avatarBytes = BitmapUtil.createScaledBytes(this, Crop.getOutput(data), new ProfileMediaConstraints());
            avatar.setImageDrawable(ContactPhotoFactory.getGroupContactPhoto(avatarBytes).asDrawable(this, 0));
          } catch (BitmapDecodingException e) {
            Log.w(TAG, e);
            Toast.makeText(this, R.string.CreateProfileActivity_error_setting_profile_photo, Toast.LENGTH_LONG).show();
          }
        }
        break;
    }
  }

  private void initializeResources() {
    TextView skipButton       = ViewUtil.findById(this, R.id.skip_button);
    TextView informationLabel = ViewUtil.findById(this, R.id.information_label);

    this.avatar       = ViewUtil.findById(this, R.id.avatar);
    this.name         = ViewUtil.findById(this, R.id.name);
    this.emojiToggle  = ViewUtil.findById(this, R.id.emoji_toggle);
    this.emojiDrawer  = ViewUtil.findById(this, R.id.emoji_drawer);
    this.container    = ViewUtil.findById(this, R.id.container);
    this.finishButton = ViewUtil.findById(this, R.id.finish_button);
    this.nextIntent   = getIntent().getParcelableExtra(NEXT_INTENT);

    this.avatar.setImageDrawable(ContactPhotoFactory.getResourceContactPhoto(R.drawable.ic_camera_alt_white_24dp)
                                                    .asDrawable(this, getResources().getColor(R.color.grey_400)));

    this.avatar.setOnClickListener(view -> {
      try {
        captureFile  = File.createTempFile("capture", "jpg", getExternalCacheDir());
      } catch (IOException e) {
        Log.w(TAG, e);
        captureFile = null;
      }

      Intent chooserIntent = createAvatarSelectionIntent(captureFile, avatarBytes != null);
      startActivityForResult(chooserIntent, REQUEST_CODE_AVATAR);
    });

    this.name.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {}
      @Override
      public void afterTextChanged(Editable s) {
        if (s.toString().getBytes().length > ProfileCipher.NAME_PADDED_LENGTH) {
          name.setError(getString(R.string.CreateProfileActivity_too_long));
          finishButton.setEnabled(false);
        } else if (name.getError() != null || !finishButton.isEnabled()) {
          name.setError(null);
          finishButton.setEnabled(true);
        }
      }
    });

    this.finishButton.setOnClickListener(view -> {
      handleUpload();
    });

    skipButton.setOnClickListener(view -> {
      if (nextIntent != null) startActivity(nextIntent);
      finish();
    });

    informationLabel.setOnClickListener(view -> {
      Intent intent = new Intent(Intent.ACTION_VIEW);
      intent.setData(Uri.parse("https://support.signal.org/hc/en-us/articles/115001434171"));

      if (getPackageManager().queryIntentActivities(intent, 0).size() > 0) {
        startActivity(intent);
      }
    });
  }

  private void initializeProfileName(boolean excludeSystem) {
    if (!TextUtils.isEmpty(TextSecurePreferences.getProfileName(this))) {
      String profileName = TextSecurePreferences.getProfileName(this);

      name.setText(profileName);
      name.setSelection(profileName.length(), profileName.length());
    } else if (!excludeSystem) {
      SystemProfileUtil.getSystemProfileName(this).addListener(new ListenableFuture.Listener<String>() {
        @Override
        public void onSuccess(String result) {
          if (!TextUtils.isEmpty(result)) {
            name.setText(result);
            name.setSelection(result.length(), result.length());
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
      new AsyncTask<Void, Void, Pair<byte[], ContactPhoto>>() {
        @Override
        protected Pair<byte[], ContactPhoto> doInBackground(Void... params) {
          try {
            byte[] data =Util.readFully(AvatarHelper.getInputStreamFor(CreateProfileActivity.this, ourAddress));
            return new Pair<>(data, ContactPhotoFactory.getSignalAvatarContactPhoto(CreateProfileActivity.this, ourAddress, null, getResources().getDimensionPixelSize(R.dimen.contact_photo_target_size)));
          } catch (IOException e) {
            Log.w(TAG, e);
            return null;
          }
        }

        @Override
        protected void onPostExecute(Pair<byte[], ContactPhoto> result) {
          if (result != null) {
            avatarBytes = result.first;
            avatar.setImageDrawable(result.second.asDrawable(CreateProfileActivity.this, 0));
          }
        }
      }.execute();
    } else if (!excludeSystem) {
      SystemProfileUtil.getSystemProfileAvatar(this, new ProfileMediaConstraints()).addListener(new ListenableFuture.Listener<byte[]>() {
        @Override
        public void onSuccess(byte[] result) {
          if (result != null) {
            avatarBytes = result;
            avatar.setImageDrawable(ContactPhotoFactory.getGroupContactPhoto(result).asDrawable(CreateProfileActivity.this, 0));
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
    this.emojiToggle.attach(emojiDrawer);

    this.emojiToggle.setOnClickListener(v -> {
      if (container.getCurrentInput() == emojiDrawer) {
        container.showSoftkey(name);
      } else {
        container.show(name, emojiDrawer);
      }
    });

    this.emojiDrawer.setEmojiEventListener(new EmojiDrawer.EmojiEventListener() {
      @Override
      public void onKeyEvent(KeyEvent keyEvent) {
        name.dispatchKeyEvent(keyEvent);
      }

      @Override
      public void onEmojiSelected(String emoji) {
        final int start = name.getSelectionStart();
        final int end   = name.getSelectionEnd();

        name.getText().replace(Math.min(start, end), Math.max(start, end), emoji);
        name.setSelection(start + emoji.length());
      }
    });

    this.container.addOnKeyboardShownListener(() -> emojiToggle.setToEmoji());
    this.name.setOnClickListener(v -> container.showSoftkey(name));
  }

  private Intent createAvatarSelectionIntent(@Nullable File captureFile, boolean includeClear) {
    List<Intent> extraIntents  = new LinkedList<>();
    Intent       galleryIntent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.INTERNAL_CONTENT_URI);
    galleryIntent.setType("image/*");

    if (!IntentUtils.isResolvable(CreateProfileActivity.this, galleryIntent)) {
      galleryIntent = new Intent(Intent.ACTION_GET_CONTENT);
      galleryIntent.setType("image/*");
    }

    Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

    if (captureFile != null && cameraIntent.resolveActivity(getPackageManager()) != null) {
      cameraIntent.putExtra(EXTRA_OUTPUT, Uri.fromFile(captureFile));
      extraIntents.add(cameraIntent);
    }

    if (includeClear) {
      extraIntents.add(new Intent("org.thoughtcrime.securesms.action.CLEAR_PROFILE_PHOTO"));
    }

    Intent chooserIntent = Intent.createChooser(galleryIntent, getString(R.string.CreateProfileActivity_profile_photo));
    chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, extraIntents.toArray(new Intent[0]));


    return chooserIntent;
  }

  private void handleUpload() {
    final String        name;
    final StreamDetails avatar;

    if (TextUtils.isEmpty(this.name.getText().toString())) name = null;
    else                                                   name = this.name.getText().toString();

    if (avatarBytes == null || avatarBytes.length == 0) avatar = null;
    else                                                avatar = new StreamDetails(new ByteArrayInputStream(avatarBytes),
                                                                                   "image/jpeg", avatarBytes.length);

    new ProgressDialogAsyncTask<Void, Void, Boolean>(this,
                                                     getString(R.string.CreateProfileActivity_updating_and_encrypting_profile),
                                                     getString(R.string.CreateProfileActivity_updating_profile))
    {
      @Override
      protected Boolean doInBackground(Void... params) {
        byte[] profileKey = ProfileKeyUtil.getProfileKey(CreateProfileActivity.this);

        try {
          accountManager.setProfileName(profileKey, name);
          TextSecurePreferences.setProfileName(getContext(), name);
        } catch (IOException e) {
          Log.w(TAG, e);
          return false;
        }

        try {
          accountManager.setProfileAvatar(profileKey, avatar);
          AvatarHelper.setAvatar(getContext(), Address.fromSerialized(TextSecurePreferences.getLocalNumber(getContext())), avatarBytes);
        } catch (IOException e) {
          Log.w(TAG, e);
          return false;
        }

        ApplicationContext.getInstance(getContext()).getJobManager().add(new MultiDeviceProfileKeyUpdateJob(getContext()));

        return true;
      }

      @Override
      public void onPostExecute(Boolean result) {
        super.onPostExecute(result);

        if (result) {
          if (captureFile != null) captureFile.delete();
          if (nextIntent != null)  startActivity(nextIntent);

          finish();
        } else        {
          Toast.makeText(CreateProfileActivity.this, R.string.CreateProfileActivity_problem_setting_profile, Toast.LENGTH_LONG).show();
        }
      }
    }.execute();
  }


}
