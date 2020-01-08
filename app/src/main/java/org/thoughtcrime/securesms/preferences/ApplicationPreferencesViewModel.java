package org.thoughtcrime.securesms.preferences;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProviders;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MediaDatabase;
import org.thoughtcrime.securesms.preferences.widgets.StorageGraphView;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;

import java.util.Arrays;

public class ApplicationPreferencesViewModel extends ViewModel {

  private final MutableLiveData<StorageGraphView.StorageBreakdown> storageBreakdown = new MutableLiveData<>();

  LiveData<StorageGraphView.StorageBreakdown> getStorageBreakdown() {
    return storageBreakdown;
  }

  static ApplicationPreferencesViewModel getApplicationPreferencesViewModel(@NonNull FragmentActivity activity) {
    return ViewModelProviders.of(activity).get(ApplicationPreferencesViewModel.class);
  }

  void refreshStorageBreakdown(@NonNull Context context) {
    SignalExecutors.BOUNDED.execute(() -> {
      MediaDatabase.StorageBreakdown breakdown = DatabaseFactory.getMediaDatabase(context)
                                                                .getStorageBreakdown();

      StorageGraphView.StorageBreakdown latestStorageBreakdown = new StorageGraphView.StorageBreakdown(Arrays.asList(
        new StorageGraphView.Entry(ContextCompat.getColor(context, R.color.storage_color_photos), breakdown.getPhotoSize()),
        new StorageGraphView.Entry(ContextCompat.getColor(context, R.color.storage_color_videos), breakdown.getVideoSize()),
        new StorageGraphView.Entry(ContextCompat.getColor(context, R.color.storage_color_files), breakdown.getDocumentSize()),
        new StorageGraphView.Entry(ContextCompat.getColor(context, R.color.storage_color_audio), breakdown.getAudioSize())
      ));

      storageBreakdown.postValue(latestStorageBreakdown);
    });
  }
}
