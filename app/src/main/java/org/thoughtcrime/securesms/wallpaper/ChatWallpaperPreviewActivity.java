package org.thoughtcrime.securesms.wallpaper;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.viewpager2.widget.ViewPager2;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.PassphraseRequiredActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.conversation.colors.ChatColors;
import org.thoughtcrime.securesms.conversation.colors.ColorizerView;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.FullscreenHelper;
import org.thoughtcrime.securesms.util.Projection;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.WindowUtil;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel;

import java.util.Collections;

public class ChatWallpaperPreviewActivity extends PassphraseRequiredActivity {

  public  static final String EXTRA_CHAT_WALLPAPER   = "extra.chat.wallpaper";
  private static final String EXTRA_DIM_IN_DARK_MODE = "extra.dim.in.dark.mode";
  private static final String EXTRA_RECIPIENT_ID     = "extra.recipient.id";

  private final DynamicTheme dynamicTheme = new DynamicNoActionBarTheme();

  private ChatWallpaperPreviewAdapter adapter;
  private ColorizerView               colorizerView;
  private View                        bubble2;
  private OnPageChanged               onPageChanged;
  private ViewPager2                  viewPager;

  public static @NonNull Intent create(@NonNull Context context, @NonNull ChatWallpaper selection, @NonNull RecipientId recipientId, boolean dimInDarkMode) {
    Intent intent = new Intent(context, ChatWallpaperPreviewActivity.class);

    intent.putExtra(EXTRA_CHAT_WALLPAPER, selection);
    intent.putExtra(EXTRA_DIM_IN_DARK_MODE, dimInDarkMode);
    intent.putExtra(EXTRA_RECIPIENT_ID, recipientId);

    return intent;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState, boolean ready) {
    dynamicTheme.onCreate(this);

    setContentView(R.layout.chat_wallpaper_preview_activity);

    adapter       = new ChatWallpaperPreviewAdapter();
    colorizerView = findViewById(R.id.colorizer);
    bubble2       = findViewById(R.id.preview_bubble_2);
    viewPager     = findViewById(R.id.preview_pager);

    View                             submit        = findViewById(R.id.preview_set_wallpaper);
    ChatWallpaperRepository          repository    = new ChatWallpaperRepository();
    ChatWallpaper                    selected      = getIntent().getParcelableExtra(EXTRA_CHAT_WALLPAPER);
    boolean                          dim           = getIntent().getBooleanExtra(EXTRA_DIM_IN_DARK_MODE, false);
    Toolbar                          toolbar       = findViewById(R.id.toolbar);
    TextView                         bubble2Text   = findViewById(R.id.preview_bubble_2_text);

    toolbar.setNavigationOnClickListener(unused -> {
      finish();
    });

    viewPager.setAdapter(adapter);

    adapter.submitList(Collections.singletonList(new ChatWallpaperSelectionMappingModel(selected)));
    repository.getAllWallpaper(wallpapers -> adapter.submitList(Stream.of(wallpapers)
                                                                      .map(wallpaper -> ChatWallpaperFactory.updateWithDimming(wallpaper, dim ? ChatWallpaper.FIXED_DIM_LEVEL_FOR_DARK_THEME : 0f))
                                                                      .<MappingModel<?>>map(ChatWallpaperSelectionMappingModel::new)
                                                                      .toList()));

    submit.setOnClickListener(unused -> {
      ChatWallpaperSelectionMappingModel model = (ChatWallpaperSelectionMappingModel) adapter.getCurrentList().get(viewPager.getCurrentItem());

      setResult(RESULT_OK, new Intent().putExtra(EXTRA_CHAT_WALLPAPER, model.getWallpaper()));
      finish();
    });

    RecipientId recipientId = getIntent().getParcelableExtra(EXTRA_RECIPIENT_ID);

    if (recipientId != null) {
      Recipient.live(recipientId)
               .getLiveDataResolved()
               .observe(this, recipient -> {
                 bubble2Text.setText(getString(R.string.ChatWallpaperPreviewActivity__set_wallpaper_for_s,
                                               recipient.isSelf() ? getString(R.string.note_to_self)
                                                                  : recipient.getDisplayName(this)));
                 addUpdateBubbleColorListeners(recipient.getChatColors(), selected.getAutoChatColors());
               });
    } else {
      addUpdateBubbleColorListeners(SignalStore.chatColors().getChatColors(), selected.getAutoChatColors());
    }

    new FullscreenHelper(this).showSystemUI();
    WindowUtil.setLightStatusBarFromTheme(this);
    WindowUtil.setLightNavigationBarFromTheme(this);
  }

  @Override
  protected void onDestroy() {
    if (onPageChanged != null) {
      viewPager.unregisterOnPageChangeCallback(onPageChanged);
    }

    super.onDestroy();
  }

  private void addUpdateBubbleColorListeners(@Nullable ChatColors chatColors, @NonNull ChatColors selectedWallpaperAutoColors) {
    if (chatColors == null || chatColors.getId().equals(ChatColors.Id.Auto.INSTANCE)) {
      onPageChanged = new OnPageChanged();
      viewPager.registerOnPageChangeCallback(onPageChanged);
      bubble2.addOnLayoutChangeListener(new UpdateChatColorsOnNextLayoutChange(selectedWallpaperAutoColors));
    } else {
      bubble2.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
        updateChatColors(chatColors);
      });
    }
  }

  private class OnPageChanged extends ViewPager2.OnPageChangeCallback {
    @Override
    public void onPageSelected(int position) {
      ChatWallpaperSelectionMappingModel model = (ChatWallpaperSelectionMappingModel) adapter.getCurrentList().get(position);

      updateChatColors(model.getWallpaper().getAutoChatColors());
    }
  }

  private void updateChatColors(@NonNull ChatColors chatColors) {
    Drawable mask = chatColors.getChatBubbleMask();

    colorizerView.setBackground(mask);

    colorizerView.setProjections(
        Collections.singletonList(Projection.relativeToViewWithCommonRoot(bubble2, colorizerView, new Projection.Corners(ViewUtil.dpToPx(18))))
    );

    bubble2.getBackground().setColorFilter(chatColors.getChatBubbleColorFilter());
  }

  private class UpdateChatColorsOnNextLayoutChange implements View.OnLayoutChangeListener {

    private final ChatColors chatColors;

    private UpdateChatColorsOnNextLayoutChange(@NonNull ChatColors chatColors) {
      this.chatColors = chatColors;
    }

    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
      v.removeOnLayoutChangeListener(this);
      updateChatColors(chatColors);
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
  }
}
