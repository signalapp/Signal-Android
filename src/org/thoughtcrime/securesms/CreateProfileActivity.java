package org.thoughtcrime.securesms;


import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.soundcloud.android.crop.Crop;

import org.thoughtcrime.securesms.components.InputAwareLayout;
import org.thoughtcrime.securesms.components.emoji.EmojiDrawer;
import org.thoughtcrime.securesms.components.emoji.EmojiToggle;
import org.thoughtcrime.securesms.contacts.avatars.ContactPhotoFactory;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.profiles.ProfileMediaConstraints;
import org.thoughtcrime.securesms.profiles.SystemProfileUtil;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.BitmapDecodingException;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.IntentUtils;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.concurrent.ListenableFuture;
import org.thoughtcrime.securesms.util.task.ProgressDialogAsyncTask;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.util.StreamDetails;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

import javax.inject.Inject;

public class CreateProfileActivity extends PassphraseRequiredActionBarActivity implements InjectableType {

  private static final String TAG = CreateProfileActivity.class.getSimpleName();

  private static final int REQUEST_CODE_AVATAR = 1;

  @Inject SignalServiceAccountManager accountManager;

  private InputAwareLayout container;
  private ImageView        avatar;
  private Button           finishButton;
  private EditText         name;
  private EmojiToggle      emojiToggle;
  private EmojiDrawer      emojiDrawer;

  private byte[] avatarBytes;

  @Override
  public void onCreate(Bundle bundle, @NonNull MasterSecret masterSecret) {
    super.onCreate(bundle, masterSecret);

    setContentView(R.layout.profile_create_activity);

    getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    getSupportActionBar().setTitle("Your profile info");

    initializeResources();
    initializeEmojiInput();
    initializeDeviceOwner();

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
          new Crop(data.getData()).output(outputFile).asSquare().start(this);
        }

        break;
      case Crop.REQUEST_CROP:
        if (resultCode == Activity.RESULT_OK) {
          try {
            avatarBytes = BitmapUtil.createScaledBytes(this, Crop.getOutput(data), new ProfileMediaConstraints());
            avatar.setImageDrawable(ContactPhotoFactory.getGroupContactPhoto(avatarBytes).asDrawable(this, 0));
          } catch (BitmapDecodingException e) {
            Log.w(TAG, e);
            Toast.makeText(this, "Error setting profile photo", Toast.LENGTH_LONG).show();
          }
        }
        break;
    }
  }

  private void initializeResources() {
    this.avatar       = ViewUtil.findById(this, R.id.avatar);
    this.name         = ViewUtil.findById(this, R.id.name);
    this.emojiToggle  = ViewUtil.findById(this, R.id.emoji_toggle);
    this.emojiDrawer  = ViewUtil.findById(this, R.id.emoji_drawer);
    this.container    = ViewUtil.findById(this, R.id.container);
    this.finishButton = ViewUtil.findById(this, R.id.finish_button);

    this.avatar.setImageDrawable(ContactPhotoFactory.getResourceContactPhoto(R.drawable.ic_camera_alt_white_24dp)
                                                    .asDrawable(this, getResources().getColor(R.color.grey_400)));

    this.avatar.setOnClickListener(view -> {
      Intent galleryIntent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.INTERNAL_CONTENT_URI);
      galleryIntent.setType("image/*");

      if (!IntentUtils.isResolvable(CreateProfileActivity.this, galleryIntent)) {
        galleryIntent = new Intent(Intent.ACTION_GET_CONTENT);
        galleryIntent.setType("image/*");
      }

      Intent cameraIntent  = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
      Intent chooserIntent = Intent.createChooser(galleryIntent, "Profile photo");
      chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[] { cameraIntent });

      startActivityForResult(chooserIntent, REQUEST_CODE_AVATAR);
    });

    this.finishButton.setOnClickListener(view -> {
      handleUpload();
    });
  }

  private void initializeDeviceOwner() {
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

  private void handleUpload() {
    final String        name;
    final StreamDetails avatar;

    if (TextUtils.isEmpty(this.name.getText().toString())) name = null;
    else                                                   name = this.name.getText().toString();

    if (avatarBytes == null || avatarBytes.length == 0) avatar = null;
    else                                                avatar = new StreamDetails(new ByteArrayInputStream(avatarBytes),
                                                                                   "image/jpeg", avatarBytes.length);

    new ProgressDialogAsyncTask<Void, Void, Boolean>(this, "Updating and encrypting profile", "Updating profile") {
      @Override
      protected Boolean doInBackground(Void... params) {
        String encodedProfileKey = TextSecurePreferences.getProfileKey(CreateProfileActivity.this);

        if (encodedProfileKey == null) {
          encodedProfileKey = Util.getSecret(32);
          TextSecurePreferences.setProfileKey(CreateProfileActivity.this, encodedProfileKey);
        }

        try {
          accountManager.setProfileName(Base64.decode(encodedProfileKey), name);
        } catch (IOException e) {
          Log.w(TAG, e);
          return false;
        }

        try {
          accountManager.setProfileAvatar(Base64.decode(encodedProfileKey), avatar);;
        } catch (IOException e) {
          Log.w(TAG, e);
          return false;
        }

        return true;
      }

      @Override
      public void onPostExecute(Boolean result) {
        super.onPostExecute(result);

        if (result) finish();
        else        Toast.makeText(CreateProfileActivity.this, "Problem setting profile", Toast.LENGTH_LONG).show();;
      }
    }.execute();
  }


}
