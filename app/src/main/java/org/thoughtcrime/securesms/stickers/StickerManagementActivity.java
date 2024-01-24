package org.thoughtcrime.securesms.stickers;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import org.thoughtcrime.securesms.PassphraseRequiredActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragment;
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragmentArgs;
import org.thoughtcrime.securesms.sharing.MultiShareArgs;
import org.thoughtcrime.securesms.util.DeviceProperties;
import org.thoughtcrime.securesms.util.DynamicTheme;

import java.util.Collections;

/**
 * Allows the user to view and manage (install, uninstall, etc) their stickers.
 */
public final class StickerManagementActivity extends PassphraseRequiredActivity implements StickerManagementAdapter.EventListener {

  private final DynamicTheme dynamicTheme = new DynamicTheme();

  private RecyclerView               list;
  private StickerManagementAdapter   adapter;
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

    getSupportFragmentManager().setFragmentResultListener(MultiselectForwardFragment.RESULT_KEY, this, (requestKey, result) -> {
      if (result.getBoolean(MultiselectForwardFragment.RESULT_SENT, false)) {
        finish();
      }
    });
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
    MultiselectForwardFragment.showBottomSheet(
        getSupportFragmentManager(),
        new MultiselectForwardFragmentArgs(
            true,
            Collections.singletonList(new MultiShareArgs.Builder()
                                          .withDraftText(StickerUrl.createShareLink(packId, packKey))
                                          .build()),
            R.string.MultiselectForwardFragment__share_with
        )
    );
  }

  private void initView() {
    this.list    = findViewById(R.id.sticker_management_list);
    this.adapter = new StickerManagementAdapter(Glide.with(this), this, DeviceProperties.shouldAllowApngStickerAnimation(this));

    list.setLayoutManager(new LinearLayoutManager(this));
    list.setAdapter(adapter);
    new ItemTouchHelper(new StickerManagementItemTouchHelper(new ItemTouchCallback())).attachToRecyclerView(list);
  }

  private void initToolbar() {
    getSupportActionBar().setTitle(R.string.StickerManagementActivity_stickers);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
  }

  private void initViewModel() {
    StickerManagementRepository repository = new StickerManagementRepository(this);
    viewModel = new ViewModelProvider(this, new StickerManagementViewModel.Factory(getApplication(), repository)).get(StickerManagementViewModel.class);

    viewModel.init();
    viewModel.getStickerPacks().observe(this, packResult -> {
      if (packResult == null) return;

      adapter.setPackLists(packResult.getInstalledPacks(), packResult.getAvailablePacks(), packResult.getBlessedPacks());
    });
  }

  private class ItemTouchCallback implements StickerManagementItemTouchHelper.Callback {
    @Override
    public boolean onMove(int start, int end) {
      return adapter.onMove(start, end);
    }

    @Override
    public boolean isMovable(int position) {
      return adapter.isMovable(position);
    }

    @Override
    public void onMoveCommitted() {
      viewModel.onOrderChanged(adapter.getInstalledPacksInOrder());
    }
  }
}
