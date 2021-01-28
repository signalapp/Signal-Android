package org.thoughtcrime.securesms.sharing;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.MappingAdapter;

class ShareSelectionAdapter extends MappingAdapter {
  ShareSelectionAdapter() {
    registerFactory(ShareSelectionMappingModel.class,
                    ShareSelectionViewHolder.createFactory(R.layout.share_contact_selection_item));
  }
}
