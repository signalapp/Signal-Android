package org.thoughtcrime.securesms.profiles.spoofing;

import android.content.Intent;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.PluralsRes;
import androidx.annotation.StringRes;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.FullScreenDialogFragment;
import org.thoughtcrime.securesms.groups.BadGroupIdException;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.ui.bottomsheet.RecipientBottomSheetDialogFragment;
import org.thoughtcrime.securesms.stories.settings.my.SignalConnectionsBottomSheetDialogFragment;
import org.thoughtcrime.securesms.util.BottomSheetUtil;

public class ReviewCardDialogFragment extends FullScreenDialogFragment {

  private static final String EXTRA_TITLE_RES_ID               = "extra.title.res.id";
  private static final String EXTRA_DESCRIPTION_RES_ID         = "extra.description.res.id";
  private static final String EXTRA_GROUPS_IN_COMMON_RES_ID    = "extra.groups.in.common.res.id";
  private static final String EXTRA_NO_GROUPS_IN_COMMON_RES_ID = "extra.no.groups.in.common.res.id";
  private static final String EXTRA_RECIPIENT_ID               = "extra.recipient.id";
  private static final String EXTRA_GROUP_ID                   = "extra.group.id";

  private ReviewCardViewModel viewModel;

  public static ReviewCardDialogFragment createForReviewRequest(@NonNull RecipientId recipientId) {
    return create(R.string.ReviewCardDialogFragment__review_request,
                  R.string.ReviewCardDialogFragment__if_youre_not_sure,
                  R.string.ReviewCardDialogFragment__no_groups_in_common,
                  R.plurals.ReviewCardDialogFragment__d_groups_in_common,
                  recipientId,
                  null);
  }

  public static ReviewCardDialogFragment createForReviewMembers(@NonNull GroupId.V2 groupId) {
    return create(R.string.ReviewCardDialogFragment__review_members,
                  R.string.ReviewCardDialogFragment__d_group_members_have_the_same_name,
                  R.string.ReviewCardDialogFragment__no_other_groups_in_common,
                  R.plurals.ReviewCardDialogFragment__d_other_groups_in_common,
                  null,
                  groupId);
  }

  private static ReviewCardDialogFragment create(@StringRes int titleResId,
                                                 @StringRes int descriptionResId,
                                                 @StringRes int noGroupsInCommonResId,
                                                 @PluralsRes int groupsInCommonResId,
                                                 @Nullable RecipientId recipientId,
                                                 @Nullable GroupId.V2 groupId)
  {
    ReviewCardDialogFragment fragment = new ReviewCardDialogFragment();
    Bundle                   args     = new Bundle();

    args.putInt(EXTRA_TITLE_RES_ID, titleResId);
    args.putInt(EXTRA_DESCRIPTION_RES_ID, descriptionResId);
    args.putInt(EXTRA_GROUPS_IN_COMMON_RES_ID, groupsInCommonResId);
    args.putInt(EXTRA_NO_GROUPS_IN_COMMON_RES_ID, noGroupsInCommonResId);
    args.putParcelable(EXTRA_RECIPIENT_ID, recipientId);
    args.putString(EXTRA_GROUP_ID, groupId != null ? groupId.toString() : null);

    fragment.setArguments(args);

    return fragment;
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    try {
      initializeViewModel();
    } catch (BadGroupIdException e) {
      throw new IllegalStateException(e);
    }

    TextView     description = view.findViewById(R.id.description);
    RecyclerView recycler    = view.findViewById(R.id.recycler);

    ReviewCardAdapter adapter = new ReviewCardAdapter(getNoGroupsInCommonResId(), getGroupsInCommonResId(), new AdapterCallbacks());
    recycler.setAdapter(adapter);

    viewModel.getReviewCards().observe(getViewLifecycleOwner(), cards -> {
      adapter.submitList(cards);
      description.setText(getString(getDescriptionResId(), cards.size()));
    });

    viewModel.getReviewEvents().observe(getViewLifecycleOwner(), this::onReviewEvent);
  }

