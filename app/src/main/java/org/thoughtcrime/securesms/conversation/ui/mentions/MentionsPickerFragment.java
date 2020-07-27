package org.thoughtcrime.securesms.conversation.ui.mentions;

import android.os.Bundle;
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
import org.thoughtcrime.securesms.util.ViewUtil;

import java.util.List;

public class MentionsPickerFragment extends LoggingFragment {

  private MentionsPickerAdapter     adapter;
  private RecyclerView              list;
  private BottomSheetBehavior<View> behavior;
  private MentionsPickerViewModel   viewModel;

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.mentions_picker_fragment, container, false);

    list     = view.findViewById(R.id.mentions_picker_list);
    behavior = BottomSheetBehavior.from(view.findViewById(R.id.mentions_picker_bottom_sheet));

    return view;
  }

  @Override
  public void onActivityCreated(@Nullable Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    initializeList();

    viewModel = ViewModelProviders.of(requireActivity()).get(MentionsPickerViewModel.class);
    viewModel.getMentionList().observe(getViewLifecycleOwner(), this::updateList);
  }

  private void initializeList() {
    adapter = new MentionsPickerAdapter(this::handleMentionClicked);

    RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(requireContext()) {
      @Override
      public void onLayoutCompleted(RecyclerView.State state) {
        super.onLayoutCompleted(state);
        updateBottomSheetBehavior(adapter.getItemCount());
      }
    };

    list.setLayoutManager(layoutManager);
    list.setAdapter(adapter);
    list.setItemAnimator(null);
  }

  private void handleMentionClicked(@NonNull Recipient recipient) {
    viewModel.onSelectionChange(recipient);
  }

  private void updateList(@NonNull List<MappingModel<?>> mappingModels) {
    adapter.submitList(mappingModels);
    if (mappingModels.isEmpty()) {
      updateBottomSheetBehavior(0);
    }
  }

  private void updateBottomSheetBehavior(int count) {
    if (count > 0) {
      if (behavior.getPeekHeight() == 0) {
        behavior.setPeekHeight(ViewUtil.dpToPx(240), true);
        behavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
      }
    } else {
      behavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
      behavior.setPeekHeight(0);
    }
  }
}
