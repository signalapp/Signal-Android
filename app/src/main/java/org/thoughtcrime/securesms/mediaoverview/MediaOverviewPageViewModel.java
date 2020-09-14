package org.thoughtcrime.securesms.mediaoverview;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.thoughtcrime.securesms.database.MediaDatabase.Sorting;
import org.thoughtcrime.securesms.database.loaders.MediaLoader;

public class MediaOverviewPageViewModel extends ViewModel {
  private final MutableLiveData<ActionModeTitleData> actionModeTitleData = new MutableLiveData<>(
      new ActionModeTitleData(0, 0, false));

  public MutableLiveData<ActionModeTitleData> getActionModeTitleData() {
    return actionModeTitleData;
  }

  public void updateActionModeTitle(int mediaCount, long selectedMediaSize, boolean isDetailLayout,
                                    Sorting sortOrder, MediaLoader.MediaType mediaType)
  {
    boolean shouldShowTotalFileSize = isDetailLayout                             ||
                                      mediaType != MediaLoader.MediaType.GALLERY ||
                                      sortOrder   == Sorting.Largest;
    actionModeTitleData.setValue(new ActionModeTitleData(mediaCount, selectedMediaSize, shouldShowTotalFileSize));
  }
}
