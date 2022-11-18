package org.thoughtcrime.securesms.groups.ui.managegroup;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.interpolator.view.animation.FastOutLinearInInterpolator;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.DialogWithListActivity;
import org.thoughtcrime.securesms.InviteActivity;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.MainActivity;
import org.thoughtcrime.securesms.MuteDialog;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.ui.GroupMemberListView;
import org.thoughtcrime.securesms.groups.ui.LeaveGroupDialog;
import org.thoughtcrime.securesms.groups.ui.managegroup.dialogs.GroupRightsDialog;
import org.thoughtcrime.securesms.groups.ui.managegroup.dialogs.GroupsLearnMoreBottomSheetDialogFragment;
import org.thoughtcrime.securesms.groups.ui.migration.GroupsV1MigrationInitiationBottomSheetDialogFragment;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.profiles.edit.EditProfileActivity;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.ui.disappearingmessages.RecipientDisappearingMessagesActivity;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.LongClickMovementMethod;
import org.thoughtcrime.securesms.util.StickyHeaderDecoration;
import org.thoughtcrime.securesms.util.adapter.FixedViewsAdapter;
import org.thoughtcrime.securesms.util.adapter.RecyclerViewConcatenateAdapterStickyHeader;
import org.thoughtcrime.securesms.util.views.LearnMoreTextView;
import org.thoughtcrime.securesms.components.emoji.EmojiTextView;
import org.thoughtcrime.securesms.groups.ui.managegroup.dialogs.GroupDescriptionDialog;
import org.thoughtcrime.securesms.groups.v2.GroupDescriptionUtil;

import java.util.Locale;
import java.util.Objects;

import static org.thoughtcrime.securesms.conversation.ConversationActivity.RECIPIENT_EXTRA;

public class ManageGroupFragment extends LoggingFragment {
    private static final String GROUP_ID = "GROUP_ID";

    private static final String TAG = Log.tag(ManageGroupFragment.class);

    public static final String DIALOG_TAG = "DIALOG";

    private ManageGroupViewModel viewModel;
    private EmojiTextView groupDescription;
    private LearnMoreTextView groupInfoText;
    private RelativeLayout rlContainer;
    private RecyclerView mSettingList;
    private TextView mDisappearingMessages;
    private TextView mMuteNotifications;
    private TextView mMuteNotificationsUntilLabel;
    private TextView mCustomNotifications;
    private TextView mMentions;
    private TextView mAddMembers;
    private TextView mEditGroupInfo;
    private TextView mEditGroup;
    private TextView mBlockGroup;
    private TextView mUnblockGroup;
    private TextView mLeaveGroup;
    private GroupMemberListView groupMemberList;

    private FixedViewsAdapter disappearingMessagesAdapter, muteNotificationsAdapter;
    private FixedViewsAdapter customNotificationsAdapter, mentionsAdapter, addMembersAdapter, editGroupInfoAdapter;
    private FixedViewsAdapter editGroupAdapter, blockGroupAdapter, unblockGroupAdapter, leaveGroupAdapter;

    private int mFocusHeight;
    private int mNormalHeight;
    private int mFocusSmallHeight;
    private int mSmallHeight;
    private int mNormalPaddingX;
    private int mFocusPaddingX;
    private int mFocusTextSize;
    private int mNormalTextSize;
    private int mFocusSmallTextSize;
    private int mSmallTextSize;

