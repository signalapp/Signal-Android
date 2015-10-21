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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.provider.ContactsContract;
import android.provider.ContactsContract.QuickContact;
import android.text.method.ScrollingMovementMethod;
import android.transition.Explode;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.AlertDialogWrapper;

import org.thoughtcrime.securesms.ConversationFragment.SelectionClickListener;
import org.thoughtcrime.securesms.components.ThumbnailView;
import org.thoughtcrime.securesms.contacts.ContactPhotoFactory;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.NotificationMmsMessageRecord;
import org.thoughtcrime.securesms.jobs.MmsDownloadJob;
import org.thoughtcrime.securesms.jobs.MmsSendJob;
import org.thoughtcrime.securesms.jobs.SmsSendJob;
import org.thoughtcrime.securesms.mms.AudioSlide;
import org.thoughtcrime.securesms.mms.ImageSlide;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.BitmapDecodingException;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.Emoji;
import org.thoughtcrime.securesms.util.FutureTaskListener;
import org.thoughtcrime.securesms.util.ListenableFutureTask;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import de.gdata.messaging.util.GDataLinkMovementMethod;

import de.gdata.messaging.util.GDataPreferences;
import de.gdata.messaging.util.GUtil;
import de.gdata.messaging.util.ProfileAccessor;

/**
 * A view that displays an individual conversation item within a conversation
 * thread.  Used by ComposeMessageActivity's ListActivity via a ConversationAdapter.
 *
 * @author Moxie Marlinspike
 */

public class ConversationItem extends LinearLayout {
  private final static String TAG = ConversationItem.class.getSimpleName();
  private static final int SECOND = 1000;
  //Workaround to hide encryption string from group creation
  private static final long TYPE_WRONG_CREATED = -2139029483;
  private static final long TYPE_WRONG_ENCRYPTED = -2136932329;
  private static final long TYPE_LEFT_GROUP = -2136866793;
  public static final long TYPE_WRONG_KEY = -2145386476;
  public static final int GROUP_CONVERSATION = 1;
  public static final int SINGLE_CONVERSATION = 2;

    private final int STYLE_ATTRIBUTES[] = new int[]{R.attr.conversation_item_sent_push_background,
      R.attr.conversation_item_sent_push_triangle_background,
      R.attr.conversation_item_sent_background,
      R.attr.conversation_item_sent_triangle_background,
      R.attr.conversation_item_sent_pending_background,
      R.attr.conversation_item_sent_pending_triangle_background,
      R.attr.conversation_item_sent_push_pending_background,
      R.attr.conversation_item_sent_push_pending_triangle_background};

  private final static int SENT_PUSH = 0;
  private final static int SENT_PUSH_TRIANGLE = 1;
  private final static int SENT_SMS = 2;
  private final static int SENT_SMS_TRIANGLE = 3;
  private final static int SENT_SMS_PENDING = 4;
  private final static int SENT_SMS_PENDING_TRIANGLE = 5;
  private final static int SENT_PUSH_PENDING = 6;
  private final static int SENT_PUSH_PENDING_TRIANGLE = 7;

  private Handler failedIconHandler;
  private MessageRecord messageRecord;
  private MasterSecret masterSecret;
  private boolean groupThread;
  private boolean pushDestination;

  private View conversationParent;
  private TextView bodyText;
  private TextView dateText;
  private TextView indicatorText;
  private TextView groupStatusText;
  private ImageView secureImage;
  private ImageView bombImage;
  private ImageView failedImage;
  private ImageView contactPhoto;
  private ImageView deliveryImage;
  private View triangleTick;
  private ImageView pendingIndicator;

  private static String openedMessageId = "";

  private Set<MessageRecord> batchSelected;
  private SelectionClickListener selectionClickListener;
  private View mmsContainer;
  private ThumbnailView mmsThumbnail;
  private Button mmsDownloadButton;
  private TextView mmsDownloadingLabel;
  private ListenableFutureTask<SlideDeck> slideDeck;
  private FutureTaskListener<SlideDeck> slideDeckListener;
  private TypedArray backgroundDrawables;

  private final FailedIconClickListener failedIconClickListener = new FailedIconClickListener();
  private final MmsDownloadClickListener mmsDownloadClickListener = new MmsDownloadClickListener();
  private final MmsPreferencesClickListener mmsPreferencesClickListener = new MmsPreferencesClickListener();
  private final ClickListener clickListener = new ClickListener();
  private final Handler handler = new Handler();
  private final Context context;

  private ConversationFragment conversationFragment;
  private ThumbnailView thumbnailDestroyDialog;
  private ImageView loadingDestroyIndicator;
  private GDataPreferences mPreferences;
  private Dialog alertDialogDestroy;

