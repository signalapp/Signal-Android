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

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.format.DateUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.QuickContactBadge;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.thoughtcrime.securesms.database.MessageRecord;
import org.thoughtcrime.securesms.recipients.Recipients;

import java.util.Set;

/**
 * A view that displays the element in a list of multiple conversation threads.
 * Used by SecureSMS's ListActivity via a ConversationListAdapter.
 *
 * @author Moxie Marlinspike
 */

public class ConversationListItem extends RelativeLayout {

  private Set<Long>         selectedThreads;
  private Recipients        recipients;
  private long              threadId;
  private boolean           first;
  private TextView          subjectView;
  private TextView          fromView;
  private TextView          dateView;
  private CheckBox          checkbox;
  private QuickContactBadge contactPhoto;

  public ConversationListItem(Context context, boolean first) {
    this(context, (Set<Long>)null);

    this.first = true;
    contactPhoto.setVisibility(View.GONE);
  }

  public ConversationListItem(Context context, Set<Long> selectedThreads) {
    super(context);

    LayoutInflater li = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    li.inflate(R.layout.conversation_list_item_view, this, true);

    this.selectedThreads = selectedThreads;
    this.subjectView     = (TextView)findViewById(R.id.subject);
    this.fromView        = (TextView)findViewById(R.id.from);
    this.dateView        = (TextView)findViewById(R.id.date);
    this.contactPhoto    = (QuickContactBadge)findViewById(R.id.contact_photo);
    this.checkbox        = (CheckBox)findViewById(R.id.checkbox);

    intializeListeners();
  }

  public ConversationListItem(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public void set(MessageRecord message, boolean batchMode) {
    this.recipients = message.getRecipients();
    this.threadId   = message.getThreadId();
    this.fromView.setText(formatFrom(recipients, message.getCount(), message.getRead()));

    if (message.isKeyExchange())
      this.subjectView.setText(R.string.ConversationListItem_key_exchange_message,
                               TextView.BufferType.SPANNABLE);
    else
      this.subjectView.setText(message.getBody(), TextView.BufferType.SPANNABLE);

    if (message.getEmphasis())
      ((Spannable)this.subjectView.getText()).setSpan(new StyleSpan(android.graphics.Typeface.ITALIC), 0, this.subjectView.getText().length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

    if (message.getDate() > 0)
      this.dateView.setText(DateUtils.getRelativeTimeSpanString(getContext(), message.getDate(), false));

    if (selectedThreads != null)
      this.checkbox.setChecked(selectedThreads.contains(threadId));

    if (!first) {
      if (batchMode) checkbox.setVisibility(View.VISIBLE);
      else           checkbox.setVisibility(View.GONE);

      contactPhoto.setImageBitmap(this.recipients.getPrimaryRecipient().getContactPhoto());
      contactPhoto.assignContactFromPhone(this.recipients.getPrimaryRecipient().getNumber(), true);
      contactPhoto.setVisibility(View.VISIBLE);
    }
  }

  private void intializeListeners() {
    checkbox.setOnCheckedChangeListener(new CheckedChangedListener());
  }

  private CharSequence formatFrom(Recipients from, long count, boolean read) {
    SpannableStringBuilder builder = new SpannableStringBuilder(from.toShortString());

    if (count > 0) {
      builder.append(" " + count);
      builder.setSpan(new ForegroundColorSpan(Color.parseColor("#66333333")),
                      from.toShortString().length(), builder.length(),
                      Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
    }

    if (!read) {
      builder.setSpan(new StyleSpan(Typeface.BOLD), 0, builder.length(),
                      Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
    }

    return builder;
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
