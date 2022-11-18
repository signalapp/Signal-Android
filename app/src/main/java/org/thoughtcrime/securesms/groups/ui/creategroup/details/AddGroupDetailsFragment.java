package org.thoughtcrime.securesms.groups.ui.creategroup.details;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.interpolator.view.animation.FastOutLinearInInterpolator;
import androidx.lifecycle.ViewModelProviders;

import org.signal.core.util.EditTextUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.MyEditText;
import org.thoughtcrime.securesms.groups.ui.GroupMemberListView;
import org.thoughtcrime.securesms.groups.ui.creategroup.CreateGroupActivity;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.StickyHeaderDecoration;
import org.thoughtcrime.securesms.util.adapter.FixedViewsAdapter;
import org.thoughtcrime.securesms.util.adapter.RecyclerViewConcatenateAdapterStickyHeader;
import org.thoughtcrime.securesms.util.text.AfterTextChanged;

import java.util.Arrays;
import java.util.List;

public class AddGroupDetailsFragment extends LoggingFragment{

  private static final String TAG = Log.tag(AddGroupDetailsFragment.class);

  private static final short REQUEST_DISAPPEARING_TIMER  = 28621;

  private MyEditText name;
  private TextView create;
  private TextView addMembers;
  private RelativeLayout rlContainer;
  private GroupMemberListView members;
  private Callback                 callback;
  private AddGroupDetailsViewModel viewModel;
  private View                     disappearingMessagesRow;

  private int mFocusHeight;
  private int mNormalHeight;
  private int mNormalPaddingX;
  private int mFocusPaddingX;
  private int mFocusTextSize;
  private int mNormalTextSize;

