package org.thoughtcrime.securesms.giph.mp4;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Fragment which displays GyphyImages.
 */
public class GiphyMp4Fragment extends Fragment {

  private static final String IS_FOR_MMS = "is_for_mms";

  public GiphyMp4Fragment() {
    super(R.layout.giphy_mp4_fragment);
  }

  public static Fragment create(boolean isForMms) {
    Fragment fragment = new GiphyMp4Fragment();
    Bundle   bundle   = new Bundle();

    bundle.putBoolean(IS_FOR_MMS, isForMms);
    fragment.setArguments(bundle);

    return fragment;
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    boolean                                   isForMms           = requireArguments().getBoolean(IS_FOR_MMS, false);
    FrameLayout                               frameLayout        = view.findViewById(R.id.giphy_parent);
    RecyclerView                              recycler           = view.findViewById(R.id.giphy_recycler);
    GiphyMp4ViewModel                         viewModel          = ViewModelProviders.of(requireActivity(), new GiphyMp4ViewModel.Factory(isForMms)).get(GiphyMp4ViewModel.class);
    GiphyMp4MediaSourceFactory                mediaSourceFactory = new GiphyMp4MediaSourceFactory(ApplicationDependencies.getOkHttpClient());
    GiphyMp4Adapter                           adapter            = new GiphyMp4Adapter(mediaSourceFactory, viewModel::saveToBlob);
    List<GiphyMp4PlayerHolder>                holders            = injectVideoViews(frameLayout);
    GiphyMp4AdapterPlaybackControllerCallback callback           = new GiphyMp4AdapterPlaybackControllerCallback(holders);

    recycler.setLayoutManager(new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));
    recycler.setAdapter(adapter);
    recycler.setItemAnimator(null);

    GiphyMp4AdapterPlaybackController.attach(recycler, callback, GiphyMp4PlaybackPolicy.maxSimultaneousPlaybackInSearchResults());

    viewModel.getImages().observe(getViewLifecycleOwner(), adapter::submitList);

    viewModel.getPagingController().observe(getViewLifecycleOwner(), adapter::setPagingController);
  }

  private List<GiphyMp4PlayerHolder> injectVideoViews(@NonNull ViewGroup viewGroup) {
    int                        nPlayers       = GiphyMp4PlaybackPolicy.maxSimultaneousPlaybackInSearchResults();
    List<GiphyMp4PlayerHolder> holders        = new ArrayList<>(nPlayers);
    GiphyMp4ExoPlayerProvider  playerProvider = new GiphyMp4ExoPlayerProvider(requireContext());

    for (int i = 0; i < nPlayers; i++) {
      FrameLayout container = (FrameLayout) LayoutInflater.from(requireContext())
                                                          .inflate(R.layout.giphy_mp4_player, viewGroup, false);
      GiphyMp4VideoPlayer  player    = container.findViewById(R.id.video_player);
      ExoPlayer            exoPlayer = playerProvider.create();
      GiphyMp4PlayerHolder holder    = new GiphyMp4PlayerHolder(container, player);

      getViewLifecycleOwner().getLifecycle().addObserver(player);
      player.setExoPlayer(exoPlayer);
      player.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL);
      exoPlayer.addListener(holder);

      holders.add(holder);
      viewGroup.addView(container);
    }

    return holders;
  }

}
