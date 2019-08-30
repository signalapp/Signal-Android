package org.thoughtcrime.securesms.scribbles;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.emoji.MediaKeyboard;
import org.thoughtcrime.securesms.components.emoji.MediaKeyboardProvider;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.model.StickerRecord;
import org.thoughtcrime.securesms.stickers.StickerKeyboardProvider;
import org.thoughtcrime.securesms.stickers.StickerManagementActivity;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;

public final class NewStickerSelectActivity extends FragmentActivity {

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                         WindowManager.LayoutParams.FLAG_FULLSCREEN);

    setContentView(R.layout.scribble_select_new_sticker_activity);

    MediaKeyboard mediaKeyboard = findViewById(R.id.emoji_drawer);

    mediaKeyboard.setProviders(0, new StickerKeyboardProvider(this, new StickerKeyboardProvider.StickerEventListener() {
      @Override
      public void onStickerSelected(@NonNull StickerRecord sticker) {
        Intent intent = new Intent();
        intent.setData(sticker.getUri());
        setResult(RESULT_OK, intent);

        SignalExecutors.BOUNDED.execute(() ->
         DatabaseFactory.getStickerDatabase(getApplicationContext())
                        .updateStickerLastUsedTime(sticker.getRowId(), System.currentTimeMillis())
        );

        finish();
      }

      @Override
      public void onStickerManagementClicked() {
        startActivity(StickerManagementActivity.getIntent(NewStickerSelectActivity.this));
      }
    }
    ));

    mediaKeyboard.setKeyboardListener(new MediaKeyboard.MediaKeyboardListener() {
      @Override
      public void onShown() {
      }

      @Override
      public void onHidden() {
        finish();
      }

      @Override
      public void onKeyboardProviderChanged(@NonNull MediaKeyboardProvider provider) {
      }
    });

    mediaKeyboard.show();
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
