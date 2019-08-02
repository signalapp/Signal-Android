package org.thoughtcrime.securesms.revealable;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;

import com.pnikosis.materialishprogress.ProgressWheel;

import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader.DecryptableUri;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.util.Util;

public class RevealableMessageActivity extends PassphraseRequiredActionBarActivity {

  private static final String TAG = Log.tag(RevealableMessageActivity.class);

  private static final String KEY_MESSAGE_ID = "message_id";

  private ImageView                  image;
  private View                       closeButton;
  private RevealableMessageViewModel viewModel;

  public static Intent getIntent(@NonNull Context context, long messageId) {
    Intent intent = new Intent(context, RevealableMessageActivity.class);
    intent.putExtra(KEY_MESSAGE_ID, messageId);
    return intent;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState, boolean ready) {
    super.onCreate(savedInstanceState, ready);
    setContentView(R.layout.revealable_message_activity);

    this.image         = findViewById(R.id.reveal_image);
    this.closeButton   = findViewById(R.id.reveal_close_button);

    image.setOnClickListener(v -> finish());
    closeButton.setOnClickListener(v -> finish());

    initViewModel(getIntent().getLongExtra(KEY_MESSAGE_ID, -1));
  }

  private void initViewModel(long messageId) {
    RevealableMessageRepository repository = new RevealableMessageRepository(this);
    viewModel = ViewModelProviders.of(this, new RevealableMessageViewModel.Factory(getApplication(), messageId, repository))
                                  .get(RevealableMessageViewModel.class);

    viewModel.getMessage().observe(this, (message) -> {
      if (message == null) return;

      if (message.isPresent()) {
        //noinspection ConstantConditions
        GlideApp.with(this)
                .load(new DecryptableUri(message.get().getSlideDeck().getThumbnailSlide().getUri()))
                .into(image);
      } else {
        image.setImageDrawable(null);
        finish();
      }
    });
  }
}
