package org.thoughtcrime.securesms.messagerequests;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.Group;
import androidx.core.text.HtmlCompat;

import com.google.android.material.button.MaterialButton;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.Debouncer;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.HtmlUtil;
import org.thoughtcrime.securesms.util.views.LearnMoreTextView;

import java.util.stream.Stream;

public class MessageRequestsBottomView extends ConstraintLayout {

  private final Debouncer showProgressDebouncer = new Debouncer(250);

  private LearnMoreTextView question;
  private MaterialButton    accept;
  private MaterialButton    gv1Continue;
  private MaterialButton    block;
  private MaterialButton    delete;
  private MaterialButton    bigDelete;
  private MaterialButton    bigUnblock;
  private View              busyIndicator;

  private Group normalButtons;
  private Group blockedButtons;
  private Group gv1MigrationButtons;
  private Group activeGroup;

  public MessageRequestsBottomView(Context context) {
    super(context);
  }

  public MessageRequestsBottomView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public MessageRequestsBottomView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();

    inflate(getContext(), R.layout.message_request_bottom_bar, this);

    question            = findViewById(R.id.message_request_question);
    accept              = findViewById(R.id.message_request_accept);
    block               = findViewById(R.id.message_request_block);
    delete              = findViewById(R.id.message_request_delete);
    bigDelete           = findViewById(R.id.message_request_big_delete);
    bigUnblock          = findViewById(R.id.message_request_big_unblock);
    gv1Continue         = findViewById(R.id.message_request_gv1_migration);
    normalButtons       = findViewById(R.id.message_request_normal_buttons);
    blockedButtons      = findViewById(R.id.message_request_blocked_buttons);
    gv1MigrationButtons = findViewById(R.id.message_request_gv1_migration_buttons);
    busyIndicator       = findViewById(R.id.message_request_busy_indicator);

