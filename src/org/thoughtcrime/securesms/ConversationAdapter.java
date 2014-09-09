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

import android.annotation.TargetApi;
import android.content.Context;
import android.database.Cursor;
import android.os.Build;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.AbsListView;
import android.support.v4.widget.CursorAdapter;

import org.whispersystems.textsecure.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsSmsColumns;
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.util.LRUCache;

import java.lang.ref.SoftReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A cursor adapter for a conversation thread.  Ultimately
 * used by ComposeMessageActivity to display a conversation
 * thread in a ListActivity.
 *
 * @author Moxie Marlinspike
 *
 */
public class ConversationAdapter extends CursorAdapter implements AbsListView.RecyclerListener {

  private static final int MAX_CACHE_SIZE = 40;
  private final Map<String,SoftReference<MessageRecord>> messageRecordCache =
      Collections.synchronizedMap(new LRUCache<String, SoftReference<MessageRecord>>(MAX_CACHE_SIZE));

  public static final int MESSAGE_TYPE_OUTGOING = 0;
  public static final int MESSAGE_TYPE_INCOMING = 1;
  public static final int MESSAGE_TYPE_GROUP_ACTION = 2;

  /**
   * The minimum SDK version for animation support. ICS is required for the >= Honeycomb animation
   * framework and View.animate() helper.
   */
  public static final int ANIMATION_TARGET_API = Build.VERSION_CODES.ICE_CREAM_SANDWICH;
  public static final boolean ANIMATION_SUPPORTED = Build.VERSION.SDK_INT >= ANIMATION_TARGET_API;
  public static final int ANIMATION_START_DELAY = 500;
  public static final int ANIMATION_DURATION = 250;
  public static final float ANIMATION_OVERSHOOT_AMOUNT = 1.1f;

  private final Handler failedIconClickHandler;
  /**
   * A handler for posting view animations. Used to let the view layout and measure itself.
   */
  private final Handler animationHandler = new Handler();
  private final Context context;
  private final MasterSecret masterSecret;
  private final boolean groupThread;
  private final boolean pushDestination;
  private final LayoutInflater inflater;
  private boolean animateNext = false;
  private final Set<Long> seenRows = new HashSet<Long>();
  private final Set<Long> animatingRows = new HashSet<Long>();

  public ConversationAdapter(Context context, MasterSecret masterSecret,
                             Handler failedIconClickHandler, boolean groupThread, boolean pushDestination)
  {
    super(context, null, true);
    this.context                = context;
    this.masterSecret           = masterSecret;
    this.failedIconClickHandler = failedIconClickHandler;
    this.groupThread            = groupThread;
    this.pushDestination        = pushDestination;
    this.inflater               = LayoutInflater.from(context);
  }

  @Override
  public void bindView(View view, Context context, Cursor cursor) {
    ConversationItem item       = (ConversationItem)view;
    long id                     = cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabase.ID));
    String type                 = cursor.getString(cursor.getColumnIndexOrThrow(MmsSmsDatabase.TRANSPORT));
    MessageRecord messageRecord = getMessageRecord(id, cursor, type);

    item.set(masterSecret, messageRecord, failedIconClickHandler, groupThread, pushDestination);

    if (animateNext && !seenRows.contains(id)) {
      animatingRows.add(id);
      queueAnimationForView(view);
      animateNext = false;
    } else if (!animatingRows.contains(id)) {
      cancelAnimationForView(view);
    }

    seenRows.add(id);
  }

  @Override
  public View newView(Context context, Cursor cursor, ViewGroup parent) {
    View view;

    int type = getItemViewType(cursor);

    switch (type) {
      case ConversationAdapter.MESSAGE_TYPE_OUTGOING:
        view = inflater.inflate(R.layout.conversation_item_sent, parent, false);
        break;
      case ConversationAdapter.MESSAGE_TYPE_INCOMING:
        view = inflater.inflate(R.layout.conversation_item_received, parent, false);
        break;
      case ConversationAdapter.MESSAGE_TYPE_GROUP_ACTION:
        view = inflater.inflate(R.layout.conversation_item_activity, parent, false);
        break;
      default: throw new IllegalArgumentException("unsupported item view type given to ConversationAdapter");
    }

    bindView(view, context, cursor);
    return view;
  }

  @Override
  public int getViewTypeCount() {
    return 3;
  }

  @Override
  public int getItemViewType(int position) {
    Cursor cursor = (Cursor)getItem(position);
    return getItemViewType(cursor);
  }

  private int getItemViewType(Cursor cursor) {
    long id                     = cursor.getLong(cursor.getColumnIndexOrThrow(MmsSmsColumns.ID));
    String type                 = cursor.getString(cursor.getColumnIndexOrThrow(MmsSmsDatabase.TRANSPORT));
    MessageRecord messageRecord = getMessageRecord(id, cursor, type);

    if      (messageRecord.isGroupAction()) return MESSAGE_TYPE_GROUP_ACTION;
    else if (messageRecord.isOutgoing())    return MESSAGE_TYPE_OUTGOING;
    else                                    return MESSAGE_TYPE_INCOMING;
  }

  @TargetApi(ANIMATION_TARGET_API)
  private void cancelAnimationForView(View view) {
    if (ANIMATION_SUPPORTED) {
      view.setScaleX(1.f);
      view.setScaleY(1.f);
      view.animate().cancel();
    }
  }

  @TargetApi(ANIMATION_TARGET_API)
  private void queueAnimationForView(final View view) {
    if (ANIMATION_SUPPORTED) {
      view.setScaleX(0.f);
      view.setScaleY(0.f);

      animationHandler.post(new Runnable() {
        @Override
        public void run() {
          startAnimationForView(view);
        }
      });
    }
  }

  @TargetApi(ANIMATION_TARGET_API)
  private void startAnimationForView(final View view) {
    if (ANIMATION_SUPPORTED) {
      view.setPivotX(view.getWidth());
      view.setPivotY(view.getHeight());

      view.animate()
        .scaleX(1.f)
        .scaleY(1.f)
        .setStartDelay(ANIMATION_START_DELAY)
        .setDuration(ANIMATION_DURATION)
        .setInterpolator(new OvershootInterpolator(ANIMATION_OVERSHOOT_AMOUNT))
        .start();
    }
  }

  private MessageRecord getMessageRecord(long messageId, Cursor cursor, String type) {
    SoftReference<MessageRecord> reference = messageRecordCache.get(type + messageId);

    if (reference != null) {
      MessageRecord record = reference.get();

      if (record != null)
        return record;
    }

    MmsSmsDatabase.Reader reader = DatabaseFactory.getMmsSmsDatabase(context)
                                                  .readerFor(cursor, masterSecret);

    MessageRecord messageRecord = reader.getCurrent();

    messageRecordCache.put(type + messageId, new SoftReference<MessageRecord>(messageRecord));

    return messageRecord;
  }

  @Override
  protected void onContentChanged() {
    super.onContentChanged();
    messageRecordCache.clear();
  }

  public void close() {
    this.getCursor().close();
  }

  /**
   * Requests the adapter for an animation on the next row's appearance.
   */
  public void animateNext() {
    this.animateNext = true;
  }

  @Override
  public void onMovedToScrapHeap(View view) {
    ((ConversationItem)view).unbind();
  }
}
