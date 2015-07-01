/**
 * Copyright (C) 2014 Open Whisper Systems
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

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.AlertDialogWrapper;

import org.thoughtcrime.securesms.components.ThumbnailView;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.mms.AttachmentManager;
import org.thoughtcrime.securesms.mms.AudioSlide;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.util.BitmapUtil;

import de.gdata.messaging.util.GDataPreferences;
import de.gdata.messaging.util.GUtil;
import de.gdata.messaging.util.ProfileAccessor;

public class ProfileFragment extends Fragment {

  private MasterSecret masterSecret;
  private GDataPreferences gDataPreferences;
  private String profileId = "";

  private static final int PICK_IMAGE = 1;
  private EditText profileStatus;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
    return GUtil.setFontForFragment(getActivity(), inflater.inflate(R.layout.profile_fragment, container, false));
  }

  @Override
  public void onActivityCreated(Bundle bundle) {
    super.onActivityCreated(bundle);
    initializeResources();
    refreshLayout();
  }

  private void refreshLayout() {
    gDataPreferences = new GDataPreferences(getActivity());
    boolean isMyProfile = (GUtil.numberToLong(gDataPreferences.getE164Number()+"")+"").contains(GUtil.numberToLong(profileId)+"");
    profileStatus = (EditText) getView().findViewById(R.id.profile_status);
    ThumbnailView profilePicture = (ThumbnailView) getView().findViewById(R.id.profile_picture);
    if(masterSecret != null && !isMyProfile) {
      try {
        profilePicture.setImageResource(ProfileAccessor.getProfileAsImageSlide(getActivity(), masterSecret, profileId), masterSecret);
      } catch (IllegalStateException e) {
        Log.w("GDATA", "UNABLE TO LOAD PROFILE IMAGE");
      }
      profileStatus.setText(ProfileAccessor.getProfileStatusForRecepient(getActivity(), profileId), TextView.BufferType.EDITABLE);
      profileStatus.setEnabled(false);
    } else {
      profilePicture.setImageResource(ProfileAccessor.getMyProfilePicture(getActivity()));
      profileStatus.setText(ProfileAccessor.getProfileStatus(getActivity()), TextView.BufferType.EDITABLE);
    }
    profilePicture.setThumbnailClickListener(new ThumbnailClickListener());
    final ImageView profileStatusEdit = (ImageView) getView().findViewById(R.id.profile_status_edit);
    ImageView profileImageEdit = (ImageView) getView().findViewById(R.id.profile_picture_edit);
    if(!isMyProfile) {
      profileStatusEdit.setVisibility(View.GONE);
      profileImageEdit.setVisibility(View.GONE);
    } else {
      profileStatusEdit.setVisibility(View.VISIBLE);
      profileImageEdit.setVisibility(View.VISIBLE);
      profileStatusEdit.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          profileStatus.setEnabled(!profileStatus.isEnabled());
          if (!profileStatus.isEnabled()) {
            ProfileAccessor.setProfileStatus(getActivity(), profileStatus.getText() + "");
            profileStatusEdit.setImageDrawable(getResources().getDrawable(android.R.drawable.ic_menu_edit));
          } else {
            profileStatusEdit.setImageDrawable(getResources().getDrawable(android.R.drawable.ic_menu_save));
          }
        }
      });
      profileImageEdit.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          AttachmentManager.selectImage(getActivity(), PICK_IMAGE);
        }
      });
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    refreshLayout();
  }

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
  }
  private void initializeResources() {
    this.masterSecret    = getActivity().getIntent().getParcelableExtra("master_secret");
    this.profileId    = getActivity().getIntent().getStringExtra("profile_id");
  }
  private class ThumbnailClickListener implements ThumbnailView.ThumbnailClickListener {
  private void fireIntent(Slide slide) {
    Intent intent = new Intent(Intent.ACTION_VIEW);
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    intent.setDataAndType(PartAuthority.getPublicPartUri(slide.getUri()), slide.getContentType());
    try {
      getActivity().startActivity(intent);
    } catch (ActivityNotFoundException anfe) {
      Toast.makeText(getActivity(), R.string.ConversationItem_unable_to_open_media, Toast.LENGTH_LONG).show();
    }
  }
  public void onClick(final View v, final Slide slide) {
     if (MediaPreviewActivity.isContentTypeSupported(slide.getContentType())) {
      Intent intent = new Intent(getActivity(), MediaPreviewActivity.class);
      intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      intent.setDataAndType(slide.getUri(), slide.getContentType());
      intent.putExtra(MediaPreviewActivity.MASTER_SECRET_EXTRA, masterSecret);
       intent.putExtra(MediaPreviewActivity.RECIPIENT_EXTRA, RecipientFactory.getRecipientsFromString(getActivity(), String.valueOf(profileId), false).getPrimaryRecipient().getRecipientId());
      getActivity().startActivity(intent);
    } else {
      AlertDialogWrapper.Builder builder = new AlertDialogWrapper.Builder(getActivity());
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
}
