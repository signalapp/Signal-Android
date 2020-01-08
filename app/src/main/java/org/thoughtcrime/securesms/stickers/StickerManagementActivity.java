package org.thoughtcrime.securesms.stickers;

import androidx.lifecycle.ViewModelProviders;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.MenuItem;

import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.ShareActivity;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.util.DynamicTheme;

/**
 * Allows the user to view and manage (install, uninstall, etc) their stickers.
 */
public final class StickerManagementActivity extends PassphraseRequiredActionBarActivity implements StickerManagementAdapter.EventListener {

  private final DynamicTheme dynamicTheme = new DynamicTheme();

  private RecyclerView             list;
  private StickerManagementAdapter adapter;

  private StickerManagementViewModel viewModel;

  public static Intent getIntent(@NonNull Context context) {
    return new Intent(context, StickerManagementActivity.class);
  }

  @Override
  protected void onPreCreate() {
    super.onPreCreate();
    dynamicTheme.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState, boolean ready) {
    setContentView(R.layout.sticker_management_activity);

    initView();
    initToolbar();
    initViewModel();
  }

  @Override
  protected void onStart() {
    super.onStart();
    viewModel.onVisible();
  }

  @Override
  protected void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      onBackPressed();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  public void onStickerPackClicked(@NonNull String packId, @NonNull String packKey) {
    startActivity(StickerPackPreviewActivity.getIntent(packId, packKey));
  }

  @Override
  public void onStickerPackUninstallClicked(@NonNull String packId, @NonNull String packKey) {
    viewModel.onStickerPackUninstallClicked(packId, packKey);
  }

  @Override
  public void onStickerPackInstallClicked(@NonNull String packId, @NonNull String packKey) {
    viewModel.onStickerPackInstallClicked(packId, packKey);
  }

  @Override
  public void onStickerPackShareClicked(@NonNull String packId, @NonNull String packKey) {
    Intent composeIntent = new Intent(this, ShareActivity.class);
    composeIntent.putExtra(Intent.EXTRA_TEXT, StickerUrl.createShareLink(packId, packKey));
    startActivity(composeIntent);
    finish();
  }

  private void initView() {
    this.list    = findViewById(R.id.sticker_management_list);
    this.adapter = new StickerManagementAdapter(GlideApp.with(this), this);

    list.setLayoutManager(new LinearLayoutManager(this));
    list.setAdapter(adapter);
  }

  private void initToolbar() {
    getSupportActionBar().setTitle(R.string.StickerManagementActivity_stickers);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
  }

  private void initViewModel() {
    StickerManagementRepository repository = new StickerManagementRepository(this);
    viewModel = ViewModelProviders.of(this, new StickerManagementViewModel.Factory(getApplication(), repository)).get(StickerManagementViewModel.class);

    viewModel.init();
    viewModel.getStickerPacks().observe(this, packResult -> {
      if (packResult == null) return;

      adapter.setPackLists(packResult.getInstalledPacks(), packResult.getAvailablePacks(), packResult.getBlessedPacks());
    });
  }
}
