package org.thoughtcrime.securesms.conversation.ui.mentions;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;

import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.VibrateUtil;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel;

import java.util.Collections;
import java.util.List;

public class MentionsPickerFragment extends LoggingFragment {

  private       MentionsPickerAdapter     adapter;
  private       RecyclerView              list;
  private       BottomSheetBehavior<View> behavior;
  private       MentionsPickerViewModel   viewModel;
  private final Runnable                  lockSheetAfterListUpdate = () -> behavior.setHideable(false);
  private final Handler                   handler                  = new Handler(Looper.getMainLooper());

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.mentions_picker_fragment, container, false);

    list          = view.findViewById(R.id.mentions_picker_list);
    behavior      = BottomSheetBehavior.from(view.findViewById(R.id.mentions_picker_bottom_sheet));

    initializeBehavior();

    return view;
  }

  @Override
  public void onActivityCreated(@Nullable Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    viewModel = new ViewModelProvider(requireActivity()).get(MentionsPickerViewModel.class);

    initializeList();

    viewModel.getMentionList().observe(getViewLifecycleOwner(), this::updateList);

    viewModel.isShowing().observe(getViewLifecycleOwner(), isShowing -> {
      if (isShowing) {
        VibrateUtil.vibrateTick(requireContext());
      }
    });
  }

  private void initializeBehavior() {
    behavior.setHideable(true);
    behavior.setState(BottomSheetBehavior.STATE_HIDDEN);

    behavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
      @Override
      public void onStateChanged(@NonNull View bottomSheet, int newState) {
        if (newState == BottomSheetBehavior.STATE_HIDDEN) {
          adapter.submitList(Collections.emptyList());
        } else {
        }
      }

      @Override
      public void onSlide(@NonNull View bottomSheet, float slideOffset) {
      }
    });
  }

  private void initializeList() {
    adapter = new MentionsPickerAdapter(this::handleMentionClicked, () -> updateBottomSheetBehavior(adapter.getItemCount()));

    list.setLayoutManager(new LinearLayoutManager(requireContext()));
    list.setAdapter(adapter);
    list.setItemAnimator(null);
  }

  private void handleMentionClicked(@NonNull Recipient recipient) {
    viewModel.onSelectionChange(recipient);
  }

  private void updateList(@NonNull List<MappingModel<?>> mappingModels) {
    if (adapter.getItemCount() > 0 && mappingModels.isEmpty()) {
      updateBottomSheetBehavior(0);
    } else {
      adapter.submitList(mappingModels);
    }
  }

  private void updateBottomSheetBehavior(int count) {
    boolean isShowing = count > 0;

    viewModel.setIsShowing(isShowing);

    if (isShowing) {
      list.scrollToPosition(0);
      behavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
      handler.post(lockSheetAfterListUpdate);
    } else {
      handler.removeCallbacks(lockSheetAfterListUpdate);
      behavior.setHideable(true);
      behavior.setState(BottomSheetBehavior.STATE_HIDDEN);
    }
  }
}
