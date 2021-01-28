package org.thoughtcrime.securesms.wallpaper;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.navigation.Navigation;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.DisplayMetricsUtil;
import org.thoughtcrime.securesms.util.ThemeUtil;

public class ChatWallpaperFragment extends Fragment {

  private boolean  isSettingDimFromViewModel;
  private TextView clearWallpaper;
  private View     resetAllWallpaper;
  private View     divider;

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

    clearWallpaper       = view.findViewById(R.id.chat_wallpaper_clear_wallpaper);
    resetAllWallpaper    = view.findViewById(R.id.chat_wallpaper_reset_all_wallpapers);
    divider              = view.findViewById(R.id.chat_wallpaper_divider);

    forceAspectRatioToScreenByAdjustingHeight(chatWallpaperPreview);

    viewModel.getCurrentWallpaper().observe(getViewLifecycleOwner(), wallpaper -> {
      if (wallpaper.isPresent()) {
        wallpaper.get().loadInto(chatWallpaperPreview);
      } else {
        chatWallpaperPreview.setImageDrawable(null);
        chatWallpaperPreview.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.signal_background_primary));
      }
    });

    viewModel.getDimInDarkTheme().observe(getViewLifecycleOwner(), shouldDimInNightMode -> {
      if (shouldDimInNightMode != dimInNightMode.isChecked()) {
        isSettingDimFromViewModel = true;
        dimInNightMode.setChecked(shouldDimInNightMode);
        isSettingDimFromViewModel = false;
      }

      chatWallpaperDim.setAlpha(ChatWallpaper.FIXED_DIM_LEVEL_FOR_DARK_THEME);
      chatWallpaperDim.setVisibility(shouldDimInNightMode && ThemeUtil.isDarkTheme(requireContext()) ? View.VISIBLE : View.GONE);
    });

    viewModel.getEnableWallpaperControls().observe(getViewLifecycleOwner(), enableWallpaperControls -> {
      dimInNightMode.setEnabled(enableWallpaperControls);
      dimInNightMode.setAlpha(enableWallpaperControls ? 1 : 0.5f);
      clearWallpaper.setEnabled(enableWallpaperControls);
      clearWallpaper.setAlpha(enableWallpaperControls ? 1 : 0.5f);
    });

    chatWallpaperPreview.setOnClickListener(unused -> setWallpaper.performClick());
    setWallpaper.setOnClickListener(unused -> Navigation.findNavController(view)
                                                        .navigate(R.id.action_chatWallpaperFragment_to_chatWallpaperSelectionFragment));

    resetAllWallpaper.setVisibility(viewModel.isGlobal() ? View.VISIBLE : View.GONE);

    clearWallpaper.setOnClickListener(unused -> {
      confirmAction(viewModel.isGlobal() ? R.string.ChatWallpaperFragment__clear_wallpaper_this_will_not
                                         : R.string.ChatWallpaperFragment__clear_wallpaper_for_this_chat,
                    R.string.ChatWallpaperFragment__clear,
                    () -> {
                      viewModel.setWallpaper(null);
                      viewModel.setDimInDarkTheme(true);
                      viewModel.saveWallpaperSelection();
                    });
    });

    resetAllWallpaper.setOnClickListener(unused -> {
      confirmAction(R.string.ChatWallpaperFragment__reset_all_wallpapers_including_custom,
                    R.string.ChatWallpaperFragment__reset,
                    () -> {
                      viewModel.setWallpaper(null);
                      viewModel.setDimInDarkTheme(true);
                      viewModel.resetAllWallpaper();
                    });
    });

    dimInNightMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
      if (!isSettingDimFromViewModel) {
        viewModel.setDimInDarkTheme(isChecked);
      }
    });
  }

  private void confirmAction(@StringRes int title, @StringRes int positiveActionLabel, @NonNull Runnable onPositiveAction) {
    new AlertDialog.Builder(requireContext())
                   .setMessage(title)
                   .setPositiveButton(positiveActionLabel, (dialog, which) -> {
                     onPositiveAction.run();
                     dialog.dismiss();
                   })
                   .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                     dialog.dismiss();
                   })
                   .setCancelable(true)
                   .show();
  }

  private void forceAspectRatioToScreenByAdjustingHeight(@NonNull View view) {
    DisplayMetrics displayMetrics = new DisplayMetrics();
    requireActivity().getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
    DisplayMetricsUtil.forceAspectRatioToScreenByAdjustingHeight(displayMetrics, view);
  }
}
