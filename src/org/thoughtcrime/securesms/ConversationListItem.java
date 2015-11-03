/**
 * Copyright (C) 2011 Whisper Systems
 * <p/>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.provider.Contacts.Intents;
import android.provider.ContactsContract.QuickContact;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.thoughtcrime.securesms.components.CircledImageView;
import org.thoughtcrime.securesms.database.MmsSmsColumns;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.mms.ImageSlide;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.Emoji;

import java.util.Set;

import de.gdata.messaging.util.GDataPreferences;
import de.gdata.messaging.util.GUtil;
import de.gdata.messaging.util.ProfileAccessor;

import de.gdata.messaging.util.ProfileAccessor;

/**
 * A view that displays the element in a list of multiple conversation threads.
 * Used by SecureSMS's ListActivity via a ConversationListAdapter.
 *
 * @author Moxie Marlinspike
 */

public class ConversationListItem extends RelativeLayout
        implements Recipient.RecipientModifiedListener {
    private final static String TAG = ConversationListItem.class.getSimpleName();

    private Context context;
    private Set<Long> selectedThreads;
    private Recipients recipients;
    private long threadId;
    private TextView subjectView;
    private TextView fromView;
    private TextView dateView;
    private TextView unreadCountView;
    private long count;
    private boolean read;

    private CircledImageView contactPhotoImage;

    private final Handler handler = new Handler();
    private int distributionType;

    public ConversationListItem(Context context) {
        super(context);
        this.context = context;
    }

    public ConversationListItem(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
    }
    @Override
    protected void onFinishInflate() {
        this.subjectView = (TextView) findViewById(R.id.subject);
        this.fromView = (TextView) findViewById(R.id.from);
        this.dateView = (TextView) findViewById(R.id.date);
        this.unreadCountView = (TextView) findViewById(R.id.tab_layout_count);

        int color = new GDataPreferences(getContext()).getCurrentColorHex();
        if(getContext().getResources().getColor(R.color.gdata_primary_color) == color) {
            color = getContext().getResources().getColor(R.color.gdata_red);
        }
        unreadCountView.getBackground().setColorFilter(GUtil.adjustAlpha(color, GUtil.ALPHA_20_PERCENT), PorterDuff.Mode.SRC_ATOP);

        this.contactPhotoImage = (CircledImageView) findViewById(R.id.contact_photo_image);

        initializeContactWidgetVisibility();
    }

    public void set(ThreadRecord thread, Set<Long> selectedThreads, boolean batchMode) {
        this.selectedThreads = selectedThreads;
        this.recipients = thread.getRecipients();
        this.threadId = thread.getThreadId();
        this.count = thread.getCount();
        this.read = thread.isRead();
        this.distributionType = thread.getDistributionType();

        this.recipients.addListener(this);
        this.fromView.setText(formatFrom(recipients, count, read));
        SpannableString body = thread.getDisplayBody();
        if (thread.getBody().isSelfDestruction() && !MmsSmsColumns.Types.isOutgoingMessageType(thread.type)) {
            body = new SpannableString(context.getString(R.string.self_destruction_body).replace("#1#", "" + thread.getBody().getSelfDestructionDuration()));
        }
        this.subjectView.setText(Emoji.getInstance(context).emojify(body,
                        Emoji.EMOJI_SMALL,
                        new Emoji.InvalidatingPageLoadedListener(subjectView)),
                TextView.BufferType.SPANNABLE);

        if (thread.getDate() > 0)
            this.dateView.setText(DateUtils.getRelativeTimeSpanString(getContext(), thread.getDate()));

        if(read) {
            unreadCountView.setVisibility(View.GONE);
            new GDataPreferences(getContext()).saveUnreadCountForThread(threadId+"", count);
        } else {
            unreadCountView.setVisibility(View.VISIBLE);
            Long unreadCount = count - new GDataPreferences(getContext()).getUnreadCountForThread(threadId + "");
            unreadCountView.setText(unreadCount+ "");

        }
        setBackground(read, batchMode);
        setContactPhoto(this.recipients.getPrimaryRecipient());
    }

    public void unbind() {
        if (this.recipients != null)
            this.recipients.removeListener(this);
    }

    private void initializeContactWidgetVisibility() {
        contactPhotoImage.setVisibility(View.VISIBLE);
    }

    private void handleOpenProfile(String profileId) {
        final Intent intent = new Intent(getContext(), ProfileActivity.class);
        intent.putExtra("master_secret", ProfileAccessor.getMasterSecred());
        intent.putExtra("profile_id", profileId);
        intent.putExtra("is_group", getRecipients().isGroupRecipient());
        intent.putExtra(ConversationActivity.RECIPIENTS_EXTRA, getRecipients().getIds());
        getContext().startActivity(intent);
    }

    private void setContactPhoto(final Recipient recipient) {
        if (recipient == null) return;

        ImageSlide profileSlide = ProfileAccessor.getProfileAsImageSlide(context, recipient.getNumber());
        if (profileSlide != null && !recipient.isGroupRecipient()) {
            ProfileAccessor.buildGlideRequest(profileSlide).into(contactPhotoImage);
        } else {
            contactPhotoImage.setImageBitmap(recipient.getCircleCroppedContactPhoto());
        }
        contactPhotoImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String profileId = GUtil.numberToLong(recipient.getNumber()) + "";
                if (ProfileAccessor.getPartId(context, profileId).getRowId() > -1L) {
                    handleOpenProfile(profileId);
                } else if (recipient.isGroupRecipient()) {
                    handleOpenProfile(profileId);
                } else if (recipient.getContactUri() != null) {
                    QuickContact.showQuickContact(context, contactPhotoImage, recipient.getContactUri(), QuickContact.MODE_LARGE, null);
                } else {
                    Intent intent = new Intent(Intents.SHOW_OR_CREATE_CONTACT, Uri.fromParts("tel", recipient.getNumber(), null));
                    context.startActivity(intent);
                }
            }
        });
    }

    private void setBackground(boolean read, boolean batch) {
        int[] attributes = new int[]{R.attr.conversation_list_item_background_selected,
                R.attr.conversation_list_item_background_read,
                R.attr.conversation_list_item_background_unread};

        TypedArray drawables = context.obtainStyledAttributes(attributes);

        if (batch && selectedThreads.contains(threadId)) {
            setBackgroundDrawable(drawables.getDrawable(0));
        } else if (read) {
            setBackgroundDrawable(drawables.getDrawable(1));
        } else {
            setBackgroundDrawable(drawables.getDrawable(2));
        }

        drawables.recycle();
    }

    private CharSequence formatFrom(Recipients from, long count, boolean read) {
        int attributes[] = new int[]{R.attr.conversation_list_item_count_color};
        TypedArray colors = context.obtainStyledAttributes(attributes);

        final String fromString;
        final boolean isUnnamedGroup = from.isGroupRecipient() && TextUtils.isEmpty(from.getPrimaryRecipient().getName());
        if (isUnnamedGroup) {
            fromString = context.getString(R.string.ConversationActivity_unnamed_group);
        } else {
            fromString = from.toShortString();
        }
        SpannableStringBuilder builder = new SpannableStringBuilder(fromString);


        final int typeface;
        if (isUnnamedGroup) {
            if (!read) typeface = Typeface.BOLD_ITALIC;
            else typeface = Typeface.ITALIC;
        } else if (!read) {
            typeface = Typeface.BOLD;
        } else {
            typeface = Typeface.NORMAL;
        }

        builder.setSpan(new StyleSpan(typeface), 0, builder.length(),
                Spannable.SPAN_INCLUSIVE_EXCLUSIVE);


        colors.recycle();
        return builder;
    }

    public Recipients getRecipients() {
        return recipients;
    }

    public long getThreadId() {
        return threadId;
    }

    public int getDistributionType() {
        return distributionType;
    }

    @Override
    public void onModified(Recipient recipient) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                ConversationListItem.this.fromView.setText(formatFrom(recipients, count, read));
                setContactPhoto(ConversationListItem.this.recipients.getPrimaryRecipient());
            }
        });
    }
}
