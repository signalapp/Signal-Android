package org.thoughtcrime.securesms.stickers;

import android.app.Application;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.database.DatabaseObserver;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.stickers.StickerPackPreviewRepository.StickerManifestResult;

import java.util.Optional;


final class StickerPackPreviewViewModel extends ViewModel {

  private final Application                                      application;
  private final StickerPackPreviewRepository                     previewRepository;
  private final StickerManagementRepository                      managementRepository;
  private final MutableLiveData<Optional<StickerManifestResult>> stickerManifest;
  private final DatabaseObserver.Observer                        packObserver;

  private String packId;
  private String packKey;

  private StickerPackPreviewViewModel(@NonNull Application application,
                                      @NonNull StickerPackPreviewRepository previewRepository,
                                      @NonNull StickerManagementRepository  managementRepository)
  {
    this.application          = application;
    this.previewRepository    = previewRepository;
    this.managementRepository = managementRepository;
    this.stickerManifest      = new MutableLiveData<>();
    this.packObserver         = () -> {
      if (!TextUtils.isEmpty(packId) && !TextUtils.isEmpty(packKey)) {
        previewRepository.getStickerManifest(packId, packKey, stickerManifest::postValue);
      }
    };

    AppDependencies.getDatabaseObserver().registerStickerPackObserver(packObserver);
  }

  LiveData<Optional<StickerManifestResult>> getStickerManifest(@NonNull String packId, @NonNull String packKey) {
    this.packId  = packId;
    this.packKey = packKey;

    previewRepository.getStickerManifest(packId, packKey, stickerManifest::postValue);

    return stickerManifest;
  }

  void onInstallClicked() {
    managementRepository.installStickerPack(packId, packKey, true);
  }

  void onRemoveClicked() {
    managementRepository.uninstallStickerPack(packId, packKey);
  }

  @Override
  protected void onCleared() {
    AppDependencies.getDatabaseObserver().unregisterObserver(packObserver);
  }

  static class Factory extends ViewModelProvider.NewInstanceFactory {
    private final Application                  application;
    private final StickerPackPreviewRepository previewRepository;
    private final StickerManagementRepository  managementRepository;

    Factory(@NonNull Application application,
            @NonNull StickerPackPreviewRepository previewRepository,
            @NonNull StickerManagementRepository managementRepository)
    {
      this.application          = application;
      this.previewRepository    = previewRepository;
      this.managementRepository = managementRepository;
    }

    @Override
    public @NonNull<T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection ConstantConditions
      return modelClass.cast(new StickerPackPreviewViewModel(application, previewRepository, managementRepository));
    }
  }
}
