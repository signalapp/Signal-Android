package org.thoughtcrime.securesms.giph.ui;


import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.PassphraseRequiredActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.conversation.MessageSendType;
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4Fragment;
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4SaveResult;
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4ViewModel;
import org.thoughtcrime.securesms.keyboard.emoji.KeyboardPageSearchView;
import org.thoughtcrime.securesms.mediasend.Media;
import org.thoughtcrime.securesms.mediasend.v2.MediaSelectionActivity;
import org.thoughtcrime.securesms.mms.SlideFactory;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.views.SimpleProgressDialog;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;

public class GiphyActivity extends PassphraseRequiredActivity implements KeyboardPageSearchView.Callbacks {

  public static final String EXTRA_IS_MMS       = "extra_is_mms";
  public static final String EXTRA_RECIPIENT_ID = "extra_recipient_id";
  public static final String EXTRA_TRANSPORT    = "extra_transport";
  public static final String EXTRA_TEXT         = "extra_text";

  private static final int MEDIA_SENDER = 12;

  private final DynamicTheme dynamicTheme = new DynamicNoActionBarTheme();

  private GiphyMp4ViewModel giphyMp4ViewModel;
  private AlertDialog       progressDialog;
  private RecipientId       recipientId;
  private MessageSendType   sendType;
  private CharSequence      text;

  @Override
  public void onPreCreate() {
    dynamicTheme.onCreate(this);
  }

  @SuppressLint("MissingInflatedId")
  @Override
  public void onCreate(Bundle bundle, boolean ready) {
    setContentView(R.layout.giphy_activity);

    final boolean forMms = getIntent().getBooleanExtra(EXTRA_IS_MMS, false);

    recipientId = getIntent().getParcelableExtra(EXTRA_RECIPIENT_ID);
    sendType    = getIntent().getParcelableExtra(EXTRA_TRANSPORT);
    text        = getIntent().getCharSequenceExtra(EXTRA_TEXT);

    giphyMp4ViewModel = new ViewModelProvider(this, new GiphyMp4ViewModel.Factory(forMms)).get(GiphyMp4ViewModel.class);
    giphyMp4ViewModel.getSaveResultEvents().observe(this, this::handleGiphyMp4SaveResult);

    initializeToolbar();

    Fragment fragment = GiphyMp4Fragment.create(forMms);
    getSupportFragmentManager().beginTransaction()
                               .replace(R.id.fragment_container, fragment)
                               .commit();

    ViewUtil.focusAndShowKeyboard(findViewById(R.id.emoji_search_entry));
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    if (requestCode == MEDIA_SENDER && resultCode == RESULT_OK) {
      setResult(RESULT_OK, data);
      finish();
    } else {
      super.onActivityResult(requestCode, resultCode, data);
    }
  }

  private void initializeToolbar() {
    KeyboardPageSearchView searchView = findViewById(R.id.giphy_search_text);
    searchView.setCallbacks(this);
    searchView.enableBackNavigation(true);
    ViewUtil.focusAndShowKeyboard(searchView);
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
    SlideFactory.MediaType mediaType = Objects.requireNonNull(SlideFactory.MediaType.from(BlobProvider.getMimeType(success.getBlobUri())));
    String                 mimeType  = MediaUtil.getMimeType(this, success.getBlobUri());
    if (mimeType == null) {
      mimeType = mediaType.toFallbackMimeType();
    }

    Media media = new Media(success.getBlobUri(), mimeType, 0, success.getWidth(), success.getHeight(), 0, 0, false, true, Optional.empty(), Optional.empty(), Optional.empty());
    startActivityForResult(MediaSelectionActivity.editor(this, sendType, Collections.singletonList(media), recipientId, text), MEDIA_SENDER);
  }

  private void handleGiphyMp4ErrorResult(@NonNull GiphyMp4SaveResult.Error error) {
    Toast.makeText(this, R.string.GiphyActivity_error_while_retrieving_full_resolution_gif, Toast.LENGTH_LONG).show();
  }

  @Override
  public void onQueryChanged(@NonNull String query) {
    giphyMp4ViewModel.updateSearchQuery(query);
  }

  @Override
  public void onNavigationClicked() {
    ViewUtil.hideKeyboard(this, findViewById(android.R.id.content));
    finish();
  }

  @Override
  public void onFocusLost() {}

  @Override
  public void onFocusGained() {}

  @Override
  public void onClicked() {}
}
