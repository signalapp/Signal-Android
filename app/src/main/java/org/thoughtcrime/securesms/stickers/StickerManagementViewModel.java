package org.thoughtcrime.securesms.stickers;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.database.DatabaseObserver;
import org.thoughtcrime.securesms.database.model.StickerPackRecord;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.stickers.StickerManagementRepository.PackResult;

import java.util.List;

final class StickerManagementViewModel extends ViewModel {

  private final Application                 application;
  private final StickerManagementRepository repository;
  private final MutableLiveData<PackResult> packs;
  private final DatabaseObserver.Observer   observer;

  private StickerManagementViewModel(@NonNull Application application, @NonNull StickerManagementRepository repository) {
    this.application = application;
    this.repository  = repository;
    this.packs       = new MutableLiveData<>();
    this.observer    = () -> {
      repository.deleteOrphanedStickerPacks();
      repository.getStickerPacks(packs::postValue);
    };

    AppDependencies.getDatabaseObserver().registerStickerPackObserver(observer);
  }

  void init() {
    repository.deleteOrphanedStickerPacks();
    repository.fetchUnretrievedReferencePacks();
  }

  void onVisible() {
    repository.deleteOrphanedStickerPacks();
  }

  @NonNull LiveData<PackResult> getStickerPacks() {
    repository.getStickerPacks(packs::postValue);
    return packs;
  }

  void onStickerPackUninstallClicked(@NonNull String packId, @NonNull String packKey) {
    repository.uninstallStickerPack(packId, packKey);
  }

  void onStickerPackInstallClicked(@NonNull String packId, @NonNull String packKey) {
    repository.installStickerPack(packId, packKey, false);
  }

  void onOrderChanged(List<StickerPackRecord> packsInOrder) {
    repository.setPackOrder(packsInOrder);
  }

  @Override
  protected void onCleared() {
    AppDependencies.getDatabaseObserver().unregisterObserver(observer);
  }

  static class Factory extends ViewModelProvider.NewInstanceFactory {

    private final Application                 application;
    private final StickerManagementRepository repository;

    Factory(@NonNull Application application, @NonNull StickerManagementRepository repository) {
      this.application = application;
      this.repository  = repository;
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection ConstantConditions
      return modelClass.cast(new StickerManagementViewModel(application, repository));
    }
  }
}
