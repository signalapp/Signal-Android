package org.thoughtcrime.securesms.components.webrtc.participantslist;

import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.OptionalLong;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.webrtc.CallParticipantsState;
import org.thoughtcrime.securesms.components.webrtc.WebRtcCallViewModel;
import org.thoughtcrime.securesms.events.CallParticipant;
import org.thoughtcrime.securesms.events.WebRtcViewModel;
import org.thoughtcrime.securesms.util.BottomSheetUtil;
import org.thoughtcrime.securesms.util.MappingModel;

import java.util.ArrayList;
import java.util.List;

public class CallParticipantsListDialog extends BottomSheetDialogFragment {

  private RecyclerView                participantList;
  private CallParticipantsListAdapter adapter;

  public static void show(@NonNull FragmentManager manager) {
    CallParticipantsListDialog fragment = new CallParticipantsListDialog();

    fragment.show(manager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG);
  }

  public static void dismiss(@NonNull FragmentManager manager) {
    Fragment fragment = manager.findFragmentByTag(BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG);
    if (fragment instanceof CallParticipantsListDialog) {
      ((CallParticipantsListDialog) fragment).dismissAllowingStateLoss();
    }
  }

  @Override
  public void show(@NonNull FragmentManager manager, @Nullable String tag) {
    BottomSheetUtil.show(manager, tag, this);
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    setStyle(DialogFragment.STYLE_NORMAL, R.style.Theme_Signal_RoundedBottomSheet);
    super.onCreate(savedInstanceState);
  }

  @Override
  public @NonNull View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    ContextThemeWrapper contextThemeWrapper = new ContextThemeWrapper(inflater.getContext(), R.style.TextSecure_DarkTheme);
    LayoutInflater      themedInflater      = LayoutInflater.from(contextThemeWrapper);

    participantList = (RecyclerView) themedInflater.inflate(R.layout.call_participants_list_dialog, container, false);

    return participantList;
  }

  @Override
  public void onActivityCreated(@Nullable Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);

    final WebRtcCallViewModel viewModel = ViewModelProviders.of(requireActivity()).get(WebRtcCallViewModel.class);

    initializeList();

    viewModel.getCallParticipantsState().observe(getViewLifecycleOwner(), this::updateList);
  }

  private void initializeList() {
    adapter = new CallParticipantsListAdapter();

    participantList.setLayoutManager(new LinearLayoutManager(requireContext()));
    participantList.setAdapter(adapter);
  }

  private void updateList(@NonNull CallParticipantsState callParticipantsState) {
    List<MappingModel<?>> items = new ArrayList<>();

    boolean      includeSelf = callParticipantsState.getGroupCallState() == WebRtcViewModel.GroupCallState.CONNECTED_AND_JOINED;
    OptionalLong headerCount = callParticipantsState.getParticipantCount();

    headerCount.executeIfPresent(count -> {
      items.add(new CallParticipantsListHeader((int) count));

      if (includeSelf) {
        items.add(new CallParticipantViewState(callParticipantsState.getLocalParticipant()));
      }

      for (CallParticipant callParticipant : callParticipantsState.getAllRemoteParticipants()) {
        items.add(new CallParticipantViewState(callParticipant));
      }
    });

    adapter.submitList(items);
  }
}
