/**
 * Copyright (C) 2014 Open Whisper Systems
 * <p/>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.Image;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.util.Pair;
import android.view.ContextThemeWrapper;
import android.view.Display;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.AlertDialogWrapper;
import com.google.protobuf.ByteString;

import org.thoughtcrime.securesms.components.PushRecipientsPanel;
import org.thoughtcrime.securesms.components.ThumbnailView;
import org.thoughtcrime.securesms.contacts.ContactAccessor;
import org.thoughtcrime.securesms.contacts.ContactPhotoFactory;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.NotInDirectoryException;
import org.thoughtcrime.securesms.database.TextSecureDirectory;
import org.thoughtcrime.securesms.mms.AttachmentManager;
import org.thoughtcrime.securesms.mms.AttachmentTypeSelectorAdapter;
import org.thoughtcrime.securesms.mms.ImageSlide;
import org.thoughtcrime.securesms.mms.OutgoingGroupMediaMessage;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.mms.ProfileImageTypeSelectorAdapter;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.Dialogs;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.ProgressDialogAsyncTask;
import org.thoughtcrime.securesms.util.SelectedRecipientsAdapter;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.textsecure.api.util.InvalidNumberException;
import org.whispersystems.textsecure.internal.push.PushMessageProtos;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import de.gdata.messaging.util.GDataPreferences;
import de.gdata.messaging.util.GService;
import de.gdata.messaging.util.GUtil;
import de.gdata.messaging.util.ProfileAccessor;
import ws.com.google.android.mms.MmsException;

public class ProfileFragment extends Fragment {

    private static final int PADDING_TOP = 0;
    private MasterSecret masterSecret;
    private GDataPreferences gDataPreferences;
    private String profileId = "";

    private static final int PICK_IMAGE = 1;
    private static final int TAKE_PHOTO = 2;
    private String profileStatusString = "";
    private AutoCompleteTextView profileStatus;
    private ImageView xCloseButton;
    private ImageView phoneCall;
    private TextView imageText;
    private TextView statusDate;
    private TextView profilePhone;
    private ThumbnailView profilePicture;
    private Recipient recipient;
    private ScrollView scrollView;
    private LinearLayout historyLayout;
    private boolean hasChanged = false;
    private boolean isGroup;
    private Recipients recipients;
    private ListView groupMember;
    private Set<Recipient> selectedContacts;
    private Set<Recipient> existingContacts = null;

    private ProfileImageTypeSelectorAdapter attachmentAdapter;
    private static final int GROUP_EDIT = 5;
    private byte[] groupId;
    private RelativeLayout layout_status;
    private RelativeLayout layout_phone;
    private RelativeLayout layout_group;
    private boolean hasLeft = false;
    private HorizontalScrollView historyScrollView;
    private TextView historyContentTextView;
    private RelativeLayout historyLine;
    private TextView profileHeader;
    private PushRecipientsPanel recipientsPanel;
    private boolean keyboardIsVisible = false;
    private boolean contactsHaveChanged = false;
    private Button leaveGroup;
    private long threadId = -1;
    private int heightMemberList = 0;

    private ViewTreeObserver.OnScrollChangedListener onScrollChangeListener;
    private SeekBar seekBarFont;
    private FloatingActionButton floatingActionColorButton;
    private RelativeLayout layoutColor;
    private CheckBox chatPartnersColor;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
        return GUtil.setFontForFragment(getActivity(), inflater.inflate(R.layout.profile_fragment, container, false));
    }

    @Override
    public void onActivityCreated(Bundle bundle) {
        super.onActivityCreated(bundle);
        initializeResources();
        refreshLayout();
        this.getView().setFocusableInTouchMode(true);
        this.getView().setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    finishAndSave();
                    return true;
                }
                return false;
            }
        });
    }

    private void refreshLayout() {
        gDataPreferences = new GDataPreferences(getActivity());
        boolean isMyProfile = (GUtil.numberToLong(gDataPreferences.getE164Number() + "") + "").contains(GUtil
                .numberToLong(profileId) + "");

        layout_status = (RelativeLayout) getView().findViewById(R.id.layout_status);
        layout_phone = (RelativeLayout) getView().findViewById(R.id.layout_phone);
        layout_group = (RelativeLayout) getView().findViewById(R.id.layout_member);

        statusDate = (TextView) getView().findViewById(R.id.profile__date);
        leaveGroup = (Button) getView().findViewById(R.id.buttonLeaveGroup);
        profileHeader = (TextView) getView().findViewById(R.id.profile_header);
        profileStatus = (AutoCompleteTextView) getView().findViewById(R.id.profile_status);
        xCloseButton = (ImageView) getView().findViewById(R.id.profile_close);
        imageText = (TextView) getView().findViewById(R.id.image_text);
        profilePhone = (TextView) getView().findViewById(R.id.profile_phone);
        groupMember = (ListView) getView().findViewById(R.id.selected_contacts_list);
        historyLayout = (LinearLayout) getView().findViewById(R.id.historylayout);
        historyContentTextView = (TextView) getView().findViewById(R.id.history_content);
        historyScrollView = (HorizontalScrollView) getView().findViewById(R.id.horizontal_scroll);
        recipientsPanel = (PushRecipientsPanel) getView().findViewById(R.id.recipients);
        profilePhone.setText(profileId);
        profilePicture = (ThumbnailView) getView().findViewById(R.id.profile_picture);
        phoneCall = (ImageView) getView().findViewById(R.id.phone_call);
        (getView().findViewById(R.id.contacts_button)).setOnClickListener(new AddRecipientButtonListener());
        historyLine = (RelativeLayout) getView().findViewById(R.id.layout_history);
        recipient = recipients.getPrimaryRecipient();
        attachmentAdapter = new ProfileImageTypeSelectorAdapter(getActivity());
        scrollView = (ScrollView) getView().findViewById(R.id.scrollView);
        seekBarFont = (SeekBar)getView().findViewById(R.id.seekbar_font);
        chatPartnersColor = (CheckBox) getView().findViewById(R.id.enabled_chat_partners_color);
        layoutColor = (RelativeLayout)getView().findViewById(R.id.layout_color);
        floatingActionColorButton = (FloatingActionButton) getView().findViewById(R.id.fab_new_color);
        final ImageView profileStatusEdit = (ImageView) getView().findViewById(R.id.profile_status_edit);

        if (!isGroup) {
            ImageSlide slide = ProfileAccessor.getProfileAsImageSlide(getActivity(), masterSecret, profileId);
            if (slide != null && !isMyProfile) {
                if (masterSecret != null) {
                    try {
                        profilePicture.setImageResource(slide, masterSecret);
                    } catch (IllegalStateException e) {
                        Log.w("GDATA", "Unable to load profile image");
                    }
                    profileStatus.setText(ProfileAccessor.getProfileStatusForRecepient(getActivity(), profileId),
                            TextView.BufferType.EDITABLE);
                    profileStatus.setEnabled(false);
                    layout_status.setOnTouchListener(new View.OnTouchListener() {
                        @Override
                        public boolean onTouch(View view, MotionEvent motionEvent) {
                            profileStatusEdit.performClick();
                            return false;
                        }
                    });
                    statusDate.setText(GUtil.getDate(
                            ProfileAccessor.getProfileUpdateTimeForRecepient(getActivity(), profileId),
                            "dd.MM.yyyy hh:mm:ss"));
                    imageText.setText(recipient.getName());
                }
                profilePicture.setThumbnailClickListener(new ThumbnailClickListener());
            } else if (ProfileAccessor.getMyProfilePicture(getActivity()).hasImage() && isMyProfile) {
                profileStatus.setText(ProfileAccessor.getProfileStatus(getActivity()), TextView.BufferType.EDITABLE);
                imageText.setText(getString(R.string.MediaPreviewActivity_you));
                initColorSeekbar();
                profilePicture.setThumbnailClickListener(new ThumbnailClickListener());
                if ((ProfileAccessor.getMyProfilePicture(getActivity()).getUri() + "").equals("")) {
                    profilePicture.setImageBitmap(ContactPhotoFactory.getDefaultContactPhoto(getActivity()));
                } else {
                    profilePicture.setImageResource(ProfileAccessor.getMyProfilePicture(getActivity()));
                }
                historyLine.setVisibility(View.GONE);
            } else {
                imageText.setText(recipient.getName());
                profilePicture.setImageBitmap(recipient.getContactPhoto());
            }
            layout_group.setVisibility(View.GONE);
        } else {
            String groupName = recipient.getName();
            Bitmap avatar = recipient.getContactPhoto();
            String encodedGroupId = recipient.getNumber();
            if (encodedGroupId != null) {
                try {
                    groupId = GroupUtil.getDecodedId(encodedGroupId);
                } catch (IOException ioe) {
                    groupId = null;
                }
            }
            GroupDatabase db = DatabaseFactory.getGroupDatabase(getActivity());
            Recipients recipients = db.getGroupMembers(groupId, false);

            recipientsPanel.setPanelChangeListener(new PushRecipientsPanel.RecipientsPanelChangedListener() {
                @Override
                public void onRecipientsPanelUpdate(Recipients recipients) {
                    Log.w("GDATA", "onRecipientsPanelUpdate received.");
                    if (recipients != null) {
                        addAllSelectedContacts(recipients.getRecipientsList());
                        syncAdapterWithSelectedContacts();
                    }
                }
            });

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

                SelectedRecipientsAdapter adapter = new SelectedRecipientsAdapter(getActivity(), android.R.id.text1,
                        new ArrayList<SelectedRecipientsAdapter.RecipientWrapper>());
                adapter.clear();

                if (existingContacts != null) {
                    for (Recipient contact : existingContacts) {
                        adapter.add(new SelectedRecipientsAdapter.RecipientWrapper(contact, false));
                    }
                }
                adapter.setMasterSecret(masterSecret);
                adapter.setThreadId(threadId);
                groupMember.setAdapter(adapter);
                adapter.notifyDataSetChanged();
            }
            if (avatar != null) {
                profilePicture.setVisibility(View.GONE);
                getView().findViewById(R.id.profile_picture_group).setVisibility(View.VISIBLE);
                scaleImage((ImageView) getView().findViewById(R.id.profile_picture_group), avatar);
            }
            imageText.setText(groupName);
            profileStatus.setText(groupName);
            layout_phone.setVisibility(View.GONE);

            leaveGroup.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if(isActiveGroup()) {
                        handleLeavePushGroup();
                    } else {
                        handleDeleteThread();
                    }
                }
            });
            if(!isActiveGroup()) {
                leaveGroup.setText(getString(R.string.conversation__menu_delete_thread));
            }

            heightMemberList = GUtil.setListViewHeightBasedOnChildren(groupMember);
        }
        ImageView profileImageEdit = (ImageView) getView().findViewById(R.id.profile_picture_edit);
        ImageView profileImageDelete = (ImageView) getView().findViewById(R.id.profile_picture_delete);
        if (!isMyProfile && !isGroup) {
            profileStatusEdit.setVisibility(View.GONE);
            profileImageDelete.setVisibility(View.GONE);
            profileImageEdit.setVisibility(View.GONE);
        } else {
            if (isGroup) {
                profileImageDelete.setVisibility(View.GONE);
                profileHeader.setText(getString(R.string.group_title));
            } else {
                profileImageDelete.setVisibility(View.VISIBLE);
            }
            profileStatusEdit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    profileStatus.setEnabled(!profileStatus.isEnabled());
                    if (!profileStatus.isEnabled()) {
                        hasChanged = true;
                        hasLeft = false;
                        profileStatusEdit.setImageDrawable(getResources().getDrawable(R.drawable.ic_content_edit));

                        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(
                                Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(profileStatus.getWindowToken(), 0);
                        if (isGroup) {
                            new UpdateWhisperGroupAsyncTask().execute();
                        } else {
                            ProfileAccessor.setProfileStatus(getActivity(), profileStatus.getText() + "");
                        }
                    } else {
                        profileStatusEdit.setImageDrawable(getResources().getDrawable(R.drawable.ic_send_sms_gdata));
                        profileStatus.showDropDown();
                        profileStatus.requestFocus();
                    }
                }
            });
            profileImageDelete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    hasChanged = true;
                    hasLeft = false;
                    ProfileAccessor.deleteMyProfilePicture(getActivity());
                    refreshLayout();
                }
            });
            profileImageEdit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    hasChanged = true;
                    hasLeft = false;
                    handleAddAttachment();
                }
            });
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(getActivity(),
                android.R.layout.simple_dropdown_item_1line, getResources().getStringArray(R.array.status_suggestions));
        profileStatus.setAdapter(adapter);
        profileStatus.setCompletionHint(getString(R.string.status_hint));
        profileStatus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                profileStatus.dismissDropDown();
            }
        });
        profileStatus.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                profileStatus.setEnabled(!profileStatus.isEnabled());
                if (!profileStatus.isEnabled()) {
                    hasChanged = true;
                    hasLeft = false;
                    profileStatusEdit.setImageDrawable(getResources().getDrawable(R.drawable.ic_content_edit));

                    InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(
                            Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(profileStatus.getWindowToken(), 0);
                    if (isGroup) {
                        new UpdateWhisperGroupAsyncTask().execute();
                    } else {
                        ProfileAccessor.setProfileStatus(getActivity(), profileStatus.getText() + "");
                    }
                }
            }
            });
        if(!isMyProfile) {
            setMediaHistoryImages();
        }
        xCloseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finishAndSave();
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
        scrollContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // getActivity().finish();
            }
        });
        onScrollChangeListener = new ViewTreeObserver.OnScrollChangedListener() {

            @Override
            public void onScrollChanged() {
                if (BuildConfig.VERSION_CODE >= 11) {
                    scrollContainer.setBackgroundColor(Color.WHITE);
                    scrollContainer.setAlpha((float) ((1000.0 / scrollContainer.getHeight()) * scrollView.getHeight()));
                }
                int keyboardHeight = 150;
                int paddingBottom = 250;
                int scrollViewHeight = scrollView.getHeight();
                if(getActivity().getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    scrollViewHeight = 2*scrollViewHeight;
                }
                int heightDiff = scrollView.getRootView().getHeight() - scrollView.getHeight();
                if (pxToDp(heightDiff) > keyboardHeight) {
                    keyboardIsVisible = true;
                } else {
                    keyboardIsVisible = false;
                }
                if (!keyboardIsVisible) {
                    if ((mainLayout.getTop() - scrollViewHeight) > scrollView.getScrollY() - pxToDp(paddingBottom)) {
                        finishAndSave();
                    }
                    if ((scrollViewHeight + (heightMemberList)) < (scrollView.getScrollY() - mainLayout.getTop())) {
                        finishAndSave();
                    }
                }
            }
        };
        scrollView.getViewTreeObserver().addOnScrollChangedListener(onScrollChangeListener);
        scrollView.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {

                ViewTreeObserver observer = scrollView.getViewTreeObserver();
                observer.addOnScrollChangedListener(onScrollChangeListener);
                return false;
            }

        });
    }
    private void handleLeavePushGroup() {
        if (recipients == null) {
            Toast.makeText(getActivity(), getString(R.string.ConversationActivity_invalid_recipient),
                    Toast.LENGTH_LONG).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(getString(R.string.ConversationActivity_leave_group));
        builder.setIcon(Dialogs.resolveIcon(getActivity(), R.attr.dialog_info_icon));
        builder.setCancelable(true);
        builder.setMessage(getString(R.string.ConversationActivity_are_you_sure_you_want_to_leave_this_group));
        builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Context self = getActivity();
                try {
                    byte[] groupId = GroupUtil.getDecodedId(recipients.getPrimaryRecipient().getNumber());
                    DatabaseFactory.getGroupDatabase(self).setActive(groupId, false);

                    PushMessageProtos.PushMessageContent.GroupContext context = PushMessageProtos.PushMessageContent.GroupContext.newBuilder()
                            .setId(ByteString.copyFrom(groupId))
                            .setType(PushMessageProtos.PushMessageContent.GroupContext.Type.QUIT)
                            .build();

                    OutgoingGroupMediaMessage outgoingMessage = new OutgoingGroupMediaMessage(self, recipients,
                            context, null);
                    MessageSender.send(self, masterSecret, outgoingMessage, threadId, false);
                    DatabaseFactory.getGroupDatabase(self).remove(groupId, TextSecurePreferences.getLocalNumber(self));
                } catch (IOException e) {
                    Toast.makeText(self, R.string.ConversationActivity_error_leaving_group, Toast.LENGTH_LONG).show();
                }
                getActivity().finish();
            }
        });

        builder.setNegativeButton(R.string.no, null);
        builder.show();
    }
    private boolean isActiveGroup() {
        try {
            byte[] groupId = GroupUtil.getDecodedId(recipients.getPrimaryRecipient().getNumber());
            GroupDatabase.GroupRecord record = DatabaseFactory.getGroupDatabase(getActivity()).getGroup(groupId);

            return record != null && record.isActive();
        } catch (IOException e) {
            Log.w("ConversationActivity", e);
            return false;
        }
    }
    private void handleDeleteThread() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.ConversationActivity_delete_thread_confirmation);
        builder.setIcon(Dialogs.resolveIcon(getActivity(), R.attr.dialog_alert_icon));
        builder.setCancelable(true);
        builder.setMessage(R.string.ConversationActivity_are_you_sure_that_you_want_to_permanently_delete_this_conversation_question);
        builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (threadId > 0) {
                    DatabaseFactory.getThreadDatabase(getActivity()).deleteConversation(threadId);
                    getActivity().finish();
                }
            }
        });

        builder.setNegativeButton(R.string.no, null);
        builder.show();
    }
    private class AddRecipientButtonListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            contactsHaveChanged = true;
            hasLeft = false;
            Intent intent = new Intent(getActivity(), PushContactSelectionActivity.class);
            if (existingContacts != null) intent.putExtra(PushContactSelectionActivity.PUSH_ONLY_EXTRA, true);
            startActivityForResult(intent, PICK_CONTACT);
        }
    }
    private static final int PICK_CONTACT = 1;
    @Override
    public void onActivityResult(int reqCode, int resultCode, Intent data) {
         switch (reqCode) {
            case PICK_CONTACT:
                if (data == null || resultCode != Activity.RESULT_OK)
                    return;
                List<ContactAccessor.ContactData> selected = data.getParcelableArrayListExtra("contacts");
                for (ContactAccessor.ContactData contact : selected) {
                    for (ContactAccessor.NumberData numberData : contact.numbers) {

                        Recipient recipient = RecipientFactory.getRecipientsFromString(getActivity(), numberData.number, false)
                                .getPrimaryRecipient();

                        if (!selectedContacts.contains(recipient)
                                && (existingContacts == null || !existingContacts.contains(recipient))) {
                            addSelectedContact(recipient);
                        }
                    }
                }
                syncAdapterWithSelectedContacts();
                break;
        }
    }
    private void initColorSeekbar() {
        layoutColor.setVisibility(View.VISIBLE);
        chatPartnersColor.setChecked(gDataPreferences.getChatPartnersColorEnabled());
        chatPartnersColor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                gDataPreferences.saveChatPartnersColorEnabled(chatPartnersColor.isChecked());
            }
        });

        seekBarFont.setMax(256 * 5 - 2);
        int oldColor = gDataPreferences.getCurrentColorHex();
        seekBarFont.setProgress(gDataPreferences.getColorProgress());
        floatingActionColorButton.setRippleColor(oldColor);
        seekBarFont.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            int color = Color.BLUE;
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if(fromUser){
                    int r = 0;
                    int g = 0;
                    int b = 0;

                    if(progress < 256){
                        b = progress;
                    } else if(progress < 256*2) {
                        g = progress%256;
                        b = 256 - progress%256;
                    } else if(progress < 256*3) {
                        g = 255;
                        b = progress%256;
                    } else if(progress < 256*4) {
                        r = progress%256;
                        g = 256 - progress%256;
                        b = 256 - progress%256;
                    } else if(progress < 256*5) {
                        r = 255;
                        g = 0;
                        b = progress%256;
                    } else if(progress < 256*6) {
                        r = 255;
                        g = progress%256;
                        b = 256 - progress%256;
                    } else if(progress < 256*7) {
                        r = 255;
                        g = 255;
                        b = progress%256;
                    }
                    color = Color.argb(255, r, g, b);
                    floatingActionColorButton.setRippleColor(color);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                gDataPreferences.saveCurrentColorValue(color);
                gDataPreferences.saveCurrentSeekBarColorProgress(seekBar.getProgress());
                hasChanged = true;
            }
        });
    }
    private void setMediaHistoryImages() {
        String[] mediaHistoryUris = gDataPreferences.getMediaUriHistoryForId(GUtil.numberToLong(recipient.getNumber()));

        if(mediaHistoryUris.length > 0) {

            while (historyLayout.getChildCount() >= 1) {
                historyLayout.removeView(historyLayout.getChildAt(0));
            }

            for (int i = 0; i < mediaHistoryUris.length; i++) {
                Slide mediaHistorySlide = ProfileAccessor.getSlideForUri(getActivity(), masterSecret, mediaHistoryUris[i]);
                if (mediaHistorySlide != null && masterSecret != null && !(mediaHistorySlide.getUri() + "").equals("")) {
                    ThumbnailView historyMedia = new ThumbnailView(getActivity());

                    android.widget.LinearLayout.LayoutParams layoutParams = new android.widget.LinearLayout.LayoutParams(
                            dpToPx(100), dpToPx(100));
                    historyMedia.setBackgroundColor(getResources().getColor(R.color.conversation_list_divider_light));
                    layoutParams.setMargins(5, 0, 5, 0);

                    historyMedia.setLayoutParams(layoutParams);
                    ProfileAccessor.buildEncryptedPartGlideRequest(mediaHistorySlide, masterSecret).into(historyMedia);
                    historyLayout.addView(historyMedia);
                    historyMedia.setSlide(mediaHistorySlide);
                }
            }
            LinearLayout ll = ((LinearLayout) historyScrollView.getChildAt(0));
            if (ll.getChildCount() > 0 && historyContentTextView != null && mediaHistoryUris != null && mediaHistoryUris.length > 0) {
                historyContentTextView.setVisibility(View.GONE);
            }
            for (int l = 0; l < ll.getChildCount(); l++) {
                ((ThumbnailView) ll.getChildAt(l)).setOnClickListener(mediaOnClickListener);
            }
        }
    }
    View.OnClickListener mediaOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            final Slide slide = ((ThumbnailView) view).getSlide();
            if (slide != null && MediaPreviewActivity.isContentTypeSupported(slide.getContentType())) {
                Intent intent = new Intent(getActivity(), MediaPreviewActivity.class);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.setDataAndType(slide.getUri(), slide.getContentType());
                intent.putExtra(MediaPreviewActivity.MASTER_SECRET_EXTRA, masterSecret);
                intent.putExtra(MediaPreviewActivity.RECIPIENT_EXTRA, recipient.getRecipientId());
                intent.putExtra(MediaPreviewActivity.DATE_EXTRA, System.currentTimeMillis());
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
    };
    private void fireIntent(Slide slide) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setDataAndType(PartAuthority.getPublicPartUri(slide.getUri()), slide.getContentType());
        try {
            getActivity().startActivity(intent);
        } catch (ActivityNotFoundException anfe) {
            Log.w("GDATA", anfe.getMessage() + " - " + slide.getContentType());
        }
    }

    private void addAllSelectedContacts(Collection<Recipient> contacts) {
        for (Recipient contact : contacts) {
            addSelectedContact(contact);
        }
    }
    private void syncAdapterWithSelectedContacts() {
        SelectedRecipientsAdapter adapter = (SelectedRecipientsAdapter) groupMember.getAdapter();
        adapter.clear();
        for (Recipient contact : selectedContacts) {
            adapter.add(new SelectedRecipientsAdapter.RecipientWrapper(contact, true));
        }
        if (existingContacts != null) {
            for (Recipient contact : existingContacts) {
                adapter.add(new SelectedRecipientsAdapter.RecipientWrapper(contact, false));
            }
        }
        adapter.notifyDataSetChanged();
        heightMemberList = GUtil.setListViewHeightBasedOnChildren(groupMember);
    }
    private void addSelectedContact(Recipient contact) {
        final boolean isPushUser = isActiveInDirectory(getActivity(), contact);
        if (existingContacts != null && !isPushUser) {
            Toast.makeText(getActivity(),
                    R.string.GroupCreateActivity_cannot_add_non_push_to_existing_group,
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (!selectedContacts.contains(contact) && (existingContacts == null || !existingContacts.contains(contact)))
            selectedContacts.add(contact);
    }
    private static boolean isActiveInDirectory(Context context, Recipient recipient) {
        try {
            if (!TextSecureDirectory.getInstance(context).isActiveNumber(Util.canonicalizeNumber(context, recipient.getNumber()))) {
                return false;
            }
        } catch (NotInDirectoryException e) {
            return false;
        } catch (InvalidNumberException e) {
            return false;
        }
        return true;
    }
    private void finishAndSave() {
        hasLeft = true;
        if (hasChanged || contactsHaveChanged) {
            if(isGroup) {
                new UpdateWhisperGroupAsyncTask().execute();
            } else {
                ProfileAccessor.sendProfileUpdateToAllContacts(getActivity(), masterSecret);
            }
            hasChanged = false;
            contactsHaveChanged = false;
        }
        getActivity().finish();
    }

    private int dpToPx(int dp) {
        float density = getActivity().getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }
    private int pxToDp(int px) {
        float density = getActivity().getResources().getDisplayMetrics().density;
        return Math.round((float) px / density);
    }
    private class AttachmentTypeListener implements DialogInterface.OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            addAttachment(attachmentAdapter.buttonToCommand(which));
        }
    }

    private void handleAddAttachment() {
        AlertDialog.Builder builder = new AlertDialog.Builder(new ContextThemeWrapper(getActivity(),
                R.style.GSecure_Light_Dialog));
        builder.setIcon(R.drawable.ic_dialog_attach);
        builder.setTitle(R.string.ConversationActivity_add_attachment);
        builder.setAdapter(attachmentAdapter, new AttachmentTypeListener());
        builder.show();
    }

    private void addAttachment(int type) {
        Log.w("ComposeMessageActivity", "Selected: " + type);
        switch (type) {
            case AttachmentTypeSelectorAdapter.ADD_IMAGE:
                AttachmentManager.selectImage(getActivity(), PICK_IMAGE);
                break;
            case AttachmentTypeSelectorAdapter.TAKE_PHOTO:
                AttachmentManager.takePhoto(getActivity(), TAKE_PHOTO);
                break;
        }
    }
    private void handleDial(Recipient recipient) {
        try {
            if (recipient == null) return;

            Intent dialIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + recipient.getNumber()));
            startActivity(dialIntent);
        } catch (ActivityNotFoundException anfe) {
            Dialogs.showAlertDialog(getActivity(), getString(R.string.ConversationActivity_calls_not_supported),
                    getString(R.string.ConversationActivity_this_device_does_not_appear_to_support_dial_actions));
        }
    }

    @Override
    public void onResume() {
        if(hasChanged) {
            if(isGroup && !contactsHaveChanged) {
                new UpdateWhisperGroupAsyncTask().execute();
                hasChanged = false;
                new FillExistingGroupInfoAsyncTask().execute();
            }
            refreshLayout();
        }
        super.onResume();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if(profilePicture!= null) {
            if(profilePicture.getForeground() != null) {
                if (profilePicture.getForeground() instanceof BitmapDrawable) {
                    BitmapDrawable bitmapDrawable = (BitmapDrawable) profilePicture.getForeground();
                    Bitmap bitmap = bitmapDrawable.getBitmap();
                    bitmap.recycle();
                }
            }
            profilePicture.setImageBitmap(null);
            profilePicture.setForeground(null);
            profilePicture.setBackground(null);
            System.gc();
        }
    }

    public static final String RECIPIENTS_EXTRA = "recipients";

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
    }

    private void initializeResources() {
        this.masterSecret = getActivity().getIntent().getParcelableExtra("master_secret");
        this.profileId = getActivity().getIntent().getStringExtra("profile_id");
        this.isGroup = getActivity().getIntent().getBooleanExtra("is_group", false);
        threadId = getActivity().getIntent().getLongExtra(ConversationActivity.THREAD_ID_EXTRA, -1);
        this.recipients = RecipientFactory.getRecipientsForIds(getActivity(), getActivity().getIntent()
                .getLongArrayExtra(RECIPIENTS_EXTRA), true);
        selectedContacts = new HashSet<Recipient>();
    }
        @Override
    public void onPause() {
        super.onPause();
        if (hasChanged && hasLeft && !isGroup) {
            ProfileAccessor.sendProfileUpdateToAllContacts(getActivity(), masterSecret);
            hasChanged = false;
        }
            if(hasChanged && hasLeft && isGroup) {
                new UpdateWhisperGroupAsyncTask().execute();
                hasChanged = false;
                contactsHaveChanged = false;
            }
    }

    private class UpdateWhisperGroupAsyncTask extends AsyncTask<Void,Void,Pair<Long,Recipients>> {
        private long RES_BAD_NUMBER = -2;
        private long RES_MMS_EXCEPTION = -3;
        @Override
        protected Pair<Long, Recipients> doInBackground(Void... params) {
            byte[] avatarBytes = null;
            final Bitmap bitmap;
            if (ProfileActivity.getAvatarTemp() == null) {
                final GroupDatabase db = DatabaseFactory.getGroupDatabase(getActivity());
                GroupDatabase.GroupRecord group = db.getGroup(groupId);
                bitmap = getExistingBitmapForGroup(group);
            } else {
                bitmap = ProfileActivity.getAvatarTemp();
            }
            if (bitmap != null) {
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
                avatarBytes = stream.toByteArray();
            }
            try {
                Set<Recipient> unionContacts = new HashSet<Recipient>(selectedContacts);
                unionContacts.addAll(existingContacts);
                return handleUpdatePushGroup(groupId, profileStatusString, avatarBytes, unionContacts);
            } catch (MmsException e) {
                Log.w("GDATA", e);
                return new Pair<Long,Recipients>(RES_MMS_EXCEPTION, null);
            } catch (InvalidNumberException e) {
                Log.w("GDATA", e);
                return new Pair<Long,Recipients>(RES_BAD_NUMBER, null);
            }
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            profileStatusString = (profileStatus != null && profileStatus.getText() != null) ? profileStatus.getText().toString(): "";
        }

        @Override
        protected void onPostExecute(Pair<Long, Recipients> groupInfo) {
            final long threadId = groupInfo.first;
             if (threadId == RES_BAD_NUMBER) {
                Toast.makeText(getActivity(), R.string.GroupCreateActivity_contacts_invalid_number, Toast.LENGTH_LONG).show();
            } else if (threadId == RES_MMS_EXCEPTION) {
                Toast.makeText(getActivity(), R.string.GroupCreateActivity_contacts_mms_exception, Toast.LENGTH_LONG).show();
            }
        }
    }
    public static final String MASTER_SECRET_EXTRA   = "master_secret";

    private Pair<Long, Recipients> handleUpdatePushGroup(byte[] groupId, String groupName,
                                                         byte[] avatar, Set<Recipient> members)
            throws InvalidNumberException, MmsException
    {
        GroupDatabase groupDatabase     = DatabaseFactory.getGroupDatabase(GService.appContext);
        Set<String>  memberE164Numbers = getE164Numbers(members);
        memberE164Numbers.add(TextSecurePreferences.getLocalNumber(GService.appContext));

        for (String number : memberE164Numbers)
            Log.w("GDATA", "Updating: " + number);

        groupDatabase.updateMembers(groupId, new LinkedList<String>(memberE164Numbers));
        groupDatabase.updateTitle(groupId, groupName);
        groupDatabase.updateAvatar(groupId, avatar);

        return handlePushOperation(groupId, groupName, avatar, memberE164Numbers);
    }
    private Pair<Long, Recipients> handlePushOperation(byte[] groupId, String groupName, byte[] avatar,
                                                       Set<String> e164numbers)
            throws InvalidNumberException
    {

        String     groupRecipientId = GroupUtil.getEncodedId(groupId);
        Recipients groupRecipient   = RecipientFactory.getRecipientsFromString(GService.appContext, groupRecipientId, false);

        PushMessageProtos.PushMessageContent.GroupContext context = PushMessageProtos.PushMessageContent.GroupContext.newBuilder()
                .setId(ByteString.copyFrom(groupId))
                .setType(PushMessageProtos.PushMessageContent.GroupContext.Type.UPDATE)
                .setName(groupName)
                .addAllMembers(e164numbers)
                .build();

        OutgoingGroupMediaMessage outgoingMessage = new OutgoingGroupMediaMessage(GService.appContext, groupRecipient, context, avatar);
        long                      threadId        = MessageSender.send(GService.appContext, masterSecret, outgoingMessage, -1, false);

        return new Pair<>(threadId, groupRecipient);

    }
    private Set<String> getE164Numbers(Set<Recipient> recipients)
            throws InvalidNumberException
    {
        Set<String> results = new HashSet<String>();

        for (Recipient recipient : recipients) {
            results.add(Util.canonicalizeNumber(GService.appContext, recipient.getNumber()));
        }

        return results;
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        finishAndSave();
    }
    private class ThumbnailClickListener implements ThumbnailView.ThumbnailClickListener {
        private void fireIntent(Slide slide) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setDataAndType(PartAuthority.getPublicPartUri(slide.getUri()), slide.getContentType());
            intent.putExtra("destroyImage", true);
            try {
                getActivity().startActivity(intent);
            } catch (ActivityNotFoundException anfe) {
                Toast.makeText(getActivity(), R.string.ConversationItem_unable_to_open_media, Toast.LENGTH_LONG).show();
            }
        }

        public void onClick(final View v, final Slide slide) {
            if (slide != null) {
                if (MediaPreviewActivity.isContentTypeSupported(slide.getContentType())) {
                    Intent intent = new Intent(getActivity(), MediaPreviewActivity.class);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    intent.setDataAndType(slide.getUri(), slide.getContentType());
                    intent.putExtra(MediaPreviewActivity.MASTER_SECRET_EXTRA, masterSecret);

                    Recipient primaryRecipient = RecipientFactory.getRecipientsFromString(getActivity(),
                            String.valueOf(profileId), false).getPrimaryRecipient();

                    if (primaryRecipient != null) {
                        intent.putExtra(MediaPreviewActivity.RECIPIENT_EXTRA, primaryRecipient.getRecipientId());
                    }
                    intent.putExtra("destroyImage", true);
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
    public Bitmap getExistingBitmapForGroup(GroupDatabase.GroupRecord group) {
        if (group != null) {
            final byte[] existingAvatar = group.getAvatar();
            if (existingAvatar != null) {
                return BitmapFactory.decodeByteArray(existingAvatar, 0, existingAvatar.length);
            }
        }
            return null;
    }
    private class FillExistingGroupInfoAsyncTask extends ProgressDialogAsyncTask<Void,Void,Void> {

        private String existingTitle;
        private Bitmap existingAvatarBmp;

        public FillExistingGroupInfoAsyncTask() {
            super(getActivity(),
                    R.string.GroupCreateActivity_loading_group_details,
                    R.string.please_wait);
        }

        @Override
        protected Void doInBackground(Void... voids) {
            final GroupDatabase db = DatabaseFactory.getGroupDatabase(getActivity());
            GroupDatabase.GroupRecord group = db.getGroup(groupId);
            final Recipients recipients = db.getGroupMembers(groupId, false);
            if (recipients != null) {
                final List<Recipient> recipientList = recipients.getRecipientsList();
                if (recipientList != null) {
                    if (existingContacts == null)
                        existingContacts = new HashSet<>(recipientList.size());
                    existingContacts.addAll(recipientList);
                }
            }
            existingTitle = group.getTitle();
            existingAvatarBmp = getExistingBitmapForGroup(group);
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            if (existingTitle != null) {
                profileStatus.setText(existingTitle);
                imageText.setText(existingTitle);
            }
            if (existingAvatarBmp != null) {
                ProfileActivity.setAvatarTemp(existingAvatarBmp);
                profilePicture.setVisibility(View.GONE);
                scaleImage((ImageView) getView().findViewById(R.id.profile_picture_group), existingAvatarBmp);
            }

        }
    }
    private void scaleImage(ImageView view, Bitmap bitmap)
    {
        Bitmap scaledBitmap = scaleCenterCrop(bitmap, dpToPx(350), getActivity().getWindowManager().getDefaultDisplay().getWidth());

        int width = scaledBitmap.getWidth();
        int height = scaledBitmap.getHeight();

        view.setImageDrawable(new BitmapDrawable(scaledBitmap));

        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) view.getLayoutParams();
        params.width = width;
        params.height = height;

        view.setLayoutParams(params);
    }
    public Bitmap scaleCenterCrop(Bitmap source, int newHeight, int newWidth) {
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();

        float xScale = (float) newWidth / sourceWidth;
        float yScale = (float) newHeight / sourceHeight;
        float scale = Math.max(xScale, yScale);

        float scaledWidth = scale * sourceWidth;
        float scaledHeight = scale * sourceHeight;

        float left = (newWidth - scaledWidth) / 2;
        float top = (newHeight - scaledHeight) / 2;

        RectF targetRect = new RectF(left, top, left + scaledWidth, top + scaledHeight);

        Bitmap dest = Bitmap.createBitmap(newWidth, newHeight, source.getConfig());
        Canvas canvas = new Canvas(dest);
        canvas.drawBitmap(source, null, targetRect, null);
        return dest;
    }
}
