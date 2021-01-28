package org.thoughtcrime.securesms.conversation.ui.mentions;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;

import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.MappingModel;
import org.thoughtcrime.securesms.util.VibrateUtil;

import java.util.Collections;
import java.util.List;

public class MentionsPickerFragment extends LoggingFragment {

  private       MentionsPickerAdapter     adapter;
  private       RecyclerView              list;
  private       View                      topDivider;
  private       View                      bottomDivider;
  private       BottomSheetBehavior<View> behavior;
  private       MentionsPickerViewModel   viewModel;
  private final Runnable                  lockSheetAfterListUpdate = () -> behavior.setHideable(false);
  private final Handler                   handler                  = new Handler(Looper.getMainLooper());

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.mentions_picker_fragment, container, false);

    list          = view.findViewById(R.id.mentions_picker_list);
    topDivider    = view.findViewById(R.id.mentions_picker_top_divider);
    bottomDivider = view.findViewById(R.id.mentions_picker_bottom_divider);
    behavior      = BottomSheetBehavior.from(view.findViewById(R.id.mentions_picker_bottom_sheet));

    initializeBehavior();

    return view;
  }

  @Override
  public void onActivityCreated(@Nullable Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    viewModel = ViewModelProviders.of(requireActivity()).get(MentionsPickerViewModel.class);

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
          showDividers(false);
        } else {
          showDividers(true);
        }
      }

      @Override
      public void onSlide(@NonNull View bottomSheet, float slideOffset) {
        showDividers(Float.isNaN(slideOffset) || slideOffset > -0.8f);
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
      showDividers(true);
    } else {
      handler.removeCallbacks(lockSheetAfterListUpdate);
      behavior.setHideable(true);
      behavior.setState(BottomSheetBehavior.STATE_HIDDEN);
    }
  }

  private void showDividers(boolean showDividers) {
    topDivider.setVisibility(showDividers ? View.VISIBLE : View.GONE);
    bottomDivider.setVisibility(showDividers ? View.VISIBLE : View.GONE);
  }
}