    static ManageGroupFragment newInstance(@NonNull String groupId) {
        ManageGroupFragment fragment = new ManageGroupFragment();
        Bundle args = new Bundle();

        args.putString(GROUP_ID, groupId);
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public @Nullable
    View onCreateView(@NonNull LayoutInflater inflater,
                      @Nullable ViewGroup container,
                      @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.pigeon_group_manage_fragment, container, false);
//        groupDescription            = view.findViewById(R.id.manage_group_description);
        groupInfoText = view.findViewById(R.id.manage_group_info_text);
        rlContainer = view.findViewById(R.id.rl_container);
        mSettingList = view.findViewById(R.id.conversation_setting_list);

        Resources res = getContext().getResources();
        mFocusHeight = res.getDimensionPixelSize(R.dimen.focus_item_height);
        mNormalHeight = res.getDimensionPixelSize(R.dimen.item_height);
        mFocusSmallHeight = res.getDimensionPixelSize(R.dimen.focus_small_height);
        mSmallHeight = res.getDimensionPixelSize(R.dimen.small_height);
        mFocusTextSize = res.getDimensionPixelSize(R.dimen.focus_item_textsize);
        mNormalTextSize = res.getDimensionPixelSize(R.dimen.item_textsize);
        mFocusSmallTextSize = res.getDimensionPixelSize(R.dimen.focus_small_textsize);
        mSmallTextSize = res.getDimensionPixelSize(R.dimen.small_textsize);
        mFocusPaddingX = res.getDimensionPixelSize(R.dimen.focus_item_padding_x);
        mNormalPaddingX = res.getDimensionPixelSize(R.dimen.item_padding_x);

        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        Context context = requireContext();
        GroupId groupId = getGroupId();
        ManageGroupViewModel.Factory factory = new ManageGroupViewModel.Factory(context, groupId);

        initializeCursor();
        Log.d(TAG, "onActivityCreated groupId.isPush() : " + groupId.isPush());
        if (groupId.isPush()) {
            disappearingMessagesAdapter.show();
            blockGroupAdapter.show();
            unblockGroupAdapter.show();
            leaveGroupAdapter.show();
        } else {
            disappearingMessagesAdapter.hide();
            blockGroupAdapter.hide();
            unblockGroupAdapter.hide();
            leaveGroupAdapter.hide();
        }

        viewModel = ViewModelProviders.of(requireActivity(), factory).get(ManageGroupViewModel.class);

//    viewModel.getCanEditGroupAttributes().observe(getViewLifecycleOwner(), canEdit -> {
//      Log.d(TAG, "canEdit : " + canEdit);
//      mDisappearingMessages.setEnabled(canEdit);
//    });
        viewModel.getDisappearingMessageTimer().observe(getViewLifecycleOwner(), string -> {
//            mDisappearingMessages.setText(getString(R.string.ManageRecipientActivity_disappearing_messages)+ " " + string);
        });
        /*disappearingMessagesRow.setOnClickListener(v -> {
            Recipient recipient = viewModel.getGroupRecipient().getValue();
            if (recipient != null) {
                startActivity(RecipientDisappearingMessagesActivity.forRecipient(requireContext(), recipient.getId()));
            }
        });*/
        if (NotificationChannels.supported()) {
            viewModel.hasCustomNotifications().observe(getViewLifecycleOwner(), hasCustomNotifications -> {
                addOnOffString(mCustomNotifications,
                        getString(R.string.ManageRecipientActivity_custom_notifications),
                        hasCustomNotifications, context);
            });
        }
        viewModel.getMentionSetting().observe(getViewLifecycleOwner(), value -> {
            mMentions.setText(getString(R.string.ManageGroupActivity_mentions) + " " + value);
        });

//        viewModel.getDescription().observe(getViewLifecycleOwner(), this::updateGroupDescription);
        viewModel.getGroupRecipient().observe(getViewLifecycleOwner(), groupRecipient -> {

           mCustomNotifications.setOnClickListener(v->{
               if (getActivity() != null) {
                   ((ManageGroupActivity) getActivity()).replaceFragment(groupRecipient.getId());
               }
           });

        });

        mLeaveGroup.setOnClickListener(v -> LeaveGroupDialog.handleLeavePushGroup(requireActivity(), groupId.requirePush(), () -> startActivity(MainActivity.clearTop(context))));

        mDisappearingMessages.setOnClickListener(v -> viewModel.handleExpirationSelection(context));
        mBlockGroup.setOnClickListener(v -> viewModel.blockAndLeave(requireActivity()));
        mUnblockGroup.setOnClickListener(v -> viewModel.unblock(requireActivity()));

        viewModel.getMembershipRights().observe(getViewLifecycleOwner(), r -> {
               if (r != null) {
                   mAddMembers.setText(getText(R.string.ManageGroupActivity_add_members) +  " "  + getString(r.getString()));
                   mAddMembers.setOnClickListener(v -> new GroupRightsDialog(context, GroupRightsDialog.Type.MEMBERSHIP, r, (from, to) -> viewModel.applyMembershipRightsChange(to)).show());
               }
             }
        );

        viewModel.getEditGroupAttributesRights().observe(getViewLifecycleOwner(), r -> {
            if (r != null) {
                mEditGroupInfo.setText(
                        getString(R.string.ManageGroupActivity_edit_group_info)
                                + " " + getString(r.getString()));
                mEditGroupInfo.setOnClickListener(v -> new GroupRightsDialog(context, GroupRightsDialog.Type.ATTRIBUTES, r, (from, to) -> viewModel.applyAttributesRightsChange(to)).show());
            }
        });

        viewModel.getIsAdmin().observe(getViewLifecycleOwner(), admin -> {
            Log.d(TAG, "isAdmin :  " + admin);
            if (admin) {
                addMembersAdapter.show();
                editGroupInfoAdapter.show();
            } else {
                addMembersAdapter.hide();
                editGroupInfoAdapter.hide();
            }
        });

        final View.OnClickListener muteClickListener = view -> {
            if (mMuteNotificationsUntilLabel.getVisibility() == View.GONE) {
                Intent intent = new Intent(getContext(), DialogWithListActivity.class);
                intent.putExtra(DialogWithListActivity.MODE, DialogWithListActivity.FOR_MUTE);
                intent.putExtra(RECIPIENT_EXTRA, viewModel.getGroupRecipient().getValue().getId());
                getContext().startActivity(intent);
            } else {
                viewModel.clearMuteUntil();
            }
        };

        viewModel.getMuteState().observe(getViewLifecycleOwner(), muteState -> {
            mMuteNotifications.setOnClickListener(muteClickListener);
            Log.d(TAG, "muteState : " + muteState.isMuted());
            addOnOffString(mMuteNotifications,
                    getString(R.string.ManageGroupActivity_mute_notifications),
                    muteState.isMuted(), context);
            mMuteNotificationsUntilLabel.setVisibility(muteState.isMuted() ? View.VISIBLE : View.GONE);

            if (muteState.isMuted()) {
                if (muteState.getMutedUntil() == Long.MAX_VALUE) {
                    mMuteNotificationsUntilLabel.setText(R.string.ManageGroupActivity_always);
                } else {
                    mMuteNotificationsUntilLabel.setText(getString(R.string.ManageGroupActivity_until_s,
                            DateUtils.getTimeString(requireContext(),
                                    Locale.getDefault(),
                                    muteState.getMutedUntil())));
                }
                if (mMuteNotifications.hasFocus()) startSmallFocusAnimation(mMuteNotificationsUntilLabel, true);
            }
        });

        Log.d(TAG, "groupId.isV2() : " + groupId.isV2());
        if (groupId.isV2()) {
            mentionsAdapter.show();
        } else {
            mentionsAdapter.hide();
        }
        mMentions.setOnClickListener(v -> viewModel.handleMentionNotificationSelection());

        viewModel.getCanLeaveGroup().observe(getViewLifecycleOwner(), canLeave -> {
            Log.d(TAG, "canLeave ： " + canLeave);
            if (canLeave) {
                leaveGroupAdapter.show();
            } else {
                leaveGroupAdapter.hide();
            }
        });
        viewModel.getCanBlockGroup().observe(getViewLifecycleOwner(), canBlock -> {
            Log.d(TAG, "canBlock ： " + canBlock);
            if (canBlock) {
                blockGroupAdapter.show();
                unblockGroupAdapter.hide();
            } else {
                blockGroupAdapter.hide();
                unblockGroupAdapter.show();
            }
        });

        viewModel.getGroupInfoMessage().observe(getViewLifecycleOwner(), message -> {
            switch (message) {
                case LEGACY_GROUP_LEARN_MORE:
                    groupInfoText.setText(R.string.ManageGroupActivity_legacy_group_learn_more);
                    groupInfoText.setOnLinkClickListener(v -> GroupsLearnMoreBottomSheetDialogFragment.show(requireFragmentManager()));
                    groupInfoText.setLearnMoreVisible(true);
                    groupInfoText.setVisibility(View.VISIBLE);
                    break;
                case LEGACY_GROUP_UPGRADE:
                    groupInfoText.setText(R.string.ManageGroupActivity_legacy_group_upgrade);
                    groupInfoText.setOnLinkClickListener(v -> GroupsV1MigrationInitiationBottomSheetDialogFragment.showForInitiation(requireFragmentManager(), Recipient.externalPossiblyMigratedGroup(requireContext(), groupId).getId()));
                    groupInfoText.setLearnMoreVisible(true, R.string.ManageGroupActivity_upgrade_this_group);
                    groupInfoText.setVisibility(View.VISIBLE);
                    break;
                case LEGACY_GROUP_TOO_LARGE:
                    groupInfoText.setText(context.getString(R.string.ManageGroupActivity_legacy_group_too_large, FeatureFlags.groupLimits().getHardLimit() - 1));
                    groupInfoText.setLearnMoreVisible(false);
                    groupInfoText.setVisibility(View.VISIBLE);
                    break;
                case MMS_WARNING:
                    groupInfoText.setText(R.string.ManageGroupActivity_this_is_an_insecure_mms_group);
                    groupInfoText.setOnLinkClickListener(v -> startActivity(new Intent(requireContext(), InviteActivity.class)));
                    groupInfoText.setLearnMoreVisible(true, R.string.ManageGroupActivity_invite_now);
                    groupInfoText.setVisibility(View.VISIBLE);
                    break;
                default:
                    groupInfoText.setVisibility(View.GONE);
                    break;
            }
        });
    }

