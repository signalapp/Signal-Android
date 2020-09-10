package org.thoughtcrime.securesms.components.webrtc;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.bumptech.glide.load.engine.DiskCacheStrategy;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.contacts.avatars.ContactPhoto;
import org.thoughtcrime.securesms.contacts.avatars.FallbackContactPhoto;
import org.thoughtcrime.securesms.contacts.avatars.ResourceContactPhoto;
import org.thoughtcrime.securesms.events.CallParticipant;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.AvatarUtil;

import java.util.Objects;

/**
 * Encapsulates views needed to show a call participant including their
 * avatar in full screen or pip mode, and their video feed.
 */
public class CallParticipantView extends ConstraintLayout {

  private static final FallbackPhotoProvider FALLBACK_PHOTO_PROVIDER = new FallbackPhotoProvider();

  private RecipientId         recipientId;
  private AvatarImageView     avatar;
  private TextureViewRenderer renderer;
  private ImageView           pipAvatar;
  private ContactPhoto        contactPhoto;

  public CallParticipantView(@NonNull Context context) {
    super(context);
    onFinishInflate();
  }

  public CallParticipantView(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  public CallParticipantView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    avatar    = findViewById(R.id.call_participant_item_avatar);
    pipAvatar = findViewById(R.id.call_participant_item_pip_avatar);
    renderer  = findViewById(R.id.call_participant_renderer);

    avatar.setFallbackPhotoProvider(FALLBACK_PHOTO_PROVIDER);
  }

  void setCallParticipant(@NonNull CallParticipant participant) {
    boolean participantChanged = recipientId == null || !recipientId.equals(participant.getRecipient().getId());
    recipientId = participant.getRecipient().getId();

    renderer.setVisibility(participant.isVideoEnabled() ? View.VISIBLE : View.GONE);

    if (participant.isVideoEnabled()) {
      if (participant.getVideoSink().getEglBase() != null) {
        renderer.init(participant.getVideoSink().getEglBase());
      }
      renderer.attachBroadcastVideoSink(participant.getVideoSink());
    } else {
      renderer.attachBroadcastVideoSink(null);
    }

    if (participantChanged || !Objects.equals(contactPhoto, participant.getRecipient().getContactPhoto())) {
      avatar.setAvatar(participant.getRecipient());
      AvatarUtil.loadBlurredIconIntoViewBackground(participant.getRecipient(), this);
      setPipAvatar(participant.getRecipient());
      contactPhoto = participant.getRecipient().getContactPhoto();
    }
  }

  void setRenderInPip(boolean shouldRenderInPip) {
    avatar.setVisibility(shouldRenderInPip ? View.GONE : View.VISIBLE);
    pipAvatar.setVisibility(shouldRenderInPip ? View.VISIBLE : View.GONE);
  }

  private void setPipAvatar(@NonNull Recipient recipient) {
    ContactPhoto         contactPhoto  = recipient.getContactPhoto();
    FallbackContactPhoto fallbackPhoto = recipient.getFallbackContactPhoto(FALLBACK_PHOTO_PROVIDER);

    GlideApp.with(this)
            .load(contactPhoto)
            .fallback(fallbackPhoto.asCallCard(getContext()))
            .error(fallbackPhoto.asCallCard(getContext()))
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .into(pipAvatar);

    pipAvatar.setScaleType(contactPhoto == null ? ImageView.ScaleType.CENTER_INSIDE : ImageView.ScaleType.CENTER_CROP);
    pipAvatar.setBackgroundColor(recipient.getColor().toActionBarColor(getContext()));
  }

  private static final class FallbackPhotoProvider extends Recipient.FallbackPhotoProvider {
    @Override
    public @NonNull FallbackContactPhoto getPhotoForRecipientWithoutName() {
      ResourceContactPhoto photo = new ResourceContactPhoto(R.drawable.ic_profile_outline_120);
      photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
      return photo;
    }
  }
}
