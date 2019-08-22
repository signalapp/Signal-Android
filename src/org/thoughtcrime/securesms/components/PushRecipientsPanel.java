/**
 * Copyright (C) 2011 Whisper Systems
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
package org.thoughtcrime.securesms.components;

import android.content.Context;

import androidx.annotation.NonNull;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.RelativeLayout;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.contacts.RecipientsAdapter;
import org.thoughtcrime.securesms.contacts.RecipientsEditor;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientModifiedListener;

import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Panel component combining both an editable field with a button for
 * a list-based contact selector.
 *
 * @author Moxie Marlinspike
 */
public class PushRecipientsPanel extends RelativeLayout implements RecipientModifiedListener {
  private final String                         TAG = PushRecipientsPanel.class.getSimpleName();
  private       RecipientsPanelChangedListener panelChangeListener;

  private RecipientsEditor recipientsText;
  private View             panel;

  private static final int RECIPIENTS_MAX_LENGTH = 312;

  public PushRecipientsPanel(Context context) {
    super(context);
    initialize();
  }

  public PushRecipientsPanel(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  public PushRecipientsPanel(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    initialize();
  }

  public List<Recipient> getRecipients() {
    String rawText = recipientsText.getText().toString();
    return getRecipientsFromString(getContext(), rawText, true);
  }

  public void disable() {
    recipientsText.setText("");
    panel.setVisibility(View.GONE);
  }

  public void setPanelChangeListener(RecipientsPanelChangedListener panelChangeListener) {
    this.panelChangeListener = panelChangeListener;
  }

  private void initialize() {
    LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    inflater.inflate(R.layout.push_recipients_panel, this, true);

    View imageButton = findViewById(R.id.contacts_button);
    ((MarginLayoutParams) imageButton.getLayoutParams()).topMargin = 0;

    panel = findViewById(R.id.recipients_panel);
    initRecipientsEditor();
  }

  private void initRecipientsEditor() {

    this.recipientsText = (RecipientsEditor)findViewById(R.id.recipients_text);

    List<Recipient> recipients = getRecipients();

    for (Recipient recipient : recipients) {
      recipient.addListener(this);
    }

    recipientsText.setAdapter(new RecipientsAdapter(this.getContext()));
    recipientsText.populate(recipients);

    recipientsText.setOnFocusChangeListener(new FocusChangedListener());
    recipientsText.setOnItemClickListener(new AdapterView.OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        if (panelChangeListener != null) {
          panelChangeListener.onRecipientsPanelUpdate(getRecipients());
        }
        recipientsText.setText("");
      }
    });
  }

  private @NonNull List<Recipient> getRecipientsFromString(Context context, @NonNull String rawText, boolean asynchronous) {
    StringTokenizer tokenizer  = new StringTokenizer(rawText, ",");
    List<Recipient> recipients = new LinkedList<>();

    while (tokenizer.hasMoreTokens()) {
      String token = tokenizer.nextToken().trim();

      if (!TextUtils.isEmpty(token)) {
        if (hasBracketedNumber(token)) recipients.add(Recipient.from(context, Address.fromExternal(context, parseBracketedNumber(token)), asynchronous));
        else                           recipients.add(Recipient.from(context, Address.fromExternal(context, token), asynchronous));
      }
    }

    return recipients;
  }

  private boolean hasBracketedNumber(String recipient) {
    int openBracketIndex = recipient.indexOf('<');

    return (openBracketIndex != -1) &&
           (recipient.indexOf('>', openBracketIndex) != -1);
  }

  private  String parseBracketedNumber(String recipient) {
    int begin    = recipient.indexOf('<');
    int end      = recipient.indexOf('>', begin);
    String value = recipient.substring(begin + 1, end);

    return value;
  }

  @Override
  public void onModified(Recipient recipient) {
    recipientsText.populate(getRecipients());
  }

  private class FocusChangedListener implements View.OnFocusChangeListener {
    public void onFocusChange(View v, boolean hasFocus) {
      if (!hasFocus && (panelChangeListener != null)) {
        panelChangeListener.onRecipientsPanelUpdate(getRecipients());
      }
    }
  }

  public interface RecipientsPanelChangedListener {
    public void onRecipientsPanelUpdate(List<Recipient> recipients);
  }

}