    private void updateGroupDescription(@NonNull ManageGroupViewModel.Description description) {
        if (!TextUtils.isEmpty(description.getDescription()) || description.canEditDescription()) {
            groupDescription.setVisibility(View.VISIBLE);
            groupDescription.setMovementMethod(LongClickMovementMethod.getInstance(requireContext()));
        } else {
            groupDescription.setVisibility(View.GONE);
            groupDescription.setMovementMethod(null);
        }

        if (TextUtils.isEmpty(description.getDescription())) {
            if (description.canEditDescription()) {
                groupDescription.setOverflowText(null);
                groupDescription.setText(R.string.ManageGroupActivity_add_group_description);
                groupDescription.setOnClickListener(v -> startActivity(EditProfileActivity.getIntentForGroupProfile(requireActivity(), getGroupId())));
            }
        } else {
            groupDescription.setOnClickListener(null);
            GroupDescriptionUtil.setText(requireContext(),
                                         groupDescription,
                                         description.getDescription(),
                                         description.shouldLinkifyWebLinks(),
                                         () -> GroupDescriptionDialog.show(getChildFragmentManager(), getGroupId(), null, description.shouldLinkifyWebLinks()));
        }
    }

    private GroupId getGroupId() {
        return GroupId.parseOrThrow(Objects.requireNonNull(requireArguments().getString(GROUP_ID)));
    }

