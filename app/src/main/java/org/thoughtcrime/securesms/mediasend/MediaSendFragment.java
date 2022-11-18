package org.thoughtcrime.securesms.mediasend;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.viewpager.widget.ViewPager;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.ControllableViewPager;
import org.thoughtcrime.securesms.mms.MediaConstraints;
import org.thoughtcrime.securesms.util.Util;

import java.util.List;
import java.util.Map;

/**
 * Allows the user to edit and caption a set of media items before choosing to send them.
 */
public class MediaSendFragment extends Fragment {

  private ViewGroup                     playbackControlsContainer;
  private ControllableViewPager         fragmentPager;
  private MediaSendFragmentPagerAdapter fragmentPagerAdapter;

  private MediaSendViewModel viewModel;

  public static MediaSendFragment newInstance() {
    Bundle args = new Bundle();

    MediaSendFragment fragment = new MediaSendFragment();
    fragment.setArguments(args);
    return fragment;
  }

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.mediasend_fragment, container, false);
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    initViewModel();
    fragmentPager             = view.findViewById(R.id.mediasend_pager);
    playbackControlsContainer = view.findViewById(R.id.mediasend_playback_controls_container);

    fragmentPagerAdapter = new MediaSendFragmentPagerAdapter(getChildFragmentManager(), viewModel.isSms() ? MediaConstraints.getMmsMediaConstraints(-1) : MediaConstraints.getPushMediaConstraints(null));
    fragmentPager.setAdapter(fragmentPagerAdapter);

    FragmentPageChangeListener pageChangeListener = new FragmentPageChangeListener();
    fragmentPager.addOnPageChangeListener(pageChangeListener);
    fragmentPager.post(() -> pageChangeListener.onPageSelected(fragmentPager.getCurrentItem()));
  }

  @Override
  public void onStart() {
    super.onStart();

    fragmentPagerAdapter.restoreState(viewModel.getDrawState());
    viewModel.onImageEditorStarted();
  }

  @Override
  public void onHiddenChanged(boolean hidden) {
    super.onHiddenChanged(hidden);
    if (!hidden) {
      viewModel.onImageEditorStarted();
    } else {
      fragmentPagerAdapter.notifyHidden();
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    fragmentPagerAdapter.notifyHidden();
  }

  @Override
  public void onStop() {
    super.onStop();
    fragmentPagerAdapter.saveAllState();
    viewModel.saveDrawState(fragmentPagerAdapter.getSavedState());
  }

  public void onTouchEventsNeeded(boolean needed) {
    if (fragmentPager != null) {
      fragmentPager.setEnabled(!needed);
    }
  }

  public List<Media> getAllMedia() {
    return fragmentPagerAdapter.getAllMedia();
  }

  public @NonNull Map<Uri, Object> getSavedState() {
    return fragmentPagerAdapter.getSavedState();
  }

  public int getCurrentImagePosition() {
    return fragmentPager.getCurrentItem();
  }

  private void initViewModel() {
    viewModel = ViewModelProviders.of(requireActivity(), new MediaSendViewModel.Factory(requireActivity().getApplication(), new MediaRepository())).get(MediaSendViewModel.class);

    viewModel.getSelectedMedia().observe(getViewLifecycleOwner(), media -> {
      if (Util.isEmpty(media)) {
        return;
      }

      fragmentPagerAdapter.setMedia(media);
    });

    viewModel.getPosition().observe(getViewLifecycleOwner(), position -> {
      if (position == null || position < 0) return;

      fragmentPager.setCurrentItem(position, true);

      View playbackControls = fragmentPagerAdapter.getPlaybackControls(position);

      if (playbackControls != null) {
        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        playbackControls.setLayoutParams(params);
        playbackControlsContainer.removeAllViews();
        playbackControlsContainer.addView(playbackControls);
      } else {
        playbackControlsContainer.removeAllViews();
      }
    });
  }

  void pausePlayback() {
    fragmentPagerAdapter.pausePlayback();
  }

  private class FragmentPageChangeListener extends ViewPager.SimpleOnPageChangeListener {
    @Override
    public void onPageSelected(int position) {
      viewModel.onPageChanged(position);
      fragmentPagerAdapter.notifyPageChanged(position);
    }
  }
}
