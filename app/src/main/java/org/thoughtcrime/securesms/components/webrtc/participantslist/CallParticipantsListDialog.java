package org.thoughtcrime.securesms.components.webrtc.participantslist;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;

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

public class CallParticipantsListDialog extends DialogFragment {

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
    getDialog().requestWindowFeature(Window.FEATURE_NO_TITLE);
    ContextThemeWrapper contextThemeWrapper = new ContextThemeWrapper(inflater.getContext(), R.style.TextSecure_DarkTheme);
    LayoutInflater      themedInflater      = LayoutInflater.from(contextThemeWrapper);

    participantList = (RecyclerView) themedInflater.inflate(R.layout.call_participants_list_dialog, container, false);
    slideToUp(participantList);
    return participantList;
  }

  @Override
  public void onActivityCreated(@Nullable Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);

    final WebRtcCallViewModel viewModel = ViewModelProviders.of(requireActivity()).get(WebRtcCallViewModel.class);

    initializeList();

    viewModel.getCallParticipantsState().observe(getViewLifecycleOwner(), this::updateList);
  }

  @Override public void onStart() {
    super.onStart();
    initWindow();
  }

  private void initializeList() {
    adapter = new CallParticipantsListAdapter();

    participantList.setLayoutManager(new LinearLayoutManager(requireContext()));
    participantList.setAdapter(adapter);
  }

  private void initWindow() {
    Window window = getDialog().getWindow();
    WindowManager.LayoutParams params = window.getAttributes();
    params.gravity = Gravity.BOTTOM;
    params.width = WindowManager.LayoutParams.MATCH_PARENT;
    window.setAttributes(params);
    window.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
  }

  public static void slideToUp(View view){
    Animation slide = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 0.0f,
                                             Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF,
                                             1.0f, Animation.RELATIVE_TO_SELF, 0.0f);
    slide.setDuration(400);
    slide.setFillAfter(true);
    slide.setFillEnabled(true);
    view.startAnimation(slide);
    slide.setAnimationListener(new Animation.AnimationListener() {
      @Override
      public void onAnimationStart(Animation animation) {
      }
      @Override
      public void onAnimationEnd(Animation animation) {
      }
      @Override
      public void onAnimationRepeat(Animation animation) {
      }
    });
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