    private void initializeCursor() {
        Log.d(TAG, "initializeCursor");
        RecyclerViewConcatenateAdapterStickyHeader concatenateAdapter = new RecyclerViewConcatenateAdapterStickyHeader();
        disappearingMessagesAdapter = new FixedViewsAdapter(requireContext(), 72, rlContainer, createDisappearingMessagesView());
        concatenateAdapter.addAdapter(disappearingMessagesAdapter);
        muteNotificationsAdapter = new FixedViewsAdapter(requireContext(), 72, rlContainer, createMuteNotificationsView());
        concatenateAdapter.addAdapter(muteNotificationsAdapter);
        customNotificationsAdapter = new FixedViewsAdapter(requireContext(), 72, rlContainer, createCustomNotificationsView());
        concatenateAdapter.addAdapter(customNotificationsAdapter);
        mentionsAdapter = new FixedViewsAdapter(requireContext(), 72, rlContainer, createMentionsView());
        concatenateAdapter.addAdapter(mentionsAdapter);
        addMembersAdapter = new FixedViewsAdapter(requireContext(), 72, rlContainer, createAddMembersView());
        concatenateAdapter.addAdapter(addMembersAdapter);
        editGroupInfoAdapter = new FixedViewsAdapter(requireContext(), 72, rlContainer, createEditGroupInfoView());
        concatenateAdapter.addAdapter(editGroupInfoAdapter);
        blockGroupAdapter = new FixedViewsAdapter(requireContext(), 72, rlContainer, createBlockGroupView());
        concatenateAdapter.addAdapter(blockGroupAdapter);
        unblockGroupAdapter = new FixedViewsAdapter(requireContext(), 72, rlContainer, createUnblockGroupView());
        concatenateAdapter.addAdapter(unblockGroupAdapter);
        leaveGroupAdapter = new FixedViewsAdapter(requireContext(), 72, rlContainer, createLeaveGroupView());
        concatenateAdapter.addAdapter(leaveGroupAdapter);

        Log.d(TAG, "concatenateAdapter SIZE : " + concatenateAdapter.getItemCount());
        mSettingList.setLayoutManager(new LinearLayoutManager(getActivity()));
        mSettingList.setAdapter(concatenateAdapter);
        mSettingList.setClipToPadding(false);
        mSettingList.setClipChildren(false);
        mSettingList.setPadding(0, 76, 0, 200);
        mSettingList.addItemDecoration(new StickyHeaderDecoration(concatenateAdapter, true, true));
    }

//  private View createEditGroupView() {
//    View view = LayoutInflater.from(requireContext())
//            .inflate(R.layout.conversation_setting_edit_group_item, (ViewGroup) requireView(), false);
//    return view;
//  }

