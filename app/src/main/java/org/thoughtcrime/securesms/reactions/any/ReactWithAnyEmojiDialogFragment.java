package org.thoughtcrime.securesms.reactions.any;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.loader.app.LoaderManager;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.shape.CornerFamily;
import com.google.android.material.shape.ShapeAppearanceModel;

import org.thoughtcrime.securesms.components.emoji.EmojiEventListener;
import org.thoughtcrime.securesms.components.emoji.EmojiPageView;
import org.thoughtcrime.securesms.components.emoji.EmojiPageViewGridAdapter;
import org.thoughtcrime.securesms.conversation.v2.ViewUtil;
import org.thoughtcrime.securesms.database.model.MessageId;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.keyboard.emoji.KeyboardPageSearchView;
import org.thoughtcrime.securesms.util.LifecycleDisposable;

import network.loki.messenger.R;

public final class ReactWithAnyEmojiDialogFragment extends BottomSheetDialogFragment implements EmojiEventListener,
                                                                                                           EmojiPageViewGridAdapter.VariationSelectorListener
{

  private static final String ARG_MESSAGE_ID = "arg_message_id";
  private static final String ARG_IS_MMS     = "arg_is_mms";
  private static final String ARG_START_PAGE = "arg_start_page";
  private static final String ARG_SHADOWS    = "arg_shadows";

  private ReactWithAnyEmojiViewModel viewModel;
  private Callback                   callback;
  private EmojiPageView              emojiPageView;
  private KeyboardPageSearchView search;

  private final LifecycleDisposable disposables = new LifecycleDisposable();

  public static DialogFragment createForMessageRecord(@NonNull MessageRecord messageRecord, int startingPage) {
    DialogFragment fragment = new ReactWithAnyEmojiDialogFragment();
    Bundle         args     = new Bundle();

    args.putLong(ARG_MESSAGE_ID, messageRecord.getId());
    args.putBoolean(ARG_IS_MMS, messageRecord.isMms());
    args.putInt(ARG_START_PAGE, startingPage);
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
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  @Override
  public @NonNull Dialog onCreateDialog(Bundle savedInstanceState) {
    BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
    dialog.getBehavior().setPeekHeight((int) (getResources().getDisplayMetrics().heightPixels * 0.50));

    ShapeAppearanceModel shapeAppearanceModel = ShapeAppearanceModel.builder()
                                                                    .setTopLeftCorner(CornerFamily.ROUNDED, ViewUtil.dpToPx(requireContext(), 18))
                                                                    .setTopRightCorner(CornerFamily.ROUNDED, ViewUtil.dpToPx(requireContext(), 18))
                                                                    .build();

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
    return inflater.inflate(R.layout.react_with_any_emoji_dialog_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    disposables.bindTo(getViewLifecycleOwner());

    emojiPageView = view.findViewById(R.id.react_with_any_emoji_page_view);
    emojiPageView.initialize(this, this, true);

    search = view.findViewById(R.id.react_with_any_emoji_search);

    initializeViewModel();

//    EmojiKeyboardPageCategoriesAdapter categoriesAdapter = new EmojiKeyboardPageCategoriesAdapter(key -> {
//      scrollTo(key);
//      viewModel.selectPage(key);
//    });

    disposables.add(viewModel.getEmojiList().subscribe(pages -> emojiPageView.setList(pages, null)));
//    disposables.add(viewModel.getCategories().subscribe(categoriesAdapter::submitList));
//    disposables.add(viewModel.getSelectedKey().subscribe(key -> categoriesRecycler.post(() -> {
//      int index = categoriesAdapter.indexOfFirst(EmojiKeyboardPageCategoryMappingModel.class, m -> m.getKey().equals(key));
//
//      if (index != -1) {
//        categoriesRecycler.smoothScrollToPosition(index);
//      }
//    })));

    search.setCallbacks(new SearchCallbacks());
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
    ReactWithAnyEmojiRepository        repository = new ReactWithAnyEmojiRepository(requireContext());
    ReactWithAnyEmojiViewModel.Factory factory    = new ReactWithAnyEmojiViewModel.Factory(repository, args.getLong(ARG_MESSAGE_ID), args.getBoolean(ARG_IS_MMS));

    viewModel = new ViewModelProvider(this, factory).get(ReactWithAnyEmojiViewModel.class);
  }

  @Override
  public void onEmojiSelected(String emoji) {
    viewModel.onEmojiSelected(emoji);
    Bundle    args      = requireArguments();
    MessageId messageId = new MessageId(args.getLong(ARG_MESSAGE_ID), args.getBoolean(ARG_IS_MMS));
    callback.onReactWithAnyEmojiSelected(emoji, messageId);
    dismiss();
  }

  @Override
  public void onKeyEvent(KeyEvent keyEvent) {
  }

  @Override
  public void onVariationSelectorStateChanged(boolean open) { }

  public interface Callback {
    void onReactWithAnyEmojiDialogDismissed();

    void onReactWithAnyEmojiSelected(@NonNull String emoji, MessageId messageId);
  }

  private class SearchCallbacks implements KeyboardPageSearchView.Callbacks {
    @Override
    public void onQueryChanged(@NonNull String query) {
      boolean hasQuery = !TextUtils.isEmpty(query);
      search.enableBackNavigation(hasQuery);
      /*if (hasQuery) {
        ViewUtil.fadeOut(tabBar, 250, View.INVISIBLE);
      } else {
        ViewUtil.fadeIn(tabBar, 250);
      }*/
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
