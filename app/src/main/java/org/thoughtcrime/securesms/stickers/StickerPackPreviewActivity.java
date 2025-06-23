package org.thoughtcrime.securesms.stickers;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Point;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.util.Pair;
import org.thoughtcrime.securesms.PassphraseRequiredActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragment;
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragmentArgs;
import org.thoughtcrime.securesms.glide.cache.ApngOptions;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader;
import org.thoughtcrime.securesms.sharing.MultiShareArgs;
import org.thoughtcrime.securesms.stickers.StickerManifest.Sticker;
import org.thoughtcrime.securesms.util.DeviceProperties;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.whispersystems.signalservice.api.util.OptionalUtil;

import java.util.Collections;
import java.util.Optional;


/**
 * Shows the contents of a pack and allows the user to install it (if not installed) or remove it
 * (if installed). This is also the handler for sticker pack deep links.
 */
public final class StickerPackPreviewActivity extends PassphraseRequiredActivity
    implements StickerRolloverTouchListener.RolloverEventListener,
               StickerRolloverTouchListener.RolloverStickerRetriever,
               StickerPackPreviewAdapter.EventListener
{

  private static final String TAG = Log.tag(StickerPackPreviewActivity.class);

  private final DynamicTheme dynamicTheme = new DynamicNoActionBarTheme();

  private StickerPackPreviewViewModel viewModel;

  private ImageView    coverImage;
  private TextView     stickerTitle;
  private TextView     stickerAuthor;
  private View         installButton;
  private View         removeButton;
  private RecyclerView stickerList;
  private View         shareButton;
  private View         shareButtonImage;

  private StickerPackPreviewAdapter    adapter;
  private GridLayoutManager            layoutManager;
  private StickerRolloverTouchListener touchListener;

  public static Intent getIntent(@NonNull String packId, @NonNull String packKey) {
    Intent intent = new Intent(Intent.ACTION_VIEW, StickerUrl.createActionUri(packId, packKey));
    intent.addCategory(Intent.CATEGORY_DEFAULT);
    intent.addCategory(Intent.CATEGORY_BROWSABLE);
    return intent;
  }

  @Override
  protected void onPreCreate() {
    super.onPreCreate();
    dynamicTheme.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState, boolean ready) {
    setContentView(R.layout.sticker_preview_activity);

    Optional<Pair<String, String>> stickerParams = StickerUrl.parseExternalUri(getIntent().getData());

    if (!stickerParams.isPresent()) {
      Log.w(TAG, "Invalid URI!");
      presentError();
      return;
    }

    String packId  = stickerParams.get().first();
    String packKey = stickerParams.get().second();

    initToolbar();
    initView();
    initViewModel(packId, packKey);

    getSupportFragmentManager().setFragmentResultListener(MultiselectForwardFragment.RESULT_KEY, this, (requestKey, result) -> {
      if (result.getBoolean(MultiselectForwardFragment.RESULT_SENT, false)) {
        finish();
      }
    });
  }

  @Override
  protected void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    onScreenWidthChanged(getScreenWidth());
  }

  @Override
  public void onStickerLongPress(@NonNull View view) {
    if (touchListener != null) {
      touchListener.enterHoverMode(stickerList, view);
    }
  }

  @Override
  public void onStickerPopupStarted() {
  }

  @Override
  public void onStickerPopupEnded() {
  }

  @Override
  public @Nullable Pair<Object, String> getStickerDataFromView(@NonNull View view) {
    if (stickerList != null) {
      StickerPackPreviewAdapter.StickerViewHolder holder = (StickerPackPreviewAdapter.StickerViewHolder) stickerList.getChildViewHolder(view);
      if (holder != null) {
        return new Pair<>(holder.getCurrentGlideModel(), holder.getCurrentEmoji());
      }
    }
    return null;
  }

  private void initView() {
    this.coverImage       = findViewById(R.id.sticker_install_cover);
    this.stickerTitle     = findViewById(R.id.sticker_install_title);
    this.stickerAuthor    = findViewById(R.id.sticker_install_author);
    this.installButton    = findViewById(R.id.sticker_install_button);
    this.removeButton     = findViewById(R.id.sticker_install_remove_button);
    this.stickerList      = findViewById(R.id.sticker_install_list);
    this.shareButton      = findViewById(R.id.sticker_install_share_button);
    this.shareButtonImage = findViewById(R.id.sticker_install_share_button_image);

    this.adapter       = new StickerPackPreviewAdapter(Glide.with(this), this, DeviceProperties.shouldAllowApngStickerAnimation(this));
    this.layoutManager = new GridLayoutManager(this, 2);
    this.touchListener = new StickerRolloverTouchListener(this, Glide.with(this), this, this);
    onScreenWidthChanged(getScreenWidth());

    stickerList.setLayoutManager(layoutManager);
    stickerList.addOnItemTouchListener(touchListener);
    stickerList.setAdapter(adapter);
  }

  private void initToolbar() {
    Toolbar toolbar = findViewById(R.id.sticker_install_toolbar);

    setSupportActionBar(toolbar);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setTitle(R.string.StickerPackPreviewActivity_stickers);

    toolbar.setNavigationOnClickListener(v -> onBackPressed());
  }

  private void initViewModel(@NonNull String packId, @NonNull String packKey) {
    viewModel = new ViewModelProvider(this, new StickerPackPreviewViewModel.Factory(getApplication(),
                                                                                    new StickerPackPreviewRepository(),
                                                                                    StickerManagementRepository.INSTANCE)
    ).get(StickerPackPreviewViewModel.class);

    viewModel.getStickerManifest(packId, packKey).observe(this, manifest -> {
      if (manifest == null) return;

      if (manifest.isPresent()) {
        presentManifest(manifest.get().getManifest());
        presentButton(manifest.get().isInstalled());
        presentShareButton(manifest.get().isInstalled(), manifest.get().getManifest().getPackId(), manifest.get().getManifest().getPackKey());
      } else {
        presentError();
      }
    });
  }

  private void presentManifest(@NonNull StickerManifest manifest) {
    stickerTitle.setText(manifest.getTitle().orElse(getString(R.string.StickerPackPreviewActivity_untitled)));
    stickerAuthor.setText(manifest.getAuthor().orElse(getString(R.string.StickerPackPreviewActivity_unknown)));
    adapter.setStickers(manifest.getStickers());

    Sticker first = manifest.getStickers().isEmpty() ? null : manifest.getStickers().get(0);
    Sticker cover = OptionalUtil.or(manifest.getCover(), Optional.ofNullable(first)).orElse(null);

    if (cover != null) {
      Object model = cover.getUri().isPresent() ? new DecryptableStreamUriLoader.DecryptableUri(cover.getUri().get())
                                                : new StickerRemoteUri(cover.getPackId(), cover.getPackKey(), cover.getId());
      Glide.with(this).load(model)
              .transition(DrawableTransitionOptions.withCrossFade())
              .fitCenter()
              .set(ApngOptions.ANIMATE, DeviceProperties.shouldAllowApngStickerAnimation(this))
              .into(coverImage);
    } else {
      coverImage.setImageDrawable(null);
    }
  }

  private void presentButton(boolean installed) {
    if (installed) {
      removeButton.setVisibility(View.VISIBLE);
      removeButton.setOnClickListener(v -> {
        viewModel.onRemoveClicked();
        finish();
      });
      installButton.setVisibility(View.GONE);
      installButton.setOnClickListener(null);
    } else {
      installButton.setVisibility(View.VISIBLE);
      installButton.setOnClickListener(v -> {
        viewModel.onInstallClicked();
        finish();
      });
      removeButton.setVisibility(View.GONE);
      removeButton.setOnClickListener(null);
    }
  }

  private void presentShareButton(boolean installed, @NonNull String packId, @NonNull String packKey) {
    if (installed) {
      shareButton.setVisibility(View.VISIBLE);
      shareButtonImage.setVisibility(View.VISIBLE);
      shareButton.setOnClickListener(v -> {
        MultiselectForwardFragment.showBottomSheet(
            getSupportFragmentManager(),
            new MultiselectForwardFragmentArgs(
                Collections.singletonList(new MultiShareArgs.Builder()
                                              .withDraftText(StickerUrl.createShareLink(packId, packKey))
                                              .build()),
                R.string.MultiselectForwardFragment__share_with
            )
        );
      });
    } else {
      shareButton.setVisibility(View.GONE);
      shareButtonImage.setVisibility(View.GONE);
      shareButton.setOnClickListener(null);
    }
  }

  private void presentError() {
    Toast.makeText(this, R.string.StickerPackPreviewActivity_failed_to_load_sticker_pack, Toast.LENGTH_SHORT).show();
    finish();
  }

  private void onScreenWidthChanged(int screenWidth) {
    if (layoutManager != null) {
      int availableWidth = screenWidth - (2 * getResources().getDimensionPixelOffset(R.dimen.sticker_preview_gutter_size));
      layoutManager.setSpanCount(availableWidth / getResources().getDimensionPixelOffset(R.dimen.sticker_preview_sticker_size));
    }
  }

  private int getScreenWidth() {
    Point size = new Point();
    getWindowManager().getDefaultDisplay().getSize(size);
    return size.x;
  }
}
