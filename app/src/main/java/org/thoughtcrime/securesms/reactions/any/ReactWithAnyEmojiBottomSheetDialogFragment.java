package org.thoughtcrime.securesms.reactions.any;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.loader.app.LoaderManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.FixedRoundedCornerBottomSheetDialogFragment;
import org.thoughtcrime.securesms.components.emoji.EmojiEventListener;
import org.thoughtcrime.securesms.components.emoji.EmojiPageView;
import org.thoughtcrime.securesms.components.emoji.EmojiPageViewGridAdapter;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.keyboard.KeyboardPageCategoryIconMappingModel;
import org.thoughtcrime.securesms.keyboard.emoji.EmojiKeyboardPageCategoriesAdapter;
import org.thoughtcrime.securesms.keyboard.emoji.KeyboardPageSearchView;
import org.thoughtcrime.securesms.reactions.edit.EditReactionsActivity;
import org.signal.core.util.concurrent.LifecycleDisposable;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel;

import java.util.Optional;

import static androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE;

public final class ReactWithAnyEmojiBottomSheetDialogFragment extends FixedRoundedCornerBottomSheetDialogFragment implements EmojiEventListener,
                                                                                                                             EmojiPageViewGridAdapter.VariationSelectorListener
{

  public  static final String REACTION_STORAGE_KEY = "reactions_recent_emoji";
  private static final String ABOUT_STORAGE_KEY    = TextSecurePreferences.RECENT_STORAGE_KEY;

  private static final String ARG_MESSAGE_ID = "arg_message_id";
  private static final String ARG_IS_MMS     = "arg_is_mms";
  private static final String ARG_START_PAGE = "arg_start_page";
  private static final String ARG_SHADOWS    = "arg_shadows";
  private static final String ARG_RECENT_KEY = "arg_recent_key";
  private static final String ARG_EDIT       = "arg_edit";

  private ReactWithAnyEmojiViewModel viewModel;
  private Callback                   callback;
  private EmojiPageView              emojiPageView;
  private KeyboardPageSearchView     search;
  private View                       tabBar;

  private final LifecycleDisposable disposables = new LifecycleDisposable();

  private final UpdateCategorySelectionOnScroll categoryUpdateOnScroll = new UpdateCategorySelectionOnScroll();

  public static DialogFragment createForStory() {
    DialogFragment fragment = new ReactWithAnyEmojiBottomSheetDialogFragment();
    Bundle         args     = new Bundle();

    args.putLong(ARG_MESSAGE_ID, -1);
    args.putBoolean(ARG_IS_MMS, false);
    args.putInt(ARG_START_PAGE, -1);
    args.putString(ARG_RECENT_KEY, REACTION_STORAGE_KEY);
    args.putBoolean(ARG_EDIT, false);
    fragment.setArguments(args);

    return fragment;
  }

  public static DialogFragment createForMessageRecord(@NonNull MessageRecord messageRecord, int startingPage) {
    DialogFragment fragment = new ReactWithAnyEmojiBottomSheetDialogFragment();
    Bundle         args     = new Bundle();

    args.putLong(ARG_MESSAGE_ID, messageRecord.getId());
    args.putBoolean(ARG_IS_MMS, messageRecord.isMms());
    args.putInt(ARG_START_PAGE, startingPage);
    args.putString(ARG_RECENT_KEY, REACTION_STORAGE_KEY);
    args.putBoolean(ARG_EDIT, true);
    fragment.setArguments(args);

    return fragment;
  }

  public static DialogFragment createForAboutSelection() {
    DialogFragment fragment = new ReactWithAnyEmojiBottomSheetDialogFragment();
    Bundle         args     = new Bundle();

    args.putLong(ARG_MESSAGE_ID, -1);
    args.putBoolean(ARG_IS_MMS, false);
    args.putInt(ARG_START_PAGE, -1);
    args.putString(ARG_RECENT_KEY, ABOUT_STORAGE_KEY);
    fragment.setArguments(args);

    return fragment;
  }

  public static DialogFragment createForEditReactions() {
    DialogFragment fragment = new ReactWithAnyEmojiBottomSheetDialogFragment();
    Bundle         args     = new Bundle();

    args.putLong(ARG_MESSAGE_ID, -1);
    args.putBoolean(ARG_IS_MMS, false);
    args.putInt(ARG_START_PAGE, -1);
    args.putBoolean(ARG_SHADOWS, false);
    args.putString(ARG_RECENT_KEY, REACTION_STORAGE_KEY);
    fragment.setArguments(args);

    return fragment;
  }

  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);

    if (getParentFragment() instanceof Callback) {
      callback = (Callback) getParentFragment();
    } else {
      callback = (Callback) context;
    }
  }

  @Override
  protected int getThemeResId() {
    return R.style.Widget_Signal_ReactWithAny;
  }

  @Override
  public @NonNull Dialog onCreateDialog(Bundle savedInstanceState) {
    Dialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);

    boolean shadows = requireArguments().getBoolean(ARG_SHADOWS, true);
    if (!shadows) {
      Window window = dialog.getWindow();
      if (window != null) {
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
      }
    }

    return dialog;
  }

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.react_with_any_emoji_bottom_sheet_dialog_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    disposables.bindTo(getViewLifecycleOwner());

    emojiPageView = view.findViewById(R.id.react_with_any_emoji_page_view);
    emojiPageView.initialize(this, this, true);
    emojiPageView.addOnScrollListener(categoryUpdateOnScroll);

    search = view.findViewById(R.id.react_with_any_emoji_search);

    initializeViewModel();

    EmojiKeyboardPageCategoriesAdapter categoriesAdapter = new EmojiKeyboardPageCategoriesAdapter(key -> {
      scrollTo(key);
      viewModel.selectPage(key);
    });

    FrameLayout container = requireDialog().findViewById(R.id.container);
    tabBar = LayoutInflater.from(requireContext())
                           .inflate(R.layout.react_with_any_emoji_tabs,
                                    container,
                                    false);
    RecyclerView categoriesRecycler = tabBar.findViewById(R.id.emoji_categories_recycler);
    categoriesRecycler.setAdapter(categoriesAdapter);

    if (requireArguments().getBoolean(ARG_EDIT, false)) {
      View customizeReactions = tabBar.findViewById(R.id.customize_reactions_frame);
      customizeReactions.setVisibility(View.VISIBLE);
      customizeReactions.setOnClickListener(v -> startActivity(new Intent(requireContext(), EditReactionsActivity.class)));
    }

    container.addView(tabBar);

    emojiPageView.addOnScrollListener(new TopAndBottomShadowHelper(requireView().findViewById(R.id.react_with_any_emoji_top_shadow),
                                                                   tabBar.findViewById(R.id.react_with_any_emoji_bottom_shadow)));

    disposables.add(viewModel.getEmojiList().subscribe(pages -> emojiPageView.setList(pages, null)));
    disposables.add(viewModel.getCategories().subscribe(categoriesAdapter::submitList));
    disposables.add(viewModel.getSelectedKey().subscribe(key -> categoriesRecycler.post(() -> {
      int index = categoriesAdapter.indexOfFirst(KeyboardPageCategoryIconMappingModel.class, m -> m.getKey().equals(key));

      if (index != -1) {
        categoriesRecycler.smoothScrollToPosition(index);
      }
    })));

    search.setCallbacks(new SearchCallbacks());
  }

  private void scrollTo(@NonNull String key) {
    if (emojiPageView.getAdapter() != null) {
      int index = emojiPageView.getAdapter().indexOfFirst(EmojiPageViewGridAdapter.EmojiHeader.class, m -> m.getKey().equals(key));

      if (index != -1) {
        ((BottomSheetDialog) requireDialog()).getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        categoryUpdateOnScroll.startAutoScrolling();
        emojiPageView.smoothScrollToPositionTop(index);
      }
    }
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    LoaderManager.getInstance(requireActivity()).destroyLoader((int) requireArguments().getLong(ARG_MESSAGE_ID));
  }

  @Override
  public void onDismiss(@NonNull DialogInterface dialog) {
    super.onDismiss(dialog);

    callback.onReactWithAnyEmojiDialogDismissed();
  }

  private void initializeViewModel() {
    Bundle                             args       = requireArguments();
    ReactWithAnyEmojiRepository        repository = new ReactWithAnyEmojiRepository(requireContext(), args.getString(ARG_RECENT_KEY, REACTION_STORAGE_KEY));
    ReactWithAnyEmojiViewModel.Factory factory    = new ReactWithAnyEmojiViewModel.Factory(repository, args.getLong(ARG_MESSAGE_ID), args.getBoolean(ARG_IS_MMS));

    viewModel = new ViewModelProvider(this, factory).get(ReactWithAnyEmojiViewModel.class);
  }

  @Override
  public void onEmojiSelected(String emoji) {
    viewModel.onEmojiSelected(emoji);
    callback.onReactWithAnyEmojiSelected(emoji);
    dismiss();
  }

  @Override
  public void onKeyEvent(KeyEvent keyEvent) {
  }

  @Override
  public void onVariationSelectorStateChanged(boolean open) { }

  public interface Callback {
    void onReactWithAnyEmojiDialogDismissed();

    void onReactWithAnyEmojiSelected(@NonNull String emoji);
  }

  private class UpdateCategorySelectionOnScroll extends RecyclerView.OnScrollListener {

    private boolean doneScrolling = true;

    public void startAutoScrolling() {
      doneScrolling = false;
    }

    @Override
    public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
      if (newState == SCROLL_STATE_IDLE && !doneScrolling) {
        doneScrolling = true;
        onScrolled(recyclerView, 0, 0);
      }
    }

    @Override
    public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
      if (doneScrolling && recyclerView.getLayoutManager() != null && emojiPageView.getAdapter() != null) {
        LinearLayoutManager       layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        int                       index         = layoutManager.findFirstCompletelyVisibleItemPosition();
        Optional<MappingModel<?>> item          = emojiPageView.getAdapter().getModel(index);
        if (item.isPresent() && item.get() instanceof EmojiPageViewGridAdapter.HasKey) {
          viewModel.selectPage(((EmojiPageViewGridAdapter.HasKey) item.get()).getKey());
        }
      }
    }
  }

  private class SearchCallbacks implements KeyboardPageSearchView.Callbacks {
    @Override
    public void onQueryChanged(@NonNull String query) {
      boolean hasQuery = !TextUtils.isEmpty(query);
      search.enableBackNavigation(hasQuery);
      if (hasQuery) {
        ViewUtil.fadeOut(tabBar, 250, View.INVISIBLE);
      } else {
        ViewUtil.fadeIn(tabBar, 250);
      }
      viewModel.onQueryChanged(query);
    }

    @Override
    public void onNavigationClicked() {
      search.clearQuery();
      search.clearFocus();
      ViewUtil.hideKeyboard(requireContext(), requireView());
    }

    @Override
    public void onFocusGained() {
      ((BottomSheetDialog) requireDialog()).getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    @Override
    public void onClicked() { }

    @Override
    public void onFocusLost() { }
  }
}
