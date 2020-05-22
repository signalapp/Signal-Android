package org.thoughtcrime.securesms.reactions.any;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextSwitcher;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.shape.CornerFamily;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.emoji.EmojiKeyboardProvider;
import org.thoughtcrime.securesms.components.emoji.EmojiPageView;
import org.thoughtcrime.securesms.components.emoji.EmojiPageViewGridAdapter;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.util.ThemeUtil;
import org.thoughtcrime.securesms.util.ViewUtil;

import static org.thoughtcrime.securesms.R.layout.react_with_any_emoji_tab;

public final class ReactWithAnyEmojiBottomSheetDialogFragment extends BottomSheetDialogFragment implements EmojiKeyboardProvider.EmojiEventListener, EmojiPageViewGridAdapter.VariationSelectorListener {

  private static final String ARG_MESSAGE_ID = "arg_message_id";
  private static final String ARG_IS_MMS     = "arg_is_mms";

  private ReactWithAnyEmojiViewModel viewModel;
  private TextSwitcher               categoryLabel;
  private ViewPager2                 categoryPager;
  private ReactWithAnyEmojiAdapter   adapter;
  private OnPageChanged              onPageChanged;
  private SparseArray<EmojiPageView> pageArray = new SparseArray<>();
  private Callback                   callback;

