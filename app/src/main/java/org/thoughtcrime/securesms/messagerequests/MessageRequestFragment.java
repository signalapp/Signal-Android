package org.thoughtcrime.securesms.messagerequests;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.text.HtmlCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.conversation.ConversationItem;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MessageRequestFragment extends Fragment {

  private AvatarImageView  contactAvatar;
  private TextView         contactTitle;
  private TextView         contactSubtitle;
  private TextView         contactDescription;
  private FrameLayout      messageView;
  private TextView         question;
  private Button           accept;
  private Button           block;
  private Button           delete;
  private ConversationItem conversationItem;

  private MessageRequestFragmentViewModel viewModel;

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater,
                           @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState)
  {
    return inflater.inflate(R.layout.message_request_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    contactAvatar      = view.findViewById(R.id.message_request_avatar);
    contactTitle       = view.findViewById(R.id.message_request_title);
    contactSubtitle    = view.findViewById(R.id.message_request_subtitle);
    contactDescription = view.findViewById(R.id.message_request_description);
    messageView        = view.findViewById(R.id.message_request_message);
    question           = view.findViewById(R.id.message_request_question);
    accept             = view.findViewById(R.id.message_request_accept);
    block              = view.findViewById(R.id.message_request_block);
    delete             = view.findViewById(R.id.message_request_delete);

    initializeViewModel();
    initializeBottomViewListeners();
  }

  private void initializeViewModel() {
    viewModel = ViewModelProviders.of(requireActivity()).get(MessageRequestFragmentViewModel.class);
    viewModel.getState().observe(getViewLifecycleOwner(), state -> {
      if (state.messageRecord == null || state.recipient == null) return;

      presentConversationItemTo(state.messageRecord, state.recipient);
      presentMessageRequestBottomViewTo(state.recipient);
      presentMessageRequestProfileViewTo(state.recipient, state.groups, state.memberCount);
    });
  }

  private void presentConversationItemTo(@NonNull MessageRecord messageRecord, @NonNull Recipient recipient) {
    if (messageRecord.isGroupAction()) {
      if (conversationItem != null) {
        messageView.removeAllViews();
      }
      return;
    }

    if (conversationItem == null) {
      conversationItem = (ConversationItem) LayoutInflater.from(requireActivity()).inflate(R.layout.conversation_item_received, messageView, false);
    }

    conversationItem.bind(messageRecord,
                          Optional.absent(),
                          Optional.absent(),
                          GlideApp.with(this),
                          Locale.getDefault(),
                          Collections.emptySet(),
                          recipient,
                          null,
                          false);

    if (messageView.getChildCount() == 0 || messageView.getChildAt(0) != conversationItem) {
      messageView.removeAllViews();
      messageView.addView(conversationItem);
    }
  }

  private void presentMessageRequestProfileViewTo(@Nullable Recipient recipient, @Nullable List<String> groups, int memberCount) {
    if (recipient != null) {
      contactAvatar.setAvatar(GlideApp.with(this), recipient, false);

      String title = recipient.getDisplayName(requireContext());
      contactTitle.setText(title);

      if (recipient.isGroup()) {
        contactSubtitle.setText(getString(R.string.MessageRequestProfileView_members, memberCount));
      } else {
        String subtitle = recipient.getUsername().or(recipient.getE164()).orNull();

        if (subtitle == null || subtitle.equals(title)) {
          contactSubtitle.setVisibility(View.GONE);
        } else {
          contactSubtitle.setText(subtitle);
        }
      }
    }

    if (groups == null || groups.isEmpty()) {
      contactDescription.setVisibility(View.GONE);
    } else {
      final String description;

      switch (groups.size()) {
        case 1:
          description = getString(R.string.MessageRequestProfileView_member_of_one_group, bold(groups.get(0)));
          break;
        case 2:
          description = getString(R.string.MessageRequestProfileView_member_of_two_groups, bold(groups.get(0)), bold(groups.get(1)));
          break;
        case 3:
          description = getString(R.string.MessageRequestProfileView_member_of_many_groups, bold(groups.get(0)), bold(groups.get(1)), bold(groups.get(2)));
          break;
        default:
          int others = groups.size() - 2;
          description = getString(R.string.MessageRequestProfileView_member_of_many_groups,
                                  bold(groups.get(0)),
                                  bold(groups.get(1)),
                                  getResources().getQuantityString(R.plurals.MessageRequestProfileView_member_of_others, others, others));
      }

      contactDescription.setText(HtmlCompat.fromHtml(description, 0));
      contactDescription.setVisibility(View.VISIBLE);
    }
  }

  private @NonNull String bold(@NonNull String target) {
    return "<b>" + target + "</b>";
  }

  private void presentMessageRequestBottomViewTo(@Nullable Recipient recipient) {
    if (recipient == null) return;

    question.setText(HtmlCompat.fromHtml(getString(R.string.MessageRequestBottomView_do_you_want_to_let, bold(recipient.getDisplayName(requireContext()))), 0));
  }

  private void initializeBottomViewListeners() {
    accept.setOnClickListener(v -> viewModel.accept());
    delete.setOnClickListener(v -> viewModel.delete());
    block.setOnClickListener(v -> viewModel.block());
  }

}
