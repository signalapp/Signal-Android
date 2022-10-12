package org.thoughtcrime.securesms.reactions;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import org.session.libsession.utilities.ThemeUtil;
import org.thoughtcrime.securesms.components.emoji.EmojiImageView;
import org.thoughtcrime.securesms.database.model.MessageId;
import org.thoughtcrime.securesms.util.LifecycleDisposable;
import org.thoughtcrime.securesms.util.NumberUtil;

import java.util.Objects;

import network.loki.messenger.R;

public final class ReactionsDialogFragment extends BottomSheetDialogFragment implements ReactionViewPagerAdapter.Listener {

  private static final String ARGS_MESSAGE_ID = "reactions.args.message.id";
  private static final String ARGS_IS_MMS     = "reactions.args.is.mms";
  private static final String ARGS_IS_MODERATOR = "reactions.args.is.moderator";

  private ViewPager2                recipientPagerView;
  private ReactionViewPagerAdapter  recipientsAdapter;
  private Callback                  callback;

  private final LifecycleDisposable disposables = new LifecycleDisposable();

  public static DialogFragment create(MessageId messageId, boolean isUserModerator) {
    Bundle         args     = new Bundle();
    DialogFragment fragment = new ReactionsDialogFragment();

    args.putLong(ARGS_MESSAGE_ID, messageId.getId());
    args.putBoolean(ARGS_IS_MMS, messageId.isMms());
    args.putBoolean(ARGS_IS_MODERATOR, isUserModerator);

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
//    setStyle(DialogFragment.STYLE_NORMAL, R.style.Theme_Session_BottomSheet);
    super.onCreate(savedInstanceState);
  }

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater,
                                     @Nullable ViewGroup container,
                                     @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.reactions_bottom_sheet_dialog_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    recipientPagerView = view.findViewById(R.id.reactions_bottom_view_recipient_pager);

    disposables.bindTo(getViewLifecycleOwner());

    setUpRecipientsRecyclerView();
    setUpTabMediator(savedInstanceState);

    MessageId messageId = new MessageId(requireArguments().getLong(ARGS_MESSAGE_ID), requireArguments().getBoolean(ARGS_IS_MMS));
    recipientsAdapter.setIsUserModerator(requireArguments().getBoolean(ARGS_IS_MODERATOR));
    recipientsAdapter.setMessageId(messageId);
    setUpViewModel(messageId);
  }

  private void setUpTabMediator(@Nullable Bundle savedInstanceState) {
    if (savedInstanceState == null) {
      FrameLayout    container       = requireDialog().findViewById(R.id.container);
      TabLayout      emojiTabs       = requireDialog().findViewById(R.id.emoji_tabs);

      ViewCompat.setOnApplyWindowInsetsListener(container, (v, insets) -> insets.consumeSystemWindowInsets());

      TabLayoutMediator mediator = new TabLayoutMediator(emojiTabs, recipientPagerView, (tab, position) -> {
        tab.setCustomView(R.layout.reactions_pill);

        View           customView = Objects.requireNonNull(tab.getCustomView());
        EmojiImageView emoji      = customView.findViewById(R.id.reactions_pill_emoji);
        TextView       text       = customView.findViewById(R.id.reactions_pill_count);
        EmojiCount     emojiCount = recipientsAdapter.getEmojiCount(position);

        customView.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.reaction_pill_dialog_background));
        emoji.setImageEmoji(emojiCount.getDisplayEmoji());
        text.setText(NumberUtil.getFormattedNumber(emojiCount.getCount()));
      });

      emojiTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
        @Override
        public void onTabSelected(TabLayout.Tab tab) {
          View customView = tab.getCustomView();
          TextView text = customView.findViewById(R.id.reactions_pill_count);
          customView.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.reaction_pill_background_selected));
          text.setTextColor(ThemeUtil.getThemedColor(requireContext(), R.attr.reactionsPillSelectedTextColor));
        }

        @Override
        public void onTabUnselected(TabLayout.Tab tab) {
          View customView = tab.getCustomView();
          TextView text = customView.findViewById(R.id.reactions_pill_count);
          customView.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.reaction_pill_dialog_background));
          text.setTextColor(ThemeUtil.getThemedColor(requireContext(), R.attr.reactionsPillNormalTextColor));
        }
        @Override
        public void onTabReselected(TabLayout.Tab tab) {}
      });
      mediator.attach();
    }
  }

  private void setUpRecipientsRecyclerView() {
    recipientsAdapter = new ReactionViewPagerAdapter(this);

    recipientPagerView.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
      @Override
      public void onPageSelected(int position) {
        recipientPagerView.post(() -> recipientsAdapter.enableNestedScrollingForPosition(position));
      }

      @Override
      public void onPageScrollStateChanged(int state) {
        if (state == ViewPager2.SCROLL_STATE_IDLE) {
          recipientPagerView.requestLayout();
        }
      }
    });

    recipientPagerView.setAdapter(recipientsAdapter);
  }

  private void setUpViewModel(@NonNull MessageId messageId) {
    ReactionsViewModel.Factory factory = new ReactionsViewModel.Factory(messageId);

    ReactionsViewModel viewModel = new ViewModelProvider(this, factory).get(ReactionsViewModel.class);

    disposables.add(viewModel.getEmojiCounts().subscribe(emojiCounts -> {
      if (emojiCounts.size() < 1) {
        dismiss();
        return;
      }

      recipientsAdapter.submitList(emojiCounts);
    }));
  }

  @Override
  public void onRemoveReaction(@NonNull String emoji, @NonNull MessageId messageId, long timestamp) {
    callback.onRemoveReaction(emoji, messageId);
    dismiss();
  }

  @Override
  public void onClearAll(@NonNull String emoji, @NonNull MessageId messageId) {
    callback.onClearAll(emoji, messageId);
    dismiss();
  }

  public interface Callback {
    void onRemoveReaction(@NonNull String emoji, @NonNull MessageId messageId);

    void onClearAll(@NonNull String emoji, @NonNull MessageId messageId);
  }

}
