package org.thoughtcrime.securesms.giph.mp4;

import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import org.thoughtcrime.securesms.R;

import java.util.List;

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
    ContentLoadingProgressBar                 progressBar        = view.findViewById(R.id.content_loading);
    TextView                                  nothingFound       = view.findViewById(R.id.nothing_found);
    GiphyMp4ViewModel                         viewModel          = new ViewModelProvider(requireActivity(), new GiphyMp4ViewModel.Factory(isForMms)).get(GiphyMp4ViewModel.class);
    GiphyMp4Adapter                           adapter            = new GiphyMp4Adapter(viewModel::saveToBlob);
    List<GiphyMp4ProjectionPlayerHolder>      holders            = GiphyMp4ProjectionPlayerHolder.injectVideoViews(requireContext(),
                                                                                                                   getViewLifecycleOwner().getLifecycle(),
                                                                                                                   frameLayout,
                                                                                                                   GiphyMp4PlaybackPolicy.maxSimultaneousPlaybackInSearchResults());
    GiphyMp4ProjectionRecycler callback = new GiphyMp4ProjectionRecycler(holders);

    recycler.setLayoutManager(new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));
    recycler.setAdapter(adapter);
    recycler.setItemAnimator(null);
    progressBar.show();

    GiphyMp4PlaybackController.attach(recycler, callback, GiphyMp4PlaybackPolicy.maxSimultaneousPlaybackInSearchResults());
    viewModel.getImages().observe(getViewLifecycleOwner(), images -> {
      nothingFound.setVisibility(images.isEmpty() ? View.VISIBLE : View.INVISIBLE);
      adapter.submitList(images, progressBar::hide);
    });
    viewModel.getPagingController().observe(getViewLifecycleOwner(), adapter::setPagingController);
    viewModel.getPagedData().observe(getViewLifecycleOwner(), unused -> recycler.scrollToPosition(0));
  }
}
