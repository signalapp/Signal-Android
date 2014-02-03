/**
 * Copyright (C) 2011 Whisper Systems
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
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

import org.thoughtcrime.securesms.components.EmojiDrawer;
import org.thoughtcrime.securesms.components.EmojiToggle;
import org.thoughtcrime.securesms.components.RecipientsPanel;
import org.thoughtcrime.securesms.contacts.ContactAccessor;
import org.thoughtcrime.securesms.contacts.ContactAccessor.ContactData;
import org.thoughtcrime.securesms.crypto.KeyExchangeInitiator;
import org.thoughtcrime.securesms.crypto.KeyExchangeProcessor;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.DraftDatabase;
import org.thoughtcrime.securesms.database.DraftDatabase.Draft;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.mms.AttachmentManager;
import org.thoughtcrime.securesms.mms.AttachmentTypeSelectorAdapter;
import org.thoughtcrime.securesms.mms.MediaTooLargeException;
import org.thoughtcrime.securesms.mms.MmsSendHelper;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.protocol.Tag;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.sms.OutgoingEncryptedMessage;
import org.thoughtcrime.securesms.sms.OutgoingTextMessage;
import org.thoughtcrime.securesms.util.ActionBarUtil;
import org.thoughtcrime.securesms.util.BitmapDecodingException;
import org.thoughtcrime.securesms.util.CharacterCalculator;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.EncryptedCharacterCalculator;
import org.thoughtcrime.securesms.util.MemoryCleaner;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.textsecure.crypto.InvalidMessageException;
import org.whispersystems.textsecure.crypto.MasterCipher;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.whispersystems.textsecure.storage.Session;
import org.whispersystems.textsecure.util.Util;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import ws.com.google.android.mms.MmsException;

/**
 * Activity for displaying a message thread, as well as
 * composing/sending a new message into that thread.
 *
 * @author Moxie Marlinspike
 *
 */
