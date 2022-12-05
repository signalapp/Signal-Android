package org.thoughtcrime.securesms.logsubmit;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ShareCompat;
import androidx.core.text.util.LinkifyCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.BaseActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.ProgressCard;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.LongClickCopySpan;
import org.thoughtcrime.securesms.util.LongClickMovementMethod;
import org.thoughtcrime.securesms.util.ThemeUtil;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.views.CircularProgressMaterialButton;

import java.util.List;

public class SubmitDebugLogActivity extends BaseActivity implements SubmitDebugLogAdapter.Listener {

  private static final int CODE_SAVE = 24601;

  private RecyclerView            lineList;
  private SubmitDebugLogAdapter   adapter;
  private SubmitDebugLogViewModel viewModel;

  private View                           warningBanner;
  private View                           editBanner;
  private CircularProgressMaterialButton submitButton;
  private View                           scrollToBottomButton;
  private View                           scrollToTopButton;
  private ProgressCard                   progressCard;

  private MenuItem editMenuItem;
  private MenuItem doneMenuItem;
  private MenuItem searchMenuItem;
  private MenuItem saveMenuItem;

  private final DynamicTheme dynamicTheme = new DynamicTheme();

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

    this.editMenuItem   = menu.findItem(R.id.menu_edit_log);
    this.doneMenuItem   = menu.findItem(R.id.menu_done_editing_log);
    this.searchMenuItem = menu.findItem(R.id.menu_search);
    this.saveMenuItem   = menu.findItem(R.id.menu_save);

    SearchView searchView                        = (SearchView) searchMenuItem.getActionView();
    SearchView.OnQueryTextListener queryListener = new SearchView.OnQueryTextListener() {
      @Override
      public boolean onQueryTextSubmit(String query) {
        viewModel.onQueryUpdated(query);
        return true;
      }

      @Override
      public boolean onQueryTextChange(String query) {
        viewModel.onQueryUpdated(query);
        return true;
      }
    };

    searchMenuItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
      @Override
      public boolean onMenuItemActionExpand(MenuItem item) {
        searchView.setOnQueryTextListener(queryListener);
        return true;
      }

      @Override
      public boolean onMenuItemActionCollapse(MenuItem item) {
        searchView.setOnQueryTextListener(null);
        viewModel.onSearchClosed();
        return true;
      }
    });

    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);

    if (item.getItemId() == android.R.id.home) {
      finish();
      return true;
    } else if (item.getItemId() == R.id.menu_edit_log) {
      viewModel.onEditButtonPressed();
    } else if (item.getItemId() == R.id.menu_done_editing_log) {
      viewModel.onDoneEditingButtonPressed();
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

  @Override
  public void onLogDeleted(@NonNull LogLine logLine) {
    viewModel.onLogDeleted(logLine);
  }

  private void initView() {
    this.lineList             = findViewById(R.id.debug_log_lines);
    this.warningBanner        = findViewById(R.id.debug_log_warning_banner);
    this.editBanner           = findViewById(R.id.debug_log_edit_banner);
    this.submitButton         = findViewById(R.id.debug_log_submit_button);
    this.scrollToBottomButton = findViewById(R.id.debug_log_scroll_to_bottom);
    this.scrollToTopButton    = findViewById(R.id.debug_log_scroll_to_top);
    this.progressCard         = findViewById(R.id.debug_log_progress_card);

    this.adapter = new SubmitDebugLogAdapter(this, viewModel.getPagingController());

    this.lineList.setLayoutManager(new LinearLayoutManager(this));
    this.lineList.setAdapter(adapter);
    this.lineList.setItemAnimator(null);

    submitButton.setOnClickListener(v -> onSubmitClicked());

    scrollToBottomButton.setOnClickListener(v -> lineList.scrollToPosition(adapter.getItemCount() - 1));
    scrollToTopButton.setOnClickListener(v -> lineList.scrollToPosition(0));

    lineList.addOnScrollListener(new RecyclerView.OnScrollListener() {
      @Override
      public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
        if (((LinearLayoutManager) recyclerView.getLayoutManager()).findLastVisibleItemPosition() < adapter.getItemCount() - 10) {
          scrollToBottomButton.setVisibility(View.VISIBLE);
        } else {
          scrollToBottomButton.setVisibility(View.GONE);
        }

        if (((LinearLayoutManager) recyclerView.getLayoutManager()).findFirstVisibleItemPosition() > 10) {
          scrollToTopButton.setVisibility(View.VISIBLE);
        } else {
          scrollToTopButton.setVisibility(View.GONE);
        }
      }
    });
    this.progressCard.setVisibility(View.VISIBLE);

  }

  private void initViewModel() {
    viewModel.getLines().observe(this, this::presentLines);
    viewModel.getMode().observe(this, this::presentMode);
    viewModel.getEvents().observe(this, this::presentEvents);
  }

  private void presentLines(@NonNull List<LogLine> lines) {
    if (progressCard != null && lines.size() > 0) {
      progressCard.setVisibility(View.GONE);

      warningBanner.setVisibility(View.VISIBLE);
      submitButton.setVisibility(View.VISIBLE);
    }

    adapter.submitList(lines);
  }

  private void presentMode(@NonNull SubmitDebugLogViewModel.Mode mode) {
    switch (mode) {
      case NORMAL:
        editBanner.setVisibility(View.GONE);
        adapter.setEditing(false);
        saveMenuItem.setVisible(true);
        // TODO [greyson][log] Not yet implemented
//        editMenuItem.setVisible(true);
//        doneMenuItem.setVisible(false);
//        searchMenuItem.setVisible(true);
        break;
      case SUBMITTING:
        editBanner.setVisibility(View.GONE);
        adapter.setEditing(false);
        editMenuItem.setVisible(false);
        doneMenuItem.setVisible(false);
        searchMenuItem.setVisible(false);
        saveMenuItem.setVisible(false);
        break;
      case EDIT:
        editBanner.setVisibility(View.VISIBLE);
        adapter.setEditing(true);
        editMenuItem.setVisible(false);
        doneMenuItem.setVisible(true);
        searchMenuItem.setVisible(true);
        saveMenuItem.setVisible(false);
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
    AlertDialog.Builder builder = new AlertDialog.Builder(this)
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

    ViewUtil.setPadding(dialogView, (int) ThemeUtil.getThemedDimen(this, R.attr.dialogPreferredPadding));

    builder.setView(dialogView);
    builder.show();
  }

  private void onSubmitClicked() {
    submitButton.setSpinning();

    viewModel.onSubmitClicked().observe(this, result -> {
      if (result.isPresent()) {
        presentResultDialog(result.get());
      } else {
        Toast.makeText(this, R.string.SubmitDebugLogActivity_failed_to_submit_logs, Toast.LENGTH_LONG).show();
      }

      submitButton.cancelSpinning();
    });
  }
}
