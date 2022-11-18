package org.thoughtcrime.securesms.recipients.ui.managerecipient;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
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
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.MuteDialog;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.StickyHeaderDecoration;
import org.thoughtcrime.securesms.util.adapter.FixedViewsAdapter;
import org.thoughtcrime.securesms.util.adapter.RecyclerViewConcatenateAdapterStickyHeader;

import java.util.Locale;
import java.util.Objects;

import static org.thoughtcrime.securesms.conversation.ConversationActivity.RECIPIENT_EXTRA;
import static org.thoughtcrime.securesms.conversation.ConversationActivity.STRING_CURRENT_EXPIRATION;

public class ManageRecipientFragment extends LoggingFragment {

    private static final String TAG = Log.tag(ManageRecipientFragment.class);
    private static final String RECIPIENT_ID = "RECIPIENT_ID";
    private static final String FROM_CONVERSATION = "FROM_CONVERSATION";

    private ManageRecipientViewModel viewModel;
    private RelativeLayout rlContainer;
    private RecyclerView mSettingList;
    private TextView mDisappearingMessages;
    private TextView mMuteNotifications;
    private TextView mMuteNotificationsUntilLabel;
    private TextView mCustomNotifications;
    private TextView mViewSafetyNumber;
    private TextView mRecipientNumber;
    private TextView mBlockGroup;
    private TextView mUnblockGroup;
    private RecipientId recipientId;

    private FixedViewsAdapter disappearingMessagesAdapter, muteNotificationsAdapter, customNotificationsAdapter;
    private FixedViewsAdapter viewSafetyNumberAdapter,settingRecipientNumberAdapter, blockGroupAdapter, unblockGroupAdapter;

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

    static ManageRecipientFragment newInstance(@NonNull RecipientId recipientId, boolean fromConversation) {
        ManageRecipientFragment fragment = new ManageRecipientFragment();
        Bundle args = new Bundle();

        args.putParcelable(RECIPIENT_ID, recipientId);
        args.putBoolean(FROM_CONVERSATION, fromConversation);
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public @Nullable
    View onCreateView(@NonNull LayoutInflater inflater,
                      @Nullable ViewGroup container,
                      @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.pigeon_recipient_manage_fragment, container, false);

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
        recipientId = Objects.requireNonNull(requireArguments().getParcelable(RECIPIENT_ID));
        boolean fromConversation = requireArguments().getBoolean(FROM_CONVERSATION, false);
        ManageRecipientViewModel.Factory factory = new ManageRecipientViewModel.Factory(recipientId);

        viewModel = ViewModelProviders.of(requireActivity(), factory).get(ManageRecipientViewModel.class);

        initializeCursor();
        viewModel.getDisappearingMessageTimer().observe(getViewLifecycleOwner(), string -> {
            mDisappearingMessages.setText(getString(R.string.ManageRecipientActivity_disappearing_messages)+ " " + string);
        });
        if (NotificationChannels.supported()) {
            viewModel.hasCustomNotifications().observe(getViewLifecycleOwner(), hasCustomNotifications -> {
                addOnOffString(mCustomNotifications,
                        getString(R.string.ManageRecipientActivity_custom_notifications),
                        hasCustomNotifications, context);
            });
        }
        viewModel.getIdentity().observe(getViewLifecycleOwner(), identityRecord -> {
            if (identityRecord != null) {
                viewSafetyNumberAdapter.show();
            } else {
                viewSafetyNumberAdapter.hide();
            }
            if (identityRecord != null) {
                mViewSafetyNumber.setOnClickListener(view -> viewModel.onViewSafetyNumberClicked(requireActivity(), identityRecord));
            }
        });

        if (recipientId.equals(Recipient.self().getId())) {
            muteNotificationsAdapter.hide();
            viewSafetyNumberAdapter.hide();
            settingRecipientNumberAdapter.hide();
            blockGroupAdapter.hide();
            unblockGroupAdapter.hide();
        }

        mDisappearingMessages.setOnClickListener(v -> viewModel.handleExpirationSelection(requireContext()));
        viewModel.getMuteState().observe(getViewLifecycleOwner(), this::presentMuteState);
        mCustomNotifications.setOnClickListener(v-> {
            if (getActivity() != null) {
                ((ManageRecipientActivity) getActivity()).replaceFragment(recipientId);
            }
        });
        mBlockGroup.setOnClickListener(v -> viewModel.onBlockClicked(requireActivity()));
        mUnblockGroup.setOnClickListener(v -> viewModel.onUnblockClicked(requireActivity()));
        viewModel.getCanBlock().observe(getViewLifecycleOwner(), canBlock -> {
            if (canBlock) {
                blockGroupAdapter.show();
                unblockGroupAdapter.hide();
            } else {
                blockGroupAdapter.hide();
                unblockGroupAdapter.show();
            }
        });
    }

