package org.thoughtcrime.securesms.avatar;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.theartofdev.edmodo.cropper.CropImage;
import com.theartofdev.edmodo.cropper.CropImageView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.util.FileProviderUtil;
import org.thoughtcrime.securesms.util.IntentUtils;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import static android.provider.MediaStore.EXTRA_OUTPUT;

public final class AvatarSelection {

  private static final String TAG = AvatarSelection.class.getSimpleName();

  private AvatarSelection() {
  }

  public static final int REQUEST_CODE_CROP_IMAGE = CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE;
  public static final int REQUEST_CODE_AVATAR     = REQUEST_CODE_CROP_IMAGE + 1;

  /**
   * Returns result on {@link #REQUEST_CODE_CROP_IMAGE}
   */
  public static void circularCropImage(Activity activity, Uri inputFile, Uri outputFile, @StringRes int title) {
    CropImage.activity(inputFile)
             .setGuidelines(CropImageView.Guidelines.ON)
             .setAspectRatio(1, 1)
             .setCropShape(CropImageView.CropShape.OVAL)
             .setOutputUri(outputFile)
             .setAllowRotation(true)
             .setAllowFlipping(true)
             .setBackgroundColor(ContextCompat.getColor(activity, R.color.avatar_background))
             .setActivityTitle(activity.getString(title))
             .start(activity);
  }

  /**
   * Returns result on {@link #REQUEST_CODE_CROP_IMAGE}
   */
  public static void circularCropImage(Fragment fragment, Uri inputFile, Uri outputFile, @StringRes int title) {
    CropImage.activity(inputFile)
             .setGuidelines(CropImageView.Guidelines.ON)
             .setAspectRatio(1, 1)
             .setCropShape(CropImageView.CropShape.OVAL)
             .setOutputUri(outputFile)
             .setAllowRotation(true)
             .setAllowFlipping(true)
             .setBackgroundColor(ContextCompat.getColor(fragment.requireContext(), R.color.avatar_background))
             .setActivityTitle(fragment.requireContext().getString(title))
             .start(fragment.requireContext(), fragment);
  }

  public static Uri getResultUri(Intent data) {
    return CropImage.getActivityResult(data).getUri();
  }

  /**
   * Returns result on {@link #REQUEST_CODE_AVATAR}
   *
   * @return Temporary capture file if created.
   */
  public static File startAvatarSelection(Activity activity, boolean includeClear, boolean attemptToIncludeCamera) {
    File captureFile  = attemptToIncludeCamera ? getCaptureFile(activity) : null;

    Intent chooserIntent = createAvatarSelectionIntent(activity, captureFile, includeClear);
    activity.startActivityForResult(chooserIntent, REQUEST_CODE_AVATAR);
    return captureFile;
  }

  /**
   * Returns result on {@link #REQUEST_CODE_AVATAR}
   *
   * @return Temporary capture file if created.
   */
  public static File startAvatarSelection(Fragment fragment, boolean includeClear, boolean attemptToIncludeCamera) {
    File captureFile  = attemptToIncludeCamera ? getCaptureFile(fragment.requireContext()) : null;

    Intent chooserIntent = createAvatarSelectionIntent(fragment.requireContext(), captureFile, includeClear);
    fragment.startActivityForResult(chooserIntent, REQUEST_CODE_AVATAR);
    return captureFile;
  }

  private static @Nullable File getCaptureFile(@NonNull Context context) {
    if (!Permissions.hasAll(context, Manifest.permission.CAMERA)) {
      return null;
    }

    try {
      return File.createTempFile("capture", "jpg", context.getExternalCacheDir());
    } catch (IOException e) {
      Log.w(TAG, e);
      return null;
    }
  }

  private static Intent createAvatarSelectionIntent(Context context, @Nullable File tempCaptureFile, boolean includeClear) {
    List<Intent> extraIntents  = new LinkedList<>();
    Intent       galleryIntent = new Intent(Intent.ACTION_PICK);

    galleryIntent.setDataAndType(android.provider.MediaStore.Images.Media.INTERNAL_CONTENT_URI, "image/*");

    if (!IntentUtils.isResolvable(context, galleryIntent)) {
      galleryIntent = new Intent(Intent.ACTION_GET_CONTENT);
      galleryIntent.setType("image/*");
    }

    if (tempCaptureFile != null) {
      Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

      if (cameraIntent.resolveActivity(context.getPackageManager()) != null) {
        cameraIntent.putExtra(EXTRA_OUTPUT, FileProviderUtil.getUriFor(context, tempCaptureFile));
        extraIntents.add(cameraIntent);
      }
    }

    if (includeClear) {
      extraIntents.add(new Intent("org.thoughtcrime.securesms.action.CLEAR_PROFILE_PHOTO"));
    }

    Intent chooserIntent = Intent.createChooser(galleryIntent, context.getString(R.string.CreateProfileActivity_profile_photo));

    if (!extraIntents.isEmpty()) {
      chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, extraIntents.toArray(new Intent[0]));
    }

    return chooserIntent;
  }
}
