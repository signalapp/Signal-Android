package org.thoughtcrime.securesms.reactions;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.loader.app.LoaderManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.ThemeUtil;

public final class ReactionsBottomSheetDialogFragment extends BottomSheetDialogFragment {

  private static final String ARGS_MESSAGE_ID = "reactions.args.message.id";
  private static final String ARGS_IS_MMS     = "reactions.args.is.mms";

  private long                      messageId;
  private RecyclerView              recipientRecyclerView;
  private RecyclerView              emojiRecyclerView;
  private ReactionsLoader           reactionsLoader;
  private ReactionRecipientsAdapter recipientsAdapter;
  private ReactionEmojiCountAdapter emojiCountAdapter;
  private ReactionsViewModel        viewModel;

  public static DialogFragment create(long messageId, boolean isMms) {
    Bundle         args     = new Bundle();
    DialogFragment fragment = new ReactionsBottomSheetDialogFragment();

    args.putLong(ARGS_MESSAGE_ID, messageId);
    args.putBoolean(ARGS_IS_MMS, isMms);

    fragment.setArguments(args);

    return fragment;
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {

    if (ThemeUtil.isDarkTheme(requireContext())) {
      setStyle(DialogFragment.STYLE_NORMAL, R.style.Theme_Design_BottomSheetDialog_Fixed);
    } else {
      setStyle(DialogFragment.STYLE_NORMAL, R.style.Theme_Design_Light_BottomSheetDialog_Fixed);
    }

    super.onCreate(savedInstanceState);
  }

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater,
                                     @Nullable ViewGroup container,
                                     @Nullable Bundle savedInstanceState)
  {
    return inflater.inflate(R.layout.reactions_bottom_sheet_dialog_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    recipientRecyclerView = view.findViewById(R.id.reactions_bottom_view_recipient_recycler);
    emojiRecyclerView     = view.findViewById(R.id.reactions_bottom_view_emoji_recycler);

    emojiRecyclerView.setNestedScrollingEnabled(false);
    messageId = getArguments().getLong(ARGS_MESSAGE_ID);

    setUpRecipientsRecyclerView();
    setUpEmojiRecyclerView();
    setUpViewModel();

    LoaderManager.getInstance(requireActivity()).initLoader((int) messageId, null, reactionsLoader);
  }

  @Override
  public void onDestroyView() {
    LoaderManager.getInstance(requireActivity()).destroyLoader((int) messageId);
    super.onDestroyView();
  }

  private void setUpRecipientsRecyclerView() {
    recipientsAdapter = new ReactionRecipientsAdapter();
    recipientRecyclerView.setAdapter(recipientsAdapter);
  }

  private void setUpEmojiRecyclerView() {
    emojiCountAdapter = new ReactionEmojiCountAdapter((emoji -> viewModel.setFilterEmoji(emoji)));
    emojiRecyclerView.setAdapter(emojiCountAdapter);
  }

  private void setUpViewModel() {
    reactionsLoader = new ReactionsLoader(requireContext(),
                                          getArguments().getLong(ARGS_MESSAGE_ID),
                                          getArguments().getBoolean(ARGS_IS_MMS));

    ReactionsViewModel.Factory factory = new ReactionsViewModel.Factory(reactionsLoader);
    viewModel = ViewModelProviders.of(this, factory).get(ReactionsViewModel.class);

    viewModel.getRecipients().observe(getViewLifecycleOwner(), reactions -> {
      if (reactions.size() == 0) dismiss();

      recipientsAdapter.updateData(reactions);
    });

    viewModel.getEmojiCounts().observe(getViewLifecycleOwner(), emojiCounts -> {
      if (emojiCounts.size() == 0) dismiss();

      emojiCountAdapter.updateData(emojiCounts);
    });
  }
}
