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
package org.thoughtcrime.securesms;

import java.util.Set;

import org.thoughtcrime.securesms.database.MessageRecord;
import org.thoughtcrime.securesms.recipients.Recipients;

import android.content.Context;
import android.graphics.Color;
import android.preference.PreferenceManager;
import android.text.Spannable;
import android.text.format.DateUtils;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * A view that displays the element in a list of multiple conversation threads.
 * Used by SecureSMS's ListActivity via a ConversationListAdapter.
 *
 * @author Moxie Marlinspike
 */

public class ConversationHeaderView extends RelativeLayout {

  private final Context   context;
  private       Set<Long> selectedThreads;
	
  private Recipients recipients;
  private long       threadId;
  private boolean    first;
  private TextView   subjectView;
  private TextView   fromView;
  private TextView   dateView;
  private View       unreadIndicator;
  private View       keyIndicator;
  private CheckBox   checkbox;
  private ImageView  contactPhoto;
    
  public ConversationHeaderView(Context context, boolean first) {
    this(context, (Set<Long>)null);
    	
    this.first = true;
    contactPhoto.setVisibility(View.GONE);
    this.setBackgroundColor(Color.TRANSPARENT);
  }
    
  public ConversationHeaderView(Context context, Set<Long> selectedThreads) {
    super(context);

    LayoutInflater li = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    li.inflate(R.layout.conversation_header_view, this, true);
		
    this.context         = context;
    this.selectedThreads = selectedThreads;
    this.subjectView     = (TextView)findViewById(R.id.subject);
    this.fromView        = (TextView)findViewById(R.id.from);
    this.dateView        = (TextView)findViewById(R.id.date);
    this.unreadIndicator = findViewById(R.id.unread_indicator);
    this.keyIndicator    = findViewById(R.id.key_indicator);
    this.contactPhoto    = (ImageView)findViewById(R.id.contact_photo);		
    this.checkbox        = (CheckBox)findViewById(R.id.checkbox);
	
    intializeListeners();
    initializeColors();
  }
	
  public ConversationHeaderView(Context context, AttributeSet attrs) {
    super(context, attrs);
    this.context = context;
  }

  public void set(MessageRecord message, boolean batchMode) {
    this.recipients = message.getRecipients();
    this.threadId   = message.getThreadId();
    this.fromView.setText(formatFrom(recipients, message.getCount()));
		
    if (message.isKeyExchange())
      this.subjectView.setText("Key exchange message...", TextView.BufferType.SPANNABLE);
    else
      this.subjectView.setText(message.getBody(), TextView.BufferType.SPANNABLE);
		
    if (message.getEmphasis())
      ((Spannable)this.subjectView.getText()).setSpan(new StyleSpan(android.graphics.Typeface.ITALIC), 0, this.subjectView.getText().length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

    if (message.getDate() > 0) 
      this.dateView.setText(DateUtils.getRelativeTimeSpanString(getContext(), message.getDate(), false));

    if (selectedThreads != null)	
      this.checkbox.setChecked(selectedThreads.contains(threadId));
		
    clearIndicators();
    setIndicators(message.getRead(), message.isKeyExchange());		

    if (!first) {
      if (batchMode) checkbox.setVisibility(View.VISIBLE);
      else           checkbox.setVisibility(View.GONE);	

      if (!PreferenceManager.getDefaultSharedPreferences(context).getBoolean(ApplicationPreferencesActivity.CONVERSATION_ICONS_LIST_PREF, ApplicationPreferencesActivity.showIcon())) {
        contactPhoto.setVisibility(View.GONE);
      } else {	
        contactPhoto.setImageBitmap(message.getRecipients().getPrimaryRecipient().getContactPhoto());
        contactPhoto.setBackgroundResource(R.drawable.light_border_background);
        contactPhoto.setVisibility(View.VISIBLE);
      }
    }
  }	
	
  public void initializeColors() {
    if (!PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean(ApplicationPreferencesActivity.DARK_THREADS_PREF, true)) {
      this.setBackgroundDrawable(getResources().getDrawable(R.drawable.conversation_header_background_light));
      this.subjectView.setTextColor(Color.BLACK);
      this.fromView.setTextColor(Color.BLACK);
      this.dateView.setTextColor(Color.LTGRAY);
    } else {
      this.setBackgroundColor(Color.TRANSPARENT);
      this.subjectView.setTextColor(Color.LTGRAY);
      this.fromView.setTextColor(Color.WHITE);
      this.dateView.setTextColor(Color.LTGRAY);
    }
  }
	
  private void intializeListeners() {
    checkbox.setOnCheckedChangeListener(new CheckedChangedListener());
  }
	
  private void clearIndicators() {
    this.keyIndicator.setVisibility(View.INVISIBLE);
    this.unreadIndicator.setVisibility(View.INVISIBLE);
  }
	
  private void setIndicators(boolean read, boolean key) {
    if (!read && key) this.keyIndicator.setVisibility(View.VISIBLE);
    else if (!read)	  this.unreadIndicator.setVisibility(View.VISIBLE);
  }
	
  private String formatFrom(Recipients from, long count) {
    return from.toShortString() + (count > 0 ? " (" + count + ")" : "");
  }
	
  public Recipients getRecipients() {
    return recipients;
  }
	
  public long getThreadId() {
    return threadId;
  }
	
  private class CheckedChangedListener implements CompoundButton.OnCheckedChangeListener {
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
      if (isChecked) selectedThreads.add(threadId);
      else           selectedThreads.remove(threadId);
    }
  }
}
