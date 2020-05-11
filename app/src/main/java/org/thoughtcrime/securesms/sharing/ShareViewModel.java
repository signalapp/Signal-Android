package org.thoughtcrime.securesms.sharing;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.List;

public class ShareViewModel extends ViewModel {

  private static final String TAG = Log.tag(ShareViewModel.class);

  private final Context                              context;
  private final ShareRepository                      shareRepository;
  private final MutableLiveData<Optional<ShareData>> shareData;

  private boolean mediaUsed;
  private boolean externalShare;

  private ShareViewModel() {
    this.context         = ApplicationDependencies.getApplication();
    this.shareRepository = new ShareRepository();
    this.shareData       = new MutableLiveData<>();
  }

  void onSingleMediaShared(@NonNull Uri uri, @Nullable String mimeType) {
    externalShare = true;
    shareRepository.getResolved(uri, mimeType, shareData::postValue);
  }

  void onMultipleMediaShared(@NonNull List<Uri> uris) {
    externalShare = true;
    shareRepository.getResolved(uris, shareData::postValue);
  }

  void onNonExternalShare() {
    externalShare = false;
  }

  void onSuccessulShare() {
    mediaUsed = true;
  }

  @NonNull LiveData<Optional<ShareData>> getShareData() {
    return shareData;
  }

  boolean isExternalShare() {
    return externalShare;
  }

  @Override
  protected void onCleared() {
    ShareData data = shareData.getValue() != null ? shareData.getValue().orNull() : null;

    if (data != null && data.isExternal()  && data.isForIntent() && !mediaUsed) {
      Log.i(TAG, "Clearing out unused data.");
      BlobProvider.getInstance().delete(context, data.getUri());
    }
  }

  public static class Factory extends ViewModelProvider.NewInstanceFactory {
    @Override
    public @NonNull<T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection ConstantConditions
      return modelClass.cast(new ShareViewModel());
    }
  }
}