public class ConversationActivity extends PassphraseRequiredSherlockFragmentActivity
    implements ConversationFragment.ConversationFragmentListener
{

  public static final String RECIPIENTS_EXTRA        = "recipients";
  public static final String THREAD_ID_EXTRA         = "thread_id";
  public static final String MASTER_SECRET_EXTRA     = "master_secret";
  public static final String DRAFT_TEXT_EXTRA        = "draft_text";
  public static final String DRAFT_IMAGE_EXTRA       = "draft_image";
  public static final String DRAFT_AUDIO_EXTRA       = "draft_audio";
  public static final String DISTRIBUTION_TYPE_EXTRA = "distribution_type";

  private static final int PICK_CONTACT          = 1;
  private static final int PICK_IMAGE            = 2;
  private static final int PICK_VIDEO            = 3;
  private static final int PICK_AUDIO            = 4;
  private static final int PICK_CONTACT_INFO     = 5;

  private MasterSecret masterSecret;
  private RecipientsPanel recipientsPanel;
  private EditText composeText;
  private ImageButton addContactButton;
  private ImageButton sendButton;
  private TextView charactersLeft;

  private AttachmentTypeSelectorAdapter attachmentAdapter;
  private AttachmentManager attachmentManager;
  private BroadcastReceiver securityUpdateReceiver;
  private EmojiDrawer emojiDrawer;
  private EmojiToggle emojiToggle;

  private Recipients recipients;
  private long threadId;
  private int distributionType;
  private boolean isEncryptedConversation;
  private boolean isAuthenticatedConversation;
  private boolean isMmsEnabled = true;

  private CharacterCalculator characterCalculator = new CharacterCalculator();
  private DynamicTheme        dynamicTheme        = new DynamicTheme();
  private DynamicLanguage     dynamicLanguage     = new DynamicLanguage();

  @Override
  protected void onCreate(Bundle state) {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
    super.onCreate(state);

    setContentView(R.layout.conversation_activity);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    ActionBarUtil.initializeDefaultActionBar(this, getSupportActionBar());

    initializeReceivers();
    initializeResources();
    initializeDraft();
    initializeTitleBar();
  }

  @Override
  protected void onStart() {
    super.onStart();

    if (!isExistingConversation())
      initializeRecipientsInput();
  }

  @Override
  protected void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);

    initializeSecurity();
    initializeTitleBar();
    initializeMmsEnabledCheck();
    initializeIme();
    calculateCharactersRemaining();

    MessageNotifier.setVisibleThread(threadId);
    markThreadAsRead();
  }

  @Override
  protected void onPause() {
    super.onPause();
    MessageNotifier.setVisibleThread(-1L);
  }

  @Override
  protected void onDestroy() {
    unregisterReceiver(securityUpdateReceiver);
    saveDraft();
    MemoryCleaner.clean(masterSecret);
    super.onDestroy();
  }

  @Override
  public void onActivityResult(int reqCode, int resultCode, Intent data) {
    Log.w("ComposeMessageActivity", "onActivityResult called: " + resultCode + " , " + data);
    super.onActivityResult(reqCode, resultCode, data);

    if (data == null || resultCode != Activity.RESULT_OK)
      return;

    switch (reqCode) {
    case PICK_CONTACT:
      List<ContactData> contacts = data.getParcelableArrayListExtra("contacts");

      if (contacts != null)
        recipientsPanel.addContacts(contacts);

      break;
    case PICK_IMAGE:
      addAttachmentImage(data.getData());
      break;
    case PICK_VIDEO:
      addAttachmentVideo(data.getData());
      break;
    case PICK_AUDIO:
      addAttachmentAudio(data.getData());
      break;
    case PICK_CONTACT_INFO:
      addContactInfo(data.getData());
      break;
    }
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    MenuInflater inflater = this.getSupportMenuInflater();
    menu.clear();

    if (isSingleConversation() && isEncryptedConversation)
    {
      if (isAuthenticatedConversation) {
        inflater.inflate(R.menu.conversation_secure_identity, menu);
      } else {
        inflater.inflate(R.menu.conversation_secure_no_identity, menu);
      }
    } else if (isSingleConversation()) {
      inflater.inflate(R.menu.conversation_insecure, menu);
    }

    if (isSingleConversation()) {
      inflater.inflate(R.menu.conversation_callable, menu);
    } else if (isGroupConversation()) {
      inflater.inflate(R.menu.conversation_group_options, menu);

      if (distributionType == ThreadDatabase.DistributionTypes.BROADCAST)
      {
        menu.findItem(R.id.menu_distribution_broadcast).setChecked(true);
      } else {
        menu.findItem(R.id.menu_distribution_conversation).setChecked(true);
      }
    }

    inflater.inflate(R.menu.conversation, menu);
    super.onPrepareOptionsMenu(menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);
    switch (item.getItemId()) {
    case R.id.menu_call:                      handleDial(getRecipients().getPrimaryRecipient()); return true;
    case R.id.menu_delete_thread:             handleDeleteThread();                              return true;
    case R.id.menu_add_contact_info:          handleAddContactInfo();                            return true;
    case R.id.menu_add_attachment:            handleAddAttachment();                             return true;
    case R.id.menu_start_secure_session:      handleStartSecureSession();                        return true;
    case R.id.menu_abort_session:             handleAbortSecureSession();                        return true;
    case R.id.menu_verify_recipient:          handleVerifyRecipient();                           return true;
    case R.id.menu_group_recipients:          handleDisplayGroupRecipients();                    return true;
    case R.id.menu_distribution_broadcast:    handleDistributionBroadcastEnabled(item);          return true;
    case R.id.menu_distribution_conversation: handleDistributionConversationEnabled(item);       return true;
    case android.R.id.home:                   handleReturnToConversationList();                  return true;
    }

    return false;
  }

  @Override
  public void onCreateContextMenu (ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
    if (isEncryptedConversation) {
      android.view.MenuInflater inflater = getMenuInflater();
      inflater.inflate(R.menu.conversation_button_context, menu);
    }
  }

  @Override
  public boolean onContextItemSelected(android.view.MenuItem item) {
    switch (item.getItemId()) {
    case R.id.menu_context_send_unencrypted: sendMessage(true); return true;
    }

    return false;
  }

  @Override
  public void onBackPressed() {
    if (emojiDrawer.getVisibility() == View.VISIBLE) {
      emojiDrawer.setVisibility(View.GONE);
      emojiToggle.toggle();
    } else {
      super.onBackPressed();
    }
  }

  //////// Event Handlers

  private void handleReturnToConversationList() {
    Intent intent = new Intent(this, ConversationListActivity.class);
    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    intent.putExtra("master_secret", masterSecret);
    startActivity(intent);
    finish();
  }

  private void handleVerifyRecipient() {
    Intent verifyIdentityIntent = new Intent(this, VerifyIdentityActivity.class);
    verifyIdentityIntent.putExtra("recipient", getRecipients().getPrimaryRecipient());
    verifyIdentityIntent.putExtra("master_secret", masterSecret);
    startActivity(verifyIdentityIntent);
  }

  private void handleStartSecureSession() {
    if (getRecipients() == null) {
      Toast.makeText(this, getString(R.string.ConversationActivity_invalid_recipient),
                     Toast.LENGTH_LONG).show();
      return;
    }

    final Recipient recipient   = getRecipients().getPrimaryRecipient();
    String recipientName        = (recipient.getName() == null ? recipient.getNumber() : recipient.getName());
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle(R.string.ConversationActivity_initiate_secure_session_question);
    builder.setIcon(android.R.drawable.ic_dialog_info);
    builder.setCancelable(true);
    builder.setMessage(String.format(getString(R.string.ConversationActivity_initiate_secure_session_with_s_question),
                       recipientName));
    builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        KeyExchangeInitiator.initiate(ConversationActivity.this, masterSecret,
                                      recipient, true);
      }
    });

    builder.setNegativeButton(R.string.no, null);
    builder.show();
  }

  private void handleAbortSecureSession() {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle(R.string.ConversationActivity_abort_secure_session_confirmation);
    builder.setIcon(android.R.drawable.ic_dialog_alert);
    builder.setCancelable(true);
    builder.setMessage(R.string.ConversationActivity_are_you_sure_that_you_want_to_abort_this_secure_session_question);
    builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        if (isSingleConversation()) {
          Session.abortSessionFor(ConversationActivity.this, getRecipients().getPrimaryRecipient());
          initializeSecurity();
          initializeTitleBar();
        }
      }
    });
    builder.setNegativeButton(R.string.no, null);
    builder.show();
  }

  private void handleDistributionBroadcastEnabled(MenuItem item) {
    distributionType = ThreadDatabase.DistributionTypes.BROADCAST;
    item.setChecked(true);

    if (threadId != -1) {
      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          DatabaseFactory.getThreadDatabase(ConversationActivity.this)
                         .setDistributionType(threadId, ThreadDatabase.DistributionTypes.BROADCAST);
          return null;
        }
      }.execute();
    }
  }

  private void handleDistributionConversationEnabled(MenuItem item) {
    distributionType = ThreadDatabase.DistributionTypes.CONVERSATION;
    item.setChecked(true);

    if (threadId != -1) {
      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          DatabaseFactory.getThreadDatabase(ConversationActivity.this)
                         .setDistributionType(threadId, ThreadDatabase.DistributionTypes.CONVERSATION);
          return null;
        }
      }.execute();
    }
  }

  private void handleDial(Recipient recipient) {
    try {
      if (recipient == null) return;

      Intent dialIntent = new Intent(Intent.ACTION_DIAL,
                              Uri.parse("tel:" + recipient.getNumber()));
      startActivity(dialIntent);
    } catch (ActivityNotFoundException anfe) {
      Log.w("ConversationActivity", anfe);
      Util.showAlertDialog(this,
                           getString(R.string.ConversationActivity_calls_not_supported),
                           getString(R.string.ConversationActivity_this_device_does_not_appear_to_support_dial_actions));
    }
  }

  private void handleDisplayGroupRecipients() {
    List<String> recipientStrings = new LinkedList<String>();

    for (Recipient recipient : getRecipients().getRecipientsList()) {
      recipientStrings.add(recipient.toShortString());
    }

    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle(R.string.ConversationActivity_group_conversation_recipients);
    builder.setIcon(R.drawable.ic_groups_holo_dark);
    builder.setCancelable(true);
    builder.setItems(recipientStrings.toArray(new String[]{}), null);
    builder.setPositiveButton(android.R.string.ok, null);
    builder.show();
  }

  private void handleDeleteThread() {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle(R.string.ConversationActivity_delete_thread_confirmation);
    builder.setIcon(android.R.drawable.ic_dialog_alert);
    builder.setCancelable(true);
    builder.setMessage(R.string.ConversationActivity_are_you_sure_that_you_want_to_permanently_delete_this_conversation_question);
    builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        if (threadId > 0) {
          DatabaseFactory.getThreadDatabase(ConversationActivity.this).deleteConversation(threadId);
          finish();
        }
      }
    });

    builder.setNegativeButton(R.string.no, null);
    builder.show();
  }

  private void handleAddContactInfo() {
    Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
    startActivityForResult(intent, PICK_CONTACT_INFO);
  }

  private void handleAddAttachment() {
    if (this.isMmsEnabled) {
      AlertDialog.Builder builder = new AlertDialog.Builder(new ContextThemeWrapper(this, R.style.TextSecure_Light_Dialog));
      builder.setIcon(R.drawable.ic_dialog_attach);
      builder.setTitle(R.string.ConversationActivity_add_attachment);
      builder.setAdapter(attachmentAdapter, new AttachmentTypeListener());
      builder.show();
    } else {
      handleManualMmsRequired();
    }
  }

  private void handleManualMmsRequired() {
    Toast.makeText(this, R.string.MmsDownloader_error_reading_mms_settings, Toast.LENGTH_LONG).show();

    Intent intent = new Intent(this, PromptMmsActivity.class);
    intent.putExtras(getIntent().getExtras());
    startActivity(intent);
  }

  ///// Initializers

  private void initializeTitleBar() {
    String title    = null;
    String subtitle = null;

    if (isSingleConversation()) {
      title = getRecipients().getPrimaryRecipient().getName();

      if (title == null || title.trim().length() == 0) {
        title = getRecipients().getPrimaryRecipient().getNumber();
      } else {
        subtitle = getRecipients().getPrimaryRecipient().getNumber();
      }
    } else if (isGroupConversation()) {
      title    = getString(R.string.ConversationActivity_group_conversation);
      subtitle = String.format(getString(R.string.ConversationActivity_d_recipients_in_group),
                               getRecipients().getRecipientsList().size());
    } else {
      title    = getString(R.string.ConversationActivity_compose_message);
      subtitle = "";
    }

    this.getSupportActionBar().setTitle(title);

    if (subtitle != null && !Util.isEmpty(subtitle))
      this.getSupportActionBar().setSubtitle(PhoneNumberUtils.formatNumber(subtitle));

    this.invalidateOptionsMenu();
  }

  private void initializeDraft() {
    String draftText  = getIntent().getStringExtra(DRAFT_TEXT_EXTRA);
    Uri    draftImage = getIntent().getParcelableExtra(DRAFT_IMAGE_EXTRA);
    Uri    draftAudio = getIntent().getParcelableExtra(DRAFT_AUDIO_EXTRA);

    if (draftText != null)  composeText.setText(draftText);
    if (draftImage != null) addAttachmentImage(draftImage);
    if (draftAudio != null) addAttachmentAudio(draftAudio);

    if (draftText == null && draftImage == null && draftAudio == null) {
      initializeDraftFromDatabase();
    }
  }

  private void initializeDraftFromDatabase() {
    new AsyncTask<Void, Void, List<Draft>>() {
      @Override
      protected List<Draft> doInBackground(Void... params) {
        MasterCipher masterCipher   = new MasterCipher(masterSecret);
        DraftDatabase draftDatabase = DatabaseFactory.getDraftDatabase(ConversationActivity.this);
        List<Draft> results         = draftDatabase.getDrafts(masterCipher, threadId);

        draftDatabase.clearDrafts(threadId);

        return results;
      }

      @Override
      protected void onPostExecute(List<Draft> drafts) {
        for (Draft draft : drafts) {
          if      (draft.getType().equals(Draft.TEXT))  composeText.setText(draft.getValue());
          else if (draft.getType().equals(Draft.IMAGE)) addAttachmentImage(Uri.parse(draft.getValue()));
          else if (draft.getType().equals(Draft.AUDIO)) addAttachmentAudio(Uri.parse(draft.getValue()));
          else if (draft.getType().equals(Draft.VIDEO)) addAttachmentVideo(Uri.parse(draft.getValue()));
        }
      }
    }.execute();
  }

  private void initializeSecurity() {
    int        attributes[] = new int[]{R.attr.conversation_send_button,
                                        R.attr.conversation_send_secure_button};
    TypedArray drawables    = obtainStyledAttributes(attributes);

    if (isSingleConversation() &&
        Session.hasSession(this, masterSecret, getRecipients().getPrimaryRecipient()))
    {
      sendButton.setImageDrawable(drawables.getDrawable(1));
      this.isEncryptedConversation     = true;
      this.isAuthenticatedConversation = Session.hasRemoteIdentityKey(this, masterSecret, getRecipients().getPrimaryRecipient());
      this.characterCalculator         = new EncryptedCharacterCalculator();
    } else {
      sendButton.setImageDrawable(drawables.getDrawable(0));
      this.isEncryptedConversation     = false;
      this.isAuthenticatedConversation = false;
      this.characterCalculator         = new CharacterCalculator();
    }

    calculateCharactersRemaining();
    drawables.recycle();
  }

  private void initializeMmsEnabledCheck() {
    new AsyncTask<Void, Void, Boolean>() {
      @Override
      protected Boolean doInBackground(Void... params) {
        return MmsSendHelper.hasNecessaryApnDetails(ConversationActivity.this);
      }

      @Override
      protected void onPostExecute(Boolean isMmsEnabled) {
        ConversationActivity.this.isMmsEnabled = isMmsEnabled;
      }
    }.execute();
  }

  private void initializeIme() {
    if (TextSecurePreferences.isEnterImeKeyEnabled(this)) {
      composeText.setInputType(composeText.getInputType() & (~InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE));
    } else {
      composeText.setInputType(composeText.getInputType() | (InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE));
    }
  }

  private void initializeResources() {
    recipientsPanel     = (RecipientsPanel)findViewById(R.id.recipients);
    recipients          = getIntent().getParcelableExtra(RECIPIENTS_EXTRA);
    threadId            = getIntent().getLongExtra(THREAD_ID_EXTRA, -1);
    distributionType    = getIntent().getIntExtra(DISTRIBUTION_TYPE_EXTRA,
                                                  ThreadDatabase.DistributionTypes.DEFAULT);
    addContactButton    = (ImageButton)findViewById(R.id.contacts_button);
    sendButton          = (ImageButton)findViewById(R.id.send_button);
    composeText         = (EditText)findViewById(R.id.embedded_text_editor);
    masterSecret        = getIntent().getParcelableExtra(MASTER_SECRET_EXTRA);
    charactersLeft      = (TextView)findViewById(R.id.space_left);
    emojiDrawer         = (EmojiDrawer)findViewById(R.id.emoji_drawer);
    emojiToggle         = (EmojiToggle)findViewById(R.id.emoji_toggle);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      emojiToggle.setVisibility(View.GONE);
    }

    attachmentAdapter   = new AttachmentTypeSelectorAdapter(this);
    attachmentManager   = new AttachmentManager(this);

    SendButtonListener        sendButtonListener        = new SendButtonListener();
    ComposeKeyPressedListener composeKeyPressedListener = new ComposeKeyPressedListener();

    recipientsPanel.setPanelChangeListener(new RecipientsPanelChangeListener());
    sendButton.setOnClickListener(sendButtonListener);
    sendButton.setEnabled(true);
    addContactButton.setOnClickListener(new AddRecipientButtonListener());
    composeText.setOnKeyListener(composeKeyPressedListener);
    composeText.addTextChangedListener(composeKeyPressedListener);
    composeText.setOnEditorActionListener(sendButtonListener);
    composeText.setOnClickListener(composeKeyPressedListener);
    emojiDrawer.setComposeEditText(composeText);
    emojiToggle.setOnClickListener(new EmojiToggleListener());

    registerForContextMenu(sendButton);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
      getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
    }

    if (getIntent().getStringExtra("forwarded_message") != null) {
      composeText.setText(getString(R.string.ConversationActivity_forward_message_prefix) + ": " +
                          getIntent().getStringExtra("forwarded_message"));
    }
  }

  private void initializeRecipientsInput() {
    recipientsPanel.setVisibility(View.VISIBLE);

    if (this.recipients != null) {
      recipientsPanel.addRecipients(this.recipients);
    } else {
      InputMethodManager input = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
      input.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0);
    }
  }


  private void initializeReceivers() {
    securityUpdateReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        if (intent.getLongExtra("thread_id", -1) == -1)
          return;

        if (intent.getLongExtra("thread_id", -1) == threadId) {
          initializeSecurity();
          initializeTitleBar();
          calculateCharactersRemaining();
        }
      }
    };

    registerReceiver(securityUpdateReceiver,
                     new IntentFilter(KeyExchangeProcessor.SECURITY_UPDATE_EVENT),
                     KeyCachingService.KEY_PERMISSION, null);
  }


  //////// Helper Methods

  private void addAttachment(int type) {
    Log.w("ComposeMessageActivity", "Selected: " + type);
    switch (type) {
    case AttachmentTypeSelectorAdapter.ADD_IMAGE:
      AttachmentManager.selectImage(this, PICK_IMAGE); break;
    case AttachmentTypeSelectorAdapter.ADD_VIDEO:
      AttachmentManager.selectVideo(this, PICK_VIDEO); break;
    case AttachmentTypeSelectorAdapter.ADD_SOUND:
      AttachmentManager.selectAudio(this, PICK_AUDIO); break;
    }
  }

  private void addAttachmentImage(Uri imageUri) {
    try {
      attachmentManager.setImage(imageUri);
    } catch (IOException e) {
      Log.w("ConversationActivity", e);
      attachmentManager.clear();
      Toast.makeText(this, R.string.ConversationActivity_sorry_there_was_an_error_setting_your_attachment,
                     Toast.LENGTH_LONG).show();
    } catch (BitmapDecodingException e) {
      Log.w("ConversationActivity", e);
      attachmentManager.clear();
      Toast.makeText(this, R.string.ConversationActivity_sorry_there_was_an_error_setting_your_attachment,
                     Toast.LENGTH_LONG).show();
    }
  }

  private void addAttachmentVideo(Uri videoUri) {
    try {
      attachmentManager.setVideo(videoUri);
    } catch (IOException e) {
      attachmentManager.clear();
      Toast.makeText(this, R.string.ConversationActivity_sorry_there_was_an_error_setting_your_attachment,
                     Toast.LENGTH_LONG).show();
      Log.w("ComposeMessageActivity", e);
    } catch (MediaTooLargeException e) {
      attachmentManager.clear();
      Toast.makeText(this, R.string.ConversationActivity_sorry_the_selected_video_exceeds_message_size_restrictions,
                     Toast.LENGTH_LONG).show();
      Log.w("ComposeMessageActivity", e);
    }
  }

  private void addAttachmentAudio(Uri audioUri) {
    try {
      attachmentManager.setAudio(audioUri);
    } catch (IOException e) {
      attachmentManager.clear();
      Toast.makeText(this, R.string.ConversationActivity_sorry_there_was_an_error_setting_your_attachment,
                     Toast.LENGTH_LONG).show();
      Log.w("ComposeMessageActivity", e);
    } catch (MediaTooLargeException e) {
      attachmentManager.clear();
      Toast.makeText(this, R.string.ConversationActivity_sorry_the_selected_audio_exceeds_message_size_restrictions,
                     Toast.LENGTH_LONG).show();
      Log.w("ComposeMessageActivity", e);
    }
  }

  private void addContactInfo(Uri contactUri) {
    ContactAccessor contactDataList = ContactAccessor.getInstance();
    ContactData contactData = contactDataList.getContactData(this, contactUri);

    if      (contactData.numbers.size() == 1) composeText.append(contactData.numbers.get(0).number);
    else if (contactData.numbers.size() > 1)  selectContactInfo(contactData);
  }

  private void selectContactInfo(ContactData contactData) {
    final CharSequence[] numbers = new CharSequence[contactData.numbers.size()];
    final CharSequence[] numberItems = new CharSequence[contactData.numbers.size()];
    for (int i = 0; i < contactData.numbers.size(); i++) {
      numbers[i] = contactData.numbers.get(i).number;
      numberItems[i] = contactData.numbers.get(i).type + ": " + contactData.numbers.get(i).number;
    }

    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setIcon(R.drawable.ic_contact_picture);
    builder.setTitle(R.string.ConversationActivity_select_contact_info);

    builder.setItems(numberItems, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        composeText.append(numbers[which]);
      }
    });
    builder.show();
  }

  private List<Draft> getDraftsForCurrentState() {
    List<Draft> drafts = new LinkedList<Draft>();

    if (!Util.isEmpty(composeText)) {
      drafts.add(new Draft(Draft.TEXT, composeText.getText().toString()));
    }

    for (Slide slide : attachmentManager.getSlideDeck().getSlides()) {
      if      (slide.hasImage()) drafts.add(new Draft(Draft.IMAGE, slide.getUri().toString()));
      else if (slide.hasAudio()) drafts.add(new Draft(Draft.AUDIO, slide.getUri().toString()));
      else if (slide.hasVideo()) drafts.add(new Draft(Draft.VIDEO, slide.getUri().toString()));
    }

    return drafts;
  }

  private void saveDraft() {
    if (this.threadId <= 0 || this.recipients == null || this.recipients.isEmpty())
      return;

    final List<Draft> drafts = getDraftsForCurrentState();

    if (drafts.size() <= 0)
      return;

    final long thisThreadId             = this.threadId;
    final MasterSecret thisMasterSecret = this.masterSecret.parcelClone();

    new AsyncTask<Void, Void, Void>() {
      @Override
      protected void onPreExecute() {
        Toast.makeText(ConversationActivity.this,
                       R.string.ConversationActivity_saving_draft,
                       Toast.LENGTH_SHORT).show();
      }

      @Override
      protected Void doInBackground(Void... params) {
        MasterCipher masterCipher = new MasterCipher(thisMasterSecret);
        DatabaseFactory.getDraftDatabase(ConversationActivity.this).insertDrafts(masterCipher, thisThreadId, drafts);
        MemoryCleaner.clean(thisMasterSecret);
        return null;
      }
    }.execute();
  }

  private void calculateCharactersRemaining() {
    int charactersSpent                               = composeText.getText().toString().length();
    CharacterCalculator.CharacterState characterState = characterCalculator.calculateCharacters(charactersSpent);
    if (characterState.charactersRemaining <= 15 && charactersLeft.getVisibility() != View.VISIBLE) {
      charactersLeft.setVisibility(View.VISIBLE);
    } else if (characterState.charactersRemaining > 15 && charactersLeft.getVisibility() != View.GONE) {
      charactersLeft.setVisibility(View.GONE);
    }
    charactersLeft.setText(characterState.charactersRemaining + "/" + characterState.maxMessageSize + " (" + characterState.messagesSpent + ")");
  }

  private boolean isExistingConversation() {
    return this.recipients != null && this.threadId != -1;
  }

  private boolean isSingleConversation() {
    return getRecipients() != null && getRecipients().isSingleRecipient();
  }

  private boolean isGroupConversation() {
    return getRecipients() != null && !getRecipients().isSingleRecipient();
  }

  private Recipients getRecipients() {
    try {
      if (isExistingConversation()) return this.recipients;
      else                          return recipientsPanel.getRecipients();
    } catch (RecipientFormattingException rfe) {
      Log.w("ConversationActivity", rfe);
      return null;
    }
  }

  private String getMessage() throws InvalidMessageException {
    String rawText = composeText.getText().toString();

    if (rawText.length() < 1 && !attachmentManager.isAttachmentPresent())
      throw new InvalidMessageException(getString(R.string.ConversationActivity_message_is_empty_exclamation));

    if (!isEncryptedConversation && Tag.isTaggable(rawText))
      rawText = Tag.getTaggedMessage(rawText);

    return rawText;
  }

  private void markThreadAsRead() {
    new AsyncTask<Long, Void, Void>() {
      @Override
      protected Void doInBackground(Long... params) {
        DatabaseFactory.getThreadDatabase(ConversationActivity.this).setRead(params[0]);
        MessageNotifier.updateNotification(ConversationActivity.this, masterSecret);
        return null;
      }
    }.execute(threadId);
  }

  private void sendComplete(Recipients recipients, long threadId) {
    attachmentManager.clear();
    recipientsPanel.disable();
    composeText.setText("");

    this.recipients = recipients;
    this.threadId   = threadId;

    if (this.recipientsPanel.getVisibility() == View.VISIBLE) {
      ConversationFragment fragment
        = (ConversationFragment)this.getSupportFragmentManager()
          .findFragmentById(R.id.fragment_content);

      fragment.reload(recipients, threadId);

      this.recipientsPanel.setVisibility(View.GONE);
      initializeTitleBar();
      initializeSecurity();
    }
  }

  private void sendMessage(boolean forcePlaintext) {
    try {
      Recipients recipients   = getRecipients();

      if (recipients == null)
        throw new RecipientFormattingException("Badly formatted");

      String body             = getMessage();
      long allocatedThreadId;

      if ((!recipients.isSingleRecipient() || recipients.isEmailRecipient()) && !isMmsEnabled) {
        handleManualMmsRequired();
        return;
      } else if (attachmentManager.isAttachmentPresent()) {
        allocatedThreadId = MessageSender.sendMms(ConversationActivity.this, masterSecret, recipients,
                                                  threadId, attachmentManager.getSlideDeck(), body,
                                                  distributionType, isEncryptedConversation && !forcePlaintext);
      } else if (recipients.isEmailRecipient() || !recipients.isSingleRecipient() || recipients.isGroupRecipient()) {
        allocatedThreadId = MessageSender.sendMms(ConversationActivity.this, masterSecret, recipients,
                                                  threadId, new SlideDeck(), body, distributionType,
                                                  isEncryptedConversation && !forcePlaintext);
      } else {
        OutgoingTextMessage message;

        if (isEncryptedConversation && !forcePlaintext) {
          message = new OutgoingEncryptedMessage(recipients, body);
        } else {
          message = new OutgoingTextMessage(recipients, body);
        }

        Log.w("ConversationActivity", "Sending message...");
        allocatedThreadId = MessageSender.send(ConversationActivity.this, masterSecret,
                                               message, threadId);
      }
      sendComplete(recipients, allocatedThreadId);
    } catch (RecipientFormattingException ex) {
      Toast.makeText(ConversationActivity.this,
                     R.string.ConversationActivity_recipient_is_not_a_valid_sms_or_email_address_exclamation,
                     Toast.LENGTH_LONG).show();
      Log.w("ConversationActivity", ex);
    } catch (InvalidMessageException ex) {
      Toast.makeText(ConversationActivity.this, R.string.ConversationActivity_message_is_empty_exclamation,
                     Toast.LENGTH_SHORT).show();
      Log.w("ConversationActivity", ex);
    } catch (MmsException e) {
      Log.w("ComposeMessageActivity", e);
    }
  }

  // Listeners

  private class AddRecipientButtonListener implements OnClickListener {
    @Override
    public void onClick(View v) {
      Intent intent = new Intent(ConversationActivity.this, ContactSelectionActivity.class);
      startActivityForResult(intent, PICK_CONTACT);
    }
  }

  private class AttachmentTypeListener implements DialogInterface.OnClickListener {
    @Override
    public void onClick(DialogInterface dialog, int which) {
      addAttachment(attachmentAdapter.buttonToCommand(which));
    }
  }

  private class RecipientsPanelChangeListener implements RecipientsPanel.RecipientsPanelChangedListener {
    @Override
    public void onRecipientsPanelUpdate(Recipients recipients) {
      initializeSecurity();
      initializeTitleBar();
      calculateCharactersRemaining();
    }
  }

  private class EmojiToggleListener implements OnClickListener {
    @Override
    public void onClick(View v) {
      InputMethodManager input = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);

      if (emojiDrawer.getVisibility() == View.VISIBLE) {
        input.showSoftInput(composeText, 0);
        emojiDrawer.setVisibility(View.GONE);
      } else {
        input.hideSoftInputFromWindow(composeText.getWindowToken(), 0);
        emojiDrawer.setVisibility(View.VISIBLE);
      }
    }
  }

  private class SendButtonListener implements OnClickListener, TextView.OnEditorActionListener {
    @Override
    public void onClick(View v) {
      sendMessage(false);
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
      if (actionId == EditorInfo.IME_ACTION_SEND) {
        sendButton.performClick();
        composeText.clearFocus();
        return true;
      }
      return false;
    }
  }

  private class ComposeKeyPressedListener implements OnKeyListener, OnClickListener, TextWatcher {
    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
      if (event.getAction() == KeyEvent.ACTION_DOWN) {
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
          if (TextSecurePreferences.isEnterSendsEnabled(ConversationActivity.this)) {
            sendButton.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
            sendButton.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
            return true;
          }
        }
      }

      return false;
    }

    @Override
    public void onClick(View v) {
      if (emojiDrawer.isOpen()) {
        emojiToggle.performClick();
      }
    }

    @Override
    public void afterTextChanged(Editable s) {
      calculateCharactersRemaining();
//      if (s == null || s.length() == 0) {
//        sendButton.setClickable(false);
//        sendButton.setEnabled(false);
//        sendButton.setColorFilter(0x66FFFFFF);
//      } else {
//        sendButton.setClickable(true);
//        sendButton.setEnabled(true);
//        sendButton.setColorFilter(null);
//      }
    }
    @Override
    public void beforeTextChanged(CharSequence s, int start, int count,int after) {}
    @Override
    public void onTextChanged(CharSequence s, int start, int before,int count) {}
  }

  @Override
  public void setComposeText(String text) {
    this.composeText.setText(text);
  }

}
