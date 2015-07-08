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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.DragEvent;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.AlertDialogWrapper;

import org.thoughtcrime.securesms.components.ThumbnailView;
import org.thoughtcrime.securesms.contacts.ContactPhotoFactory;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.mms.AttachmentManager;
import org.thoughtcrime.securesms.mms.AudioSlide;
import org.thoughtcrime.securesms.mms.ImageSlide;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.Dialogs;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.ProgressDialogAsyncTask;
import org.thoughtcrime.securesms.util.SelectedRecipientsAdapter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadFactory;

import de.gdata.messaging.util.GDataPreferences;
import de.gdata.messaging.util.GUtil;
import de.gdata.messaging.util.ProfileAccessor;

public class ProfileFragment extends Fragment {

  private static final int PADDING_TOP = 300;
  private MasterSecret masterSecret;
  private GDataPreferences gDataPreferences;
  private String profileId = "";

  private static final int PICK_IMAGE = 1;
  private EditText profileStatus;
  private ImageView xCloseButton;
  private ImageView phoneCall;
  private TextView imageText;
  private TextView profilePhone;
  private ThumbnailView profilePicture;
  private Recipient recipient;
  private ScrollView scrollView;
  private boolean hasChanged = false;
  private boolean isGroup;
  private Recipients recipients;
  private ListView groupMember;
  private Set<Recipient> selectedContacts;
  private Set<Recipient> existingContacts  = null;
  private byte[] groupId;
  private RelativeLayout layout_status;
  private RelativeLayout layout_phone;
  private RelativeLayout layout_group;

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

    layout_status = (RelativeLayout) getView().findViewById(R.id.layout_status);
    layout_phone = (RelativeLayout) getView().findViewById(R.id.layout_phone);
    layout_group = (RelativeLayout) getView().findViewById(R.id.layout_member);

    profileStatus = (EditText) getView().findViewById(R.id.profile_status);
    xCloseButton = (ImageView) getView().findViewById(R.id.profile_close);
    imageText = (TextView) getView().findViewById(R.id.image_text);
    profilePhone = (TextView) getView().findViewById(R.id.profile_phone);
    groupMember        = (ListView)            getView().findViewById(R.id.selected_contacts_list);
    profilePhone.setText(profileId);
    profilePicture = (ThumbnailView) getView().findViewById(R.id.profile_picture);
    phoneCall = (ImageView) getView().findViewById(R.id.phone_call);
    recipient = recipients.getPrimaryRecipient();