    private void presentMuteState(@NonNull ManageRecipientViewModel.MuteState muteState) {
        mMuteNotifications.setOnClickListener(view -> {
            if (mMuteNotificationsUntilLabel.getVisibility() == View.GONE) {
                Intent intent = new Intent(getContext(), DialogWithListActivity.class);
                intent.putExtra(DialogWithListActivity.MODE, DialogWithListActivity.FOR_MUTE);
                intent.putExtra(RECIPIENT_EXTRA, recipientId);
                getContext().startActivity(intent);
            } else {
                viewModel.clearMuteUntil();
            }
        });
        addOnOffString(mMuteNotifications,
                getString(R.string.ManageGroupActivity_mute_notifications),
                muteState.isMuted(), getContext());
        mMuteNotificationsUntilLabel.setVisibility(muteState.isMuted() ? View.VISIBLE : View.GONE);
        if (muteState.isMuted()) {
            if (muteState.getMutedUntil() == Long.MAX_VALUE) {
                mMuteNotificationsUntilLabel.setText(R.string.ManageRecipientActivity_always);
            } else {
                mMuteNotificationsUntilLabel.setText(getString(R.string.ManageRecipientActivity_until_s,
                        DateUtils.getTimeString(requireContext(),
                                Locale.getDefault(),
                                muteState.getMutedUntil())));
            }
            if (mMuteNotifications.hasFocus()) startSmallFocusAnimation(mMuteNotificationsUntilLabel, true);
        }
    }

    private void initializeCursor() {
        RecyclerViewConcatenateAdapterStickyHeader concatenateAdapter = new RecyclerViewConcatenateAdapterStickyHeader();
        disappearingMessagesAdapter = new FixedViewsAdapter(requireContext(), 72, rlContainer, createDisappearingMessagesView());
        concatenateAdapter.addAdapter(disappearingMessagesAdapter);
        muteNotificationsAdapter = new FixedViewsAdapter(requireContext(), 72, rlContainer, createMuteNotificationsView());
        concatenateAdapter.addAdapter(muteNotificationsAdapter);
        customNotificationsAdapter = new FixedViewsAdapter(requireContext(), 72, rlContainer, createCustomNotificationsView());
        concatenateAdapter.addAdapter(customNotificationsAdapter);
        viewSafetyNumberAdapter = new FixedViewsAdapter(requireContext(), 72, rlContainer, createViewSafetyNumberView());
        concatenateAdapter.addAdapter(viewSafetyNumberAdapter);
        if (Recipient.resolved(recipientId) != null) {
            settingRecipientNumberAdapter = new FixedViewsAdapter(requireContext(), 72, rlContainer, createRecipientNumberView());
            concatenateAdapter.addAdapter(settingRecipientNumberAdapter);
        }
        blockGroupAdapter = new FixedViewsAdapter(requireContext(), 72, rlContainer, createBlockGroupView());
        concatenateAdapter.addAdapter(blockGroupAdapter);
        unblockGroupAdapter = new FixedViewsAdapter(requireContext(), 72, rlContainer, createUnblockGroupView());
        concatenateAdapter.addAdapter(unblockGroupAdapter);

        mSettingList.setLayoutManager(new LinearLayoutManager(getActivity()));
        mSettingList.setAdapter(concatenateAdapter);
        mSettingList.setClipToPadding(false);
        mSettingList.setClipChildren(false);
        mSettingList.setPadding(0, 76, 0, 200);
        mSettingList.addItemDecoration(new StickyHeaderDecoration(concatenateAdapter, true, true));
    }

    private View createDisappearingMessagesView() {
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.conversation_setting_disappearing_messages_item, (ViewGroup) requireView(), false);
        mDisappearingMessages = view.findViewById(R.id.disappearing_messages_row);
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

    private View createViewSafetyNumberView() {
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.conversation_setting_view_safety_number_item, (ViewGroup) requireView(), false);
        mViewSafetyNumber = (TextView) view;
        return view;
    }
    private View createRecipientNumberView() {
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.conversation_setting_recipient_number_item, (ViewGroup) requireView(), false);
        mRecipientNumber = (TextView) view;
        mRecipientNumber.setText(Recipient.resolved(recipientId).getNumber());
        return view;
    }
    private View createBlockGroupView() {
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.conversation_setting_block_item, (ViewGroup) requireView(), false);
        mBlockGroup = (TextView) view;
        mBlockGroup.setText(getString(R.string.ManageRecipientActivity_block));
        return view;
    }

    private View createUnblockGroupView() {
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.conversation_setting_unblock_item, (ViewGroup) requireView(), false);
        mUnblockGroup = (TextView) view;
        mUnblockGroup.setText(getString(R.string.ManageRecipientActivity_unblock));
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

    private static int booleanToOnOff(boolean isOn) {
        return isOn ? R.string.ManageGroupActivity_on
                : R.string.ManageGroupActivity_off;
    }

    private void addOnOffString(TextView view,String value, boolean isOn, Context context) {
        String text = value + " " + (isOn ? context.getString(R.string.ManageGroupActivity_on)
                : context.getString(R.string.ManageGroupActivity_off));
        view.setText(text);
    }
}
