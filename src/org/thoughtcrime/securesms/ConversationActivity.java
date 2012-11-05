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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.telephony.PhoneNumberUtils;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

import org.thoughtcrime.securesms.components.RecipientsPanel;
import org.thoughtcrime.securesms.crypto.AuthenticityCalculator;
import org.thoughtcrime.securesms.crypto.KeyExchangeInitiator;
import org.thoughtcrime.securesms.crypto.KeyExchangeProcessor;
import org.thoughtcrime.securesms.crypto.KeyUtil;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.mms.AttachmentManager;
import org.thoughtcrime.securesms.mms.AttachmentTypeSelectorAdapter;
import org.thoughtcrime.securesms.mms.MediaTooLargeException;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.protocol.Tag;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.service.MessageNotifier;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.util.CharacterCalculator;
import org.thoughtcrime.securesms.util.EncryptedCharacterCalculator;
import org.thoughtcrime.securesms.util.InvalidMessageException;
import org.thoughtcrime.securesms.util.MemoryCleaner;

import ws.com.google.android.mms.MmsException;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * Activity for displaying a message thread, as well as
 * composing/sending a new message into that thread.
 *
 * @author Moxie Marlinspike
 *
 */
public class ConversationActivity extends SherlockFragmentActivity
    implements ConversationFragment.ConversationFragmentListener
  {

  private static final int PICK_CONTACT               = 1;
  private static final int PICK_IMAGE                 = 2;
  private static final int PICK_VIDEO                 = 3;
  private static final int PICK_AUDIO                 = 4;

  private MasterSecret masterSecret;
  private RecipientsPanel recipientsPanel;
  private EditText composeText;
  private ImageButton addContactButton;
  private Button sendButton;
  private TextView charactersLeft;

  private AttachmentTypeSelectorAdapter attachmentAdapter;
  private AttachmentManager attachmentManager;
  private BroadcastReceiver killActivityReceiver;
  private BroadcastReceiver securityUpdateReceiver;

  private Recipients recipients;
  private long threadId;
  private boolean isEncryptedConversation;

  private CharacterCalculator characterCalculator = new CharacterCalculator();

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);

    setContentView(R.layout.conversation_activity);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);

    initializeReceivers();
    initializeResources();
    initializeTitleBar();
  }

  @Override
  protected void onResume() {
    super.onResume();
    initializeSecurity();
    initializeTitleBar();
    calculateCharactersRemaining();
  }

  @Override
  protected void onStart() {
    super.onStart();

    if (!isExistingConversation())
      initializeRecipientsInput();

    registerPassphraseActivityStarted();
  }

  @Override
  protected void onStop() {
    super.onStop();

    registerPassphraseActivityStopped();
  }

  @Override
  protected void onDestroy() {
    unregisterReceiver(killActivityReceiver);
    unregisterReceiver(securityUpdateReceiver);
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
      Recipients recipients = (Recipients)data.getParcelableExtra("recipients");

      if (recipients != null)
        recipientsPanel.addRecipients(recipients);

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
    }
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    MenuInflater inflater = this.getSupportMenuInflater();
    menu.clear();

    if (isSingleConversation() && isEncryptedConversation)
    {
      if (isAuthenticatedSession()) {
        inflater.inflate(R.menu.conversation_secure_verified, menu);
      } else {
        inflater.inflate(R.menu.conversation_secure_unverified, menu);
      }
    } else if (isSingleConversation()) {
      inflater.inflate(R.menu.conversation_insecure, menu);
    }

    if (isSingleConversation()) {
      inflater.inflate(R.menu.conversation_callable, menu);
    } else if (isGroupConversation()) {
      inflater.inflate(R.menu.conversation_group_options, menu);
    }

    inflater.inflate(R.menu.conversation, menu);
    super.onPrepareOptionsMenu(menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);
    switch (item.getItemId()) {
    case R.id.menu_call:                 handleDial(getRecipients().getPrimaryRecipient()); return true;
    case R.id.menu_delete_thread:        handleDeleteThread();                              return true;
    case R.id.menu_add_attachment:       handleAddAttachment();                             return true;
    case R.id.menu_start_secure_session: handleStartSecureSession();                        return true;
    case R.id.menu_abort_session:        handleAbortSecureSession();                        return true;
    case R.id.menu_verify_recipient:     handleVerifyRecipient();                           return true;
    case R.id.menu_verify_session:       handleVerifySession();                             return true;
    case R.id.menu_group_recipients:     handleDisplayGroupRecipients();                    return true;
    case android.R.id.home:              finish();                                          return true;
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

  //////// Event Handlers

  private void handleVerifyRecipient() {
    Intent verifyIdentityIntent = new Intent(this, VerifyIdentityActivity.class);
    verifyIdentityIntent.putExtra("recipient", getRecipients().getPrimaryRecipient());
    verifyIdentityIntent.putExtra("master_secret", masterSecret);
    startActivity(verifyIdentityIntent);
  }

  private void handleVerifySession() {
    Intent verifyKeysIntent = new Intent(this, VerifyKeysActivity.class);
    verifyKeysIntent.putExtra("recipient", getRecipients().getPrimaryRecipient());
    verifyKeysIntent.putExtra("master_secret", masterSecret);
    startActivity(verifyKeysIntent);
  }

  private void handleStartSecureSession() {
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
          KeyUtil.abortSessionFor(ConversationActivity.this, getRecipients().getPrimaryRecipient());
          initializeSecurity();
          initializeTitleBar();
        }
      }
    });
    builder.setNegativeButton(R.string.no, null);
    builder.show();
  }

  private void handleDial(Recipient recipient) {
    if (recipient == null) return;

    Intent dialIntent = new Intent(Intent.ACTION_DIAL,
                            Uri.parse("tel:" + recipient.getNumber()));
    startActivity(dialIntent);
  }

  private void handleDisplayGroupRecipients() {
    List<String> recipientStrings = new LinkedList<String>();

    for (Recipient recipient : getRecipients().getRecipientsList()) {
      recipientStrings.add(recipient.getName());
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

  private void handleAddAttachment() {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setIcon(R.drawable.ic_dialog_attach);
    builder.setTitle(R.string.ConversationActivity_add_attachment);
    builder.setAdapter(attachmentAdapter, new AttachmentTypeListener());
    builder.show();
  }

  ///// Initializers

  private void initializeTitleBar() {
    String title    = null;
    String subtitle = null;

    if (isSingleConversation()) {

      if (isEncryptedConversation) {
        title = AuthenticityCalculator.getAuthenticatedName(this,
                                                            getRecipients().getPrimaryRecipient(),
                                                            masterSecret);
      }

      if (title == null || title.trim().length() == 0) {
        title = getRecipients().getPrimaryRecipient().getName();
      }

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

    if (subtitle != null)
      this.getSupportActionBar().setSubtitle(PhoneNumberUtils.formatNumber(subtitle));

    this.invalidateOptionsMenu();
  }

  private void initializeSecurity() {
    if (isSingleConversation() &&
        KeyUtil.isSessionFor(this, getRecipients().getPrimaryRecipient()))
    {
      sendButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_menu_lock_holo_light, 0);
      sendButton.setCompoundDrawablePadding(15);
      this.isEncryptedConversation = true;
      this.characterCalculator     = new EncryptedCharacterCalculator();
    } else {
      sendButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
      this.isEncryptedConversation = false;
      this.characterCalculator     = new CharacterCalculator();
    }

    calculateCharactersRemaining();
  }

  private void initializeResources() {
    recipientsPanel     = (RecipientsPanel)findViewById(R.id.recipients);
    recipients          = getIntent().getParcelableExtra("recipients");
    threadId            = getIntent().getLongExtra("thread_id", -1);
    addContactButton    = (ImageButton)findViewById(R.id.contacts_button);
    sendButton          = (Button)findViewById(R.id.send_button);
    composeText         = (EditText)findViewById(R.id.embedded_text_editor);
    masterSecret        = (MasterSecret)getIntent().getParcelableExtra("master_secret");
    charactersLeft      = (TextView)findViewById(R.id.space_left);

    attachmentAdapter   = new AttachmentTypeSelectorAdapter(this);
    attachmentManager   = new AttachmentManager(this);

    SendButtonListener sendButtonListener = new SendButtonListener();

    recipientsPanel.setPanelChangeListener(new RecipientsPanelChangeListener());
    sendButton.setOnClickListener(sendButtonListener);
    addContactButton.setOnClickListener(new AddRecipientButtonListener());
    composeText.setOnKeyListener(new ComposeKeyPressedListener());
    composeText.addTextChangedListener(new OnTextChangedListener());
    composeText.setOnEditorActionListener(sendButtonListener);

    registerForContextMenu(sendButton);

    if (getIntent().getStringExtra("forwarded_message") != null)
      composeText.setText(getString(R.string.ConversationActivity_forward_message_prefix)+": " + getIntent().getStringExtra("forwarded_message"));
  }

  private void initializeRecipientsInput() {
    recipientsPanel.setVisibility(View.VISIBLE);

    if (this.recipients != null) {
      recipientsPanel.addRecipients(this.recipients);
    }
  }


  private void initializeReceivers() {
    killActivityReceiver   = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        finish();
      }
    };

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

    registerReceiver(killActivityReceiver,
                     new IntentFilter(KeyCachingService.PASSPHRASE_EXPIRED_EVENT),
                     KeyCachingService.KEY_PERMISSION, null);

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
      attachmentManager.clear();
      Toast.makeText(this, R.string.ConversationActivity_sorry_there_was_an_error_setting_your_attachment,
                     Toast.LENGTH_LONG).show();
      Log.w("ComposeMessageActivity", e);
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

  private void calculateCharactersRemaining() {
    int charactersSpent                               = composeText.getText().length();
    CharacterCalculator.CharacterState characterState = characterCalculator.calculateCharacters(charactersSpent);
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

  private boolean isAuthenticatedSession() {
    return AuthenticityCalculator.isAuthenticated(this,
                                                  getRecipients().getPrimaryRecipient(),
                                                  masterSecret);
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

    if (!isEncryptedConversation && Tag.isTaggable(this, rawText))
      rawText = Tag.getTaggedMessage(rawText);

    return rawText;
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

      String message          = getMessage();
      long allocatedThreadId;

      if (attachmentManager.isAttachmentPresent()) {
        allocatedThreadId = MessageSender.sendMms(ConversationActivity.this, masterSecret, recipients,
                                                  threadId, attachmentManager.getSlideDeck(), message,
                                                  forcePlaintext);
      } else if (recipients.isEmailRecipient()) {
        allocatedThreadId = MessageSender.sendMms(ConversationActivity.this, masterSecret, recipients,
                                                  threadId, new SlideDeck(), message, forcePlaintext);
      } else {
        allocatedThreadId = MessageSender.send(ConversationActivity.this, masterSecret, recipients,
                                               threadId, message, forcePlaintext);
      }

      sendComplete(recipients, allocatedThreadId);
      MessageNotifier.updateNotification(ConversationActivity.this, false);
    } catch (RecipientFormattingException ex) {
      Toast.makeText(ConversationActivity.this,
                     R.string.ConversationActivity_recipient_is_not_a_valid_sms_or_email_address_exclamation,
                     Toast.LENGTH_LONG).show();
      Log.w("compose", ex);
    } catch (InvalidMessageException ex) {
      Toast.makeText(ConversationActivity.this, R.string.ConversationActivity_message_is_empty_exclamation,
                     Toast.LENGTH_SHORT).show();
      Log.w("compose", ex);
    } catch (MmsException e) {
      Log.w("ComposeMessageActivity", e);
    }
  }

  private void registerPassphraseActivityStarted() {
    Intent intent = new Intent(this, KeyCachingService.class);
    intent.setAction(KeyCachingService.ACTIVITY_START_EVENT);
    startService(intent);
  }

  private void registerPassphraseActivityStopped() {
    Intent intent = new Intent(this, KeyCachingService.class);
    intent.setAction(KeyCachingService.ACTIVITY_STOP_EVENT);
    startService(intent);
  }

  // Listeners

  private class AddRecipientButtonListener implements OnClickListener {
    public void onClick(View v) {
      Intent intent = new Intent(ConversationActivity.this, ContactSelectionActivity.class);
      startActivityForResult(intent, PICK_CONTACT);
    }
  };

  private class AttachmentTypeListener implements DialogInterface.OnClickListener {
    public void onClick(DialogInterface dialog, int which) {
      addAttachment(attachmentAdapter.buttonToCommand(which));
    }
  }

  private class RecipientsPanelChangeListener implements RecipientsPanel.RecipientsPanelChangedListener {
    public void onRecipientsPanelUpdate(Recipients recipients) {
      initializeSecurity();
      initializeTitleBar();
      calculateCharactersRemaining();
    }
  }

  private class SendButtonListener implements OnClickListener, TextView.OnEditorActionListener {
    public void onClick(View v) {
      sendMessage(false);
    }

    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
      if (actionId == EditorInfo.IME_ACTION_SEND) {
        sendButton.performClick();
        composeText.clearFocus();
        return true;
      }
      return false;
    }
  };

  private class OnTextChangedListener implements TextWatcher {
    public void afterTextChanged(Editable s) {
      calculateCharactersRemaining();
    }
    public void beforeTextChanged(CharSequence s, int start, int count,int after) {}
    public void onTextChanged(CharSequence s, int start, int before,int count) {}

  }

  private class ComposeKeyPressedListener implements OnKeyListener {
    public boolean onKey(View v, int keyCode, KeyEvent event) {
      if (event.getAction() == KeyEvent.ACTION_DOWN) {
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
          if (PreferenceManager.getDefaultSharedPreferences(ConversationActivity.this).getBoolean("pref_enter_sends", false)) {
            sendButton.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
            sendButton.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
            return true;
          }
        }
      }

      return false;
    }
  }

  @Override
  public void setComposeText(String text) {
    this.composeText.setText(text);
  }
}