    scrollView = (ScrollView) getView().findViewById(R.id.scrollView);
    if(!isGroup) {
      ImageSlide slide = ProfileAccessor.getProfileAsImageSlide(getActivity(), masterSecret, profileId);
      if (slide != null && !isMyProfile) {
        if (masterSecret != null) {
          try {
            profilePicture.setImageResource(slide, masterSecret);
          } catch (IllegalStateException e) {
            Log.w("GDATA", "Unable to load profile image");
          }
          profileStatus.setText(ProfileAccessor.getProfileStatusForRecepient(getActivity(), profileId), TextView.BufferType.EDITABLE);
          profileStatus.setEnabled(false);
          imageText.setText(recipient.getName());
        }
        profilePicture.setThumbnailClickListener(new ThumbnailClickListener());
      } else if (ProfileAccessor.getMyProfilePicture(getActivity()).hasImage() && isMyProfile) {
        profileStatus.setText(ProfileAccessor.getProfileStatus(getActivity()), TextView.BufferType.EDITABLE);
        imageText.setText(getString(R.string.MediaPreviewActivity_you));
        profilePicture.setThumbnailClickListener(new ThumbnailClickListener());
        if((ProfileAccessor.getMyProfilePicture(getActivity()).getUri()+"").equals("")) {
          profilePicture.setImageBitmap(ContactPhotoFactory.getDefaultContactPhoto(getActivity()));
        } else {
          profilePicture.setImageResource(ProfileAccessor.getMyProfilePicture(getActivity()));
        }
      } else {
        imageText.setText(recipient.getName());
        profilePicture.setImageBitmap(recipient.getContactPhoto());
      }
      layout_group.setVisibility(View.GONE);
    } else {
      final String groupName = recipient.getName();
      final Bitmap avatar = recipient.getContactPhoto();
      final String encodedGroupId = recipient.getNumber();

      if (encodedGroupId != null) {
        try {
          groupId = GroupUtil.getDecodedId(encodedGroupId);
        } catch (IOException ioe) {
          groupId = null;
        }
      }
      final GroupDatabase db = DatabaseFactory.getGroupDatabase(getActivity());
      final Recipients recipients = db.getGroupMembers(groupId, false);
      if (recipients != null) {
        final List<Recipient> recipientList = recipients.getRecipientsList();
        if (recipientList != null) {
          if (existingContacts == null)
            existingContacts = new HashSet<>(recipientList.size());
          existingContacts.addAll(recipientList);
        }
        if (recipientList != null) {
          if (existingContacts == null)
            existingContacts = new HashSet<>(recipientList.size());
          existingContacts.addAll(recipientList);
        }

        SelectedRecipientsAdapter adapter = new SelectedRecipientsAdapter(getActivity(), android.R.id.text1, new ArrayList<SelectedRecipientsAdapter.RecipientWrapper>());
        adapter.clear();

        if (existingContacts != null) {
          for (Recipient contact : existingContacts) {
            adapter.add(new SelectedRecipientsAdapter.RecipientWrapper(contact, false));
          }
        }
        groupMember.setAdapter(adapter);
        adapter.notifyDataSetChanged();
      }
      if (avatar != null) {
        profilePicture.setImageBitmap(avatar);
      }
    imageText.setText(groupName);
      layout_status.setVisibility(View.GONE);
      layout_phone.setVisibility(View.GONE);
      GUtil.setListViewHeightBasedOnChildren(groupMember);
    }
    final ImageView profileStatusEdit = (ImageView) getView().findViewById(R.id.profile_status_edit);
    ImageView profileImageEdit = (ImageView) getView().findViewById(R.id.profile_picture_edit);
    ImageView profileImageDelete = (ImageView) getView().findViewById(R.id.profile_picture_delete);
    if(!isMyProfile) {
      profileStatusEdit.setVisibility(View.GONE);
      profileImageDelete.setVisibility(View.GONE);
      if(!isGroup) {
        profileImageEdit.setVisibility(View.GONE);
        profileImageDelete.setVisibility(View.GONE);
      } else {
        profileImageEdit.setVisibility(View.VISIBLE);
        profileImageEdit.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View view) {
            handleEditPushGroup();
          }
        });
      }
    } else {
      profileStatusEdit.setVisibility(View.VISIBLE);
      profileImageEdit.setVisibility(View.VISIBLE);
      profileImageDelete.setVisibility(View.VISIBLE);
      profileStatusEdit.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          profileStatus.setEnabled(!profileStatus.isEnabled());
          if (!profileStatus.isEnabled()) {
            ProfileAccessor.setProfileStatus(getActivity(), profileStatus.getText() + "");
            hasChanged = true;
            profileStatusEdit.setImageDrawable(getResources().getDrawable(android.R.drawable.ic_menu_edit));
          } else {
            profileStatusEdit.setImageDrawable(getResources().getDrawable(android.R.drawable.ic_menu_save));
          }
        }
      });
      profileImageEdit.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          hasChanged = true;
          AttachmentManager.selectImage(getActivity(), PICK_IMAGE);
        }
      });
      profileImageDelete.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          hasChanged = true;
        ProfileAccessor.deleteMyProfilePicture(getActivity());
        refreshLayout();
        }
      });
    }
    xCloseButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        getActivity().finish();
      }
    });
    phoneCall.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        handleDial(recipient);
      }
    });
    final RelativeLayout scrollContainer = (RelativeLayout) getView().findViewById(R.id.scrollContainer);
    final LinearLayout mainLayout = (LinearLayout) getView().findViewById(R.id.mainlayout);
    scrollView.setSmoothScrollingEnabled(true);
    ViewTreeObserver vto = scrollView.getViewTreeObserver();
    vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
      public void onGlobalLayout() {
        scrollView.scrollTo(0, mainLayout.getTop() - PADDING_TOP);
      }
    });
    scrollView.getViewTreeObserver().addOnScrollChangedListener(new ViewTreeObserver.OnScrollChangedListener() {

      @Override
      public void onScrollChanged() {
        if (BuildConfig.VERSION_CODE >= 11) {
          scrollContainer.setBackgroundColor(Color.WHITE);
          scrollContainer.setAlpha((float) ((1000.0 / scrollContainer.getHeight()) * scrollView.getHeight()));
        }
        if ((mainLayout.getTop() - scrollView.getHeight()) > scrollView.getScrollY()) {
          getActivity().finish();
        }
        if (mainLayout.getTop() * 2 < scrollView.getScrollY() + PADDING_TOP) {
          getActivity().finish();
        }
      }
    });
    scrollContainer.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
      //  getActivity().finish();
      }
    });

  }
  private static final int GROUP_EDIT = 5;
  private void handleEditPushGroup() {
    Intent intent = new Intent(getActivity(), GroupCreateActivity.class);
    intent.putExtra(GroupCreateActivity.MASTER_SECRET_EXTRA, masterSecret);
    intent.putExtra(GroupCreateActivity.GROUP_RECIPIENT_EXTRA, recipients.getPrimaryRecipient().getRecipientId());
    startActivityForResult(intent, GROUP_EDIT);
  }
  private void handleDial(Recipient recipient) {
    try {
      if (recipient == null) return;

      Intent dialIntent = new Intent(Intent.ACTION_DIAL,
          Uri.parse("tel:" + recipient.getNumber()));
      startActivity(dialIntent);
    } catch (ActivityNotFoundException anfe) {
      Dialogs.showAlertDialog(getActivity(),
          getString(R.string.ConversationActivity_calls_not_supported),
          getString(R.string.ConversationActivity_this_device_does_not_appear_to_support_dial_actions));
    }
  }
  @Override
  public void onResume() {
    super.onResume();
    refreshLayout();
  }
  public static final String RECIPIENTS_EXTRA = "recipients";
  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
  }
  private void initializeResources() {
    this.masterSecret    = getActivity().getIntent().getParcelableExtra("master_secret");
    this.profileId    = getActivity().getIntent().getStringExtra("profile_id");
    this.isGroup =getActivity().getIntent().getBooleanExtra("is_group", false);
    this.recipients = RecipientFactory.getRecipientsForIds(getActivity(), getActivity().getIntent().getLongArrayExtra(RECIPIENTS_EXTRA), true);
    selectedContacts = new HashSet<Recipient>();
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
    if(slide != null) {
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
    }
  }
}

  @Override
  public void onPause() {
    super.onPause();
    if(hasChanged) {
      ProfileAccessor.sendProfileUpdateToAllWithThreadContacts(getActivity(), profileId, masterSecret, true);
    }
  }
}