  public ConversationItem(Context context) {
    super(context);
    this.context = context;
  }

  public ConversationItem(Context context, AttributeSet attrs) {
    super(context, attrs);
    this.context = context;
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();

    this.bodyText = (TextView) findViewById(R.id.conversation_item_body);
    this.dateText = (TextView) findViewById(R.id.conversation_item_date);
    this.indicatorText = (TextView) findViewById(R.id.indicator_text);
    this.groupStatusText = (TextView) findViewById(R.id.group_message_status);
    this.secureImage = (ImageView) findViewById(R.id.sms_secure_indicator);
    this.bombImage = (ImageView) findViewById(R.id.image_view_bomb);
    this.failedImage = (ImageView) findViewById(R.id.sms_failed_indicator);
    this.mmsContainer = findViewById(R.id.mms_view);
    this.mmsThumbnail = (ThumbnailView) findViewById(R.id.image_view);
    this.mmsDownloadButton = (Button) findViewById(R.id.mms_download_button);
    this.mmsDownloadingLabel = (TextView) findViewById(R.id.mms_label_downloading);
    this.contactPhoto = (ImageView) findViewById(R.id.contact_photo);
    this.deliveryImage = (ImageView) findViewById(R.id.delivered_indicator);
    this.conversationParent = findViewById(R.id.conversation_item_parent);
    this.triangleTick = findViewById(R.id.triangle_tick);
    this.pendingIndicator = (ImageView) findViewById(R.id.pending_approval_indicator);
    this.backgroundDrawables = context.obtainStyledAttributes(STYLE_ATTRIBUTES);

    setOnClickListener(clickListener);
    if (failedImage != null) failedImage.setOnClickListener(failedIconClickListener);
    if (mmsDownloadButton != null) mmsDownloadButton.setOnClickListener(mmsDownloadClickListener);
    if (mmsThumbnail != null) {
      mmsThumbnail.setThumbnailClickListener(new ThumbnailClickListener());
      mmsThumbnail.setOnLongClickListener(new MultiSelectLongClickListener());
      mmsThumbnail.setVisibility(View.GONE);
    }
  }

  public void set(MasterSecret masterSecret, MessageRecord messageRecord,
                  Set<MessageRecord> batchSelected, SelectionClickListener selectionClickListener,
                  Handler failedIconHandler, boolean groupThread, boolean pushDestination, ConversationFragment fragment) {
    this.mPreferences = new GDataPreferences(getContext());
    this.masterSecret = masterSecret;
    this.messageRecord = messageRecord;
    this.batchSelected = batchSelected;
    this.selectionClickListener = selectionClickListener;
    this.failedIconHandler = failedIconHandler;
    this.groupThread = groupThread;
    this.pushDestination = pushDestination;

    this.conversationFragment = fragment;

    setConversationBackgroundDrawables(messageRecord);
    setSelectionBackgroundDrawables(messageRecord);
    setBodyText();

    if (!messageRecord.isGroupAction()) {
      setStatusIcons(messageRecord);
      setGroupMessageStatus(messageRecord);
      setEvents(messageRecord);
      setMinimumWidth();
      setMediaAttributes(messageRecord);
      if (MmsDatabase.Types.isDuplicateMessageType(messageRecord.type) || SmsDatabase.Types.isDuplicateMessageType(messageRecord.type)) {
        deleteMessage(messageRecord);
      }
    } else {
      bodyText.setTextColor(Color.BLACK);
    }
      if(ConversationActivity.currentConversationType == GROUP_CONVERSATION) {
          setContactPhoto(messageRecord);
     } else {
          if(contactPhoto != null) {
              contactPhoto.setVisibility(View.GONE);
          }
      }
        checkForBeingDestroyed(messageRecord);
  }
  public void unbind() {
    if (slideDeck != null && slideDeckListener != null)
      slideDeck.removeListener(slideDeckListener);
  }

  public MessageRecord getMessageRecord() {
    return messageRecord;
  }

  public void setHandler(Handler failedIconHandler) {
    this.failedIconHandler = failedIconHandler;
  }

