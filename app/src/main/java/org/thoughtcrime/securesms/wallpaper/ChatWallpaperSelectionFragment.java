package org.thoughtcrime.securesms.wallpaper;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.flexbox.FlexboxLayoutManager;
import com.google.android.flexbox.JustifyContent;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.ActivityTransitionUtil;

public class ChatWallpaperSelectionFragment extends Fragment {

  private static final short CHOOSE_PHOTO = 1;
  private static final short PREVIEW      = 2;

  private ChatWallpaperViewModel viewModel;

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.chat_wallpaper_selection_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    View                 chooseFromPhotos     = view.findViewById(R.id.chat_wallpaper_choose_from_photos);
    RecyclerView         recyclerView         = view.findViewById(R.id.chat_wallpaper_recycler);
    FlexboxLayoutManager flexboxLayoutManager = new FlexboxLayoutManager(requireContext());

    chooseFromPhotos.setOnClickListener(unused -> {
      // Navigate to photo selection (akin to what we did for profile avatar selection.)
      //startActivityForResult(..., CHOOSE_PHOTO);
    });

    @SuppressWarnings("CodeBlock2Expr")
    ChatWallpaperSelectionAdapter adapter = new ChatWallpaperSelectionAdapter(chatWallpaper -> {
      startActivityForResult(ChatWallpaperPreviewActivity.create(requireActivity(), chatWallpaper, viewModel.getDimInDarkTheme().getValue()), PREVIEW);
      ActivityTransitionUtil.setSlideInTransition(requireActivity());
    });

    flexboxLayoutManager.setJustifyContent(JustifyContent.SPACE_AROUND);
    recyclerView.setLayoutManager(flexboxLayoutManager);
    recyclerView.setAdapter(adapter);

    viewModel = ViewModelProviders.of(requireActivity()).get(ChatWallpaperViewModel.class);
    viewModel.getWallpapers().observe(getViewLifecycleOwner(), adapter::submitList);
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    if (requestCode == CHOOSE_PHOTO && resultCode == Activity.RESULT_OK && data != null) {
      Uri uri = data.getData();
      if (uri == null || uri == Uri.EMPTY) {
        throw new AssertionError("Should never have an empty uri.");
      } else {
        ChatWallpaper wallpaper = ChatWallpaperFactory.create(uri);
        viewModel.setWallpaper(wallpaper);
        viewModel.saveWallpaperSelection();
        Navigation.findNavController(requireView()).popBackStack();
      }
    } else if (requestCode == PREVIEW && resultCode == Activity.RESULT_OK && data != null) {
      ChatWallpaper chatWallpaper = data.getParcelableExtra(ChatWallpaperPreviewActivity.EXTRA_CHAT_WALLPAPER);
      viewModel.setWallpaper(chatWallpaper);
      viewModel.saveWallpaperSelection();
      Navigation.findNavController(requireView()).popBackStack();
    } else {
      super.onActivityResult(requestCode, resultCode, data);
    }
  }
}