    private View createDisappearingMessagesView() {
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.conversation_setting_disappearing_messages_item, (ViewGroup) requireView(), false);
        mDisappearingMessages = (TextView) view;
        return view;
    }

    private View createMuteNotificationsView() {
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.conversation_setting_group_mute_notifications_item, (ViewGroup) requireView(), false);
        mMuteNotifications = view.findViewById(R.id.group_mute_notifications);
        mMuteNotificationsUntilLabel = view.findViewById(R.id.group_mute_notifications_until);
        mMuteNotifications.setOnFocusChangeListener(mMuteItemOnFocusChangedListener);
        return view;
    }

    private View.OnFocusChangeListener mMuteItemOnFocusChangedListener = new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View view, boolean b) {
            startFocusAnimation(mMuteNotifications, b);
            if (mMuteNotificationsUntilLabel.getVisibility() == View.VISIBLE) {
                startSmallFocusAnimation(mMuteNotificationsUntilLabel, b);
            }
        }
    };

    private View createCustomNotificationsView() {
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.conversation_setting_custom_mute_notifications_item, (ViewGroup) requireView(), false);
        mCustomNotifications = (TextView) view;
        return view;
    }

    private View createMentionsView() {
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.conversation_setting_metions_item, (ViewGroup) requireView(), false);
        mMentions = (TextView) view;
        return view;
    }

    private View createAddMembersView() {
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.conversation_setting_add_members_item, (ViewGroup) requireView(), false);
        mAddMembers = (TextView) view;
        return view;
    }

    private View createEditGroupInfoView() {
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.conversation_setting_edit_group_info_item, (ViewGroup) requireView(), false);
        mEditGroupInfo = (TextView) view;
        return view;
    }

    private View createBlockGroupView() {
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.conversation_setting_block_item, (ViewGroup) requireView(), false);
        mBlockGroup = (TextView) view;
        return view;
    }

    private View createUnblockGroupView() {
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.conversation_setting_unblock_item, (ViewGroup) requireView(), false);
        mUnblockGroup = (TextView) view;
        return view;
    }

    private View createLeaveGroupView() {
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.conversation_setting_leave_group_item, (ViewGroup) requireView(), false);
        mLeaveGroup = (TextView) view;
        return view;
    }

    public void startFocusAnimation(View v, boolean focused) {
        ValueAnimator va;
        if (focused) {
            va = ValueAnimator.ofFloat(0, 1);
        } else {
            va = ValueAnimator.ofFloat(1, 0);
        }
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                float scale = (float) valueAnimator.getAnimatedValue();
                float height = ((float) (mFocusHeight - mNormalHeight)) * (scale) + (float) mNormalHeight;
                float textsize = ((float) (mFocusTextSize - mNormalTextSize)) * (scale) + mNormalTextSize;
                float padding = (float) mNormalPaddingX - ((float) (mNormalPaddingX - mFocusPaddingX)) * (scale);
                int alpha = (int) ((float) 0x81 + (float) ((0xff - 0x81)) * (scale));
                int color = alpha * 0x1000000 + 0xffffff;
                ((TextView) v).setTextSize((int) textsize);
                ((TextView) v).setTextColor(color);
                v.setPadding(
                        (int) padding, v.getPaddingTop(),
                        v.getPaddingRight(), v.getPaddingBottom());
                v.getLayoutParams().height = (int) height;
            }
        });

        FastOutLinearInInterpolator FastOutLinearInInterpolator = new FastOutLinearInInterpolator();
        va.setInterpolator(FastOutLinearInInterpolator);
        va.setDuration(270);
        va.start();
    }

    public void startSmallFocusAnimation(View v, boolean focused) {
        ValueAnimator va;
        if (focused) {
            va = ValueAnimator.ofFloat(0, 1);
        } else {
            va = ValueAnimator.ofFloat(1, 0);
        }
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                float scale = (float) valueAnimator.getAnimatedValue();
                float height = ((float) (mFocusSmallHeight - mSmallHeight)) * (scale) + (float) mSmallHeight;
                float textsize = ((float) (mFocusSmallTextSize - mSmallTextSize)) * (scale) + mSmallTextSize;
                float padding = (float) mNormalPaddingX - ((float) (mNormalPaddingX - mFocusPaddingX)) * (scale);
                int alpha = (int) ((float) 0x81 + (float) ((0xff - 0x81)) * (scale));
                int color = alpha * 0x1000000 + 0xffffff;
                ((TextView) v).setTextSize((int) textsize);
                ((TextView) v).setTextColor(color);
                v.setPadding(
                        (int) padding, v.getPaddingTop(),
                        v.getPaddingRight(), v.getPaddingBottom());
                v.getLayoutParams().height = (int) height;
            }
        });

        FastOutLinearInInterpolator FastOutLinearInInterpolator = new FastOutLinearInInterpolator();
        va.setInterpolator(FastOutLinearInInterpolator);
        va.setDuration(270);
        va.start();
    }

    private void addOnOffString(TextView view, String value, boolean isOn, Context context) {
        String text = value + " " + (isOn ? context.getString(R.string.ManageGroupActivity_on)
                : context.getString(R.string.ManageGroupActivity_off));
        view.setText(text);
    }
}
