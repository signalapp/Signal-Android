package org.thoughtcrime.securesms.groups.ui.creategroup;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.components.FromTextView;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

class ExistingGroupMappingAdapter extends MappingAdapter {

  private final List<GroupModel> groups;
  private final Consumer<GroupModel> onClickListener;

  ExistingGroupMappingAdapter(List<GroupModel> groups, Consumer<GroupModel> onClickListener) {
    this.groups = groups;
    this.onClickListener = onClickListener;
    registerFactory(GroupModel.class,
                    new LayoutFactory<>(GroupViewHolder::new, R.layout.group_selection_item));

    submitList(Collections.unmodifiableList(groups));
  }



  static class GroupModel implements MappingModel<GroupModel> {

    private final Recipient recipient;

    GroupModel(Recipient recipient) {
      this.recipient = recipient;
    }

    public Recipient getRecipient() {
      return recipient;
    }

    @Override public boolean areItemsTheSame(@NonNull GroupModel newItem) {
      return recipient.equals(newItem.recipient);
    }

    @Override public boolean areContentsTheSame(@NonNull GroupModel newItem) {
      return recipient.hasSameContent(newItem.recipient);
    }
  }

  private class GroupViewHolder extends MappingViewHolder<GroupModel> {

    public GroupViewHolder(@NonNull View itemView) {
      super(itemView);
      itemView.setOnClickListener(view -> {
        int pos = getAbsoluteAdapterPosition();
        if (pos != RecyclerView.NO_POSITION) {
          GroupModel model = groups.get(pos);
          onClickListener.accept(model);
        }
      });
    }

    @Override public void bind(@NonNull GroupModel groupModel) {
      FromTextView textView = findViewById(R.id.conversation_list_item_name);
      AvatarImageView imageView = findViewById(R.id.conversation_list_item_avatar);

      textView.setText(groupModel.getRecipient());
      imageView.setAvatar(groupModel.getRecipient());
    }

  }
}
