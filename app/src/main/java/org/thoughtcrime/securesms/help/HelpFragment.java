package org.thoughtcrime.securesms.help;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;

import com.annimon.stream.Stream;
import com.dd.CircularProgressButton;

import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.emoji.EmojiImageView;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.IntentUtils;
import org.thoughtcrime.securesms.util.text.AfterTextChanged;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HelpFragment extends Fragment {

  private EditText               problem;
  private CheckBox               includeDebugLogs;
  private View                   debugLogInfo;
  private View                   faq;
  private CircularProgressButton next;
  private View                   toaster;
  private List<EmojiImageView>   emoji;
  private HelpViewModel          helpViewModel;

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.help_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    initializeViewModels();
    initializeViews(view);
    initializeListeners();
    initializeObservers();
  }

  @Override
  public void onResume() {
    super.onResume();
    ((ApplicationPreferencesActivity) getActivity()).getSupportActionBar().setTitle(R.string.preferences__help);

    cancelSpinning(next);
    problem.setEnabled(true);
  }

  private void initializeViewModels() {
    helpViewModel = ViewModelProviders.of(this).get(HelpViewModel.class);
  }

  private void initializeViews(@NonNull View view) {
    problem          = view.findViewById(R.id.help_fragment_problem);
    includeDebugLogs = view.findViewById(R.id.help_fragment_debug);
    debugLogInfo     = view.findViewById(R.id.help_fragment_debug_info);
    faq              = view.findViewById(R.id.help_fragment_faq);
    next             = view.findViewById(R.id.help_fragment_next);
    toaster          = view.findViewById(R.id.help_fragment_next_toaster);
    emoji            = new ArrayList<>(Feeling.values().length);

    for (Feeling feeling : Feeling.values()) {
      EmojiImageView emojiView = view.findViewById(feeling.getViewId());
      emojiView.setImageEmoji(feeling.getEmojiCode());
      emoji.add(view.findViewById(feeling.getViewId()));
    }
  }

  private void initializeListeners() {
    problem.addTextChangedListener(new AfterTextChanged(e -> helpViewModel.onProblemChanged(e.toString())));
    Stream.of(emoji).forEach(view -> view.setOnClickListener(this::handleEmojiClicked));
    faq.setOnClickListener(v -> launchFaq());
    debugLogInfo.setOnClickListener(v -> launchDebugLogInfo());
    next.setOnClickListener(v -> submitForm());
    toaster.setOnClickListener(v -> Toast.makeText(requireContext(), R.string.HelpFragment__please_be_as_descriptive_as_possible, Toast.LENGTH_LONG).show());
  }

  private void initializeObservers() {
    //noinspection CodeBlock2Expr
    helpViewModel.isFormValid().observe(getViewLifecycleOwner(), isValid -> {
      next.setEnabled(isValid);
      toaster.setVisibility(isValid ? View.GONE : View.VISIBLE);
    });
  }

  private void handleEmojiClicked(@NonNull View clicked) {
    if (clicked.isSelected()) {
      clicked.setSelected(false);
    } else {
      Stream.of(emoji).forEach(view -> view.setSelected(false));
      clicked.setSelected(true);
    }
  }

  private void launchFaq() {
    Uri    data   = Uri.parse(getString(R.string.HelpFragment__link__faq));
    Intent intent = new Intent(Intent.ACTION_VIEW, data);

    startActivity(intent);
  }

  private void launchDebugLogInfo() {
    Uri    data   = Uri.parse(getString(R.string.HelpFragment__link__debug_info));
    Intent intent = new Intent(Intent.ACTION_VIEW, data);

    startActivity(intent);
  }

  private void submitForm() {
    setSpinning(next);
    problem.setEnabled(false);

    helpViewModel.onSubmitClicked(includeDebugLogs.isChecked()).observe(this, result -> {
      if (result.getDebugLogUrl().isPresent()) {
        submitFormWithDebugLog(result.getDebugLogUrl().get());
      } else if (result.isError()) {
        submitFormWithDebugLog(getString(R.string.HelpFragment__could_not_upload_logs));
      } else {
        submitFormWithDebugLog(null);
      }
    });
  }

  private void submitFormWithDebugLog(@Nullable String debugLog) {
    Feeling feeling = Stream.of(emoji)
                            .filter(View::isSelected)
                            .map(view -> Feeling.getByViewId(view.getId()))
                            .findFirst().orElse(null);

    CommunicationActions.openEmail(requireContext(),
                                   getString(R.string.RegistrationActivity_support_email),
                                   getEmailSubject(),
                                   getEmailBody(debugLog, feeling).toString());
  }

  private String getEmailSubject() {
    return getString(R.string.HelpFragment__signal_android_support_request);
  }

  private Spanned getEmailBody(@Nullable String debugLog, @Nullable Feeling feeling) {
    SpannableStringBuilder builder = new SpannableStringBuilder();

    builder.append(problem.getText().toString());
    builder.append("\n\n");
    builder.append("--- ");
    builder.append(getString(R.string.HelpFragment__support_info));
    builder.append(" ---\n");
    builder.append(getString(R.string.HelpFragment__subject));
    builder.append(" ");
    builder.append(getString(R.string.HelpFragment__signal_android_support_request));
    builder.append("\n");
    builder.append(getString(R.string.HelpFragment__device_info));
    builder.append(" ");
    builder.append(getDeviceInfo());
    builder.append("\n");
    builder.append(getString(R.string.HelpFragment__android_version));
    builder.append(" ");
    builder.append(getAndroidVersion());
    builder.append("\n");
    builder.append(getString(R.string.HelpFragment__signal_version));
    builder.append(" ");
    builder.append(getSignalVersion());
    builder.append("\n");
    builder.append(getString(R.string.HelpFragment__locale));
    builder.append(" ");
    builder.append(Locale.getDefault().toString());

    if (debugLog != null) {
      builder.append("\n");
      builder.append(getString(R.string.HelpFragment__debug_log));
      builder.append(" ");
      builder.append(debugLog);
    }

    if (feeling != null) {
      builder.append("\n\n");
      builder.append(feeling.getEmojiCode());
      builder.append("\n");
      builder.append(getString(feeling.getStringId()));
    }

    return builder;
  }

  private static CharSequence getDeviceInfo() {
    return String.format("%s %s (%s)", Build.MANUFACTURER, Build.MODEL, Build.PRODUCT);
  }

  private static CharSequence getAndroidVersion() {
    return String.format("%s (%s, %s)", Build.VERSION.RELEASE, Build.VERSION.INCREMENTAL, Build.DISPLAY);
  }

  private static CharSequence getSignalVersion() {
    return BuildConfig.VERSION_NAME;
  }

  private static void setSpinning(@Nullable CircularProgressButton button) {
    if (button != null) {
      button.setClickable(false);
      button.setIndeterminateProgressMode(true);
      button.setProgress(50);
    }
  }

  private static void cancelSpinning(@Nullable CircularProgressButton button) {
    if (button != null) {
      button.setProgress(0);
      button.setIndeterminateProgressMode(false);
      button.setClickable(true);
    }
  }

  private enum Feeling {
    ECSTATIC(R.id.help_fragment_emoji_5, R.string.HelpFragment__emoji_5, "\ud83d\ude00"),
    HAPPY(R.id.help_fragment_emoji_4, R.string.HelpFragment__emoji_4, "\ud83d\ude42"),
    AMBIVALENT(R.id.help_fragment_emoji_3, R.string.HelpFragment__emoji_3, "\ud83d\ude10"),
    UNHAPPY(R.id.help_fragment_emoji_2, R.string.HelpFragment__emoji_2, "\ud83d\ude41"),
    ANGRY(R.id.help_fragment_emoji_1, R.string.HelpFragment__emoji_1, "\ud83d\ude20");

    private final @IdRes     int          viewId;
    private final @StringRes int          stringId;
    private final            CharSequence emojiCode;

    Feeling(@IdRes int viewId, @StringRes int stringId, @NonNull CharSequence emojiCode) {
      this.viewId    = viewId;
      this.stringId  = stringId;
      this.emojiCode = emojiCode;
    }

    public @IdRes int getViewId() {
      return viewId;
    }

    public @StringRes int getStringId() {
      return stringId;
    }

    public @NonNull CharSequence getEmojiCode() {
      return emojiCode;
    }

    static Feeling getByViewId(@IdRes int viewId) {
      for (Feeling feeling : values()) {
        if (feeling.viewId == viewId) {
          return feeling;
        }
      }

      throw new AssertionError();
    }
  }
}
