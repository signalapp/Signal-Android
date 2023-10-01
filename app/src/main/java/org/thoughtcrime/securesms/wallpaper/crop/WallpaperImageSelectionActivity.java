package org.thoughtcrime.securesms.wallpaper.crop;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.mediasend.Media;
import org.thoughtcrime.securesms.mediasend.v2.gallery.MediaGalleryFragment;
import org.thoughtcrime.securesms.recipients.RecipientId;

public final class WallpaperImageSelectionActivity extends AppCompatActivity
        implements MediaGalleryFragment.Callbacks
{
  private static final String EXTRA_RECIPIENT_ID = "RECIPIENT_ID";
  private static final int    CROP               = 901;

  public static Intent getIntent(@NonNull Context context,
                                 @Nullable RecipientId recipientId)
  {
    Intent intent = new Intent(context, WallpaperImageSelectionActivity.class);
    intent.putExtra(EXTRA_RECIPIENT_ID, recipientId);
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

    setContentView(R.layout.wallpaper_image_selection_activity);

    getSupportFragmentManager().beginTransaction()
                               .replace(R.id.fragment_container, new MediaGalleryFragment())
                               .commit();
  }

  @Override
  public void onMediaSelected(@NonNull Media media) {
    startActivityForResult(WallpaperCropActivity.newIntent(this, getRecipientId(), media.getUri()), CROP);
  }

  private RecipientId getRecipientId() {
    return getIntent().getParcelableExtra(EXTRA_RECIPIENT_ID);
  }

  @Override protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (requestCode == CROP && resultCode == RESULT_OK) {
      setResult(RESULT_OK, data);
      finish();
    }
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
    throw new UnsupportedOperationException();
  }

  @Override
  public void onSubmit() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void onToolbarNavigationClicked() {
    setResult(RESULT_CANCELED);
    finish();
  }

  @Override
  public boolean isCameraEnabled() {
    return false;
  }
}
