package org.thoughtcrime.securesms.preferences.widgets;


import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceViewHolder;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.load.engine.DiskCacheStrategy;

import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.loki.redesign.utilities.MnemonicUtilities;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.loki.crypto.MnemonicCodec;

import network.loki.messenger.R;

public class ProfilePreference extends Preference {

  private View containerView;
  private ImageView avatarView;
  private TextView  profileNameView;
  private TextView  profileNumberView;
  private TextView  profileTagView;
  private String    ourDeviceWords;

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  public ProfilePreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    initialize();
  }

  public ProfilePreference(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    initialize();
  }

  public ProfilePreference(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  public ProfilePreference(Context context) {
    super(context);
    initialize();
  }

  private void initialize() {
    setLayoutResource(R.layout.profile_preference_view);
  }

  @Override
  public void onBindViewHolder(PreferenceViewHolder viewHolder) {
    super.onBindViewHolder(viewHolder);

    containerView     = viewHolder.itemView;
    avatarView        = (ImageView)viewHolder.findViewById(R.id.avatar);
    profileNameView   = (TextView)viewHolder.findViewById(R.id.profile_name);
    profileNumberView = (TextView)viewHolder.findViewById(R.id.number);
    profileTagView    = (TextView)viewHolder.findViewById(R.id.tag);

    refresh();
  }

  public void refresh() {
    if (profileNumberView == null) return;

    Context context = getContext();
    String userHexEncodedPublicKey = TextSecurePreferences.getLocalNumber(context);
    String primaryDevicePublicKey = TextSecurePreferences.getMasterHexEncodedPublicKey(context);
    String publicKey = primaryDevicePublicKey != null ? primaryDevicePublicKey : userHexEncodedPublicKey;
    final Address localAddress = Address.fromSerialized(publicKey);
    final Recipient recipient = Recipient.from(context, localAddress, false);
    final String  profileName  = TextSecurePreferences.getProfileName(context);

    containerView.setOnLongClickListener(v -> {
      ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
      ClipData clip = ClipData.newPlainText("Public Key", publicKey);
      clipboard.setPrimaryClip(clip);
      Toast.makeText(context, R.string.activity_settings_public_key_copied_message, Toast.LENGTH_SHORT).show();
      return true;
    });

    avatarView.setOutlineProvider(new ViewOutlineProvider() {

      @Override
      public void getOutline(View view, Outline outline) {
        outline.setOval(0, 0, view.getWidth(), view.getHeight());
      }
    });
    avatarView.setClipToOutline(true);

    Drawable fallback = recipient.getFallbackContactPhotoDrawable(context, false);
    GlideApp.with(getContext().getApplicationContext())
            .load(recipient.getContactPhoto())
            .fallback(fallback)
            .error(fallback)
            .circleCrop()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .into(avatarView);


    if (!TextUtils.isEmpty(profileName)) {
      profileNameView.setText(profileName);
    }

    profileNameView.setVisibility(TextUtils.isEmpty(profileName) ? View.GONE : View.VISIBLE);
    profileNumberView.setText(localAddress.toPhoneString());

    profileTagView.setVisibility(primaryDevicePublicKey == null ? View.GONE : View.VISIBLE);
    if (primaryDevicePublicKey != null && ourDeviceWords == null) {
      MnemonicCodec codec = new MnemonicCodec(MnemonicUtilities.getLanguageFileDirectory(context));
      ourDeviceWords = MnemonicUtilities.getFirst3Words(codec, userHexEncodedPublicKey);
    }

    String tag = context.getResources().getString(R.string.activity_settings_linked_device_tag);
    profileTagView.setText(String.format(tag, ourDeviceWords != null ? ourDeviceWords : "-"));
  }
}
