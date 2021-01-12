package org.thoughtcrime.securesms.wallpaper;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProviders;
import androidx.viewpager2.widget.ViewPager2;

import org.thoughtcrime.securesms.PassphraseRequiredActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.FullscreenHelper;

import java.util.Collections;

public class ChatWallpaperPreviewActivity extends PassphraseRequiredActivity {

  private static final String EXTRA_CHAT_WALLPAPER = "extra.chat.wallpaper";
  private static final String EXTRA_RECIPIENT_ID   = "extra.recipient.id";

  private final DynamicTheme dynamicTheme = new DynamicNoActionBarTheme();

  public static @NonNull Intent create(@NonNull Context context, @NonNull ChatWallpaper selection, @Nullable RecipientId recipientId) {
    Intent intent = new Intent(context, ChatWallpaperPreviewActivity.class);

    intent.putExtra(EXTRA_CHAT_WALLPAPER, selection);
    intent.putExtra(EXTRA_RECIPIENT_ID, recipientId);

    return intent;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState, boolean ready) {
    dynamicTheme.onCreate(this);

    setContentView(R.layout.chat_wallpaper_preview_activity);

    ViewPager2                       viewPager = findViewById(R.id.preview_pager);
    ChatWallpaperPreviewAdapter      adapter   = new ChatWallpaperPreviewAdapter();
    View                             submit    = findViewById(R.id.preview_set_wallpaper);
    ChatWallpaperViewModel.Factory   factory   = new ChatWallpaperViewModel.Factory(getIntent().getParcelableExtra(EXTRA_RECIPIENT_ID));
    ChatWallpaperViewModel           viewModel = ViewModelProviders.of(this, factory).get(ChatWallpaperViewModel.class);
    ChatWallpaper                    selected  = getIntent().getParcelableExtra(EXTRA_CHAT_WALLPAPER);
    Toolbar                          toolbar   = findViewById(R.id.toolbar);

    toolbar.setNavigationOnClickListener(unused -> finish());

    viewPager.setAdapter(adapter);

    adapter.submitList(Collections.singletonList(new ChatWallpaperSelectionMappingModel(selected)));
    viewModel.getWallpapers().observe(this, adapter::submitList);

    submit.setOnClickListener(unused -> {
      ChatWallpaperSelectionMappingModel model = (ChatWallpaperSelectionMappingModel) adapter.getCurrentList().get(viewPager.getCurrentItem());

      viewModel.saveWallpaperSelection(model.getWallpaper());
      setResult(RESULT_OK);
      finish();
    });

    new FullscreenHelper(this).showSystemUI();
  }

  @Override
  protected void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
  }
}
