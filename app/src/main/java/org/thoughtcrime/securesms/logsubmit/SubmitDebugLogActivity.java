package org.thoughtcrime.securesms.logsubmit;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ShareCompat;
import androidx.core.content.ContextCompat;
import androidx.core.text.util.LinkifyCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.signal.core.util.ThreadUtil;
import org.signal.debuglogsviewer.DebugLogsViewer;
import org.thoughtcrime.securesms.BaseActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.ConversationSearchBottomBar;
import org.thoughtcrime.securesms.components.ProgressCard;
import org.thoughtcrime.securesms.components.SearchView;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.LongClickCopySpan;
import org.thoughtcrime.securesms.util.LongClickMovementMethod;
import org.thoughtcrime.securesms.util.ThemeUtil;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.views.CircularProgressMaterialButton;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class SubmitDebugLogActivity extends BaseActivity {

  private static final int CODE_SAVE = 24601;

  private WebView                 logWebView;
  private SubmitDebugLogViewModel viewModel;

  private View                           warningBanner;
  private CircularProgressMaterialButton submitButton;
  private ConversationSearchBottomBar    searchNav;
  private View                           scrollToBottomButton;
  private View                           scrollToTopButton;
  private ProgressCard                   progressCard;

  private MenuItem searchMenuItem;
  private MenuItem saveMenuItem;

  private ImageButton filterButton;
  private ImageButton caseSensitiveButton;
  private TextView    searchPosition;
  private ImageButton searchUpButton;
  private ImageButton searchDownButton;
  
  private TextView uncaughtButton;
  private TextView verboseButton;
  private TextView debugButton;
  private TextView infoButton;
  private TextView warningButton;
  private TextView errorButton;

  private boolean isCaseSensitive;
  private boolean isFiltered;
  private boolean isUncaught;
  private boolean isVerbose;
  private boolean isDebug;
  private boolean isInfo;
  private boolean isWarning;
  private boolean isError;

  private final DynamicTheme dynamicTheme = new DynamicTheme();
  private final CompositeDisposable disposables = new CompositeDisposable();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    dynamicTheme.onCreate(this);
    setContentView(R.layout.submit_debug_log_activity);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setTitle(R.string.HelpSettingsFragment__debug_log);

    this.viewModel = new ViewModelProvider(this, new SubmitDebugLogViewModel.Factory()).get(SubmitDebugLogViewModel.class);

    initView();
    initViewModel();
  }

  @Override
  protected void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.submit_debug_log_normal, menu);

    this.searchMenuItem = menu.findItem(R.id.menu_search);
    this.saveMenuItem   = menu.findItem(R.id.menu_save);

    this.searchNav           = findViewById(R.id.debug_log_search_nav);
    this.filterButton        = findViewById(R.id.debug_log_filter);
    this.caseSensitiveButton = findViewById(R.id.case_sensitive_button);
    this.searchPosition      = findViewById(R.id.debug_log_search_position);
    this.searchUpButton      = findViewById(R.id.debug_log_search_up);
    this.searchDownButton    = findViewById(R.id.debug_log_search_down);

    this.uncaughtButton = findViewById(R.id.debug_log_signalUncaughtException);
    this.verboseButton  = findViewById(R.id.debug_log_verbose);
    this.debugButton    = findViewById(R.id.debug_log_debug);
    this.infoButton     = findViewById(R.id.debug_log_info);
    this.warningButton  = findViewById(R.id.debug_log_warning);
    this.errorButton    = findViewById(R.id.debug_log_error);

    searchUpButton.setOnClickListener(v -> {
      DebugLogsViewer.onSearchUp(logWebView);
      DebugLogsViewer.getSearchPosition(logWebView, position -> searchPosition.setText(position));
    });

    searchDownButton.setOnClickListener(v -> {
      DebugLogsViewer.onSearchDown(logWebView);
      DebugLogsViewer.getSearchPosition(logWebView, position -> searchPosition.setText(position));
    });

    caseSensitiveButton.setOnClickListener(v -> {
      DebugLogsViewer.onToggleCaseSensitive(logWebView);
      DebugLogsViewer.getSearchPosition(logWebView, position -> searchPosition.setText(position));
      isCaseSensitive = !isCaseSensitive;

      int backgroundColor = isCaseSensitive ? R.drawable.circle_tint_darker : R.drawable.circle_touch_highlight_background;
      caseSensitiveButton.setBackground(getResources().getDrawable(backgroundColor));
    });

    filterButton.setOnClickListener(v -> {
      isFiltered = !isFiltered;
      if (isFiltered) {
        DebugLogsViewer.onFilter(logWebView);
        searchPosition.setVisibility(View.GONE);
        searchUpButton.setVisibility(View.GONE);
        searchDownButton.setVisibility(View.GONE);
        filterButton.setBackground(getResources().getDrawable(R.drawable.circle_tint_darker));
      } else {
        DebugLogsViewer.onFilterClose(logWebView);
        searchPosition.setVisibility(View.VISIBLE);
        searchUpButton.setVisibility(View.VISIBLE);
        searchDownButton.setVisibility(View.VISIBLE);
        DebugLogsViewer.getSearchPosition(logWebView, position -> searchPosition.setText(position));
        DebugLogsViewer.scrollToTop(logWebView);
        filterButton.setBackground(getResources().getDrawable(R.drawable.circle_touch_highlight_background));
      }
    });

    SearchView searchView = (SearchView) searchMenuItem.getActionView();
    SearchView.OnQueryTextListener queryListener = new SearchView.OnQueryTextListener() {
      @Override
      public boolean onQueryTextSubmit(String query) {
        return true;
      }

      @Override
      public boolean onQueryTextChange(String query) {
        DebugLogsViewer.onSearchInput(logWebView, query);
        if (isFiltered) {
          DebugLogsViewer.onFilter(logWebView);
        } else {
          DebugLogsViewer.onSearch(logWebView);
          DebugLogsViewer.getSearchPosition(logWebView, position -> searchPosition.setText(position));
        }
        return true;
      }
    };

    searchMenuItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
      @Override
      public boolean onMenuItemActionExpand(MenuItem item) {
        searchNav.setVisibility(View.VISIBLE);
        submitButton.setVisibility(View.GONE);
        searchView.setOnQueryTextListener(queryListener);
        return true;
      }

      @Override
      public boolean onMenuItemActionCollapse(MenuItem item) {
        onFilterLevelClose();
        searchNav.setVisibility(View.GONE);
        submitButton.setVisibility(View.VISIBLE);
        DebugLogsViewer.onSearchClose(logWebView);
        DebugLogsViewer.onFilterClose(logWebView);
        DebugLogsViewer.getSearchPosition(logWebView, position -> searchPosition.setText(position));
        searchView.setOnQueryTextListener(null);
        return true;
      }
    });

    verboseButton.setOnClickListener(v -> {
      isVerbose = !isVerbose;
      onFilterLevel(v, isVerbose);
    });

    debugButton.setOnClickListener(v -> {
      isDebug = !isDebug;
      onFilterLevel(v, isDebug);
    });

    infoButton.setOnClickListener(v -> {
      isInfo = !isInfo;
      onFilterLevel(v, isInfo);
    });

    warningButton.setOnClickListener(v -> {
      isWarning = !isWarning;
      onFilterLevel(v, isWarning);
    });

    errorButton.setOnClickListener(v -> {
      isError = !isError;
      onFilterLevel(v, isError);
    });

    uncaughtButton.setOnClickListener(v -> {
      isUncaught = !isUncaught;
      onFilterLevel(v, isUncaught);
    });
    

    if (viewModel.getMode().getValue() != null) {
      presentMode(viewModel.getMode().getValue());
    }

    return true;
  }

  private void onFilterLevel(View view, boolean isChecked) {
    view.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, (isChecked) ? R.color.transparent_black_25 : R.color.signal_background_secondary)));

    List<String> selectedLevels = new ArrayList<>();

    if (isVerbose)  selectedLevels.add("\" V \"");
    if (isDebug)    selectedLevels.add("\" D \"");
    if (isInfo)     selectedLevels.add("\" I \"");
    if (isWarning)  selectedLevels.add("\" W \"");
    if (isError)    selectedLevels.add("\" E \"");
    if (isUncaught) selectedLevels.add("\" SignalUncaughtException:\"");

    DebugLogsViewer.onFilterLevel(logWebView, "[" + String.join(",", selectedLevels) + "]");
    DebugLogsViewer.getSearchPosition(logWebView, position -> searchPosition.setText(position));
  }

  private void onFilterLevelClose() {
    isVerbose  = false;
    isDebug    = false;
    isInfo     = false;
    isWarning  = false;
    isError    = false;
    isUncaught = false;

    verboseButton.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.signal_background_secondary)));
    debugButton.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.signal_background_secondary)));
    infoButton.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.signal_background_secondary)));
    warningButton.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.signal_background_secondary)));
    errorButton.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.signal_background_secondary)));
    uncaughtButton.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.signal_background_secondary)));

    DebugLogsViewer.onFilterLevel(logWebView, "[]");
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);

    if (item.getItemId() == android.R.id.home) {
      finish();
      return true;
    } else if (item.getItemId() == R.id.menu_save) {
      Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
      intent.addCategory(Intent.CATEGORY_OPENABLE);
      intent.setType("application/zip");
      intent.putExtra(Intent.EXTRA_TITLE, "signal-log-" + System.currentTimeMillis() + ".zip");

      startActivityForResult(intent, CODE_SAVE);
    }

    return false;
  }

  @Override
  public void onBackPressed() {
    if (!viewModel.onBackPressed()) {
      super.onBackPressed();
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (requestCode == CODE_SAVE && resultCode == Activity.RESULT_OK) {
      Uri uri = data != null ? data.getData() : null;
      viewModel.onDiskSaveLocationReady(uri);
      if (progressCard != null) {
        progressCard.setVisibility(View.VISIBLE);
      }
    }
  }

  private void initView() {
    this.logWebView           = findViewById(R.id.debug_log_lines);
    this.warningBanner        = findViewById(R.id.debug_log_warning_banner);
    this.submitButton         = findViewById(R.id.debug_log_submit_button);
    this.scrollToBottomButton = findViewById(R.id.debug_log_scroll_to_bottom);
    this.scrollToTopButton    = findViewById(R.id.debug_log_scroll_to_top);
    this.progressCard         = findViewById(R.id.debug_log_progress_card);

    DebugLogsViewer.initWebView(logWebView, this, () -> {
      logWebView.animate().alpha(1f).setDuration(250).start();
      subscribeToLogLines();
    });

    submitButton.setOnClickListener(v -> onSubmitClicked());
    scrollToTopButton.setOnClickListener(v -> DebugLogsViewer.scrollToTop(logWebView));
    scrollToBottomButton.setOnClickListener(v -> DebugLogsViewer.scrollToBottom(logWebView));

    this.progressCard.setVisibility(View.VISIBLE);
  }

  private void initViewModel() {
    viewModel.getMode().observe(this, this::presentMode);
    viewModel.getEvents().observe(this, this::presentEvents);
  }

  private void subscribeToLogLines() {
    Disposable disposable = viewModel.getLogLinesObservable()
        .observeOn(Schedulers.io())
        .subscribe(this::appendLines, throwable -> {
          // Handle error
          ThreadUtil.runOnMain(() -> {
            this.progressCard.setVisibility(View.GONE);
          });
        });
    disposables.add(disposable);
  }

  private void appendLines(@NonNull List<String> lines) {
    ThreadUtil.runOnMain(() -> {
      warningBanner.setVisibility(View.VISIBLE);
      submitButton.setVisibility(View.VISIBLE);
    });

    StringBuilder lineBuilder = new StringBuilder();

    for (String line : lines) {
      lineBuilder.append(line).append("\n");
    }

    DebugLogsViewer.appendLines(logWebView, lineBuilder.toString());
  }

  private void presentMode(@NonNull SubmitDebugLogViewModel.Mode mode) {
    if (searchMenuItem == null || saveMenuItem == null) {
      return;
    }

    switch (mode) {
      case LOADING:
        searchNav.setVisibility(View.GONE);
        saveMenuItem.setVisible(false);
        searchMenuItem.setVisible(false);
        progressCard.setVisibility(View.VISIBLE);
        submitButton.setEnabled(false);
        logWebView.setAlpha(0.25f);
        break;
      case NORMAL:
        searchNav.setVisibility(View.GONE);
        saveMenuItem.setVisible(true);
        searchMenuItem.setVisible(true);
        progressCard.setVisibility(View.GONE);
        submitButton.setEnabled(true);
        logWebView.setAlpha(1f);
        break;
      case SUBMITTING:
        searchNav.setVisibility(View.GONE);
        saveMenuItem.setVisible(false);
        searchMenuItem.setVisible(false);
        progressCard.setVisibility(View.GONE);
        submitButton.setSpinning();
        logWebView.setAlpha(1f);
        break;
    }
  }

  private void presentEvents(@NonNull SubmitDebugLogViewModel.Event event) {
    switch (event) {
      case FILE_SAVE_SUCCESS:
        Toast.makeText(this, R.string.SubmitDebugLogActivity_save_complete, Toast.LENGTH_SHORT).show();
        if (progressCard != null) {
          progressCard.setVisibility(View.GONE);
        }
        break;
      case FILE_SAVE_ERROR:
        Toast.makeText(this, R.string.SubmitDebugLogActivity_failed_to_save, Toast.LENGTH_SHORT).show();
        break;
    }
  }

  private void presentResultDialog(@NonNull String url) {
    AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this)
        .setTitle(R.string.SubmitDebugLogActivity_success)
        .setCancelable(false)
        .setNeutralButton(android.R.string.ok, (d, w) -> finish())
        .setPositiveButton(R.string.SubmitDebugLogActivity_share, (d, w) -> {
          ShareCompat.IntentBuilder.from(this)
                                   .setText(url)
                                   .setType("text/plain")
                                   .setEmailTo(new String[] { "support@signal.org" })
                                   .startChooser();
        });

    String            dialogText          = getResources().getString(R.string.SubmitDebugLogActivity_copy_this_url_and_add_it_to_your_issue, url);
    SpannableString   spannableDialogText = new SpannableString(dialogText);
    TextView          dialogView          = new TextView(builder.getContext());
    LongClickCopySpan longClickUrl        = new LongClickCopySpan(url);


    LinkifyCompat.addLinks(spannableDialogText, Linkify.WEB_URLS);

    URLSpan[] spans = spannableDialogText.getSpans(0, spannableDialogText.length(), URLSpan.class);
    for (URLSpan span : spans) {
      int start = spannableDialogText.getSpanStart(span);
      int end   = spannableDialogText.getSpanEnd(span);

      spannableDialogText.setSpan(longClickUrl, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    dialogView.setText(spannableDialogText);
    dialogView.setMovementMethod(LongClickMovementMethod.getInstance(this));

    ViewUtil.setPadding(dialogView, (int) ThemeUtil.getThemedDimen(this, androidx.appcompat.R.attr.dialogPreferredPadding));

    builder.setView(dialogView);
    builder.show();
  }

  private void onSubmitClicked() {
    submitButton.setSpinning();

    viewModel.onSubmitClicked(DebugLogsViewer.readLogs(logWebView)).observe(this, result -> {
      if (result.isPresent()) {
        presentResultDialog(result.get());
      } else {
        Toast.makeText(this, R.string.SubmitDebugLogActivity_failed_to_submit_logs, Toast.LENGTH_LONG).show();
      }

      submitButton.cancelSpinning();
    });
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    disposables.dispose();
  }
}