    setWallpaperEnabled(false);
  }

  public void setMessageData(@NonNull MessageRequestViewModel.MessageData messageData) {
    Recipient recipient = messageData.getRecipient();

    question.setLearnMoreVisible(false);
    question.setOnLinkClickListener(null);

    switch (messageData.getMessageState()) {
      case BLOCKED_INDIVIDUAL:
        int message = recipient.isReleaseNotes() ? R.string.MessageRequestBottomView_get_updates_and_news_from_s_you_wont_receive_any_updates_until_you_unblock_them
                                                 : recipient.isRegistered() ? R.string.MessageRequestBottomView_do_you_want_to_let_s_message_you_wont_receive_any_messages_until_you_unblock_them
                                                                            : R.string.MessageRequestBottomView_do_you_want_to_let_s_message_you_wont_receive_any_messages_until_you_unblock_them_SMS;

        question.setText(HtmlCompat.fromHtml(getContext().getString(message,
                                                                    HtmlUtil.bold(recipient.getShortDisplayName(getContext()))), 0));
        setActiveInactiveGroups(blockedButtons, normalButtons, gv1MigrationButtons);
        break;
      case BLOCKED_GROUP:
        question.setText(R.string.MessageRequestBottomView_unblock_this_group_and_share_your_name_and_photo_with_its_members);
        setActiveInactiveGroups(blockedButtons, normalButtons, gv1MigrationButtons);
        break;
      case LEGACY_INDIVIDUAL:
        question.setText(getContext().getString(R.string.MessageRequestBottomView_continue_your_conversation_with_s_and_share_your_name_and_photo, recipient.getShortDisplayName(getContext())));
        question.setLearnMoreVisible(true);
        question.setOnLinkClickListener(v -> CommunicationActions.openBrowserLink(getContext(), getContext().getString(R.string.MessageRequestBottomView_legacy_learn_more_url)));
        setActiveInactiveGroups(normalButtons, blockedButtons, gv1MigrationButtons);
        accept.setText(R.string.MessageRequestBottomView_continue);
        break;
      case LEGACY_GROUP_V1:
        question.setText(R.string.MessageRequestBottomView_continue_your_conversation_with_this_group_and_share_your_name_and_photo);
        question.setLearnMoreVisible(true);
        question.setOnLinkClickListener(v -> CommunicationActions.openBrowserLink(getContext(), getContext().getString(R.string.MessageRequestBottomView_legacy_learn_more_url)));
        setActiveInactiveGroups(normalButtons, blockedButtons, gv1MigrationButtons);
        accept.setText(R.string.MessageRequestBottomView_continue);
        break;
      case DEPRECATED_GROUP_V1:
        question.setText(R.string.MessageRequestBottomView_upgrade_this_group_to_activate_new_features);
        setActiveInactiveGroups(gv1MigrationButtons, normalButtons, blockedButtons);
        gv1Continue.setVisibility(VISIBLE);
        break;
      case DEPRECATED_GROUP_V1_TOO_LARGE:
        question.setText(getContext().getString(R.string.MessageRequestBottomView_this_legacy_group_can_no_longer_be_used, FeatureFlags.groupLimits().getHardLimit() - 1));
        setActiveInactiveGroups(gv1MigrationButtons, normalButtons, blockedButtons);
        gv1Continue.setVisibility(GONE);
        break;
      case GROUP_V1:
        question.setText(R.string.MessageRequestBottomView_do_you_want_to_join_this_group_they_wont_know_youve_seen_their_messages_until_you_accept);
        setActiveInactiveGroups(normalButtons, blockedButtons, gv1MigrationButtons);
        accept.setText(R.string.MessageRequestBottomView_accept);
        break;
      case GROUP_V2_INVITE:
        question.setText(R.string.MessageRequestBottomView_do_you_want_to_join_this_group_you_wont_see_their_messages);
        setActiveInactiveGroups(normalButtons, blockedButtons, gv1MigrationButtons);
        accept.setText(R.string.MessageRequestBottomView_accept);
        break;
      case GROUP_V2_ADD:
        question.setText(R.string.MessageRequestBottomView_join_this_group_they_wont_know_youve_seen_their_messages_until_you_accept);
        setActiveInactiveGroups(normalButtons, blockedButtons, gv1MigrationButtons);
        accept.setText(R.string.MessageRequestBottomView_accept);
        break;
      case INDIVIDUAL:
        question.setText(HtmlCompat.fromHtml(getContext().getString(R.string.MessageRequestBottomView_do_you_want_to_let_s_message_you_they_wont_know_youve_seen_their_messages_until_you_accept,
                                                                    HtmlUtil.bold(recipient.getShortDisplayName(getContext()))), 0));
        setActiveInactiveGroups(normalButtons, blockedButtons, gv1MigrationButtons);
        accept.setText(R.string.MessageRequestBottomView_accept);
        break;
      case INDIVIDUAL_HIDDEN:
        question.setText(HtmlCompat.fromHtml(getContext().getString(R.string.MessageRequestBottomView_do_you_want_to_let_s_message_you_you_removed_them_before,
                                                                    HtmlUtil.bold(recipient.getShortDisplayName(getContext()))), 0));
        setActiveInactiveGroups(normalButtons, blockedButtons, gv1MigrationButtons);
        accept.setText(R.string.MessageRequestBottomView_accept);
        break;
    }
  }

  private void setActiveInactiveGroups(@NonNull Group activeGroup, @NonNull Group... inActiveGroups) {
    int initialVisibility = this.activeGroup != null ? this.activeGroup.getVisibility() : VISIBLE;

    this.activeGroup = activeGroup;

    for (Group inactive : inActiveGroups) {
      inactive.setVisibility(GONE);
    }

    activeGroup.setVisibility(initialVisibility);
  }

  public void showBusy() {
    showProgressDebouncer.publish(() -> busyIndicator.setVisibility(VISIBLE));
    if (activeGroup != null) {
      activeGroup.setVisibility(INVISIBLE);
    }
  }

  public void hideBusy() {
    showProgressDebouncer.clear();
    busyIndicator.setVisibility(GONE);
    if (activeGroup != null) {
      activeGroup.setVisibility(VISIBLE);
    }
  }

  public void setWallpaperEnabled(boolean isEnabled) {
    MessageRequestBarColorTheme theme = MessageRequestBarColorTheme.resolveTheme(isEnabled);

    Stream.of(delete, bigDelete, block, bigUnblock, accept, gv1Continue).forEach(button -> {
      button.setBackgroundTintList(ColorStateList.valueOf(theme.getButtonBackgroundColor(getContext())));
    });

    Stream.of(delete, bigDelete, block).forEach(button -> {
      button.setTextColor(theme.getButtonForegroundDenyColor(getContext()));
    });

    Stream.of(accept, bigUnblock, gv1Continue).forEach(button -> {
      button.setTextColor(theme.getButtonForegroundAcceptColor(getContext()));
    });

    setBackgroundColor(theme.getContainerButtonBackgroundColor(getContext()));
  }

  public void setAcceptOnClickListener(OnClickListener acceptOnClickListener) {
    accept.setOnClickListener(acceptOnClickListener);
  }

  public void setDeleteOnClickListener(OnClickListener deleteOnClickListener) {
    delete.setOnClickListener(deleteOnClickListener);
    bigDelete.setOnClickListener(deleteOnClickListener);
  }

  public void setBlockOnClickListener(OnClickListener blockOnClickListener) {
    block.setOnClickListener(blockOnClickListener);
  }

  public void setUnblockOnClickListener(OnClickListener unblockOnClickListener) {
    bigUnblock.setOnClickListener(unblockOnClickListener);
  }

  public void setGroupV1MigrationContinueListener(OnClickListener acceptOnClickListener) {
    gv1Continue.setOnClickListener(acceptOnClickListener);
  }
}
