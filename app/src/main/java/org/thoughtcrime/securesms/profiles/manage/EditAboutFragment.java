package org.thoughtcrime.securesms.profiles.manage;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import org.signal.core.util.BreakIteratorCompat;
import org.signal.core.util.EditTextUtil;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.emoji.EmojiUtil;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.ProfileUploadJob;
import org.thoughtcrime.securesms.reactions.any.ReactWithAnyEmojiBottomSheetDialogFragment;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.StringUtil;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;
import org.thoughtcrime.securesms.util.text.AfterTextChanged;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.ProfileCipher;

/**
 * Let's you edit the 'About' section of your profile.
 */
public class EditAboutFragment extends Fragment implements ManageProfileActivity.EmojiController {

  public static final int ABOUT_MAX_GLYPHS              = 100;
  public static final int ABOUT_LIMIT_DISPLAY_THRESHOLD = 75;

  private static final String KEY_SELECTED_EMOJI = "selected_emoji";

  private ImageView emojiView;
  private EditText  bodyView;
  private TextView  countView;

  private String selectedEmoji;

  @Override
  public @NonNull View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.edit_about_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    this.emojiView  = view.findViewById(R.id.edit_about_emoji);
    this.bodyView   = view.findViewById(R.id.edit_about_body);
    this.countView  = view.findViewById(R.id.edit_about_count);

    view.<Toolbar>findViewById(R.id.toolbar)
        .setNavigationOnClickListener(v -> Navigation.findNavController(view)
                                                     .popBackStack());

    EditTextUtil.addGraphemeClusterLimitFilter(bodyView, ABOUT_MAX_GLYPHS);
    this.bodyView.addTextChangedListener(new AfterTextChanged(editable -> {
      trimFieldToMaxByteLength(editable);
      presentCount(editable.toString());
    }));

    this.emojiView.setOnClickListener(v -> {
      ReactWithAnyEmojiBottomSheetDialogFragment.createForAboutSelection()
                                                .show(requireFragmentManager(), "BOTTOM");
    });

    view.findViewById(R.id.edit_about_save).setOnClickListener(this::onSaveClicked);
    view.findViewById(R.id.edit_about_clear).setOnClickListener(v -> onClearClicked());

    if (savedInstanceState != null && savedInstanceState.containsKey(KEY_SELECTED_EMOJI)) {
      onEmojiSelected(savedInstanceState.getString(KEY_SELECTED_EMOJI, ""));
    } else {
      this.bodyView.setText(Recipient.self().getAbout());
      onEmojiSelected(Optional.fromNullable(Recipient.self().getAboutEmoji()).or(""));
    }

    ViewUtil.focusAndMoveCursorToEndAndOpenKeyboard(bodyView);
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle outState) {
    outState.putString(KEY_SELECTED_EMOJI, selectedEmoji);
  }

  @Override
  public void onEmojiSelected(@NonNull String emoji) {
    Drawable drawable = EmojiUtil.convertToDrawable(requireContext(), emoji);
    if (drawable != null) {
      this.emojiView.setImageDrawable(drawable);
      this.selectedEmoji = emoji;
    } else {
      this.emojiView.setImageResource(R.drawable.ic_add_emoji);
      this.selectedEmoji = "";
    }
  }

  private void presentCount(@NonNull String aboutBody) {
    BreakIteratorCompat breakIterator = BreakIteratorCompat.getInstance();
    breakIterator.setText(aboutBody);
    int glyphCount = breakIterator.countBreaks();

    if (glyphCount >= ABOUT_LIMIT_DISPLAY_THRESHOLD) {
      this.countView.setVisibility(View.VISIBLE);
      this.countView.setText(getResources().getString(R.string.EditAboutFragment_count, glyphCount, ABOUT_MAX_GLYPHS));
    } else {
      this.countView.setVisibility(View.GONE);
    }
  }


  private void onSaveClicked(View view) {
    SimpleTask.run(getViewLifecycleOwner().getLifecycle(), () -> {
      DatabaseFactory.getRecipientDatabase(requireContext()).setAbout(Recipient.self().getId(), bodyView.getText().toString(), selectedEmoji);
      ApplicationDependencies.getJobManager().add(new ProfileUploadJob());
      return null;
    }, (nothing) -> {
      Navigation.findNavController(view).popBackStack();
    });
  }

  private void onClearClicked() {
    bodyView.setText("");
    onEmojiSelected("");
  }

  private static void trimFieldToMaxByteLength(Editable s) {
    int trimmedLength = StringUtil.trimToFit(s.toString(), ProfileCipher.MAX_POSSIBLE_ABOUT_LENGTH).length();

    if (s.length() > trimmedLength) {
      s.delete(trimmedLength, s.length());
    }
  }
}
