package org.thoughtcrime.securesms.wallpaper.crop;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.mediasend.Media;
import org.thoughtcrime.securesms.mediasend.MediaFolder;
import org.thoughtcrime.securesms.mediasend.MediaPickerFolderFragment;
import org.thoughtcrime.securesms.mediasend.MediaPickerItemFragment;
import org.thoughtcrime.securesms.recipients.RecipientId;

public final class WallpaperImageSelectionActivity extends AppCompatActivity
        implements MediaPickerFolderFragment.Controller,
        MediaPickerItemFragment.Controller
{
  private static final String EXTRA_RECIPIENT_ID = "RECIPIENT_ID";
  private static final int    CROP               = 901;

  @RequiresPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
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
    setContentView(R.layout.wallpaper_image_selection_activity);

    getSupportFragmentManager().beginTransaction()
                               .replace(R.id.fragment_container, MediaPickerFolderFragment.newInstance(getString(R.string.WallpaperImageSelectionActivity__choose_wallpaper_image), true))
                               .commit();
  }

  @Override
  public void onFolderSelected(@NonNull MediaFolder folder) {
    getSupportFragmentManager().beginTransaction()
                               .replace(R.id.fragment_container, MediaPickerItemFragment.newInstance(folder.getBucketId(), folder.getTitle(), 1, false, true))
                               .addToBackStack(null)
                               .commit();
  }

  @Override
  public void onCameraSelected() {
    throw new AssertionError("Unexpected, Camera disabled");
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
}