  public static void setViewBackgroundWithoutResettingPadding(final View v, final int backgroundResId) {
    final int paddingBottom = v.getPaddingBottom();
    final int paddingLeft = v.getPaddingLeft();
    final int paddingRight = v.getPaddingRight();
    final int paddingTop = v.getPaddingTop();
    v.setBackgroundResource(backgroundResId);
    v.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom);
  }
    private void checkForBeingDestroyed(MessageRecord messageRecord) {
      String uniqueId = getUniqueMsgId(messageRecord);
        if (mPreferences.isMarkedAsRemoved(uniqueId) && !messageIsInDialog(uniqueId)) {
            mPreferences.removeFromList(uniqueId);
            deleteMessage(messageRecord);
        }
    }
  private boolean messageIsInDialog(String uniqueId) {
    return uniqueId.equals(openedMessageId) && alertDialogDestroy !=null && alertDialogDestroy.isShowing();
  }
  private boolean dialogIsClosed() {
    return openedMessageId.equals("");
  }
  private void setConversationBackgroundDrawables(MessageRecord messageRecord) {
    if (conversationParent != null && backgroundDrawables != null) {
      if (messageRecord.isOutgoing()) {
        final int background;
        final int triangleBackground;
        if (messageRecord.isPending() && pushDestination && !messageRecord.isForcedSms()) {
          background = SENT_PUSH_PENDING;
          triangleBackground = SENT_PUSH_PENDING_TRIANGLE;
        } else if (messageRecord.isPending() || messageRecord.isPendingSmsFallback()) {
          background = SENT_SMS_PENDING;
          triangleBackground = SENT_SMS_PENDING_TRIANGLE;
        } else if (messageRecord.isPush()) {
          background = SENT_PUSH;
          triangleBackground = SENT_PUSH_TRIANGLE;
        } else {
          background = SENT_SMS;
          triangleBackground = SENT_SMS_TRIANGLE;
        }

        setViewBackgroundWithoutResettingPadding(conversationParent, backgroundDrawables.getResourceId(background, -1));
        setViewBackgroundWithoutResettingPadding(triangleTick, backgroundDrawables.getResourceId(triangleBackground, -1));
      }
    }
  }

  private void setSelectionBackgroundDrawables(MessageRecord messageRecord) {
    int[] attributes = new int[]{R.attr.conversation_list_item_background_selected,
        R.attr.conversation_item_background};

    TypedArray drawables = context.obtainStyledAttributes(attributes);

    if (batchSelected.contains(messageRecord)) {
      setBackgroundDrawable(drawables.getDrawable(0));
    } else {
      setBackgroundDrawable(drawables.getDrawable(1));
    }

    drawables.recycle();
  }

  private void setBodyText() {

    bodyText.setClickable(false);
    bodyText.setFocusable(false);

    if ((messageRecord.isGroupAction() && (messageRecord.type == TYPE_WRONG_ENCRYPTED || messageRecord.type == TYPE_WRONG_CREATED))) {
      bodyText.setText(Emoji.getInstance(context).emojify(context.getString(R.string.GroupUtil_group_updated),
              new Emoji.InvalidatingPageLoadedListener(bodyText)),
          TextView.BufferType.SPANNABLE);
    } else if(messageRecord.isGroupAction() && messageRecord.type == TYPE_LEFT_GROUP) {
      bodyText.setText(Emoji.getInstance(context).emojify(context.getString(R.string.MessageRecord_left_group),
              new Emoji.InvalidatingPageLoadedListener(bodyText)),
          TextView.BufferType.SPANNABLE);
    }  else if(messageRecord.type == TYPE_WRONG_KEY && messageRecord.containsKey() && !messageRecord.getRecipients().isGroupRecipient()) {
      deleteMessage(messageRecord);
      handleKeyExchangeClicked();
    } else if (messageRecord.type == TYPE_WRONG_KEY && messageRecord.containsKey() && messageRecord.getRecipients().isGroupRecipient()) {
        deleteMessage(messageRecord);
    } else if(messageRecord != null  && !messageRecord.isFailed() && messageRecord.isGroupAction() && messageRecord.getIndividualRecipient() != null && "Unknown".equals(messageRecord.getIndividualRecipient().getName())) {
      bodyText.setText(Emoji.getInstance(context).emojify(context.getString(R.string.GroupUtil_group_updated),
                      new Emoji.InvalidatingPageLoadedListener(bodyText)),
              TextView.BufferType.SPANNABLE);
    } else if(messageRecord.isDeliveryFailed() && messageRecord.getIndividualRecipient() != null && groupThread && "Unknown".equals(messageRecord.getIndividualRecipient().getName()) && messageRecord.isOutgoing()) {
      bodyText.setText(Emoji.getInstance(context).emojify(context.getString(R.string.msg_failed),
                      new Emoji.InvalidatingPageLoadedListener(bodyText)),
              TextView.BufferType.SPANNABLE);
    } else {
      bodyText.setText(Emoji.getInstance(context).emojify(messageRecord.getDisplayBody(),
              new Emoji.InvalidatingPageLoadedListener(bodyText)),
          TextView.BufferType.SPANNABLE);
    }
    if (!messageRecord.isKeyExchange() && !messageRecord.isPendingSmsFallback()) {
      bodyText.setMovementMethod(GDataLinkMovementMethod.getInstance(conversationFragment));
    }
    if (bodyText.isClickable() && bodyText.isFocusable()) {
      bodyText.setOnLongClickListener(new MultiSelectLongClickListener());
      bodyText.setOnClickListener(new MultiSelectLongClickListener());
    }
    if (messageRecord.getBody().isSelfDestruction()) {
      if (bombImage != null) {
        bombImage.setVisibility(View.VISIBLE);
      }
      if (!messageRecord.isOutgoing()) {
        String destroyText = getContext().getString(R.string.self_destruction_body);
        destroyText = destroyText.replace("#1#", "" + messageRecord.getBody().getSelfDestructionDuration());
        String countdownText = getContext().getString(R.string.self_destruction_title);
        countdownText = countdownText.replace("#1#", "" + messageRecord.getBody().getSelfDestructionDuration());

        bodyText.setOnClickListener(new BombClickListener(bodyText.getText() + "", countdownText));
        bodyText.setText(destroyText);
        bodyText.setVisibility(View.VISIBLE);
      }
    } else if (bombImage != null) {
      bombImage.setVisibility(View.GONE);
    }
  }
  public String getUniqueMsgId(MessageRecord messageRecord) {
    return messageRecord.getRecipientDeviceId() + "" + messageRecord.getType()+messageRecord.getDateReceived()+messageRecord.getBody().getParsedBody();
  }
  public class BombClickListener implements OnClickListener {
    String text = "";
    String countdown = "";

    private int currentCountdown = 0;
    private boolean alreadyDestroyed = false;

    public BombClickListener(String text, String countdown) {
      this.text = text;
      this.countdown = countdown;
    }

    public void dismissDialog() {
      try {
        if ((alertDialogDestroy != null) && alertDialogDestroy.isShowing()) {
          alertDialogDestroy.dismiss();
        }
      } catch (final IllegalArgumentException e) {
        // Handle or log or ignore
      } catch (final Exception e) {
        // Handle or log or ignore
      } finally {
        alertDialogDestroy = null;
      }
    }

    public void refreshCountdown() {
      if (alertDialogDestroy != null) {
        String countdown = getContext().getString(R.string.self_destruction_title);
        countdown = countdown.replace("#1#", "" + currentCountdown);
        alertDialogDestroy.setTitle("" + countdown);
   if ((messageRecord.getBody().getSelfDestructionDuration() == currentCountdown)) {
          if (hasMedia(messageRecord)) {
            thumbnailDestroyDialog.setVisibility(View.VISIBLE);
            thumbnailDestroyDialog.setImageResource(masterSecret, ((MediaMmsMessageRecord) messageRecord).getId(),
                ((MediaMmsMessageRecord) messageRecord).getDateReceived(),
                ((MediaMmsMessageRecord) messageRecord).getSlideDeckFuture());

            thumbnailDestroyDialog.setThumbnailClickListener(new ThumbnailClickListener());
          } else {
            thumbnailDestroyDialog.setVisibility(View.GONE);
          }
        }
      }
    }

    @Override
    public void onClick(View v) {

      AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
      builder.setTitle(countdown);
      builder.setIcon(R.drawable.ic_action_timebomb);
      builder.setCancelable(false);
      LayoutInflater vi = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
      LinearLayout rlView = (LinearLayout) vi.inflate(R.layout.destroy_dialog, null);

      ((TextView) rlView.findViewById(R.id.textDialog)).setText(Emoji.getInstance(context).emojify(text,
                      new Emoji.InvalidatingPageLoadedListener(((TextView) rlView.findViewById(R.id.textDialog)))),
              TextView.BufferType.SPANNABLE);
      ((TextView) rlView.findViewById(R.id.textDialog)).setMovementMethod(new ScrollingMovementMethod());

      builder.setView(rlView);
      builder.setPositiveButton(R.string.self_destruction, new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
          dialog.dismiss();
          alreadyDestroyed = true;
          openedMessageId = "";
          updateListeners(messageRecord);
        }
      });
      alertDialogDestroy = builder.show();
      alertDialogDestroy.getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
              WindowManager.LayoutParams.FLAG_SECURE);

      ((ViewGroup)alertDialogDestroy.getWindow().getDecorView()).startAnimation(AnimationUtils.loadAnimation(
              context, android.R.anim.slide_in_left));

      currentCountdown = messageRecord.getBody().getSelfDestructionDuration();
      thumbnailDestroyDialog = ((ThumbnailView) alertDialogDestroy.findViewById(R.id.imageDialog));
      loadingDestroyIndicator = ((ImageView) alertDialogDestroy.findViewById(R.id.loading_indicator));

        if (getActivity() != null) {
            getActivity().runOnUiThread(new Runnable() {
          @Override
          public void run() {
            refreshCountdown();
          }
        });
      }
      new Thread(new Runnable() {
        @Override
        public void run() {
          int i = currentCountdown;
          int z = 0;
            new GDataPreferences(getContext()).setAsDestroyed(getUniqueMsgId(messageRecord));
            openedMessageId = getUniqueMsgId(messageRecord);
          while (!thumbnailDestroyDialog.isLoadingDone() && z < 10 && hasMedia(messageRecord)) {
            z++;
            try {
              Thread.sleep(SECOND);
            } catch (InterruptedException e) {
              e.printStackTrace();
            }
          }
          if (getActivity() != null) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    loadingDestroyIndicator.setVisibility(View.GONE);
                }
            });
          }
          while (i > 0 && !alreadyDestroyed) {
            try {
              Thread.sleep(SECOND);
            } catch (InterruptedException e) {
              e.printStackTrace();
            }
            i--;
            currentCountdown = i;
            if (getActivity() != null) {
              getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                  refreshCountdown();
                }
              });
            }
          }
          if (getActivity() != null) {
            getActivity().runOnUiThread(new Runnable() {
              @Override
              public void run() {
                dismissDialog();
              }
            });
          }
          MediaPreviewActivity.closeActivity();
          updateListeners(messageRecord);
          openedMessageId = "";
        }
      }).start();

    }
  }

  private void updateListeners(MessageRecord messageRecord) {
    if (messageRecord.isMms()) {
      DatabaseFactory.getMmsDatabase(getContext()).notifyListeners(messageRecord.getId());
    } else {
      DatabaseFactory.getSmsDatabase(getContext()).notifyListeners(messageRecord.getId());
    }
  }

  public void deleteMessage(MessageRecord mr) {
    if (mr.isMms()) {
      DatabaseFactory.getMmsDatabase(getContext()).delete(mr.getId());
    } else {
      DatabaseFactory.getSmsDatabase(getContext()).deleteMessage(mr.getId());
    }
  }
    private void setContactPhoto(MessageRecord messageRecord) {
      if (!messageRecord.isOutgoing()) {
        setContactPhotoForRecipient(messageRecord.getIndividualRecipient());
      }
    }

    private void setStatusIcons(final MessageRecord messageRecord) {
      failedImage.setVisibility(messageRecord.isFailed() ? View.VISIBLE : View.GONE);
      if (messageRecord.isOutgoing()) {
        pendingIndicator.setVisibility(messageRecord.isPendingSmsFallback() || messageRecord.isFailed() ? View.VISIBLE : View.GONE);
        indicatorText.setVisibility(messageRecord.isPendingSmsFallback() ? View.VISIBLE : View.GONE);
        pendingIndicator.setOnClickListener(new OnClickListener() {
          @Override
          public void onClick(View view) {
            if(messageRecord.isFailed()) {
              handleMessageApproval();
            }
          }
        });
      }
      secureImage.setVisibility(messageRecord.isSecure() ? View.VISIBLE : View.GONE);
      bodyText.setCompoundDrawablesWithIntrinsicBounds(0, 0, messageRecord.isKeyExchange() || messageRecord.displaysAKey() ? R.drawable.ic_menu_login : 0, 0);
      if(messageRecord.displaysAKey()) bodyText.setText("");

      deliveryImage.setVisibility(!messageRecord.isKeyExchange() && messageRecord.isDelivered() ? View.VISIBLE : View.GONE);

      mmsThumbnail.setVisibility(View.GONE);
      mmsDownloadButton.setVisibility(View.GONE);
      mmsDownloadingLabel.setVisibility(View.GONE);

      if (messageRecord.isFailed()) {
        dateText.setText(R.string.ConversationItem_error_sending_message_gdata);
        if(indicatorText != null) {
          indicatorText.setVisibility(View.GONE);
        }
      } else if (messageRecord.isPendingSmsFallback() && indicatorText != null) {
        dateText.setText("");
        if (messageRecord.isPendingSecureSmsFallback()) {
          if (messageRecord.isMms())
            indicatorText.setText(R.string.ConversationItem_click_to_approve_mms);
          else indicatorText.setText(R.string.ConversationItem_click_to_approve_sms);
        } else {
          indicatorText.setText(R.string.ConversationItem_click_to_approve_unencrypted);
        }
      } else if (messageRecord.isPending()) {
        dateText.setText(" ··· ");
      } else {
        final long timestamp;

        if (messageRecord.isPush()) timestamp = messageRecord.getDateSent();
        else timestamp = messageRecord.getDateReceived();

        dateText.setText(DateUtils.getRelativeTimeSpanString(getContext(), timestamp));
      }
    }

    private void setMinimumWidth() {
      if (indicatorText != null && indicatorText.getVisibility() == View.VISIBLE && indicatorText.getText() != null) {
        final float density = getResources().getDisplayMetrics().density;
        conversationParent.setMinimumWidth(indicatorText.getText().length() * (int) (6.5 * density));
      } else {
        conversationParent.setMinimumWidth(0);
      }
    }

    private void setEvents(MessageRecord messageRecord) {
      setClickable(messageRecord.isPendingSmsFallback() ||
          (messageRecord.isKeyExchange() &&
              !messageRecord.isCorruptedKeyExchange() &&
              !messageRecord.isOutgoing()));

      if (!messageRecord.isOutgoing() &&
          messageRecord.getRecipients().isSingleRecipient() &&
          !messageRecord.isSecure()) {
        checkForAutoInitiate(messageRecord.getIndividualRecipient(),
                messageRecord.getBody().getBody(),
                messageRecord.getThreadId());
      }
    }

    private void setGroupMessageStatus(MessageRecord messageRecord) {
      if (groupThread && !messageRecord.isOutgoing()) {
        this.groupStatusText.setText(messageRecord.getIndividualRecipient().toShortString());
        this.groupStatusText.setVisibility(View.VISIBLE);
      } else {
        this.groupStatusText.setVisibility(View.GONE);
      }
    }

    private void setNotificationMmsAttributes(NotificationMmsMessageRecord messageRecord) {
      String messageSize = String.format(context.getString(R.string.ConversationItem_message_size_d_kb),
          messageRecord.getMessageSize());
      String expires = String.format(context.getString(R.string.ConversationItem_expires_s),
          DateUtils.getRelativeTimeSpanString(getContext(),
              messageRecord.getExpiration(),
              false));

      dateText.setText(messageSize + "\n" + expires);

      if (MmsDatabase.Status.isDisplayDownloadButton(messageRecord.getStatus())) {
        mmsDownloadButton.setVisibility(View.VISIBLE);
        mmsDownloadingLabel.setVisibility(View.GONE);
      } else {
        mmsDownloadingLabel.setText(MmsDatabase.Status.getLabelForStatus(context, messageRecord.getStatus()));
        mmsDownloadButton.setVisibility(View.GONE);
        mmsDownloadingLabel.setVisibility(View.VISIBLE);

        if (MmsDatabase.Status.isHardError(messageRecord.getStatus()) && !messageRecord.isOutgoing())
          setOnClickListener(mmsDownloadClickListener);
        else if (MmsDatabase.Status.DOWNLOAD_APN_UNAVAILABLE == messageRecord.getStatus() && !messageRecord.isOutgoing())
          setOnClickListener(mmsPreferencesClickListener);
      }
    }

    private void setMediaAttributes(MessageRecord messageRecord) {
      if (messageRecord.isMmsNotification()) {
        setNotificationMmsAttributes((NotificationMmsMessageRecord) messageRecord);
      } else if (messageRecord.isMms()) {
        resolveMedia((MediaMmsMessageRecord) messageRecord);
        //setMediaMmsAttributes(messageRecord);
      }
    }

    private void resolveMedia(MediaMmsMessageRecord messageRecord) {
      if (hasMedia(messageRecord)) {

        if (!messageRecord.getBody().isSelfDestruction() || messageRecord.isOutgoing()) {
          mmsThumbnail.setVisibility(View.VISIBLE);
          mmsContainer.setVisibility(View.VISIBLE);
          mmsThumbnail.setImageResource(masterSecret, messageRecord.getId(),
              messageRecord.getDateReceived(),
              messageRecord.getSlideDeckFuture());

        } else {
          mmsThumbnail.setVisibility(View.GONE);
          mmsContainer.setVisibility(View.GONE);
        }

      }
    }

    private Drawable getDrawable(SlideDeck result) {
      Drawable bitmap = null;
      try {
        Uri uri = result.getThumbnailSlide().getThumbnailUri();
        InputStream is = PartAuthority.getPartStream(context, masterSecret, uri);
        bitmap = Drawable.createFromStream(is, null);
      } catch (Exception e) {
        e.printStackTrace();
      }
      return bitmap;
    }

    private boolean hasMedia(MessageRecord messageRecord) {
      return messageRecord.isMms() &&
          !messageRecord.isMmsNotification() &&
          ((MediaMmsMessageRecord) messageRecord).getPartCount() > 0;
    }

    private void checkForAutoInitiate(Recipient recipient, String body, long threadId) {
      if (!groupThread &&
          AutoInitiateActivity.isValidAutoInitiateSituation(context, masterSecret, recipient,
              body, threadId)) {
        AutoInitiateActivity.exemptThread(context, threadId);

        Intent intent = new Intent();
        intent.setClass(context, AutoInitiateActivity.class);
        intent.putExtra("threadId", threadId);
        intent.putExtra("masterSecret", masterSecret);
        intent.putExtra("recipient", recipient.getRecipientId());

        context.startActivity(intent);
      }
    }

    private void setContactPhotoForRecipient(final Recipient recipient) {
      if (contactPhoto == null) return;

      Bitmap contactPhotoBitmap;

        ImageSlide avatarSlide = ProfileAccessor.getProfileAsImageSlide(getActivity(), masterSecret, GUtil.numberToLong(recipient.getNumber()) + "");
        if (avatarSlide != null) {
            ProfileAccessor.buildGlideRequest(avatarSlide).into(contactPhoto);
            contactPhoto.setVisibility(View.VISIBLE);
        } else {
            if ((recipient.getContactPhoto() == ContactPhotoFactory.getDefaultContactPhoto(context)) && (groupThread)) {
                contactPhotoBitmap = recipient.getGeneratedAvatar(context);
            } else {
                contactPhotoBitmap = recipient.getCircleCroppedContactPhoto();
            }
            contactPhoto.setImageBitmap(contactPhotoBitmap);

            contactPhoto.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (recipient.getContactUri() != null) {
                        QuickContact.showQuickContact(context, contactPhoto, recipient.getContactUri(), QuickContact.MODE_LARGE, null);
                    } else {
                        final Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
                        intent.putExtra(ContactsContract.Intents.Insert.PHONE, recipient.getNumber());
                        intent.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);
                        context.startActivity(intent);
                    }
                }
            });
            contactPhoto.setVisibility(View.VISIBLE);
        }
    }

    /// Event handlers

    private void handleKeyExchangeClicked() {
      Intent intent = new Intent(context, ReceiveKeyActivity.class);
      intent.putExtra("recipient", messageRecord.getIndividualRecipient().getRecipientId());
      intent.putExtra("recipient_device_id", messageRecord.getRecipientDeviceId());
      intent.putExtra("body", messageRecord.getBody().getBody());
      intent.putExtra("thread_id", messageRecord.getThreadId());
      intent.putExtra("message_id", messageRecord.getId());
      intent.putExtra("is_bundle", messageRecord.isBundleKeyExchange());
      intent.putExtra("is_identity_update", messageRecord.isIdentityUpdate());
      intent.putExtra("is_push", messageRecord.isPush());
      intent.putExtra("master_secret", masterSecret);
      intent.putExtra("sent", messageRecord.isOutgoing());
      intent.putExtra("from_sender", messageRecord.type == TYPE_WRONG_KEY);
      context.startActivity(intent);
    }

    public Activity getActivity() {
      return conversationFragment.getActivity();
    }

    private class ThumbnailClickListener implements ThumbnailView.ThumbnailClickListener {
      private void fireIntent(Slide slide) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setDataAndType(PartAuthority.getPublicPartUri(slide.getUri()), slide.getContentType());
        try {
          context.startActivity(intent);
        } catch (ActivityNotFoundException anfe) {
          Log.w(TAG, "No activity existed to view the media.");
          Toast.makeText(context, R.string.ConversationItem_unable_to_open_media, Toast.LENGTH_LONG).show();
        }
      }

      public void onClick(final View v, final Slide slide) {
        boolean isAudio = slide instanceof AudioSlide;
        // if((isAudio && messageRecord.getBody().isSelfDestruction()) || !messageRecord.getBody().isSelfDestruction() || messageRecord.isOutgoing()) {
        if (!batchSelected.isEmpty()) {
          selectionClickListener.onItemClick(null, ConversationItem.this, -1, -1);
        } else if (MediaPreviewActivity.isContentTypeSupported(slide.getContentType())) {
          Intent intent = new Intent(context, MediaPreviewActivity.class);
          intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
          intent.setDataAndType(slide.getUri(), slide.getContentType());
          intent.putExtra(MediaPreviewActivity.MASTER_SECRET_EXTRA, masterSecret);
          if (!messageRecord.getBody().isSelfDestruction() || messageRecord.isOutgoing()) {
            intent.putExtra("destroyImage", false);
          } else {
            intent.putExtra("destroyImage", true);
          }
          if (!messageRecord.isOutgoing())
            intent.putExtra(MediaPreviewActivity.RECIPIENT_EXTRA, messageRecord.getIndividualRecipient().getRecipientId());
          intent.putExtra(MediaPreviewActivity.DATE_EXTRA, messageRecord.getDateReceived());
          context.startActivity(intent);
        } else {
          AlertDialogWrapper.Builder builder = new AlertDialogWrapper.Builder(context);
          builder.setTitle(R.string.ConversationItem_view_secure_media_question);
          builder.setIconAttribute(R.attr.dialog_alert_icon);
          builder.setCancelable(true);
          builder.setMessage(R.string.ConversationItem_this_media_has_been_stored_in_an_encrypted_database_external_viewer_warning);
          builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
              fireIntent(slide);
            }
          });
          builder.setNegativeButton(R.string.no, null);
          builder.show();
        }
        //  }
      }
    }

    private class MmsDownloadClickListener implements View.OnClickListener {
      public void onClick(View v) {
        NotificationMmsMessageRecord notificationRecord = (NotificationMmsMessageRecord) messageRecord;
        Log.w("MmsDownloadClickListener", "Content location: " + new String(notificationRecord.getContentLocation()));
        mmsDownloadButton.setVisibility(View.GONE);
        mmsDownloadingLabel.setVisibility(View.VISIBLE);

        ApplicationContext.getInstance(context)
            .getJobManager()
            .add(new MmsDownloadJob(context, messageRecord.getId(),
                messageRecord.getThreadId(), false));
      }
    }

    private class MmsPreferencesClickListener implements View.OnClickListener {
      public void onClick(View v) {
        Intent intent = new Intent(context, PromptMmsActivity.class);
        intent.putExtra("message_id", messageRecord.getId());
        intent.putExtra("thread_id", messageRecord.getThreadId());
        intent.putExtra("automatic", true);
        context.startActivity(intent);
      }
    }

    private class FailedIconClickListener implements View.OnClickListener {
      public void onClick(View v) {
        if (failedIconHandler != null && !messageRecord.isKeyExchange()) {
          Message message = Message.obtain();
          message.obj = messageRecord.getBody().getParsedBody();
          failedIconHandler.dispatchMessage(message);
        }
      }
    }

    private class ClickListener implements View.OnClickListener {
      public void onClick(View v) {
        if (messageRecord.isKeyExchange() &&
            !messageRecord.isOutgoing() &&
            !messageRecord.isProcessedKeyExchange() &&
            !messageRecord.isStaleKeyExchange())
          handleKeyExchangeClicked();
        else if (messageRecord.isPendingSmsFallback())
          handleMessageApproval();
      }
    }

    private class MultiSelectLongClickListener implements OnLongClickListener, OnClickListener {
      @Override
      public boolean onLongClick(View view) {
        selectionClickListener.onItemLongClick(null, ConversationItem.this, -1, -1);
        return true;
      }

      @Override
      public void onClick(View view) {
        selectionClickListener.onItemClick(null, ConversationItem.this, -1, -1);
      }
    }

    private void handleMessageApproval() {
      final int title;
      final int message;

      if (messageRecord.isPendingSecureSmsFallback()) {
        if (messageRecord.isMms())
          title = R.string.ConversationItem_click_to_approve_mms_dialog_title;
        else title = R.string.ConversationItem_click_to_approve_sms_dialog_title;

        message = -1;
      } else {
        if (messageRecord.isMms())
          title = R.string.ConversationItem_click_to_approve_unencrypted_mms_dialog_title;
        else title = R.string.ConversationItem_click_to_approve_unencrypted_sms_dialog_title;

        message = R.string.ConversationItem_click_to_approve_unencrypted_dialog_message;
      }

      AlertDialog.Builder builder = new AlertDialog.Builder(context);
      builder.setTitle(title);

      if (message > -1) builder.setMessage(message);

      builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialogInterface, int i) {
          if (messageRecord.isMms()) {
            MmsDatabase database = DatabaseFactory.getMmsDatabase(context);
            if (messageRecord.isPendingInsecureSmsFallback()) {
              database.markAsInsecure(messageRecord.getId());
            }
            database.markAsOutbox(messageRecord.getId());
            database.markAsForcedSms(messageRecord.getId());

            ApplicationContext.getInstance(context)
                .getJobManager()
                .add(new MmsSendJob(context, messageRecord.getId()));
          } else {
            SmsDatabase database = DatabaseFactory.getSmsDatabase(context);
            if (messageRecord.isPendingInsecureSmsFallback()) {
              database.markAsInsecure(messageRecord.getId());
            }
            database.markAsOutbox(messageRecord.getId());
            database.markAsForcedSms(messageRecord.getId());

            ApplicationContext.getInstance(context)
                .getJobManager()
                .add(new SmsSendJob(context, messageRecord.getId(),
                    messageRecord.getIndividualRecipient().getNumber()));
          }
        }
      });

      builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialogInterface, int i) {
          if (messageRecord.isMms()) {
            DatabaseFactory.getMmsDatabase(context).markAsSentFailed(messageRecord.getId());
          } else {
            DatabaseFactory.getSmsDatabase(context).markAsSentFailed(messageRecord.getId());
          }
        }
      });
      builder.show();
    }
  }
