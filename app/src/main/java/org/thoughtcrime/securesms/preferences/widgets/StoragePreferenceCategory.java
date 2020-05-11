package org.thoughtcrime.securesms.preferences.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceViewHolder;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.Util;

public final class StoragePreferenceCategory extends PreferenceCategory {

  private Runnable         onFreeUpSpace;
  private TextView         totalSize;
  private StorageGraphView storageGraphView;
  private StorageGraphView.StorageBreakdown storage;

  public StoragePreferenceCategory(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    initialize();
  }

  public StoragePreferenceCategory(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  public StoragePreferenceCategory(Context context) {
    super(context);
    initialize();
  }

  private void initialize() {
    setLayoutResource(R.layout.preference_storage_category);
  }

  @Override
  public void onBindViewHolder(PreferenceViewHolder view) {
    super.onBindViewHolder(view);

    totalSize        = (TextView) view.findViewById(R.id.total_size);
    storageGraphView = (StorageGraphView) view.findViewById(R.id.storageGraphView);

    view.findViewById(R.id.free_up_space)
      .setOnClickListener(v -> {
        if (onFreeUpSpace != null) {
          onFreeUpSpace.run();
        }
      });

    totalSize.setText(Util.getPrettyFileSize(0));

    if (storage != null) {
      setStorage(storage);
    }
  }

  public void setOnFreeUpSpace(Runnable onFreeUpSpace) {
    this.onFreeUpSpace = onFreeUpSpace;
  }

  public void setStorage(StorageGraphView.StorageBreakdown storage) {
    this.storage = storage;
    if (totalSize != null) {
      totalSize.setText(Util.getPrettyFileSize(storage.getTotalSize()));
    }
    if (storageGraphView != null) {
      storageGraphView.setStorageBreakdown(storage);
    }
  }
}