  private FixedViewsAdapter editGroupNameAdapter, addMembersAdapter, createGroupAdapter;

  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);

    if (context instanceof Callback) {
      callback = (Callback) context;
    } else {
      throw new ClassCastException("Parent context should implement AddGroupDetailsFragment.Callback");
    }
  }

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater,
                                     @Nullable ViewGroup container,
                                     @Nullable Bundle savedInstanceState)
  {
    return inflater.inflate(R.layout.add_group_details_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    Log.i(TAG, "onViewCreated");
    members    = view.findViewById(R.id.member_list);
    rlContainer              = view.findViewById(R.id.rl_container);
    View                mmsWarning = view.findViewById(R.id.mms_warning);
    disappearingMessagesRow = view.findViewById(R.id.group_disappearing_messages_row);

    Resources res = getContext().getResources();
    mFocusHeight = 56;//res.getDimensionPixelSize(R.dimen.focus_item_height);
    mNormalHeight = 32;//res.getDimensionPixelSize(R.dimen.item_height);
    mFocusTextSize = res.getDimensionPixelSize(R.dimen.focus_item_textsize);
    mNormalTextSize = res.getDimensionPixelSize(R.dimen.item_textsize);
    mFocusPaddingX = res.getDimensionPixelSize(R.dimen.focus_item_padding_x);
    mNormalPaddingX = res.getDimensionPixelSize(R.dimen.item_padding_x);

    initializeViewModel();

    initializeCursor();
    members.setRecipientClickListener(this::handleRecipientClick);
    members.setRecipientFocusChangeListener(this::handleRecipientFocusChange);

    create.setOnClickListener(v -> handleCreateClicked());
//    addMembers.setOnClickListener(v -> handleAddMemberClicked());
    viewModel.getMembers().observe(getViewLifecycleOwner(), list -> {
      members.setMembers(list);
    });

//    viewModel.getCanSubmitForm().observe(getViewLifecycleOwner(), isFormValid -> setCreateEnabled(isFormValid, true));
    viewModel.getIsMms().observe(getViewLifecycleOwner(), isMms -> {
      disappearingMessagesRow.setVisibility(isMms ? View.GONE : View.VISIBLE);
      mmsWarning.setVisibility(isMms ? View.VISIBLE : View.GONE);
      name.setHint(isMms ? R.string.AddGroupDetailsFragment__group_name_optional : R.string.AddGroupDetailsFragment__group_name_required);
    });
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

  private void initializeCursor() {
    RecyclerViewConcatenateAdapterStickyHeader concatenateAdapter = new RecyclerViewConcatenateAdapterStickyHeader();
    editGroupNameAdapter = new FixedViewsAdapter(requireContext(), 72, rlContainer,createEditGroupNameActionView());
    concatenateAdapter.addAdapter(editGroupNameAdapter);
//    addMembersAdapter = new FixedViewsAdapter(requireContext(), 72, rlContainer,createAddMembersActionView());
//    concatenateAdapter.addAdapter(addMembersAdapter);
    createGroupAdapter = new FixedViewsAdapter(requireContext(), 72, rlContainer,createNewGroupActionView());
    concatenateAdapter.addAdapter(createGroupAdapter);

    concatenateAdapter.addAdapter(members.getMemberListAdapter());
    members.setAdapter(concatenateAdapter);
    members.setClipToPadding(false);
    members.setClipChildren(false);
    members.setPadding(0, 76, 0, 200);
    members.addItemDecoration(new StickyHeaderDecoration(concatenateAdapter, true, true));
  }

  private View createEditGroupNameActionView() {
    View view = LayoutInflater.from(requireContext())
            .inflate(R.layout.new_group_edit_group_name_action_item, (ViewGroup) requireView(), false);
    name = (MyEditText)view;
    EditTextUtil.addGraphemeClusterLimitFilter(name, FeatureFlags.getMaxGroupNameGraphemeLength());
    name.addTextChangedListener(new AfterTextChanged(editable -> viewModel.setName(editable.toString())));
    return view;
  }

  private View createAddMembersActionView() {
    View view = LayoutInflater.from(requireContext())
            .inflate(R.layout.new_group_add_members_action_item, (ViewGroup) requireView(), false);
    addMembers = (TextView)view;
    return view;
  }

  private View createNewGroupActionView() {
    View view = LayoutInflater.from(requireContext())
            .inflate(R.layout.new_group_create_group_action_item, (ViewGroup) requireView(), false);
    create = (TextView)view;
    return view;
  }

  private void initializeViewModel() {
    AddGroupDetailsFragmentArgs      args       = AddGroupDetailsFragmentArgs.fromBundle(requireArguments());
    AddGroupDetailsRepository        repository = new AddGroupDetailsRepository(requireContext());
    AddGroupDetailsViewModel.Factory factory    = new AddGroupDetailsViewModel.Factory(Arrays.asList(args.getRecipientIds()), repository);

    viewModel = ViewModelProviders.of(this, factory).get(AddGroupDetailsViewModel.class);

    viewModel.getGroupCreateResult().observe(getViewLifecycleOwner(), this::handleGroupCreateResult);
  }

  private void handleCreateClicked() {
    Log.v(TAG, "handleCreateClicked" );
    viewModel.create();
  }

  private void handleAddMemberClicked() {
    Intent addMemberIntent = CreateGroupActivity.newIntent(getActivity());
    startActivityForResult(addMemberIntent, 1);
  }

  private void handleRecipientClick(@NonNull Recipient recipient) {
    new AlertDialog.Builder(requireContext())
                   .setMessage(getString(R.string.AddGroupDetailsFragment__remove_s_from_this_group, recipient.getDisplayName(requireContext())))
                   .setCancelable(true)
                   .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel())
                   .setPositiveButton(R.string.AddGroupDetailsFragment__remove, (dialog, which) -> {
                     viewModel.delete(recipient.getId());
                     dialog.dismiss();
                   })
                   .show();
  }

  private void handleRecipientFocusChange(@NonNull View view, boolean isFocus) {
    startFocusAnimation(view, isFocus);
  }

  private void handleGroupCreateResult(@NonNull GroupCreateResult groupCreateResult) {
    Log.d(TAG, "handleGroupCreateResult ");
    groupCreateResult.consume(this::handleGroupCreateResultSuccess, this::handleGroupCreateResultError);
  }

  private void handleGroupCreateResultSuccess(@NonNull GroupCreateResult.Success success) {
    Log.d(TAG, "handleGroupCreateResultSuccess ");
    callback.onGroupCreated(success.getGroupRecipient().getId(), success.getThreadId(), success.getInvitedMembers());
  }

  private void handleGroupCreateResultError(@NonNull GroupCreateResult.Error error) {
    Log.d(TAG, "handleGroupCreateResultError type : " + error.getErrorType());
    switch (error.getErrorType()) {
      case ERROR_IO:
      case ERROR_BUSY:
        toast(R.string.AddGroupDetailsFragment__try_again_later);
        break;
      case ERROR_FAILED:
        toast(R.string.AddGroupDetailsFragment__group_creation_failed);
        break;
      case ERROR_INVALID_NAME:
        toast(R.string.AddGroupDetailsFragment__this_field_is_required);
        break;
      default:
        throw new IllegalStateException("Unexpected error: " + error.getErrorType().name());
    }
  }

  private void toast(@StringRes int toastStringId) {
    Toast.makeText(requireContext(), toastStringId, Toast.LENGTH_SHORT)
         .show();
  }

  private void setCreateEnabled(boolean isEnabled, boolean animate) {
    if (create.isEnabled() == isEnabled) {
      return;
    }

    create.setEnabled(isEnabled);
  }

  public interface Callback {
    void onGroupCreated(@NonNull RecipientId recipientId, long threadId, @NonNull List<Recipient> invitedMembers);
    void onNavigationButtonPressed();
  }
}
