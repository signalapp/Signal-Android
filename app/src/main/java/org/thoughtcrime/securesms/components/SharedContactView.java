package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.annimon.stream.Stream;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.contactshare.Contact;
import org.thoughtcrime.securesms.contactshare.ContactUtil;
import org.thoughtcrime.securesms.database.RecipientTable;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader.DecryptableUri;
import org.thoughtcrime.securesms.recipients.LiveRecipient;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientForeverObserver;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SharedContactView extends LinearLayout implements RecipientForeverObserver {

  private ImageView              avatarView;
  private TextView               nameView;
  private TextView               numberView;
  private TextView               actionButtonView;
  private ConversationItemFooter footer;

  private Contact        contact;
  private Locale         locale;
  private RequestManager requestManager;
  private EventListener  eventListener;
  private CornerMask     cornerMask;
  private int            bigCornerRadius;
  private int            smallCornerRadius;

  private final Map<RecipientId, LiveRecipient> activeRecipients = new HashMap<>();

  public SharedContactView(Context context) {
    super(context);
    initialize(null);
  }

  public SharedContactView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    initialize(attrs);
  }

  public SharedContactView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    initialize(attrs);
  }

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  public SharedContactView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    initialize(attrs);
  }

  private void initialize(@Nullable AttributeSet attrs) {
    inflate(getContext(), R.layout.shared_contact_view, this);

    avatarView       = findViewById(R.id.contact_avatar);
    nameView         = findViewById(R.id.contact_name);
    numberView       = findViewById(R.id.contact_number);
    actionButtonView = findViewById(R.id.contact_action_button);
    footer           = findViewById(R.id.contact_footer);

    cornerMask        = new CornerMask(this);
    bigCornerRadius   = getResources().getDimensionPixelOffset(R.dimen.message_corner_radius);
    smallCornerRadius = getResources().getDimensionPixelOffset(R.dimen.message_corner_collapse_radius);

    if (attrs != null) {
      TypedArray typedArray   = getContext().getTheme().obtainStyledAttributes(attrs, R.styleable.SharedContactView, 0, 0);
      int        titleColor   = typedArray.getInt(R.styleable.SharedContactView_contact_titleColor, Color.BLACK);
      int        captionColor = typedArray.getInt(R.styleable.SharedContactView_contact_captionColor, Color.BLACK);
      int        iconColor    = typedArray.getInt(R.styleable.SharedContactView_contact_footerIconColor, Color.BLACK);
      float      footerAlpha  = typedArray.getFloat(R.styleable.SharedContactView_contact_footerAlpha, 1);
      typedArray.recycle();

      nameView.setTextColor(titleColor);
      numberView.setTextColor(captionColor);
      footer.setTextColor(captionColor);
      footer.setIconColor(iconColor);
      footer.setAlpha(footerAlpha);
    }
  }

  @Override
  protected void dispatchDraw(Canvas canvas) {
    super.dispatchDraw(canvas);
    cornerMask.mask(canvas);
  }

  public void setContact(@NonNull Contact contact, @NonNull RequestManager requestManager, @NonNull Locale locale) {
    this.requestManager = requestManager;
    this.locale         = locale;
    this.contact        = contact;

    Stream.of(activeRecipients.values()).forEach(recipient ->  recipient.removeForeverObserver(this));
    this.activeRecipients.clear();

    presentContact(contact);
    presentAvatar(contact.getAvatarAttachment() != null ? contact.getAvatarAttachment().getUri() : null);
    presentActionButtons(ContactUtil.getRecipients(contact));

    for (LiveRecipient recipient : activeRecipients.values()) {
      recipient.observeForever(this);
    }
  }

  public void setSingularStyle() {
    cornerMask.setBottomLeftRadius(bigCornerRadius);
    cornerMask.setBottomRightRadius(bigCornerRadius);
  }

  public void setClusteredIncomingStyle() {
    cornerMask.setBottomLeftRadius(smallCornerRadius);
    cornerMask.setBottomRightRadius(bigCornerRadius);
  }

  public void setClusteredOutgoingStyle() {
    cornerMask.setBottomLeftRadius(bigCornerRadius);
    cornerMask.setBottomRightRadius(smallCornerRadius);
  }

  public void setEventListener(@NonNull EventListener eventListener) {
    this.eventListener = eventListener;
  }

  public @NonNull View getAvatarView() {
    return avatarView;
  }

  public ConversationItemFooter getFooter() {
    return footer;
  }

  @Override
  public void onRecipientChanged(@NonNull Recipient recipient) {
    presentActionButtons(Collections.singletonList(recipient.getId()));
  }

  private void presentContact(@Nullable Contact contact) {
    if (contact != null) {
      nameView.setText(ContactUtil.getDisplayName(contact));
      numberView.setText(ContactUtil.getDisplayNumber(contact, locale));
    } else {
      nameView.setText("");
      numberView.setText("");
    }
  }

  private void presentAvatar(@Nullable Uri uri) {
    if (uri != null) {
      requestManager.load(new DecryptableUri(uri))
                    .fallback(R.drawable.symbol_person_display_40)
                    .circleCrop()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .dontAnimate()
                    .into(avatarView);
    } else {
      requestManager.load(R.drawable.symbol_person_display_40)
                    .circleCrop()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(avatarView);
    }
  }

  private void presentActionButtons(@NonNull List<RecipientId> recipients) {
    for (RecipientId recipientId : recipients) {
      activeRecipients.put(recipientId, Recipient.live(recipientId));
    }

    List<Recipient> pushUsers   = new ArrayList<>(recipients.size());
    List<Recipient> systemUsers = new ArrayList<>(recipients.size());

    for (LiveRecipient recipient : activeRecipients.values()) {
      if (recipient.get().getRegistered() == RecipientTable.RegisteredState.REGISTERED) {
        pushUsers.add(recipient.get());
      } else if (recipient.get().isSystemContact()) {
        systemUsers.add(recipient.get());
      }
    }

    if (!pushUsers.isEmpty()) {
      actionButtonView.setText(R.string.SharedContactView_message);
      actionButtonView.setOnClickListener(v -> {
        if (eventListener != null) {
          eventListener.onMessageClicked(pushUsers);
        }
      });
    } else if (!systemUsers.isEmpty()) {
      actionButtonView.setText(R.string.SharedContactView_invite_to_signal);
      actionButtonView.setOnClickListener(v -> {
        if (eventListener != null) {
          eventListener.onInviteClicked(systemUsers);
        }
      });
    } else {
      actionButtonView.setText(R.string.SharedContactView_add_to_contacts);
      actionButtonView.setOnClickListener(v -> {
        if (eventListener != null && contact != null) {
          eventListener.onAddToContactsClicked(contact);
        }
      });
    }
  }

  public interface EventListener {
    void onAddToContactsClicked(@NonNull Contact contact);
    void onInviteClicked(@NonNull List<Recipient> choices);
    void onMessageClicked(@NonNull List<Recipient> choices);
  }
}
