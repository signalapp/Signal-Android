package org.thoughtcrime.securesms.sharing;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter;

public class ShareSelectionAdapter extends MappingAdapter {
  public ShareSelectionAdapter() {
    registerFactory(ShareSelectionMappingModel.class,
                    ShareSelectionViewHolder.createFactory(R.layout.share_contact_selection_item));
  }
}
