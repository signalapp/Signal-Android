package org.thoughtcrime.securesms.wallpaper;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.navigation.Navigation;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.ThemeUtil;

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
    SwitchCompat           dimInNightMode       = view.findViewById(R.id.chat_wallpaper_dark_theme_dims_wallpaper);
    View                   chatWallpaperDim     = view.findViewById(R.id.chat_wallpaper_dim);
    View                   clearWallpaper       = view.findViewById(R.id.chat_wallpaper_clear_wallpaper);
    View                   resetAllWallpaper    = view.findViewById(R.id.chat_wallpaper_reset_all_wallpapers);

    viewModel.getCurrentWallpaper().observe(getViewLifecycleOwner(), wallpaper -> {
      if (wallpaper.isPresent()) {
        wallpaper.get().loadInto(chatWallpaperPreview);
      } else {
        chatWallpaperPreview.setImageDrawable(null);
        chatWallpaperPreview.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.signal_background_primary));
      }

      dimInNightMode.setEnabled(wallpaper.isPresent());
    });

    viewModel.getDimInDarkTheme().observe(getViewLifecycleOwner(), shouldDimInNightMode -> {
      if (shouldDimInNightMode != dimInNightMode.isChecked()) {
        dimInNightMode.setChecked(shouldDimInNightMode);
      }

      chatWallpaperDim.setAlpha(ChatWallpaper.FIXED_DIM_LEVEL_FOR_DARK_THEME);
      chatWallpaperDim.setVisibility(shouldDimInNightMode && ThemeUtil.isDarkTheme(requireContext()) ? View.VISIBLE : View.GONE);
    });

    setWallpaper.setOnClickListener(unused -> Navigation.findNavController(view)
                                                        .navigate(R.id.action_chatWallpaperFragment_to_chatWallpaperSelectionFragment));

    clearWallpaper.setOnClickListener(unused -> {
      viewModel.setWallpaper(null);
      viewModel.setDimInDarkTheme(false);
      viewModel.saveWallpaperSelection();
    });

    resetAllWallpaper.setVisibility(viewModel.isGlobal() ? View.VISIBLE : View.GONE);

    resetAllWallpaper.setOnClickListener(unused -> {
      viewModel.setWallpaper(null);
      viewModel.setDimInDarkTheme(false);
      viewModel.resetAllWallpaper();
    });

    dimInNightMode.setOnCheckedChangeListener((buttonView, isChecked) -> viewModel.setDimInDarkTheme(isChecked));
  }
}
