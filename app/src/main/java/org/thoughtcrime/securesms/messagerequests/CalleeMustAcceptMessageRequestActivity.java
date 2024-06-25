package org.thoughtcrime.securesms.messagerequests;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;

import org.thoughtcrime.securesms.BaseActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.concurrent.TimeUnit;

import static android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION;

public class CalleeMustAcceptMessageRequestActivity extends BaseActivity {

  private static final long   TIMEOUT_MS         = TimeUnit.SECONDS.toMillis(10);
  private static final String RECIPIENT_ID_EXTRA = "extra.recipient.id";

  private TextView        description;
  private AvatarImageView avatar;
  private View            okay;

  private final Handler  handler   = new Handler(Looper.getMainLooper());
  private final Runnable finisher = this::finish;

  public static Intent createIntent(@NonNull Context context, @NonNull RecipientId recipientId) {
    Intent intent = new Intent(context, CalleeMustAcceptMessageRequestActivity.class);
    intent.setFlags(FLAG_ACTIVITY_NO_ANIMATION);
    intent.putExtra(RECIPIENT_ID_EXTRA, recipientId);
    return intent;
  }

  @SuppressLint("SourceLockedOrientationActivity")
  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.callee_must_accept_message_request_dialog_fragment);

    boolean callingFixedToPortrait = getResources().getConfiguration().densityDpi < 480;
    if (callingFixedToPortrait) {
      setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    description = findViewById(R.id.description);
    avatar      = findViewById(R.id.avatar);
    okay        = findViewById(R.id.okay);

    okay.setOnClickListener(v -> finish());

    RecipientId                                     recipientId = getIntent().getParcelableExtra(RECIPIENT_ID_EXTRA);
    CalleeMustAcceptMessageRequestViewModel.Factory factory     = new CalleeMustAcceptMessageRequestViewModel.Factory(recipientId);
    CalleeMustAcceptMessageRequestViewModel         viewModel   = new ViewModelProvider(this, factory).get(CalleeMustAcceptMessageRequestViewModel.class);

    viewModel.getRecipient().observe(this, recipient -> {
      description.setText(getString(R.string.CalleeMustAcceptMessageRequestDialogFragment__s_will_get_a_message_request_from_you, recipient.getDisplayName(this)));
      avatar.setAvatar(Glide.with(this), recipient, false);
    });
  }

  @Override
  public void onResume() {
    super.onResume();

    handler.postDelayed(finisher, TIMEOUT_MS);
  }

  @Override
  public void onPause() {
    super.onPause();

    handler.removeCallbacks(finisher);
  }
}
