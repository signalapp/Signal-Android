package org.thoughtcrime.securesms.groups.ui.managegroup;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.interpolator.view.animation.FastOutLinearInInterpolator;
import androidx.lifecycle.ViewModelProviders;

import com.google.android.material.snackbar.Snackbar;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.PushContactSelectionActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.SelectedRecipientsDetailActivity;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.ui.GroupChangeFailureReason;
import org.thoughtcrime.securesms.groups.ui.GroupErrors;
import org.thoughtcrime.securesms.groups.ui.GroupMemberListView;
import org.thoughtcrime.securesms.groups.ui.managegroup.dialogs.GroupInviteSentDialog;
import org.thoughtcrime.securesms.profiles.edit.EditProfileActivity;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientExporter;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.AsynchronousCallback;
import org.thoughtcrime.securesms.util.StickyHeaderDecoration;
import org.thoughtcrime.securesms.util.adapter.FixedViewsAdapter;
import org.thoughtcrime.securesms.util.adapter.RecyclerViewConcatenateAdapterStickyHeader;
import org.thoughtcrime.securesms.util.views.SimpleProgressDialog;

import java.util.List;
import java.util.Objects;

public class EditGroupFragment extends LoggingFragment {
    private static final String GROUP_ID = "GROUP_ID";

    private static final String TAG = Log.tag(EditGroupFragment.class);

    private static final int PICK_CONTACT = 61341;
    public static final String DIALOG_TAG = "DIALOG";

    private ManageGroupViewModel viewModel;
    private GroupMemberListView groupMemberList;
    private TextView memberCountAboveList;
    private RelativeLayout rlContainer;
    private TextView addMembers;
    private TextView groupName;
    private TextView showAllMembers;

    private int mFocusHeight;
    private int mNormalHeight;
    private int mNormalPaddingX;
    private int mFocusPaddingX;
    private int mFocusTextSize;
    private int mNormalTextSize;

    private FixedViewsAdapter memberCountAdapter, addMembersAdapter, groupNameAdapter ,allMembersAdapter;


    static EditGroupFragment newInstance(@NonNull String groupId) {
        EditGroupFragment fragment = new EditGroupFragment();
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
        Log.d(TAG, "onActivityCreated");
        View view = inflater.inflate(R.layout.pigeon_edit_group_fragment, container, false);
        groupMemberList = view.findViewById(R.id.group_members);
        rlContainer = view.findViewById(R.id.rl_container);

        Resources res = getContext().getResources();
        mFocusHeight = res.getDimensionPixelSize(R.dimen.focus_item_height);
        mNormalHeight = res.getDimensionPixelSize(R.dimen.item_height);
        mFocusTextSize = res.getDimensionPixelSize(R.dimen.focus_item_textsize);
        mNormalTextSize = res.getDimensionPixelSize(R.dimen.item_textsize);
        mFocusPaddingX = res.getDimensionPixelSize(R.dimen.focus_item_padding_x);
        mNormalPaddingX = res.getDimensionPixelSize(R.dimen.item_padding_x);
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Log.d(TAG, "onActivityCreated");
        Context context = requireContext();
        GroupId groupId = getGroupId();
        ManageGroupViewModel.Factory factory = new ManageGroupViewModel.Factory(context, groupId);

        initializeCursor();
        viewModel = ViewModelProviders.of(requireActivity(), factory).get(ManageGroupViewModel.class);
        viewModel.getTitle().observe(getViewLifecycleOwner(), groupName::setText);
        viewModel.getMembers().observe(getViewLifecycleOwner(), members -> groupMemberList.setMembers(members));
        viewModel.getCanCollapseMemberList().observe(getViewLifecycleOwner(), canCollapseMemberList -> {
            if (canCollapseMemberList) {
                //show all members
                showAllMembers.setVisibility(View.VISIBLE);
                showAllMembers.setOnClickListener(v -> viewModel.onShowAllMembersClick(this,showAllMembers));
            }
        });
        viewModel.getFullMemberCountSummary().observe(getViewLifecycleOwner(), memberCountAboveList::setText);
        addMembers.setOnClickListener(v -> viewModel.onAddMembersClick(this, PICK_CONTACT));

        viewModel.getCanAddMembers().observe(getViewLifecycleOwner(), canEdit -> addMembers.setVisibility(canEdit ? View.VISIBLE : View.GONE));

        groupName.setOnClickListener(v -> startProfileScreen());
        groupMemberList.setRecipientClickListener(recipient -> displayContactDetailInfo(context, recipient));
        groupMemberList.setOverScrollMode(View.OVER_SCROLL_NEVER);
//        groupMemberList.setRecipientFocusChangeListener(this::handleMembersFocusChange);
    }

