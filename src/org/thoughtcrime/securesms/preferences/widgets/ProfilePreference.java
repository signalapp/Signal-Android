package org.thoughtcrime.securesms.preferences.widgets;


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

import com.lelloman.identicon.drawable.ClassicIdenticonDrawable;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.Address;
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

    String userHexEncodedPublicKey = TextSecurePreferences.getLocalNumber(getContext());
    final Address localAddress = Address.fromSerialized(userHexEncodedPublicKey);
    final String  profileName  = TextSecurePreferences.getProfileName(getContext());

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
        avatarView.getViewTreeObserver().removeOnPreDrawListener(this);
        ClassicIdenticonDrawable identicon = new ClassicIdenticonDrawable(avatarView.getWidth(), avatarView.getHeight(), userHexEncodedPublicKey.hashCode());
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
  }
}
