package org.thoughtcrime.securesms.revealable;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProviders;

import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader.DecryptableUri;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.providers.BlobProvider;

public class ViewOnceMessageActivity extends PassphraseRequiredActionBarActivity {

  private static final String TAG = Log.tag(ViewOnceMessageActivity.class);

  private static final String KEY_MESSAGE_ID = "message_id";
  private static final String KEY_URI        = "uri";

  private ImageView                image;
  private View                     closeButton;
  private ViewOnceMessageViewModel viewModel;
  private Uri                      uri;

  public static Intent getIntent(@NonNull Context context, long messageId, @NonNull Uri uri) {
    Intent intent = new Intent(context, ViewOnceMessageActivity.class);
    intent.putExtra(KEY_MESSAGE_ID, messageId);
    intent.putExtra(KEY_URI, uri);
    return intent;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState, boolean ready) {
    super.onCreate(savedInstanceState, ready);
    setContentView(R.layout.view_once_message_activity);

    this.image       = findViewById(R.id.view_once_image);
    this.closeButton = findViewById(R.id.view_once_close_button);
    this.uri         = getIntent().getParcelableExtra(KEY_URI);

    image.setOnClickListener(v -> finish());
    closeButton.setOnClickListener(v -> finish());


    initViewModel(getIntent().getLongExtra(KEY_MESSAGE_ID, -1), uri);
  }

  @Override
  protected void onStop() {
    super.onStop();
    BlobProvider.getInstance().delete(this, uri);
    finish();
  }

  private void initViewModel(long messageId, @NonNull Uri uri) {
    ViewOnceMessageRepository repository = new ViewOnceMessageRepository(this);

    viewModel = ViewModelProviders.of(this, new ViewOnceMessageViewModel.Factory(getApplication(), messageId, repository))
                                  .get(ViewOnceMessageViewModel.class);

    viewModel.getMessage().observe(this, (message) -> {
      if (message == null) return;

      if (message.isPresent()) {
        GlideApp.with(this)
                .load(new DecryptableUri(uri))
                .into(image);
      } else {
        image.setImageDrawable(null);
        finish();
      }
    });
  }
}