  public static DialogFragment createForMessageRecord(@NonNull MessageRecord messageRecord) {
    DialogFragment fragment = new ReactWithAnyEmojiBottomSheetDialogFragment();
    Bundle         args     = new Bundle();

    args.putLong(ARG_MESSAGE_ID, messageRecord.getId());
    args.putBoolean(ARG_IS_MMS, messageRecord.isMms());
    fragment.setArguments(args);

    return fragment;
  }

  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);

    callback = (Callback) context;
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    if (ThemeUtil.isDarkTheme(requireContext())) {
      setStyle(DialogFragment.STYLE_NORMAL, R.style.Theme_Signal_BottomSheetDialog_Fixed_ReactWithAny);
    } else {
      setStyle(DialogFragment.STYLE_NORMAL, R.style.Theme_Signal_Light_BottomSheetDialog_Fixed_ReactWithAny);
    }

    super.onCreate(savedInstanceState);
  }

  @Override
  public @NonNull Dialog onCreateDialog(Bundle savedInstanceState) {
    BottomSheetDialog     dialog               = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
    ShapeAppearanceModel  shapeAppearanceModel = ShapeAppearanceModel.builder()
                                                                     .setTopLeftCorner(CornerFamily.ROUNDED, ViewUtil.dpToPx(requireContext(), 8))
                                                                     .setTopRightCorner(CornerFamily.ROUNDED, ViewUtil.dpToPx(requireContext(), 8))
                                                                     .build();
    MaterialShapeDrawable dialogBackground     = new MaterialShapeDrawable(shapeAppearanceModel);

    dialogBackground.setTint(ThemeUtil.getThemedColor(requireContext(), R.attr.dialog_background_color));

    dialog.getBehavior().addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
      @Override
      public void onStateChanged(@NonNull View bottomSheet, int newState) {
        if (newState == BottomSheetBehavior.STATE_EXPANDED) {
          ViewCompat.setBackground(bottomSheet, dialogBackground);
        }
      }

      @Override
      public void onSlide(@NonNull View bottomSheet, float slideOffset) {
      }
    });

    return dialog;
  }

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater,
                                     @Nullable ViewGroup container,
                                     @Nullable Bundle savedInstanceState)
  {
    return inflater.inflate(R.layout.react_with_any_emoji_bottom_sheet_dialog_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    initializeViewModel();

    categoryLabel    = view.findViewById(R.id.category_label);
    categoryPager    = view.findViewById(R.id.category_pager);

    adapter = new ReactWithAnyEmojiAdapter(viewModel.getEmojiPageModels(), this, this, (position, pageView) -> {
      pageArray.put(position, pageView);

      if (categoryPager.getCurrentItem() == position) {
        updateFocusedRecycler(position);
      }
    });

    onPageChanged = new OnPageChanged();

    categoryPager.setAdapter(adapter);
    categoryPager.registerOnPageChangeCallback(onPageChanged);

    int startPateIndex = viewModel.getStartIndex();

    categoryPager.setCurrentItem(startPateIndex, false);
    presentCategoryLabel(viewModel.getCategoryIconAttr(startPateIndex));
  }

  @Override
  public void onActivityCreated(@Nullable Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);

    if (savedInstanceState == null) {
      FrameLayout    container       = requireDialog().findViewById(R.id.container);
      LayoutInflater layoutInflater  = LayoutInflater.from(requireContext());
      View           statusBarShader = layoutInflater.inflate(R.layout.react_with_any_emoji_status_fade, container, false);
      TabLayout      categoryTabs    = (TabLayout) layoutInflater.inflate(R.layout.react_with_any_emoji_tabs, container, false);

      ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtil.getStatusBarHeight(container));

      statusBarShader.setLayoutParams(params);
      container.addView(statusBarShader, 0);
      container.addView(categoryTabs);

      ViewCompat.setOnApplyWindowInsetsListener(container, (v, insets) -> insets.consumeSystemWindowInsets());

      new TabLayoutMediator(categoryTabs, categoryPager, (tab, position) -> {
        tab.setCustomView(react_with_any_emoji_tab)
            .setIcon(ThemeUtil.getThemedDrawable(requireContext(), viewModel.getCategoryIconAttr(position)));
      }).attach();
    }
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();

    categoryPager.unregisterOnPageChangeCallback(onPageChanged);
  }

  @Override
  public void onDismiss(@NonNull DialogInterface dialog) {
    super.onDismiss(dialog);

    callback.onReactWithAnyEmojiDialogDismissed();
  }

  private void initializeViewModel() {
    Bundle                             args       = requireArguments();
    ReactWithAnyEmojiRepository        repository = new ReactWithAnyEmojiRepository(requireContext());
    ReactWithAnyEmojiViewModel.Factory factory    = new ReactWithAnyEmojiViewModel.Factory(repository, args.getLong(ARG_MESSAGE_ID), args.getBoolean(ARG_IS_MMS));

    viewModel = ViewModelProviders.of(this, factory).get(ReactWithAnyEmojiViewModel.class);
  }

  @Override
  public void onEmojiSelected(String emoji) {
    viewModel.onEmojiSelected(emoji);
    dismiss();
  }

  @Override
  public void onKeyEvent(KeyEvent keyEvent) {
  }

  @Override
  public void onVariationSelectorStateChanged(boolean open) {
  }

  private void updateFocusedRecycler(int position) {
    for (int i = 0; i < pageArray.size(); i++) {
      pageArray.valueAt(i).setRecyclerNestedScrollingEnabled(false);
    }

    EmojiPageView toFocus = pageArray.get(position);
    if (toFocus != null) {
      toFocus.setRecyclerNestedScrollingEnabled(true);
      categoryPager.requestLayout();
    }

    presentCategoryLabel(viewModel.getCategoryIconAttr(position));
  }

  private void presentCategoryLabel(@AttrRes int iconAttr) {
    switch (iconAttr) {
      case R.attr.emoji_category_recent:
        categoryLabel.setText(getString(R.string.ReactWithAnyEmojiBottomSheetDialogFragment__recently_used));
        break;
      case R.attr.emoji_category_people:
        categoryLabel.setText(getString(R.string.ReactWithAnyEmojiBottomSheetDialogFragment__smileys_and_people));
        break;
      case R.attr.emoji_category_nature:
        categoryLabel.setText(getString(R.string.ReactWithAnyEmojiBottomSheetDialogFragment__nature));
        break;
      case R.attr.emoji_category_foods:
        categoryLabel.setText(getString(R.string.ReactWithAnyEmojiBottomSheetDialogFragment__food));
        break;
      case R.attr.emoji_category_activity:
        categoryLabel.setText(getString(R.string.ReactWithAnyEmojiBottomSheetDialogFragment__activities));
        break;
      case R.attr.emoji_category_places:
        categoryLabel.setText(getString(R.string.ReactWithAnyEmojiBottomSheetDialogFragment__places));
        break;
      case R.attr.emoji_category_objects:
        categoryLabel.setText(getString(R.string.ReactWithAnyEmojiBottomSheetDialogFragment__objects));
        break;
      case R.attr.emoji_category_symbols:
        categoryLabel.setText(getString(R.string.ReactWithAnyEmojiBottomSheetDialogFragment__symbols));
        break;
      case R.attr.emoji_category_flags:
        categoryLabel.setText(getString(R.string.ReactWithAnyEmojiBottomSheetDialogFragment__flags));
        break;
      case R.attr.emoji_category_emoticons:
        categoryLabel.setText(getString(R.string.ReactWithAnyEmojiBottomSheetDialogFragment__emoticons));
        break;
      default:
        throw new AssertionError();
    }
  }

  private class OnPageChanged extends ViewPager2.OnPageChangeCallback {
    @Override
    public void onPageSelected(int position) {
      updateFocusedRecycler(position);
    }
  }

  public interface Callback {
    void onReactWithAnyEmojiDialogDismissed();
  }
}
