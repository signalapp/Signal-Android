/**
 * Copyright (C) 2014 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms;

import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.MenuItem;
import com.doomonafireball.betterpickers.hmspicker.HmsPickerBuilder;
import com.doomonafireball.betterpickers.hmspicker.HmsPickerDialogFragment;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.RecipientNotificationsDatabase;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicTheme;

import java.text.DateFormat;
import java.util.Date;


/**
 * Activity that will let the user set a subset of the notification settings on a
 * per-recipient basis.
 *
 * @author Lukas Barth
 *
 */
public class RecipientNotificationSettingsActivity extends SherlockFragmentActivity
  implements HmsPickerDialogFragment.HmsPickerDialogHandler
{
  private static final String FRAG_TAG_TIME_PICKER = "timePickerDialogFragment";

  private Recipient recipient;

  private ToggleButton silencePermanentlyPreference;
  private TextView silenceUntilText;
  private TextView silenceTemporaryText;
  private LinearLayout silenceTemporaryContainer;

  private final DynamicTheme dynamicTheme       = new DynamicTheme();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  @Override
  protected void onCreate(Bundle icicle) {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
    super.onCreate(icicle);

    this.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    this.getSupportActionBar().setHomeButtonEnabled(true);

    this.setContentView(R.layout.recipient_notification_settings_activity);

    initializeResources();
    updateValues();
  }

  private void updateValues() {
    RecipientNotificationsDatabase notificationsDatabase = DatabaseFactory.getInstance(this).getNotificationDatabase(this);

    boolean permanentlySilenced = notificationsDatabase.isSilencedPermanently(this.recipient);
    if (permanentlySilenced) {
      this.silencePermanentlyPreference.setChecked(true);
    } else {
      this.silencePermanentlyPreference.setChecked(false);
    }

    Long silenceUntil = notificationsDatabase.getSilencedUntil(recipient);
    Long now = System.currentTimeMillis() / 1000;
    if ((silenceUntil == null) || (silenceUntil < now)) {
      this.silenceUntilText.setText(R.string.notification_settings__not_temporarily_muted);
    } else {
      Date silenceUntilDate = new Date(silenceUntil * 1000);
      DateFormat dateFormat = DateFormat.getDateTimeInstance();

      this.silenceUntilText.setText(getString(R.string.notification_settings__muted_until) + dateFormat.format(silenceUntilDate));
    }

    if (permanentlySilenced) {
      this.silenceUntilText.setEnabled(false);
      this.silenceTemporaryText.setEnabled(false);
      this.silenceTemporaryContainer.setClickable(false);
    } else {
      this.silenceUntilText.setEnabled(true);
      this.silenceTemporaryText.setEnabled(true);
      this.silenceTemporaryContainer.setClickable(true);
    }
  }

  private void initializeResources() {
    this.recipient = getIntent().getParcelableExtra("recipient");

    this.silencePermanentlyPreference = (ToggleButton) findViewById(R.id.silencePermanentlySwitch);
    this.silencePermanentlyPreference.setOnCheckedChangeListener(new SilencePermanentlyListener());

    this.silenceUntilText = (TextView) findViewById(R.id.silenceTemporaryStatus);
    this.silenceTemporaryText = (TextView) findViewById(R.id.silenceTemporaryText);

    this.silenceTemporaryContainer = (LinearLayout) findViewById(R.id.silenceTemporarilyContainer);
    this.silenceTemporaryContainer.setOnClickListener(new SilenceTemporaryClickListener());
  }

  @Override
  public boolean onMenuItemSelected(int featureId, MenuItem item) {
    int itemId = item.getItemId();

    if (itemId == android.R.id.home) {
      finish();
    }

    return true;
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);
  }

  private class SilenceTemporaryClickListener implements View.OnClickListener
  {
    @Override
    public void onClick(View view) {
      HmsPickerBuilder builder = new HmsPickerBuilder()
              .setFragmentManager(getSupportFragmentManager())
              .setStyleResId(R.style.BetterPickersDialogFragment);
      builder.show();
    }

  }

  @Override
  public void onDialogHmsSet(int reference, int hours, int minutes, int seconds) {
    long now = System.currentTimeMillis() / 1000;

    long then = now + ((hours * 60) + minutes) * 60 + seconds;

    RecipientNotificationsDatabase notificationsDatabase = DatabaseFactory
            .getNotificationDatabase(RecipientNotificationSettingsActivity.this);

    notificationsDatabase.setSilenceUntil(recipient, then);
    this.updateValues();
  }

  private class SilencePermanentlyListener implements CompoundButton.OnCheckedChangeListener {

    @Override
    public void onCheckedChanged(CompoundButton widget, boolean checked) {
      RecipientNotificationsDatabase notificationsDatabase = DatabaseFactory
              .getNotificationDatabase(RecipientNotificationSettingsActivity.this);

      notificationsDatabase.setSilencePermanently(RecipientNotificationSettingsActivity.this.recipient,
              checked);
      RecipientNotificationSettingsActivity.this.updateValues();
    }
  }

}
