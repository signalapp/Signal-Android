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
package org.privatechats.securesms.components;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.RelativeLayout;

import org.privatechats.securesms.R;
import org.privatechats.securesms.contacts.ContactAccessor;
import org.privatechats.securesms.contacts.RecipientsAdapter;
import org.privatechats.securesms.contacts.RecipientsEditor;
import org.privatechats.securesms.recipients.Recipient;
import org.privatechats.securesms.recipients.RecipientFactory;
import org.privatechats.securesms.recipients.RecipientFormattingException;
import org.privatechats.securesms.recipients.Recipients;
import org.privatechats.securesms.recipients.Recipients.RecipientsModifiedListener;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Panel component combining both an editable field with a button for
 * a list-based contact selector.
 *
 * @author Moxie Marlinspike
 */
public class SingleRecipientPanel extends RelativeLayout implements RecipientsModifiedListener {
  private final String                         TAG = SingleRecipientPanel.class.getSimpleName();
  private       RecipientsPanelChangedListener panelChangeListener;

  private RecipientsEditor recipientsText;
  private View             panel;

  private static final int RECIPIENTS_MAX_LENGTH = 312;

  public SingleRecipientPanel(Context context) {
    super(context);
    initialize();
  }

  public SingleRecipientPanel(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  public SingleRecipientPanel(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    initialize();
  }

  public void addRecipient(String name, String number) {
    Log.i(TAG, "addRecipient for " + name + "/" + number);
    if (name != null) recipientsText.append(name + "< " + number + ">, ");
    else recipientsText.append(number + ", ");
  }

  public void addRecipients(Recipients recipients) {
    List<Recipient> recipientList = recipients.getRecipientsList();
    Iterator<Recipient> iterator = recipientList.iterator();

    while (iterator.hasNext()) {
      Recipient recipient = iterator.next();
      addRecipient(recipient.getName(), recipient.getNumber());
    }
  }

  public void addContacts(List<ContactAccessor.ContactData> contacts) {
    for (ContactAccessor.ContactData contact : contacts) {
      for (ContactAccessor.NumberData number : contact.numbers) {
        addRecipient(contact.name, number.number);
      }
    }
  }

  public Recipients getRecipients() throws RecipientFormattingException {
    String rawText = recipientsText.getText().toString();
    Recipients recipients = RecipientFactory.getRecipientsFromString(getContext(), rawText, true);

    if (recipients.isEmpty())
      throw new RecipientFormattingException("Recipient List Is Empty!");

    return recipients;
  }

  public void disable() {
    clear();
    panel.setVisibility(View.GONE);
  }

  public void clear() {
    recipientsText.setText("");
  }

  public void setPanelChangeListener(RecipientsPanelChangedListener panelChangeListener) {
    this.panelChangeListener = panelChangeListener;
  }

  private void initialize() {
    LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    inflater.inflate(R.layout.single_recipient_panel, this, true);

    panel = findViewById(R.id.recipients_panel);
    initRecipientsEditor();
  }

  private void initRecipientsEditor() {
    Recipients recipients;
    recipientsText = (RecipientsEditor)findViewById(R.id.recipients_text);

    try {
      recipients = getRecipients();
    } catch (RecipientFormattingException e) {
      recipients = RecipientFactory.getRecipientsFor(getContext(), new LinkedList<Recipient>(), true);
    }
    recipients.addListener(this);

    recipientsText.setAdapter(new RecipientsAdapter(this.getContext()));
    recipientsText.populate(recipients);

    recipientsText.setOnFocusChangeListener(new FocusChangedListener());
    recipientsText.setOnItemClickListener(new AdapterView.OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        if (panelChangeListener != null) {
          try {
            panelChangeListener.onRecipientsPanelUpdate(getRecipients());
          } catch (RecipientFormattingException rfe) {
            panelChangeListener.onRecipientsPanelUpdate(null);
          }
        }
        recipientsText.setText("");
      }
    });
  }

  @Override public void onModified(Recipients recipients) {
    recipientsText.populate(recipients);
  }

  private class FocusChangedListener implements OnFocusChangeListener {
    public void onFocusChange(View v, boolean hasFocus) {
      if (!hasFocus && (panelChangeListener != null)) {
        try {
          panelChangeListener.onRecipientsPanelUpdate(getRecipients());
        } catch (RecipientFormattingException rfe) {
          panelChangeListener.onRecipientsPanelUpdate(null);
        }
      }
    }
  }

  public interface RecipientsPanelChangedListener {
    public void onRecipientsPanelUpdate(Recipients recipients);
  }

}