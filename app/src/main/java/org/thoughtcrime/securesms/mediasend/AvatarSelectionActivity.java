package org.thoughtcrime.securesms.mediasend;

import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import org.signal.imageeditor.core.model.EditorModel;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.mediasend.v2.gallery.MediaGalleryFragment;
import org.thoughtcrime.securesms.mms.MediaConstraints;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.scribbles.ImageEditorFragment;
import org.thoughtcrime.securesms.util.MediaUtil;

import java.io.FileDescriptor;
import java.util.Collections;
import java.util.Optional;

import io.reactivex.rxjava3.core.Flowable;

public class AvatarSelectionActivity extends AppCompatActivity implements CameraFragment.Controller, ImageEditorFragment.Controller, MediaGalleryFragment.Callbacks {

  private static final Point AVATAR_DIMENSIONS = new Point(AvatarHelper.AVATAR_DIMENSIONS, AvatarHelper.AVATAR_DIMENSIONS);

  private static final String IMAGE_CAPTURE = "IMAGE_CAPTURE";
  private static final String IMAGE_EDITOR  = "IMAGE_EDITOR";
  private static final String ARG_GALLERY   = "ARG_GALLERY";

  public static final String EXTRA_MEDIA = "avatar.media";

  private Media currentMedia;

  public static Intent getIntentForCameraCapture(@NonNull Context context) {
    return new Intent(context, AvatarSelectionActivity.class);
  }

  public static Intent getIntentForGallery(@NonNull Context context) {
    Intent intent = getIntentForCameraCapture(context);

    intent.putExtra(ARG_GALLERY, true);

    return intent;
  }

  @Override
  protected void attachBaseContext(@NonNull Context newBase) {
    getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_YES);
    super.attachBaseContext(newBase);
  }

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    getWindow().addFlags(
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
    );

    setContentView(R.layout.avatar_selection_activity);

    if (isGalleryFirst()) {
      onGalleryClicked();
    } else {
      onNavigateToCamera();
    }
  }

  @Override
  public void onCameraError() {
    Toast.makeText(this, R.string.default_error_msg, Toast.LENGTH_SHORT).show();
    finish();
  }

  @Override
  public void onImageCaptured(@NonNull byte[] data, int width, int height) {
    Uri blobUri = BlobProvider.getInstance()
                              .forData(data)
                              .withMimeType(MediaUtil.IMAGE_JPEG)
                              .createForSingleSessionInMemory();

    onMediaSelected(new Media(blobUri,
                              MediaUtil.IMAGE_JPEG,
                              System.currentTimeMillis(),
                              width,
                              height,
                              data.length,
                              0,
                              false,
                              false,
                              Optional.of(Media.ALL_MEDIA_BUCKET_ID),
                              Optional.empty(),
                              Optional.empty(),
                              Optional.empty()));
  }

  @Override
  public void onVideoCaptured(@NonNull FileDescriptor fd) {
    throw new UnsupportedOperationException("Cannot set profile as video");
  }

  @Override
  public void onVideoCaptureError() {
    throw new AssertionError("This should never happen");
  }

  @Override
  public void onGalleryClicked() {
    if (isGalleryFirst() && popToRoot()) {
      return;
    }

    MediaGalleryFragment fragment    = new MediaGalleryFragment();
    FragmentTransaction  transaction = getSupportFragmentManager().beginTransaction()
                                                                  .replace(R.id.fragment_container, fragment);

    if (isCameraFirst()) {
      transaction.addToBackStack(null);
    }

    transaction.commit();
  }

  @Override
  public void onCameraCountButtonClicked() {
    throw new UnsupportedOperationException("Cannot select more than one photo");
  }

  @Override
  public @NonNull Flowable<Optional<Media>> getMostRecentMediaItem() {
    return Flowable.just(Optional.empty());
  }

  @Override
  public @NonNull MediaConstraints getMediaConstraints() {
    return MediaConstraints.getPushMediaConstraints();
  }

  @Override
  public int getMaxVideoDuration() {
    return -1;
  }

  @Override
  public void onTouchEventsNeeded(boolean needed) {
  }

  @Override
  public void onRequestFullScreen(boolean fullScreen, boolean hideKeyboard) {
  }

  @Override
  public void onMediaSelected(@NonNull Media media) {
    currentMedia = media;

    getSupportFragmentManager().beginTransaction()
                               .replace(R.id.fragment_container, ImageEditorFragment.newInstanceForAvatarCapture(media.getUri()), IMAGE_EDITOR)
                               .addToBackStack(IMAGE_EDITOR)
                               .commit();
  }

  @Override
  public void onDoneEditing() {
    handleSave();
  }

  @Override
  public void onCancelEditing() {
    finish();
  }

  @Override
  public void onMainImageLoaded() {
  }

  @Override
  public void onMainImageFailedToLoad() {
  }

  @Override
  public void restoreState() {
  }

  @Override
  public void onQrCodeFound(@NonNull String data) {
  }

  public boolean popToRoot() {
    final int backStackCount = getSupportFragmentManager().getBackStackEntryCount();
    if (backStackCount == 0) {
      return false;
    }

    for (int i = 0; i < backStackCount; i++) {
      getSupportFragmentManager().popBackStack();
    }

    return true;
  }

  private boolean isGalleryFirst() {
    return getIntent().getBooleanExtra(ARG_GALLERY, false);
  }

  private boolean isCameraFirst() {
    return !isGalleryFirst();
  }

  private void handleSave() {
    ImageEditorFragment fragment = (ImageEditorFragment) getSupportFragmentManager().findFragmentByTag(IMAGE_EDITOR);
    if (fragment == null) {
      throw new AssertionError();
    }

    ImageEditorFragment.Data data  = (ImageEditorFragment.Data) fragment.saveState();

    EditorModel model = data.readModel();
    if (model == null) {
      throw new AssertionError();
    }

    MediaRepository.transformMedia(this,
                                   Collections.singletonList(currentMedia),
                                   Collections.singletonMap(currentMedia, new ImageEditorModelRenderMediaTransform(model, AVATAR_DIMENSIONS)),
                                   output -> {
                                                Media transformed = output.get(currentMedia);

                                                Intent result = new Intent();
                                                result.putExtra(EXTRA_MEDIA, transformed);
                                                setResult(RESULT_OK, result);
                                                finish();
                                              });
  }

  @Override
  public boolean isMultiselectEnabled() {
    return false;
  }

  @Override
  public void onMediaUnselected(@NonNull Media media) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void onSelectedMediaClicked(@NonNull Media media) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void onNavigateToCamera() {
    if (isCameraFirst() && popToRoot()) {
      return;
    }

    Fragment            fragment    = CameraFragment.newInstanceForAvatarCapture();
    FragmentTransaction transaction = getSupportFragmentManager().beginTransaction()
                                                                 .replace(R.id.fragment_container, fragment, IMAGE_CAPTURE);

    if (isGalleryFirst()) {
      transaction.addToBackStack(null);
    }

    transaction.commit();
  }

  @Override
  public void onSubmit() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void onToolbarNavigationClicked() {
    finish();
  }

  @Override
  public boolean isCameraEnabled() {
    return true;
  }
}
