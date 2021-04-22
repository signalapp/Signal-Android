package org.thoughtcrime.securesms.giph.ui;


import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;

import org.thoughtcrime.securesms.PassphraseRequiredActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4Fragment;
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4SaveResult;
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4ViewModel;
import org.thoughtcrime.securesms.util.DynamicDarkToolbarTheme;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.WindowUtil;
import org.thoughtcrime.securesms.util.views.SimpleProgressDialog;

public class GiphyActivity extends PassphraseRequiredActivity implements GiphyActivityToolbar.OnFilterChangedListener {

  public static final String EXTRA_IS_MMS     = "extra_is_mms";
  public static final String EXTRA_WIDTH      = "extra_width";
  public static final String EXTRA_HEIGHT     = "extra_height";
  public static final String EXTRA_COLOR      = "extra_color";

  private final DynamicTheme    dynamicTheme    = new DynamicDarkToolbarTheme();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private GiphyMp4ViewModel giphyMp4ViewModel;
  private AlertDialog       progressDialog;

  @Override
  public void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }

  @Override
  public void onCreate(Bundle bundle, boolean ready) {
    setContentView(R.layout.giphy_activity);

    final boolean forMms = getIntent().getBooleanExtra(EXTRA_IS_MMS, false);

    giphyMp4ViewModel = ViewModelProviders.of(this, new GiphyMp4ViewModel.Factory(forMms)).get(GiphyMp4ViewModel.class);
    giphyMp4ViewModel.getSaveResultEvents().observe(this, this::handleGiphyMp4SaveResult);

    initializeToolbar();

    Fragment fragment = GiphyMp4Fragment.create(forMms);
    getSupportFragmentManager().beginTransaction()
                               .replace(R.id.fragment_container, fragment)
                               .commit();
  }

  private void initializeToolbar() {
    GiphyActivityToolbar toolbar = findViewById(R.id.giphy_toolbar);
    toolbar.setOnFilterChangedListener(this);

    final int conversationColor = getConversationColor();
    toolbar.setBackgroundColor(conversationColor);
    WindowUtil.setStatusBarColor(getWindow(), conversationColor);

    setSupportActionBar(toolbar);

    getSupportActionBar().setDisplayHomeAsUpEnabled(false);
    getSupportActionBar().setDisplayShowTitleEnabled(false);
  }

  private void handleGiphyMp4SaveResult(@NonNull GiphyMp4SaveResult result) {
    if (result instanceof GiphyMp4SaveResult.Success) {
      hideProgressDialog();
      handleGiphyMp4SuccessfulResult((GiphyMp4SaveResult.Success) result);
    } else if (result instanceof GiphyMp4SaveResult.Error) {
      hideProgressDialog();
      handleGiphyMp4ErrorResult((GiphyMp4SaveResult.Error) result);
    } else {
      progressDialog = SimpleProgressDialog.show(this);
    }
  }

  private void hideProgressDialog() {
    if (progressDialog != null) {
      progressDialog.dismiss();
    }
  }

  private void handleGiphyMp4SuccessfulResult(@NonNull GiphyMp4SaveResult.Success success) {
    Intent intent = new Intent();
    intent.setData(success.getBlobUri());
    intent.putExtra(EXTRA_WIDTH, success.getWidth());
    intent.putExtra(EXTRA_HEIGHT, success.getHeight());

    setResult(RESULT_OK, intent);
    finish();
  }

  private void handleGiphyMp4ErrorResult(@NonNull GiphyMp4SaveResult.Error error) {
    Toast.makeText(this, R.string.GiphyActivity_error_while_retrieving_full_resolution_gif, Toast.LENGTH_LONG).show();
  }

  private @ColorInt int getConversationColor() {
    return getIntent().getIntExtra(EXTRA_COLOR, ActivityCompat.getColor(this, R.color.core_ultramarine));
  }

  @Override
  public void onFilterChanged(String filter) {
    giphyMp4ViewModel.updateSearchQuery(filter);
  }
}
