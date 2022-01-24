package org.thoughtcrime.securesms.wallpaper;

import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.navigation.Navigation;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.conversation.colors.ColorizerView;
import org.thoughtcrime.securesms.util.DisplayMetricsUtil;
import org.thoughtcrime.securesms.util.Projection;
import org.thoughtcrime.securesms.util.ThemeUtil;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.navigation.SafeNavigation;

import java.util.Collections;

public class ChatWallpaperFragment extends Fragment {

  private boolean  isSettingDimFromViewModel;

  private ChatWallpaperViewModel viewModel;

  @Override
  public @NonNull View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.chat_wallpaper_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    viewModel = ViewModelProviders.of(requireActivity()).get(ChatWallpaperViewModel.class);

    AvatarImageView        portrait             = view.findViewById(R.id.chat_wallpaper_preview_top_bar_portrait);
    Toolbar                toolbar              = view.findViewById(R.id.toolbar);
    ImageView              chatWallpaperPreview = view.findViewById(R.id.chat_wallpaper_preview_background);
    View                   setWallpaper         = view.findViewById(R.id.chat_wallpaper_set_wallpaper);
    SwitchCompat           dimInNightMode       = view.findViewById(R.id.chat_wallpaper_dark_theme_dims_wallpaper);
    View                   chatWallpaperDim     = view.findViewById(R.id.chat_wallpaper_dim);
    TextView               setChatColor         = view.findViewById(R.id.chat_wallpaper_set_chat_color);
    TextView               resetChatColors      = view.findViewById(R.id.chat_wallpaper_reset_chat_colors);
    ImageView              sentBubble           = view.findViewById(R.id.chat_wallpaper_preview_bubble_2);
    ColorizerView          colorizerView        = view.findViewById(R.id.colorizer);
    TextView               resetAllWallpaper    = view.findViewById(R.id.chat_wallpaper_reset_all_wallpapers);
    AppCompatImageView     recvBubble           = view.findViewById(R.id.chat_wallpaper_preview_bubble_1);

    toolbar.setTitle(R.string.preferences__chat_color_and_wallpaper);
    toolbar.setNavigationOnClickListener(nav -> {
      if (!Navigation.findNavController(nav).popBackStack()) {
        requireActivity().finish();
      }
    });

    forceAspectRatioToScreenByAdjustingHeight(chatWallpaperPreview);

    viewModel.getWallpaperPreviewPortrait().observe(getViewLifecycleOwner(),
                                                    wallpaperPreviewPortrait -> wallpaperPreviewPortrait.applyToAvatarImageView(portrait));

    viewModel.getCurrentWallpaper().observe(getViewLifecycleOwner(), wallpaper -> {
      if (wallpaper.isPresent()) {
        wallpaper.get().loadInto(chatWallpaperPreview);
        ImageViewCompat.setImageTintList(recvBubble, ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.conversation_item_wallpaper_bubble_color)));
      } else {
        chatWallpaperPreview.setImageDrawable(null);
        ImageViewCompat.setImageTintList(recvBubble, ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.signal_background_secondary)));
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
    });

    chatWallpaperPreview.setOnClickListener(unused -> setWallpaper.performClick());
    setWallpaper.setOnClickListener(unused -> SafeNavigation.safeNavigate(Navigation.findNavController(view),
                                                                          R.id.action_chatWallpaperFragment_to_chatWallpaperSelectionFragment));
    setChatColor.setOnClickListener(unused -> SafeNavigation.safeNavigate(Navigation.findNavController(view),
                                                                          ChatWallpaperFragmentDirections.actionChatWallpaperFragmentToChatColorSelectionFragment(viewModel.getRecipientId())));

    if (viewModel.isGlobal()) {
      resetAllWallpaper.setOnClickListener(unused -> {
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.ChatWallpaperFragment__reset_wallpaper)
            .setMessage(R.string.ChatWallpaperFragment__would_you_like_to_override_all_wallpapers)
            .setPositiveButton(R.string.ChatWallpaperFragment__reset_default_wallpaper, (dialog, which) -> {
              viewModel.setWallpaper(null);
              viewModel.setDimInDarkTheme(true);
              viewModel.saveWallpaperSelection();
              dialog.dismiss();
            })
            .setNegativeButton(R.string.ChatWallpaperFragment__reset_all_wallpapers, (dialog, which) -> {
              viewModel.setWallpaper(null);
              viewModel.setDimInDarkTheme(true);
              viewModel.resetAllWallpaper();
              dialog.dismiss();
            })
            .setNeutralButton(android.R.string.cancel, (dialog, which) -> {
              dialog.dismiss();
            })
            .show();
      });

      resetChatColors.setOnClickListener(unused -> {
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.ChatWallpaperFragment__reset_chat_colors)
            .setMessage(R.string.ChatWallpaperFragment__would_you_like_to_override_all_chat_colors)
            .setPositiveButton(R.string.ChatWallpaperFragment__reset_default_colors, (dialog, which) -> {
              viewModel.clearChatColor();
              dialog.dismiss();
            })
            .setNegativeButton(R.string.ChatWallpaperFragment__reset_all_colors, (dialog, which) -> {
              viewModel.resetAllChatColors();
              dialog.dismiss();
            })
            .setNeutralButton(android.R.string.cancel, (dialog, which) -> {
              dialog.dismiss();
            })
            .show();
      });
    } else {
      resetAllWallpaper.setText(R.string.ChatWallpaperFragment__reset_wallpaper);
      resetChatColors.setText(R.string.ChatWallpaperFragment__reset_chat_color);

      resetAllWallpaper.setOnClickListener(unused -> {
        new MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.ChatWallpaperFragment__reset_wallpaper_question)
            .setPositiveButton(R.string.ChatWallpaperFragment__reset, (dialog, which) -> {
              viewModel.setWallpaper(null);
              viewModel.setDimInDarkTheme(true);
              viewModel.saveWallpaperSelection();
              viewModel.refreshChatColors();
            })
            .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
            .show();
      });

      resetChatColors.setOnClickListener(unused -> {
        new MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.ChatWallpaperFragment__reset_chat_color_question)
            .setPositiveButton(R.string.ChatWallpaperFragment__reset, (dialog, which) -> viewModel.clearChatColor())
            .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
            .show();
      });
    }

    dimInNightMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
      if (!isSettingDimFromViewModel) {
        viewModel.setDimInDarkTheme(isChecked);
      }
    });

    viewModel.getCurrentChatColors().observe(getViewLifecycleOwner(), chatColors -> {
      sentBubble.getDrawable().setColorFilter(chatColors.getChatBubbleColorFilter());
      colorizerView.setBackground(chatColors.getChatBubbleMask());
      Projection projection = Projection.relativeToViewWithCommonRoot(sentBubble, colorizerView, new Projection.Corners(ViewUtil.dpToPx(10)));
      colorizerView.setProjections(Collections.singletonList(projection));

      Drawable colorCircle = chatColors.asCircle();
      colorCircle.setBounds(0, 0, ViewUtil.dpToPx(16), ViewUtil.dpToPx(16));
      TextViewCompat.setCompoundDrawablesRelative(setChatColor, null, null, colorCircle, null);
    });

    sentBubble.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> viewModel.refreshChatColors());
  }

  @Override
  public void onResume() {
    super.onResume();
    viewModel.refreshChatColors();
  }

  private void forceAspectRatioToScreenByAdjustingHeight(@NonNull View view) {
    DisplayMetrics displayMetrics = new DisplayMetrics();
    requireActivity().getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
    DisplayMetricsUtil.forceAspectRatioToScreenByAdjustingHeight(displayMetrics, view);
  }
}
