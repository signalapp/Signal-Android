package org.thoughtcrime.securesms.mediaoverview;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.SavedStateViewModelFactory;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.database.MediaTable.Sorting;

public class MediaOverviewViewModel extends ViewModel {

  private final MutableLiveData<Sorting> sortOrder;
  private final MutableLiveData<Boolean> detailLayout;

  public MediaOverviewViewModel(@NonNull SavedStateHandle savedStateHandle) {
    sortOrder    = savedStateHandle.getLiveData("SORT_ORDER", Sorting.Newest);
    detailLayout = savedStateHandle.getLiveData("DETAIL_LAYOUT", false);
  }

  public LiveData<Sorting> getSortOrder() {
    return sortOrder;
  }

  public LiveData<Boolean> getDetailLayout() {
    return detailLayout;
  }

  public void setSortOrder(@NonNull Sorting sortOrder) {
    this.sortOrder.setValue(sortOrder);
  }

  public void setDetailLayout(boolean detailLayout) {
    this.detailLayout.setValue(detailLayout);
  }

  static MediaOverviewViewModel getMediaOverviewViewModel(@NonNull FragmentActivity activity) {
    SavedStateViewModelFactory savedStateViewModelFactory = new SavedStateViewModelFactory(activity.getApplication(), activity);

    return new ViewModelProvider(activity, savedStateViewModelFactory).get(MediaOverviewViewModel.class);
  }
}
