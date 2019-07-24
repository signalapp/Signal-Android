package org.thoughtcrime.securesms.stickers;

import androidx.lifecycle.ViewModelProviders;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.Toolbar;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.ShareActivity;
import org.thoughtcrime.securesms.jobs.StickerPackDownloadJob;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.stickers.StickerManifest.Sticker;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.ThemeUtil;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;

/**
 * Shows the contents of a pack and allows the user to install it (if not installed) or remove it
 * (if installed). This is also the handler for sticker pack deep links.
 */
public final class StickerPackPreviewActivity extends PassphraseRequiredActionBarActivity {

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

  private StickerPackPreviewAdapter adapter;
  private GridLayoutManager         layoutManager;

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

    Optional<Pair<String, String>> stickerParams = StickerUrl.parseActionUri(getIntent().getData());

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

  private void initView() {
    this.coverImage       = findViewById(R.id.sticker_install_cover);
    this.stickerTitle     = findViewById(R.id.sticker_install_title);
    this.stickerAuthor    = findViewById(R.id.sticker_install_author);
    this.installButton    = findViewById(R.id.sticker_install_button);
    this.removeButton     = findViewById(R.id.sticker_install_remove_button);
    this.stickerList      = findViewById(R.id.sticker_install_list);
    this.shareButton      = findViewById(R.id.sticker_install_share_button);
    this.shareButtonImage = findViewById(R.id.sticker_install_share_button_image);

    this.adapter       = new StickerPackPreviewAdapter(GlideApp.with(this));
    this.layoutManager = new GridLayoutManager(this, 2);
    onScreenWidthChanged(getScreenWidth());

    stickerList.setLayoutManager(layoutManager);
    stickerList.setAdapter(adapter);
  }

  private void initToolbar() {
    Toolbar toolbar = findViewById(R.id.sticker_install_toolbar);

    setSupportActionBar(toolbar);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setTitle(R.string.StickerPackPreviewActivity_stickers);

    toolbar.setNavigationOnClickListener(v -> onBackPressed());

    if (!ThemeUtil.isDarkTheme(this) && Build.VERSION.SDK_INT >= 23) {
      setStatusBarColor(ThemeUtil.getThemedColor(this, R.attr.sticker_preview_status_bar_color));
      getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    }
  }

  private void initViewModel(@NonNull String packId, @NonNull String packKey) {
    viewModel = ViewModelProviders.of(this, new StickerPackPreviewViewModel.Factory(getApplication(),
                                                                                    new StickerPackPreviewRepository(this),
                                                                                    new StickerManagementRepository(this)))
                                  .get(StickerPackPreviewViewModel.class);

    viewModel.getStickerManifest(packId, packKey).observe(this, manifest -> {
      if (manifest == null) return;

      if (manifest.isPresent()) {
        presentManifest(manifest.get().getManifest());
        presentButton(manifest.get().isInstalled());
        // TODO [Stickers]: Re-enable later
//        presentShareButton(manifest.get().isInstalled(), manifest.get().getManifest().getPackId(), manifest.get().getManifest().getPackKey());
      } else {
        presentError();
      }
    });
  }

  private void presentManifest(@NonNull StickerManifest manifest) {
    stickerTitle.setText(manifest.getTitle().or(getString(R.string.StickerPackPreviewActivity_untitled)));
    stickerAuthor.setText(manifest.getAuthor().or(getString(R.string.StickerPackPreviewActivity_unknown)));
    adapter.setStickers(manifest.getStickers());

    installButton.setOnClickListener(v -> {
      SimpleTask.run(() -> {
        ApplicationContext.getInstance(this)
                          .getJobManager()
                          .add(new StickerPackDownloadJob(manifest.getPackId(), manifest.getPackKey(), false));

        return null;
      }, (nothing) -> finish());
    });

    Sticker first = manifest.getStickers().isEmpty() ? null : manifest.getStickers().get(0);
    Sticker cover = manifest.getCover().or(Optional.fromNullable(first)).orNull();

    if (cover != null) {
      Object  model = cover.getUri().isPresent() ? new DecryptableStreamUriLoader.DecryptableUri(cover.getUri().get())
                                                 : new StickerRemoteUri(cover.getPackId(), cover.getPackKey(), cover.getId());
      GlideApp.with(this).load(model)
                         .transition(DrawableTransitionOptions.withCrossFade())
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
        Intent composeIntent = new Intent(this, ShareActivity.class);
        composeIntent.putExtra(Intent.EXTRA_TEXT, StickerUrl.createShareLink(packId, packKey));
        startActivity(composeIntent);
        finish();
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

  private void onScreenWidthChanged(int newWidth) {
    if (layoutManager != null) {
      layoutManager.setSpanCount(newWidth / getResources().getDimensionPixelOffset(R.dimen.sticker_preview_sticker_size));
    }
  }

  private int getScreenWidth() {
    Point size = new Point();
    getWindowManager().getDefaultDisplay().getSize(size);
    return size.x;
  }
}
