package org.thoughtcrime.securesms.help;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.ViewModelProvider;

import com.annimon.stream.Stream;

import org.signal.core.util.ResourceUtil;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.emoji.EmojiImageView;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.SupportEmailUtil;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.text.AfterTextChanged;
import org.thoughtcrime.securesms.util.views.CircularProgressMaterialButton;

import java.util.ArrayList;
import java.util.List;

public class HelpFragment extends LoggingFragment {

  public static final String START_CATEGORY_INDEX = "start_category_index";
  public static final int    PAYMENT_INDEX        = 6;
  public static final int    DONATION_INDEX       = 7;
  public static final int    SMS_EXPORT_INDEX     = 8;

  private EditText                       problem;
  private CheckBox                       includeDebugLogs;
  private View                           debugLogInfo;
  private View                           faq;
  private CircularProgressMaterialButton next;
  private View                           toaster;
  private List<EmojiImageView>           emoji;
  private HelpViewModel                  helpViewModel;
  private Spinner                        categorySpinner;
  private ArrayAdapter<CharSequence>     categoryAdapter;

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

    next.cancelSpinning();
    problem.setEnabled(true);
  }

  private void initializeViewModels() {
    helpViewModel = new ViewModelProvider(this).get(HelpViewModel.class);
  }

  private void initializeViews(@NonNull View view) {
    problem          = view.findViewById(R.id.help_fragment_problem);
    includeDebugLogs = view.findViewById(R.id.help_fragment_debug);
    debugLogInfo     = view.findViewById(R.id.help_fragment_debug_info);
    faq              = view.findViewById(R.id.help_fragment_faq);
    next             = view.findViewById(R.id.help_fragment_next);
    toaster          = view.findViewById(R.id.help_fragment_next_toaster);
    categorySpinner  = view.findViewById(R.id.help_fragment_category);
    emoji            = new ArrayList<>(Feeling.values().length);

    for (Feeling feeling : Feeling.values()) {
      EmojiImageView emojiView = view.findViewById(feeling.getViewId());
      emojiView.setImageEmoji(feeling.getEmojiCode());
      emoji.add(view.findViewById(feeling.getViewId()));
    }

    categoryAdapter = ArrayAdapter.createFromResource(requireContext(), R.array.HelpFragment__categories_5, android.R.layout.simple_spinner_item);
    categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

    categorySpinner.setAdapter(categoryAdapter);

    Bundle args = getArguments();
    if (args != null) {
      categorySpinner.setSelection(Util.clamp(args.getInt(START_CATEGORY_INDEX, 0), 0, categorySpinner.getCount() - 1));
    }
  }

  private void initializeListeners() {
    problem.addTextChangedListener(new AfterTextChanged(e -> helpViewModel.onProblemChanged(e.toString())));
    Stream.of(emoji).forEach(view -> view.setOnClickListener(this::handleEmojiClicked));
    faq.setOnClickListener(v -> launchFaq());
    debugLogInfo.setOnClickListener(v -> launchDebugLogInfo());
    next.setOnClickListener(v -> submitForm());
    toaster.setOnClickListener(v -> {
      if (helpViewModel.getCategoryIndex() == 0) {
        categorySpinner.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.shake_horizontal));
      }

      Toast.makeText(requireContext(), R.string.HelpFragment__please_be_as_descriptive_as_possible, Toast.LENGTH_LONG).show();
    });
    categorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        helpViewModel.onCategorySelected(position);
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {
      }
    });
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
    next.setSpinning();
    problem.setEnabled(false);

    helpViewModel.onSubmitClicked(includeDebugLogs.isChecked()).observe(getViewLifecycleOwner(), result -> {
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
                                   SupportEmailUtil.getSupportEmailAddress(requireContext()),
                                   getEmailSubject(),
                                   getEmailBody(debugLog, feeling));
  }

  private String getEmailSubject() {
    return getString(R.string.HelpFragment__signal_android_support_request);
  }

  private String getEmailBody(@Nullable String debugLog, @Nullable Feeling feeling) {
    StringBuilder suffix = new StringBuilder();

    if (debugLog != null) {
      suffix.append("\n");
      suffix.append(getString(R.string.HelpFragment__debug_log));
      suffix.append(" ");
      suffix.append(debugLog);
    }

    if (feeling != null) {
      suffix.append("\n\n");
      suffix.append(feeling.getEmojiCode());
      suffix.append("\n");
      suffix.append(getString(feeling.getStringId()));
    }

    String[] englishCategories = ResourceUtil.getEnglishResources(requireContext()).getStringArray(R.array.HelpFragment__categories_5);
    String   category          = (helpViewModel.getCategoryIndex() >= 0 && helpViewModel.getCategoryIndex() < englishCategories.length) ? englishCategories[helpViewModel.getCategoryIndex()]
                                                                                                                                        : categoryAdapter.getItem(helpViewModel.getCategoryIndex()).toString();

    return SupportEmailUtil.generateSupportEmailBody(requireContext(),
                                                     R.string.HelpFragment__signal_android_support_request,
                                                     " - " + category,
                                                     problem.getText().toString() + "\n\n",
                                                     suffix.toString());
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