  private void initializeViewModel() throws BadGroupIdException {
    ReviewCardRepository        repository = getRepository();
    ReviewCardViewModel.Factory factory    = new ReviewCardViewModel.Factory(repository, getGroupId());

    viewModel = new ViewModelProvider(this, factory).get(ReviewCardViewModel.class);
  }

  private @StringRes int getDescriptionResId() {
    return requireArguments().getInt(EXTRA_DESCRIPTION_RES_ID);
  }

  private @PluralsRes int getGroupsInCommonResId() {
    return requireArguments().getInt(EXTRA_GROUPS_IN_COMMON_RES_ID);
  }

  private @StringRes int getNoGroupsInCommonResId() {
    return requireArguments().getInt(EXTRA_NO_GROUPS_IN_COMMON_RES_ID);
  }

  private @Nullable RecipientId getRecipientId() {
    return requireArguments().getParcelable(EXTRA_RECIPIENT_ID);
  }

  private @Nullable GroupId.V2 getGroupId() throws BadGroupIdException {
    GroupId groupId = GroupId.parseNullable(requireArguments().getString(EXTRA_GROUP_ID));

    if (groupId != null) {
      return groupId.requireV2();
    } else {
      return null;
    }
  }

  private @NonNull ReviewCardRepository getRepository() throws BadGroupIdException {
    RecipientId recipientId = getRecipientId();
    GroupId.V2  groupId     = getGroupId();

    if (recipientId != null) {
      return new ReviewCardRepository(requireContext(), recipientId);
    } else if (groupId != null) {
      return new ReviewCardRepository(requireContext(), groupId);
    } else {
      throw new AssertionError();
    }
  }

  private void onReviewEvent(ReviewCardViewModel.Event reviewEvent) {
    switch (reviewEvent) {
      case DISMISS:
        dismiss();
        break;
      case REMOVE_FAILED:
        toast(R.string.ReviewCardDialogFragment__failed_to_remove_group_member);
        break;
      default:
        throw new IllegalArgumentException("Unhandled event: " + reviewEvent);
    }
  }

  private void toast(@StringRes int message) {
    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
  }

  @Override
  protected int getTitle() {
    return requireArguments().getInt(EXTRA_TITLE_RES_ID);
  }

  @Override
  protected int getDialogLayoutResource() {
    return R.layout.fragment_review;
  }

  private final class AdapterCallbacks implements ReviewCardAdapter.Callbacks {

    @Override
    public void onCardClicked(@NonNull ReviewCard card) {
      RecipientBottomSheetDialogFragment.show(getParentFragmentManager(), card.getReviewRecipient().getId(), null);
    }

    @Override
    public void onActionClicked(@NonNull ReviewCard card, @NonNull ReviewCard.Action action) {
      switch (action) {
        case UPDATE_CONTACT:
          Intent contactEditIntent = new Intent(Intent.ACTION_EDIT);
          contactEditIntent.setDataAndType(card.getReviewRecipient().getContactUri(), ContactsContract.Contacts.CONTENT_ITEM_TYPE);
          startActivity(contactEditIntent);
          break;
        case REMOVE_FROM_GROUP:
          new MaterialAlertDialogBuilder(requireContext())
                         .setMessage(getString(R.string.ReviewCardDialogFragment__remove_s_from_group,
                                               card.getReviewRecipient().getDisplayName(requireContext())))
                         .setPositiveButton(R.string.ReviewCardDialogFragment__remove, (dialog, which) -> {
                           viewModel.act(card, action);
                           dialog.dismiss();
                         })
                         .setNegativeButton(android.R.string.cancel,
                                            (dialog, which) -> dialog.dismiss())
                         .setCancelable(true)
                         .show();
          break;
        default:
          viewModel.act(card, action);
      }
    }

    @Override
    public void onSignalConnectionClicked() {
      new SignalConnectionsBottomSheetDialogFragment().show(getParentFragmentManager(), BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG);
    }
  }
}
