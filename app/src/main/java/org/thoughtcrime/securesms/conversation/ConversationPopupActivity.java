package org.thoughtcrime.securesms.conversation;

import android.os.Bundle;
import android.view.Display;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.WindowManager;

import androidx.appcompat.widget.Toolbar;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;

public class ConversationPopupActivity extends ConversationActivity {

  private static final String TAG = Log.tag(ConversationPopupActivity.class);

  @Override
  protected void onPreCreate() {
    super.onPreCreate();
    overridePendingTransition(R.anim.slide_from_top, R.anim.slide_to_top);
  }

  @Override
  protected void onCreate(Bundle bundle, boolean ready) {
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

    super.onCreate(bundle, ready);
  }

  @Override
  protected void onResume() {
    super.onResume();
    getTitleView().setOnClickListener(null);
    getComposeText().requestFocus();
    getQuickAttachmentToggle().disable();
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
  public void onInitializeToolbar(Toolbar toolbar) {
  }

  @Override
  public void onSendComplete(long threadId) {
    finish();
  }

  @Override
  public boolean onUpdateReminders() {
    if (getReminderView().resolved()) {
      getReminderView().get().setVisibility(View.GONE);
    }

    return false;
  }
}
