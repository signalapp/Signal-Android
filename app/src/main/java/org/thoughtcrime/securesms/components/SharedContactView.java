package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.widget.ImageViewCompat;

import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import org.signal.glide.decryptableuri.DecryptableUri;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.contactshare.Contact;
import org.thoughtcrime.securesms.contactshare.ContactUtil;
import org.thoughtcrime.securesms.contactshare.SharedContactPresentation;
import org.thoughtcrime.securesms.avatar.fallback.FallbackAvatar;
import org.thoughtcrime.securesms.avatar.fallback.FallbackAvatarDrawable;
import org.thoughtcrime.securesms.conversation.colors.AvatarColor;
import org.thoughtcrime.securesms.database.RecipientTable;
import org.thoughtcrime.securesms.recipients.LiveRecipient;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientForeverObserver;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.ViewUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SharedContactView extends LinearLayout implements RecipientForeverObserver {

  private ImageView              avatarView;
  private TextView               nameView;
  private AppCompatImageView     disclosureView;
  private TextView               actionButtonView;
  private ConversationItemFooter footer;

  private Contact                    contact;
  private SharedContactPresentation  presentation;
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

  @RequiresApi(api = 21)
  public SharedContactView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    initialize(attrs);
  }

  private void initialize(@Nullable AttributeSet attrs) {
    inflate(getContext(), R.layout.shared_contact_view, this);

    avatarView       = findViewById(R.id.contact_avatar);
    nameView         = findViewById(R.id.contact_name);
    disclosureView   = findViewById(R.id.contact_disclosure);
    actionButtonView = findViewById(R.id.contact_action_button);
    footer           = findViewById(R.id.contact_footer);

    cornerMask        = new CornerMask(this);
    bigCornerRadius   = getResources().getDimensionPixelOffset(R.dimen.message_corner_radius);
    smallCornerRadius = getResources().getDimensionPixelOffset(R.dimen.message_corner_collapse_radius);

    if (attrs != null) {
      TypedArray typedArray      = getContext().getTheme().obtainStyledAttributes(attrs, R.styleable.SharedContactView, 0, 0);
      int        titleColor      = typedArray.getInt(R.styleable.SharedContactView_contact_titleColor, Color.BLACK);
      int        captionColor    = typedArray.getInt(R.styleable.SharedContactView_contact_captionColor, Color.BLACK);
      int        chevronColor    = typedArray.getInt(R.styleable.SharedContactView_contact_chevronColor, Color.BLACK);
      int        iconColor       = typedArray.getInt(R.styleable.SharedContactView_contact_footerIconColor, Color.BLACK);
      float      footerAlpha     = typedArray.getFloat(R.styleable.SharedContactView_contact_footerAlpha, 1);
      int        actionTextColor = typedArray.getInt(R.styleable.SharedContactView_contact_actionTextColor, Color.BLACK);
      int        actionBgColor   = typedArray.getInt(R.styleable.SharedContactView_contact_actionBackgroundColor, Color.TRANSPARENT);
      typedArray.recycle();

      nameView.setTextColor(titleColor);
      footer.setTextColor(captionColor);
      footer.setIconColor(iconColor);
      footer.setAlpha(footerAlpha);

      ImageViewCompat.setImageTintList(disclosureView, ColorStateList.valueOf(chevronColor));
      actionButtonView.setTextColor(actionTextColor);
      actionButtonView.getBackground().mutate().setColorFilter(actionBgColor, PorterDuff.Mode.SRC_IN);
    }
  }

  @Override
  protected void dispatchDraw(Canvas canvas) {
    super.dispatchDraw(canvas);
    cornerMask.mask(canvas);
  }

  public void setContact(@NonNull Contact contact,
                         @NonNull SharedContactPresentation presentation,
                         @NonNull RequestManager requestManager,
                         @NonNull Locale locale)
  {
    this.requestManager = requestManager;
    this.locale         = locale;
    this.contact        = contact;
    this.presentation   = presentation;

    activeRecipients.values().stream().forEach(recipient ->  recipient.removeForeverObserver(this));
    this.activeRecipients.clear();

    for (RecipientId recipientId : presentation.getRecipientIds()) {
      activeRecipients.put(recipientId, Recipient.live(recipientId));
    }

    presentContact(contact);
    presentAvatar(contact, contact.getAvatarAttachment() != null ? contact.getAvatarAttachment().getUri() : null);
    presentActionButtons(contact);

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

  /**
   * The width this card wants in order to show its content without truncating. Measure directly to
   * prevent measure loop with the bubble this sits in.
   */
  public int getNaturalContentWidth() {
    int horizontalPadding = 2 * getResources().getDimensionPixelSize(R.dimen.message_bubble_horizontal_padding);

    int nameRowWidth = avatarView.getLayoutParams().width +
                       ViewUtil.getRightMargin(avatarView) +
                       desiredTextWidth(nameView) +
                       ViewUtil.getLeftMargin(disclosureView) +
                       disclosureView.getLayoutParams().width;

    int actionWidth = actionButtonView.getVisibility() == VISIBLE ? desiredTextWidth(actionButtonView) : 0;

    return horizontalPadding + Math.max(nameRowWidth, actionWidth);
  }

  /**
   * Width the text wants, measured off the paint rather than by calling measure().
   */
  private static int desiredTextWidth(@NonNull TextView view) {
    CharSequence text = view.getText();

    if (text == null) {
      return 0;
    }

    return (int) Math.ceil(view.getPaint().measureText(text.toString())) + view.getPaddingLeft() + view.getPaddingRight();
  }

  public @NonNull View getAvatarView() {
    return avatarView;
  }

  public ConversationItemFooter getFooter() {
    return footer;
  }

  @Override
  public void onRecipientChanged(@NonNull Recipient recipient) {
    if (contact != null) {
      presentActionButtons(contact);
    }
  }

  private void presentContact(@Nullable Contact contact) {
    nameView.setText(contact != null ? ContactUtil.getDisplayName(contact) : "");
  }

  private void presentAvatar(@NonNull Contact contact, @Nullable Uri uri) {
    Drawable fallback = buildFallbackAvatar(contact);

    if (uri != null) {
      requestManager.load(new DecryptableUri(uri))
                    .fallback(fallback)
                    .error(fallback)
                    .circleCrop()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .dontAnimate()
                    .into(avatarView);
    } else {
      avatarView.setImageDrawable(fallback);
    }
  }

  /**
   * Initials when the card carries a personal name, matching how the app draws recipients without a
   * photo. A company name has no initials worth showing, so those keep the person glyph.
   */
  private @NonNull Drawable buildFallbackAvatar(@NonNull Contact contact) {
    // No recipient to derive a colour from, so every shared card uses the same one.
    FallbackAvatar fallbackAvatar;

    if (contact.getName().isEmpty() && !TextUtils.isEmpty(contact.getOrganization())) {
      fallbackAvatar = new FallbackAvatar.Resource.Person(AvatarColor.A100);
    } else {
      fallbackAvatar = FallbackAvatar.forTextOrDefault(ContactUtil.getDisplayName(contact), AvatarColor.A100);
    }

    return new FallbackAvatarDrawable(getContext(), fallbackAvatar).circleCrop();
  }

  /** Address book membership comes off the recipient snapshot, so this re-runs via {@link #onRecipientChanged}. */
  private void presentActionButtons(@NonNull Contact contact) {
    List<Recipient> registered      = new ArrayList<>(activeRecipients.size());
    boolean         isSystemContact = false;

    for (LiveRecipient recipient : activeRecipients.values()) {
      if (recipient.get().getRegistered() == RecipientTable.RegisteredState.REGISTERED) {
        registered.add(recipient.get());
      }

      isSystemContact |= recipient.get().isSystemContact();
    }

    boolean hasContactDetails = !contact.getPhoneNumbers().isEmpty() ||
                                !contact.getEmails().isEmpty() ||
                                !contact.getPostalAddresses().isEmpty();

    actionButtonView.setVisibility(VISIBLE);

    if (presentation.isOnSignal()) {
      actionButtonView.setText(R.string.SharedContactView_message);
      actionButtonView.setOnClickListener(v -> {
        if (eventListener != null) {
          eventListener.onMessageClicked(contact, registered);
        }
      });
    } else if (isSystemContact) {
      actionButtonView.setText(R.string.SharedContactView_invite_to_signal);
      actionButtonView.setOnClickListener(v -> {
        if (eventListener != null) {
          eventListener.onInviteClicked(contact);
        }
      });
    } else if (hasContactDetails) {
      actionButtonView.setText(R.string.SharedContactView_add_to_contacts);
      actionButtonView.setOnClickListener(v -> {
        if (eventListener != null) {
          eventListener.onAddToContactsClicked(contact);
        }
      });
    } else {
      actionButtonView.setText("");
      actionButtonView.setOnClickListener(null);
      actionButtonView.setVisibility(GONE);
    }
  }

  public interface EventListener {
    void onAddToContactsClicked(@NonNull Contact contact);
    void onInviteClicked(@NonNull Contact contact);
    void onMessageClicked(@NonNull Contact contact, @NonNull List<Recipient> choices);
  }
}
