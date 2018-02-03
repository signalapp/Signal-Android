package org.thoughtcrime.securesms.preferences.widgets;


import android.content.Context;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceViewHolder;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.load.engine.DiskCacheStrategy;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.contacts.avatars.ProfileContactPhoto;
import org.thoughtcrime.securesms.contacts.avatars.ResourceContactPhoto;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

public class ProfilePreference extends Preference {

  private ImageView avatarView;
  private TextView  profileNameView;
  private TextView  profileNumberView;

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
    avatarView        = (ImageView)viewHolder.findViewById(R.id.avatar);
    profileNameView   = (TextView)viewHolder.findViewById(R.id.profile_name);
    profileNumberView = (TextView)viewHolder.findViewById(R.id.number);

    refresh();
  }

  public void refresh() {
    if (profileNumberView == null) return;

    final Address localAddress = Address.fromSerialized(TextSecurePreferences.getLocalNumber(getContext()));
    final String  profileName  = TextSecurePreferences.getProfileName(getContext());

    GlideApp.with(getContext().getApplicationContext())
            .load(new ProfileContactPhoto(localAddress, String.valueOf(TextSecurePreferences.getProfileAvatarId(getContext()))))
            .error(new ResourceContactPhoto(R.drawable.ic_camera_alt_white_24dp).asDrawable(getContext(), getContext().getResources().getColor(R.color.grey_400)))
            .circleCrop()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .into(avatarView);

    if (!TextUtils.isEmpty(profileName)) {
      profileNameView.setText(profileName);
    }

    profileNumberView.setText(localAddress.toPhoneString());
  }
}
