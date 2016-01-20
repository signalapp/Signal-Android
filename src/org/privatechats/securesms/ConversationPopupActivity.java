package org.privatechats.securesms;

import android.content.Intent;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityOptionsCompat;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;

import org.privatechats.securesms.crypto.MasterSecret;
import org.privatechats.securesms.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;

public class ConversationPopupActivity extends ConversationActivity {

  private static final String TAG = ConversationPopupActivity.class.getSimpleName();

  @Override
  protected void onPreCreate() {
    super.onPreCreate();
    overridePendingTransition(R.anim.slide_from_top, R.anim.slide_to_top);
  }

  @Override
  protected void onCreate(Bundle bundle, @NonNull MasterSecret masterSecret) {
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                         WindowManager.LayoutParams.FLAG_DIM_BEHIND);

    WindowManager.LayoutParams params = getWindow().getAttributes();
    params.alpha     = 1.0f;
    params.dimAmount = 0.1f;
    params.gravity   = Gravity.TOP;
    getWindow().setAttributes(params);

    Display display = getWindowManager().getDefaultDisplay();
    int     width   = display.getWidth();
    int     height  = display.getHeight();

    if (height > width) getWindow().setLayout((int) (width * .85), (int) (height * .5));
    else                getWindow().setLayout((int) (width * .7), (int) (height * .75));

    super.onCreate(bundle, masterSecret);

    titleView.setOnClickListener(null);
  }

  @Override
  protected void onResume() {
    super.onResume();
    composeText.requestFocus();
    quickAttachmentToggle.disable();
  }

  @Override
  protected void onPause() {
    super.onPause();
    if (isFinishing()) overridePendingTransition(R.anim.slide_from_top, R.anim.slide_to_top);
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    MenuInflater inflater = this.getMenuInflater();
    menu.clear();

    inflater.inflate(R.menu.conversation_popup, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.menu_expand:
        saveDraft().addListener(new ListenableFuture.Listener<Long>() {
          @Override
          public void onSuccess(Long result) {
            ActivityOptionsCompat transition = ActivityOptionsCompat.makeScaleUpAnimation(getWindow().getDecorView(), 0, 0, getWindow().getAttributes().width, getWindow().getAttributes().height);
            Intent intent = new Intent(ConversationPopupActivity.this, ConversationActivity.class);
            intent.putExtra(ConversationActivity.RECIPIENTS_EXTRA, getRecipients().getIds());
            intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, result);

            if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) {
              startActivity(intent, transition.toBundle());
            } else {
              startActivity(intent);
              overridePendingTransition(R.anim.fade_scale_in, R.anim.slide_to_right);
            }

            finish();
          }

          @Override
          public void onFailure(ExecutionException e) {
            Log.w(TAG, e);
          }
        });
        return true;
    }

    return false;
  }

  @Override
  protected void initializeActionBar() {
    super.initializeActionBar();
    getSupportActionBar().setDisplayHomeAsUpEnabled(false);
  }

  @Override
  protected void sendComplete(long threadId) {
    super.sendComplete(threadId);
    finish();
  }

  @Override
  protected void updateInviteReminder() {
    reminderView.setVisibility(View.GONE);
  }
}
