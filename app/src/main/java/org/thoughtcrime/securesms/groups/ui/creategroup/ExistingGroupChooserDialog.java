package org.thoughtcrime.securesms.groups.ui.creategroup;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.conversation.ConversationIntents;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.groups.ui.creategroup.details.AddGroupDetailsActivity;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.List;
import java.util.stream.Collectors;

public class ExistingGroupChooserDialog extends DialogFragment {

  private final int REQUEST_CODE = 2840;

  private final List<GroupDatabase.GroupRecord> groups;
  private final List<RecipientId>               recipientIds;

  ExistingGroupChooserDialog(List<GroupDatabase.GroupRecord> groups, List<RecipientId> recipientIds) {
    this.groups       = groups;
    this.recipientIds = recipientIds;
  }

  @NonNull @Override public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
    AlertDialog.Builder alertBuilder = new AlertDialog.Builder(getActivity());

    LayoutInflater inflater     = requireActivity().getLayoutInflater();
    View           view         = inflater.inflate(R.layout.choose_existing_group_dialog, null);
    RecyclerView   recyclerView = view.findViewById(R.id.selected_list);
    ExistingGroupMappingAdapter adapter =
        new ExistingGroupMappingAdapter(groups.stream()
                                              .map(this::createModel)
                                              .collect(Collectors.toList()),
                                        this::onClick);
    recyclerView.setAdapter(adapter);

    view.findViewById(R.id.cancel_button).setOnClickListener(view1 -> {
      ExistingGroupChooserDialog.this.getDialog().cancel();
      startActivityForResult(AddGroupDetailsActivity.newIntent(requireActivity(), recipientIds),
                             REQUEST_CODE);
    });


    return alertBuilder.setView(view).create();
  }

  private void onClick(ExistingGroupMappingAdapter.GroupModel group) {
    long threadId = SignalDatabase.threads().getOrCreateThreadIdFor(group.getRecipient(),
                                                                    ThreadDatabase.DistributionTypes.CONVERSATION);
    Intent intent = ConversationIntents.createBuilder(requireActivity(),
                                                      group.getRecipient().getId(),
                                                      threadId)
                                       .build();
    startActivity(intent);
    dismiss();
    requireActivity().finish();
  }

  private ExistingGroupMappingAdapter.GroupModel createModel(GroupDatabase.GroupRecord record) {
    RecipientId recipientId    = SignalDatabase.recipients().getOrInsertFromGroupId(record.getId());
    Recipient   groupRecipient = Recipient.resolved(recipientId);
    return new ExistingGroupMappingAdapter.GroupModel(groupRecipient);
  }
}
