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

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.contacts.RecipientsAdapter;
import org.thoughtcrime.securesms.contacts.RecipientsEditor;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.recipients.Recipients;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RelativeLayout;

/**
 * Panel component combining both an editable field with a button for 
 * a list-based contact selector.
 * 
 * @author Moxie Marlinspike
 */
public class RecipientsPanel extends RelativeLayout {	
	
  private RecipientsPanelChangedListener panelChangeListener;
  private RecipientsEditor recipientsText;
  private View panel;

  private static final int RECIPIENTS_MAX_LENGTH = 312;
    
  public RecipientsPanel(Context context) {
    super(context);
    initialize();
  }

  public RecipientsPanel(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  public RecipientsPanel(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    initialize();
  }
		
  public void addRecipient(String name, String number) {
    if (name != null) recipientsText.append(name + "< " + number + ">, ");
    else              recipientsText.append(number + ", ");
  }
		
  public void addRecipients(Recipients recipients) {
    List<Recipient> recipientList = recipients.getRecipientsList();
    Iterator<Recipient> iterator  = recipientList.iterator();
		
    while (iterator.hasNext()) {
      Recipient recipient = iterator.next();
      addRecipient(recipient.getName(), recipient.getNumber());
    }
  }
	
  public Recipients getRecipients() throws RecipientFormattingException {
    String rawText        = recipientsText.getText().toString();
    Recipients recipients = RecipientFactory.getRecipientsFromString(getContext(), rawText);
		
    if (recipients.isEmpty())
      throw new RecipientFormattingException("Recipient List Is Empty!");
		
    return recipients;		
  }
	
  public void disable() {
    recipientsText.setText("");
    panel.setVisibility(View.GONE);	
  }
	
  public void setPanelChangeListener(RecipientsPanelChangedListener panelChangeListener) {
    this.panelChangeListener = panelChangeListener;
  }

  private void initialize() {
    LayoutInflater inflater = (LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    inflater.inflate(R.layout.recipients_panel, this, true);
		
    panel = findViewById(R.id.recipients_panel);		
    initRecipientsEditor();        
  }
	
  private void initRecipientsEditor() {
    Recipients recipients = null;
    recipientsText        = (RecipientsEditor)findViewById(R.id.recipients_text);
    	
    try {
      recipients = getRecipients();
    } catch (RecipientFormattingException e) {
      recipients = new Recipients( new LinkedList<Recipient>() );
    }

    recipientsText.setAdapter(new RecipientsAdapter(this.getContext()));
    recipientsText.populate(recipients);
    recipientsText.setOnFocusChangeListener(new FocusChangedListener());
  }
	
  private class FocusChangedListener implements View.OnFocusChangeListener {
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
