package org.thoughtcrime.securesms.preferences;


import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.preference.Preference;
import android.support.annotation.RequiresApi;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.contacts.avatars.ContactPhoto;
import org.thoughtcrime.securesms.contacts.avatars.ContactPhotoFactory;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.ViewUtil;

public class ProfilePreference extends Preference {

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
  protected void onBindView(View view) {
    super.onBindView(view);

    final ImageView avatar        = ViewUtil.findById(view, R.id.avatar);
    final TextView  profileName   = ViewUtil.findById(view, R.id.profile_name);
    final TextView  profileNumber = ViewUtil.findById(view, R.id.number);
    final Address   localAddress  = Address.fromSerialized(TextSecurePreferences.getLocalNumber(getContext()));

    new AsyncTask<Void, Void, ContactPhoto>() {
      @Override
      protected ContactPhoto doInBackground(Void... params) {
        return ContactPhotoFactory.getSignalAvatarContactPhoto(getContext(), localAddress, null, getContext().getResources().getDimensionPixelSize(R.dimen.contact_photo_target_size));
      }

      @Override
      protected void onPostExecute(ContactPhoto contactPhoto) {
        avatar.setImageDrawable(contactPhoto.asDrawable(getContext(), 0));
      }
    }.execute();

    if (!TextUtils.isEmpty(TextSecurePreferences.getProfileName(getContext()))) {
      profileName.setText(TextSecurePreferences.getProfileName(getContext()));
    }

    profileNumber.setText(localAddress.toPhoneString());
  }
}
