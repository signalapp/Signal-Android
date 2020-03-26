package org.thoughtcrime.securesms.mediasend;

import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProviders;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.TransportOptions;
import org.thoughtcrime.securesms.imageeditor.model.EditorModel;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.scribbles.ImageEditorFragment;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.FileDescriptor;
import java.util.Collections;

public class AvatarSelectionActivity extends AppCompatActivity implements CameraFragment.Controller, ImageEditorFragment.Controller, MediaPickerFolderFragment.Controller, MediaPickerItemFragment.Controller {

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
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.avatar_selection_activity);

    MediaSendViewModel viewModel = ViewModelProviders.of(this, new MediaSendViewModel.Factory(getApplication(), new MediaRepository())).get(MediaSendViewModel.class);
    viewModel.setTransport(TransportOptions.getPushTransportOption(this));

    if (isGalleryFirst()) {
      onGalleryClicked();
    } else {
      onCameraSelected();
    }
  }

  @Override
  public void onCameraError() {
    Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show();
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
                              Optional.of(Media.ALL_MEDIA_BUCKET_ID),
                              Optional.absent(),
                              Optional.absent()));
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

    MediaPickerFolderFragment fragment    = MediaPickerFolderFragment.newInstance(this, null);
    FragmentTransaction       transaction = getSupportFragmentManager().beginTransaction()
                                                                 .replace(R.id.fragment_container, fragment);

    if (isCameraFirst()) {
      transaction.addToBackStack(null);
    }

    transaction.commit();
  }

  @Override
  public int getDisplayRotation() {
    return getWindowManager().getDefaultDisplay().getRotation();
  }

  @Override
  public void onCameraCountButtonClicked() {
    throw new UnsupportedOperationException("Cannot select more than one photo");
  }

  @Override
  public void onTouchEventsNeeded(boolean needed) {
  }

  @Override
  public void onRequestFullScreen(boolean fullScreen, boolean hideKeyboard) {
  }

  @Override
  public void onFolderSelected(@NonNull MediaFolder folder) {
    getSupportFragmentManager().beginTransaction()
                               .replace(R.id.fragment_container, MediaPickerItemFragment.newInstance(folder.getBucketId(), folder.getTitle(), 1, false))
                               .addToBackStack(null)
                               .commit();
  }

  @Override
  public void onMediaSelected(@NonNull Media media) {
    currentMedia = media;

    getSupportFragmentManager().beginTransaction()
                               .replace(R.id.fragment_container, ImageEditorFragment.newInstanceForAvatar(media.getUri()), IMAGE_EDITOR)
                               .addToBackStack(IMAGE_EDITOR)
                               .commit();
  }

  @Override
  public void onCameraSelected() {
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
  public void onDoneEditing() {
    handleSave();
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
}
