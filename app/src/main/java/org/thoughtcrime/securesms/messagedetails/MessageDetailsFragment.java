package org.thoughtcrime.securesms.messagedetails;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.FullScreenDialogFragment;
import org.thoughtcrime.securesms.conversation.colors.Colorizer;
import org.thoughtcrime.securesms.conversation.colors.RecyclerViewColorizer;
import org.thoughtcrime.securesms.conversation.ui.edit.EditMessageHistoryDialog;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4PlaybackController;
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4ProjectionPlayerHolder;
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4ProjectionRecycler;
import org.thoughtcrime.securesms.messagedetails.MessageDetailsAdapter.MessageDetailsViewState;
import org.thoughtcrime.securesms.messagedetails.MessageDetailsViewModel.Factory;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.safety.SafetyNumberBottomSheet;
import org.thoughtcrime.securesms.util.Material3OnScrollHelper;
import org.thoughtcrime.securesms.util.MessageRecordUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class MessageDetailsFragment extends FullScreenDialogFragment implements MessageDetailsAdapter.Callbacks {

  private static final String MESSAGE_ID_EXTRA = "message_id";
  private static final String RECIPIENT_EXTRA  = "recipient_id";

  private RequestManager          requestManager;
  private MessageDetailsViewModel viewModel;
  private MessageDetailsAdapter   adapter;
  private Colorizer               colorizer;
  private RecyclerViewColorizer   recyclerViewColorizer;

  public static @NonNull DialogFragment create(@NonNull MessageRecord message, @NonNull RecipientId recipientId) {
    DialogFragment dialogFragment = new MessageDetailsFragment();
    Bundle         args           = new Bundle();

    args.putLong(MESSAGE_ID_EXTRA, message.getId());
    args.putParcelable(RECIPIENT_EXTRA, recipientId);

    dialogFragment.setArguments(args);

    return dialogFragment;
  }

  @Override
  protected int getTitle() {
    return R.string.AndroidManifest__message_details;
  }

  @Override
  protected int getDialogLayoutResource() {
    return R.layout.message_details_fragment;
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    requestManager = Glide.with(this);

    initializeList(view);
    initializeViewModel();
    initializeVideoPlayer(view);
  }

  @Override
  public void onDismiss(@NonNull DialogInterface dialog) {
    super.onDismiss(dialog);

    if (getActivity() instanceof Callback) {
      ((Callback) getActivity()).onMessageDetailsFragmentDismissed();
    } else if (getParentFragment() instanceof Callback) {
      ((Callback) getParentFragment()).onMessageDetailsFragmentDismissed();
    }
  }

  private void initializeList(@NonNull View view) {
    RecyclerView list          = view.findViewById(R.id.message_details_list);
    View         toolbarShadow = view.findViewById(R.id.toolbar_shadow);

    colorizer             = new Colorizer();
    adapter               = new MessageDetailsAdapter(getViewLifecycleOwner(), requestManager, colorizer, this);
    recyclerViewColorizer = new RecyclerViewColorizer(list);

    list.setAdapter(adapter);
    list.setItemAnimator(null);
    new Material3OnScrollHelper(requireActivity(), toolbarShadow, getViewLifecycleOwner()).attach(list);
  }

  private void initializeViewModel() {
    final RecipientId recipientId = requireArguments().getParcelable(RECIPIENT_EXTRA);
    final Long        messageId   = requireArguments().getLong(MESSAGE_ID_EXTRA, -1);
    final Factory     factory     = new Factory(recipientId, messageId);

    viewModel = new ViewModelProvider(this, factory).get(MessageDetailsViewModel.class);
    viewModel.getMessageDetails().observe(this, details -> {
      if (details == null) {
        dismissAllowingStateLoss();
      } else {
        adapter.submitList(convertToRows(details));
      }
    });
    viewModel.getRecipient().observe(this, recipient -> recyclerViewColorizer.setChatColors(recipient.getChatColors()));
  }

  private void initializeVideoPlayer(@NonNull View view) {
    FrameLayout                          videoContainer = view.findViewById(R.id.video_container);
    RecyclerView                         recyclerView   = view.findViewById(R.id.message_details_list);
    List<GiphyMp4ProjectionPlayerHolder> holders        = GiphyMp4ProjectionPlayerHolder.injectVideoViews(requireContext(), getLifecycle(), videoContainer, 1);
    GiphyMp4ProjectionRecycler           callback       = new GiphyMp4ProjectionRecycler(holders);

    GiphyMp4PlaybackController.attach(recyclerView, callback, 1);
  }

  private List<MessageDetailsViewState<?>> convertToRows(MessageDetails details) {
    List<MessageDetailsViewState<?>> list = new ArrayList<>();

    list.add(new MessageDetailsViewState<>(details.getConversationMessage(), MessageDetailsViewState.MESSAGE_HEADER));

    if (MessageRecordUtil.isEditMessage(details.getConversationMessage().getMessageRecord())) {
      list.add(new MessageDetailsViewState<>(details.getConversationMessage().getMessageRecord(), MessageDetailsViewState.EDIT_HISTORY));
    }

    if (details.getConversationMessage().getMessageRecord().isOutgoing()) {
      addRecipients(list, RecipientHeader.NOT_SENT, details.getNotSent());
      addRecipients(list, RecipientHeader.VIEWED, details.getViewed());
      addRecipients(list, RecipientHeader.READ, details.getRead());
      addRecipients(list, RecipientHeader.DELIVERED, details.getDelivered());
      addRecipients(list, RecipientHeader.SENT_TO, details.getSent());
      addRecipients(list, RecipientHeader.PENDING, details.getPending());
      addRecipients(list, RecipientHeader.SKIPPED, details.getSkipped());
    } else {
      addRecipients(list, RecipientHeader.SENT_FROM, details.getSent());
    }

    return list;
  }

  private boolean addRecipients(List<MessageDetailsViewState<?>> list, RecipientHeader header, Collection<RecipientDeliveryStatus> recipients) {
    if (recipients.isEmpty()) {
      return false;
    }

    list.add(new MessageDetailsViewState<>(header, MessageDetailsViewState.RECIPIENT_HEADER));
    for (RecipientDeliveryStatus status : recipients) {
      list.add(new MessageDetailsViewState<>(status, MessageDetailsViewState.RECIPIENT));
    }
    return true;
  }

  @Override
  public void onErrorClicked(@NonNull MessageRecord messageRecord) {
    SafetyNumberBottomSheet
        .forMessageRecord(requireContext(), messageRecord)
        .show(getChildFragmentManager());
  }

  @Override
  public void onViewEditHistoryClicked(MessageRecord record) {
    if (record.isOutgoing()) {
      EditMessageHistoryDialog.show(getParentFragmentManager(), record.getToRecipient().getId(), record);
    } else {
      EditMessageHistoryDialog.show(getParentFragmentManager(), record.getFromRecipient().getId(), record);
    }
  }

  @Override
  public void onInternalDetailsClicked(MessageRecord record) {
    InternalMessageDetailsFragment.create(record).show(getParentFragmentManager(), InternalMessageDetailsFragment.class.getSimpleName());
  }

  public interface Callback {
    void onMessageDetailsFragmentDismissed();
  }
}
