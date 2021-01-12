package org.thoughtcrime.securesms.wallpaper;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.navigation.Navigation;

import org.thoughtcrime.securesms.R;

public class ChatWallpaperFragment extends Fragment {
  @Override
  public @NonNull View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.chat_wallpaper_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    ChatWallpaperViewModel viewModel            = ViewModelProviders.of(requireActivity()).get(ChatWallpaperViewModel.class);
    ImageView              chatWallpaperPreview = view.findViewById(R.id.chat_wallpaper_preview_background);
    View                   setWallpaper         = view.findViewById(R.id.chat_wallpaper_set_wallpaper);

    viewModel.setWallpaper(GradientChatWallpaper.GRADIENT_1);

    viewModel.getCurrentWallpaper().observe(getViewLifecycleOwner(), wallpaper -> {
      if (wallpaper.isPresent()) {
        wallpaper.get().loadInto(chatWallpaperPreview);
      } else {
        chatWallpaperPreview.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.signal_background_primary));
      }
    });

    setWallpaper.setOnClickListener(unused -> Navigation.findNavController(view)
                                                        .navigate(R.id.action_chatWallpaperFragment_to_chatWallpaperSelectionFragment));
  }
}
