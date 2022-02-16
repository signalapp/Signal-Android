package org.thoughtcrime.securesms.scribbles;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.ViewModelProviders;

import org.signal.core.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.emoji.MediaKeyboard;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.StickerRecord;
import org.thoughtcrime.securesms.keyboard.KeyboardPage;
import org.thoughtcrime.securesms.keyboard.KeyboardPagerViewModel;
import org.thoughtcrime.securesms.keyboard.sticker.StickerKeyboardPageFragment;
import org.thoughtcrime.securesms.keyboard.sticker.StickerSearchDialogFragment;
import org.thoughtcrime.securesms.stickers.StickerEventListener;
import org.thoughtcrime.securesms.stickers.StickerManagementActivity;
import org.thoughtcrime.securesms.util.ViewUtil;

public final class ImageEditorStickerSelectActivity extends AppCompatActivity implements StickerEventListener, MediaKeyboard.MediaKeyboardListener, StickerKeyboardPageFragment.Callback {

  @Override
  protected void attachBaseContext(@NonNull Context newBase) {
    getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_YES);
    super.attachBaseContext(newBase);
  }

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.scribble_select_new_sticker_activity);

    KeyboardPagerViewModel keyboardPagerViewModel = ViewModelProviders.of(this).get(KeyboardPagerViewModel.class);
    keyboardPagerViewModel.setOnlyPage(KeyboardPage.STICKER);

    MediaKeyboard mediaKeyboard = findViewById(R.id.emoji_drawer);
    mediaKeyboard.show();
  }

  @Override
  public void onShown() {
  }

  @Override
  public void onHidden() {
    finish();
  }

  @Override
  public void onKeyboardChanged(@NonNull KeyboardPage page) {
  }

  @Override
  public void onStickerSelected(@NonNull StickerRecord sticker) {
    Intent intent = new Intent();
    intent.setData(sticker.getUri());
    setResult(RESULT_OK, intent);

    SignalExecutors.BOUNDED.execute(() -> SignalDatabase.stickers().updateStickerLastUsedTime(sticker.getRowId(), System.currentTimeMillis()));
    ViewUtil.hideKeyboard(this, findViewById(android.R.id.content));
    finish();
  }

  @Override
  public void onStickerManagementClicked() {
    startActivity(StickerManagementActivity.getIntent(ImageEditorStickerSelectActivity.this));
  }


  @Override
  public void openStickerSearch() {
    StickerSearchDialogFragment.show(getSupportFragmentManager());
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      onBackPressed();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }
}
