package org.thoughtcrime.securesms.preferences.widgets;


import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Outline;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceViewHolder;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.loki.JazzIdenticonDrawable;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import network.loki.messenger.R;

public class ProfilePreference extends Preference {

  private View containerView;
  private ImageView avatarView;
  private TextView  profileNameView;
  private TextView  profileNumberView;
  private TextView  profileTagView;

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

    String userHexEncodedPublicKey = TextSecurePreferences.getLocalNumber(getContext());
    String primaryDevicePublicKey = TextSecurePreferences.getMasterHexEncodedPublicKey(getContext());
    String publicKey = primaryDevicePublicKey != null ? primaryDevicePublicKey : userHexEncodedPublicKey;
    final Address localAddress = Address.fromSerialized(publicKey);
    final String  profileName  = TextSecurePreferences.getProfileName(getContext());

    Context context = getContext();
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
    avatarView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {

      @Override
      public boolean onPreDraw() {
        int width = avatarView.getWidth();
        int height = avatarView.getHeight();
        if (width == 0 || height == 0) return true;
        avatarView.getViewTreeObserver().removeOnPreDrawListener(this);
        JazzIdenticonDrawable identicon = new JazzIdenticonDrawable(width, height, publicKey.toLowerCase());
        avatarView.setImageDrawable(identicon);
        return true;
      }
    });

    /*
    GlideApp.with(getContext().getApplicationContext())
            .load(new ProfileContactPhoto(localAddress, String.valueOf(TextSecurePreferences.getProfileAvatarId(getContext()))))
            .error(new ResourceContactPhoto(R.drawable.ic_camera_alt_white_24dp).asDrawable(getContext(), getContext().getResources().getColor(R.color.grey_400)))
            .circleCrop()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .into(avatarView);
     */

    if (!TextUtils.isEmpty(profileName)) {
      profileNameView.setText(profileName);
    }

    profileNameView.setVisibility(TextUtils.isEmpty(profileName) ? View.GONE : View.VISIBLE);
    profileNumberView.setText(localAddress.toPhoneString());

    profileTagView.setVisibility(primaryDevicePublicKey == null ? View.GONE : View.VISIBLE);
    profileTagView.setText(R.string.activity_settings_secondary_device_tag);
  }
}