    private GroupId getGroupId() {
        return GroupId.parseOrThrow(Objects.requireNonNull(requireArguments().getString(GROUP_ID)));
    }

    private void displayContactDetailInfo(Context context, Recipient p) {
        Intent intent = new Intent(context, SelectedRecipientsDetailActivity.class);
        intent.putExtra("name",p.getDisplayName(context));
        intent.putExtra("number",p.requireE164());
        intent.putExtra("intentAdd", RecipientExporter.export(p).asAddContactIntent());
        intent.putExtra("intentExist", RecipientExporter.export(p).asExistContactIntent());
        context.startActivity(intent);
    }

    private void startProfileScreen() {
        startActivity(EditProfileActivity.getIntentForGroupProfile(requireActivity(), getGroupId()));
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_CONTACT && data != null) {
            List<RecipientId> selected = data.getParcelableArrayListExtra(PushContactSelectionActivity.KEY_SELECTED_RECIPIENTS);
            SimpleProgressDialog.DismissibleDialog progress = SimpleProgressDialog.showDelayed(requireContext());

            viewModel.onAddMembers(selected, new AsynchronousCallback.MainThread<ManageGroupViewModel.AddMembersResult, GroupChangeFailureReason>() {
                @Override
                public void onComplete(ManageGroupViewModel.AddMembersResult result) {
                    progress.dismiss();
                    if (!result.getNewInvitedMembers().isEmpty()) {
                        GroupInviteSentDialog.showInvitesSent(requireContext(), result.getNewInvitedMembers());
                    }

                    if (result.getNumberOfMembersAdded() > 0) {
                        String string = getResources().getQuantityString(R.plurals.ManageGroupActivity_added,
                                result.getNumberOfMembersAdded(),
                                result.getNumberOfMembersAdded());
                        Snackbar.make(requireView(), string, Snackbar.LENGTH_SHORT).setTextColor(Color.WHITE).show();
                    }
                }

                @Override
                public void onError(@Nullable GroupChangeFailureReason error) {
                    progress.dismiss();
                    Toast.makeText(requireContext(), GroupErrors.getUserDisplayMessage(error), Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private void initializeCursor() {
        Log.d(TAG, "initializeCursor");
        RecyclerViewConcatenateAdapterStickyHeader concatenateAdapter = new RecyclerViewConcatenateAdapterStickyHeader();
        memberCountAdapter = new FixedViewsAdapter(requireContext(), 72, rlContainer, createMembersCountView());
        concatenateAdapter.addAdapter(memberCountAdapter);
        groupNameAdapter = new FixedViewsAdapter(requireContext(), 72, rlContainer, createGroupNameView());
        concatenateAdapter.addAdapter(groupNameAdapter);
        addMembersAdapter = new FixedViewsAdapter(requireContext(), 72, rlContainer, createAddMembersActionView());
        concatenateAdapter.addAdapter(addMembersAdapter);
        concatenateAdapter.addAdapter(groupMemberList.getMemberListAdapter());
        groupMemberList.setAdapter(concatenateAdapter);
        groupMemberList.setClipToPadding(false);
        groupMemberList.setClipChildren(false);
        groupMemberList.setPadding(0, 76, 0, 200);
        groupMemberList.addItemDecoration(new StickyHeaderDecoration(concatenateAdapter, true, true));
        allMembersAdapter = new FixedViewsAdapter(requireContext(), 72, rlContainer, createAllMembersView());
        concatenateAdapter.addAdapter(allMembersAdapter);
    }

    private View createAddMembersActionView() {
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.edit_group_add_member_action_item, (ViewGroup) requireView(), false);
        addMembers = (TextView) view;
        return view;
    }

    private View createGroupNameView() {
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.edit_group_group_name_action_item, (ViewGroup) requireView(), false);
        groupName = (TextView)view;
        return view;
    }

    private View createMembersCountView() {
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.edit_group_member_count_item, (ViewGroup) requireView(), false);
        memberCountAboveList = (TextView) view;
        return view;
    }

    private View createAllMembersView(){
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.edit_group_all_members_action_item, (ViewGroup) requireView(), false);
        showAllMembers = (TextView) view;
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
                ((TextView)v).setTextSize((int) textsize);
                ((TextView)v).setTextColor(color);
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

    private void handleMembersFocusChange(@NonNull View view, boolean isFocus) {
        startFocusAnimation(view, isFocus);
    }
}
