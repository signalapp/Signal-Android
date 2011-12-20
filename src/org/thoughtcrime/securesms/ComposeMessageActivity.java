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

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.thoughtcrime.securesms.components.RecipientsPanel;
import org.thoughtcrime.securesms.crypto.AuthenticityCalculator;
import org.thoughtcrime.securesms.crypto.DecryptingQueue;
import org.thoughtcrime.securesms.crypto.KeyExchangeInitiator;
import org.thoughtcrime.securesms.crypto.KeyExchangeProcessor;
import org.thoughtcrime.securesms.crypto.KeyUtil;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MessageRecord;
import org.thoughtcrime.securesms.database.SessionRecord;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.mms.AttachmentManager;
import org.thoughtcrime.securesms.mms.AttachmentTypeSelectorAdapter;
import org.thoughtcrime.securesms.mms.MediaTooLargeException;
import org.thoughtcrime.securesms.mms.SlideDeck;
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
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.ClipboardManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Activity for displaying a message thread, as well as
 * composing/sending a new message into that thread.
 * 
 * @author Moxie Marlinspike
 *
 */
public class ComposeMessageActivity extends Activity {
		
  private static final int PICK_CONTACT               = 1;
  private static final int PICK_IMAGE                 = 2;
  private static final int PICK_VIDEO                 = 3;
  private static final int PICK_AUDIO                 = 4;
	
  private static final int MENU_OPTION_CALL           = 2;
  //	private static final int MENU_OPTION_VIEW_CONTACT   = 3;
  private static final int MENU_OPTION_VERIFY_KEYS    = 4;
  private static final int MENU_OPTION_DELETE_THREAD  = 5;
  private static final int MENU_OPTION_START_SESSION  = 6;
  private static final int MENU_OPTION_DELETE_KEYS    = 7;
  private static final int MENU_OPTION_ADD_ATTACHMENT = 8;
  private static final int MENU_OPTION_DETAILS        = 9;
  private static final int MENU_OPTION_VERIFY_IDENTITY = 10;
  private static final int MENU_OPTION_REDECRYPT       = 11;
	
  private static final int MENU_OPTION_COPY           = 100;
  private static final int MENU_OPTION_DELETE         = 101;
  private static final int MENU_OPTION_FORWARD        = 102;
  private static final int MENU_OPTION_SEND_CLEARTEXT = 103;
  private static final int MENU_OPTION_SEND_DELAYED   = 104;

  private static final int MESSAGE_ITEM_GROUP         = 0;
  private static final int SEND_BUTTON_GROUP          = 1;
	
  private MasterSecret masterSecret;
  private ConversationAdapter conversationAdapter;
  private ListView conversationView;
  private RecipientsPanel recipientsPanel;
  private EditText composeText;
  private ImageButton addContactButton;
  private Button sendButton;
  private TextView charactersLeft;
  private TextView titleBar;
	
  private View greyLock;
  private View redLock;
	
  private AttachmentTypeSelectorAdapter attachmentAdapter;
  private AttachmentManager attachmentManager;
  private KillActivityReceiver killActivityReceiver;
  private SecurityUpdateReceiver securityUpdateReceiver;
	
  private Recipients recipients;
  private long threadId;
  private boolean sendEncrypted;
  private CharacterCalculator characterCalculator = new CharacterCalculator();

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    Log.w("ComposeMessageActivity", "onCreate called...");
    getWindow().requestFeature(Window.FEATURE_NO_TITLE);
    setContentView(R.layout.compose_message_activity);
    
    initializeReceivers();
    initializeResources();
    initializeTitleBar();
    initializeColors();
  }

  @Override
  protected void onResume() {
    super.onResume();
    Log.w("ComposeMessageActivity", "onResume called...");
    initializeSecurity(recipients);
    initializeTitleBar();
    calculateCharactersRemaining();
  }
    
  @Override
  protected void onStart() {
    super.onStart();
    Log.w("ComposeMessageActivity", "onStart called...");
    if (isExistingConversation()) initializeConversationAdapter();
    else                          initializeRecipientsInput();    
    
    registerPassphraseActivityStarted();
  }

  @Override
  protected void onStop() {
    super.onStop();
    Log.w("ComposeMessageActivity", "onStop called...");
    if (this.conversationAdapter != null)
      this.conversationAdapter.close();
    
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
    super.onPrepareOptionsMenu(menu);
    menu.clear();

    if (recipients != null && recipients.isSingleRecipient())
      menu.add(0, MENU_OPTION_CALL, Menu.NONE, "Call").setIcon(android.R.drawable.ic_menu_call);
			
    menu.add(0, MENU_OPTION_DELETE_THREAD, Menu.NONE, "Delete Thread").setIcon(android.R.drawable.ic_menu_delete);
    menu.add(0, MENU_OPTION_ADD_ATTACHMENT, Menu.NONE, "Add Attachment").setIcon(R.drawable.ic_menu_attachment);
		
    if (recipients != null && recipients.isSingleRecipient() && SessionRecord.hasSession(this, recipients.getPrimaryRecipient())) {
      SubMenu secureSettingsMenu = menu.addSubMenu("Secure Session Options").setIcon(android.R.drawable.ic_menu_more);
      secureSettingsMenu.add(0, MENU_OPTION_VERIFY_KEYS, Menu.NONE, "Verify Secure Session").setIcon(R.drawable.ic_lock_message_sms);
      secureSettingsMenu.add(0, MENU_OPTION_VERIFY_IDENTITY, Menu.NONE, "Verify Recipient Identity").setIcon(android.R.drawable.ic_menu_zoom);
      secureSettingsMenu.add(0, MENU_OPTION_DELETE_KEYS, Menu.NONE, "Abort Secure Session").setIcon(android.R.drawable.ic_menu_revert);
    } else if (recipients != null && recipients.isSingleRecipient()) {
      menu.add(0, MENU_OPTION_START_SESSION, Menu.NONE, "Start Secure Session").setIcon(R.drawable.ic_lock_message_sms);
    }

    return true;
  }
	
  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);
		
    switch (item.getItemId()) {
    case MENU_OPTION_CALL:            dial(recipients.getPrimaryRecipient());  return true;
    case MENU_OPTION_DELETE_THREAD:   deleteThread();                          return true;
    case MENU_OPTION_VERIFY_KEYS:     verifyKeys();                            return true;
    case MENU_OPTION_START_SESSION:   initiateSecureSession();                 return true;
    case MENU_OPTION_DELETE_KEYS:     abortSecureSession();                    return true;
    case MENU_OPTION_ADD_ATTACHMENT:  addAttachment();                         return true;
    case MENU_OPTION_VERIFY_IDENTITY: verifyIdentity();                        return true;
    }
		
    return false;
  }
	
  @Override
  public void onCreateContextMenu (ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
    if (v == sendButton) createSendButtonContextMenu(menu);
    else                 createMessageItemContextMenu(menu);
  }

  private void createSendButtonContextMenu(ContextMenu menu) {
    if (sendEncrypted)	
      menu.add(SEND_BUTTON_GROUP, MENU_OPTION_SEND_CLEARTEXT, Menu.NONE, "Send unencrypted");
		
  }
	
  private void createMessageItemContextMenu(ContextMenu menu) {
    menu.add(MESSAGE_ITEM_GROUP, MENU_OPTION_COPY, Menu.NONE, "Copy text");
    menu.add(MESSAGE_ITEM_GROUP, MENU_OPTION_DELETE, Menu.NONE, "Delete");
    menu.add(MESSAGE_ITEM_GROUP, MENU_OPTION_DETAILS, Menu.NONE, "Message Details");
    menu.add(MESSAGE_ITEM_GROUP, MENU_OPTION_FORWARD, Menu.NONE, "Forward message");

    Cursor cursor                     = ((CursorAdapter)conversationAdapter).getCursor();
    ConversationItem conversationItem = (ConversationItem)(conversationAdapter.newView(this, cursor, null));
    MessageRecord messageRecord       = conversationItem.getMessageRecord();

    if (messageRecord.isFailedDecryptType())
      menu.add(MESSAGE_ITEM_GROUP, MENU_OPTION_REDECRYPT, Menu.NONE, "Attempt decrypt again");
  }
	
  @Override
  public boolean onContextItemSelected(MenuItem item) {
    if      (item.getGroupId() == MESSAGE_ITEM_GROUP) return onMessageContextItemSelected(item);
    else if (item.getGroupId() == SEND_BUTTON_GROUP)  return onSendContextItemSelected(item);
		
    return false;
  }

  private boolean onSendContextItemSelected(MenuItem item) {
    switch (item.getItemId()) {
    case MENU_OPTION_SEND_CLEARTEXT: sendMessage(false); return true;
    }
    return false;
  }
	
  private boolean onMessageContextItemSelected(MenuItem item) {
    Cursor cursor                     = ((CursorAdapter)conversationAdapter).getCursor();
    ConversationItem conversationItem = (ConversationItem)(conversationAdapter.newView(this, cursor, null));
    String address                    = cursor.getString(cursor.getColumnIndexOrThrow(SmsDatabase.ADDRESS));
    String body                       = cursor.getString(cursor.getColumnIndexOrThrow(SmsDatabase.BODY));
    MessageRecord messageRecord       = conversationItem.getMessageRecord();

    switch(item.getItemId()) {
    case MENU_OPTION_COPY:      copyMessageToClipboard(messageRecord);          return true;
    case MENU_OPTION_DELETE:    deleteMessage(messageRecord);                   return true;
    case MENU_OPTION_DETAILS:   displayMessageDetails(messageRecord);           return true;
    case MENU_OPTION_REDECRYPT: redecryptMessage(messageRecord, address, body); return true;
    case MENU_OPTION_FORWARD:   forwardMessage(messageRecord);                  return true;
    }
		
    return false;
  }
	
  private void verifyIdentity() {
    Intent verifyIdentityIntent = new Intent(this, VerifyIdentityActivity.class);
    verifyIdentityIntent.putExtra("recipient", recipients.getPrimaryRecipient());
    verifyIdentityIntent.putExtra("master_secret", masterSecret);
    startActivity(verifyIdentityIntent);		
  }
	
  private void verifyKeys() {
    Intent verifyKeysIntent = new Intent(this, VerifyKeysActivity.class);
    verifyKeysIntent.putExtra("recipient", recipients.getPrimaryRecipient());
    verifyKeysIntent.putExtra("master_secret", masterSecret);
    startActivity(verifyKeysIntent);
  }
	
  private void initiateSecureSession() {
    Recipient recipient         = recipients.getPrimaryRecipient();
    String recipientName        = (recipient.getName() == null ? recipient.getNumber() : recipient.getName());
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle("Initiate Secure Session?");
    builder.setIcon(android.R.drawable.ic_dialog_info);
    builder.setCancelable(true);
    builder.setMessage("Initiate secure session with " + recipientName + "?");
    builder.setPositiveButton(R.string.yes, new InitiateSecureSessionListener());
    builder.setNegativeButton(R.string.no, null);
    builder.show();
  }
	
  private void abortSecureSession() {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle("Abort Secure Session Confirmation");
    builder.setIcon(android.R.drawable.ic_dialog_alert);
    builder.setCancelable(true);
    builder.setMessage("Are you sure that you want to abort this secure session?");
    builder.setPositiveButton(R.string.yes, new AbortSessionListener());
    builder.setNegativeButton(R.string.no, null);
    builder.show();
  }
	
  private void dial(Recipient recipient) {
    if (recipient == null) {
      // XXX toast?
      return;
    }
	    
    Intent dialIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + recipient.getNumber()));
    startActivity(dialIntent);
  }

  private void displayMessageDetails(MessageRecord messageRecord) {
    String sender    = messageRecord.getRecipients().getPrimaryRecipient().getNumber();
    String transport = messageRecord.isMms() ? "mms" : "sms";
    long date        = messageRecord.getDate();

    SimpleDateFormat dateFormatter = new SimpleDateFormat("EEE MMM d, yyyy 'at' hh:mm:ss a zzz");
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle("Message Details");
    builder.setIcon(android.R.drawable.ic_dialog_info);
    builder.setCancelable(false);
    builder.setMessage("Sender: " + sender + "\nTransport: " + transport.toUpperCase() + "\nSent/Received: " + dateFormatter.format(new Date(date)));
    builder.setPositiveButton("Ok", null);
    builder.show();
  }
	
  private void forwardMessage(MessageRecord messageRecord) {
    Intent composeIntent = new Intent(this, ComposeMessageActivity.class);
    composeIntent.putExtra("forwarded_message", messageRecord.getBody());
    composeIntent.putExtra("master_secret", masterSecret);
    startActivity(composeIntent);
  }
	
  private void redecryptMessage(MessageRecord messageRecord, String address, String body) {
    DatabaseFactory.getEncryptingSmsDatabase(this).markAsDecrypting(messageRecord.getId());
    DecryptingQueue.scheduleDecryption(this, masterSecret, messageRecord.getId(), address, body);
  }
	
  private void copyMessageToClipboard(MessageRecord messageRecord) {
    String body = messageRecord.getBody();
    if (body == null) return;

    ClipboardManager clipboard = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
    clipboard.setText(body);
  }
	
  private void deleteMessage(MessageRecord messageRecord) {
    long messageId   = messageRecord.getId();
    String transport = messageRecord.isMms() ? "mms" : "sms";

    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle("Delete Message Confirmation");
    builder.setIcon(android.R.drawable.ic_dialog_alert);
    builder.setCancelable(true);
    builder.setMessage("Are you sure that you want to permanently delete this message?");
    builder.setPositiveButton(R.string.yes, new DeleteMessageListener(messageId, transport));
    builder.setNegativeButton(R.string.no, null);
    builder.show();		
  }
	
  private void deleteThread() {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle("Delete Thread Confirmation");
    builder.setIcon(android.R.drawable.ic_dialog_alert);
    builder.setCancelable(true);
    builder.setMessage("Are you sure that you want to permanently delete this conversation?");
    builder.setPositiveButton(R.string.yes, new DeleteThreadListener());
    builder.setNegativeButton(R.string.no, null);
    builder.show();
  }
	
  private void addAttachment() {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setIcon(R.drawable.ic_dialog_attach);
    builder.setTitle("Add attachment");
    builder.setAdapter(attachmentAdapter, new AttachmentTypeListener());		
    builder.show();
  }
	
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
      Toast.makeText(this, "Sorry, there was an error setting your attachment.", Toast.LENGTH_LONG).show();
      Log.w("ComposeMessageActivity", e);
    }
  }
	
  private void addAttachmentVideo(Uri videoUri) {
    try {
      attachmentManager.setVideo(videoUri);			
    } catch (IOException e) {
      attachmentManager.clear();
      Toast.makeText(this, "Sorry, there was an error setting your attachment.", Toast.LENGTH_LONG).show();
      Log.w("ComposeMessageActivity", e);
    } catch (MediaTooLargeException e) {
      attachmentManager.clear();
      Toast.makeText(this, "Sorry, the selected video exceeds message size restrictions.", Toast.LENGTH_LONG).show();
      Log.w("ComposeMessageActivity", e);
    }
  }
	
  private void addAttachmentAudio(Uri audioUri) {
    try {
      attachmentManager.setAudio(audioUri);			
    } catch (IOException e) {
      attachmentManager.clear();
      Toast.makeText(this, "Sorry, there was an error setting your attachment.", Toast.LENGTH_LONG).show();
      Log.w("ComposeMessageActivity", e);			
    } catch (MediaTooLargeException e) {
      attachmentManager.clear();
      Toast.makeText(this, "Sorry, the selected audio exceeds message size restrictions.", Toast.LENGTH_LONG).show();
      Log.w("ComposeMessageActivity", e);			
    }
  }
			
  private void initializeReceivers() {
    killActivityReceiver   = new KillActivityReceiver();
    securityUpdateReceiver = new SecurityUpdateReceiver();
		
    registerReceiver(killActivityReceiver, new IntentFilter(KeyCachingService.PASSPHRASE_EXPIRED_EVENT), KeyCachingService.KEY_PERMISSION, null);
    registerReceiver(securityUpdateReceiver, new IntentFilter(KeyExchangeProcessor.SECURITY_UPDATE_EVENT), KeyCachingService.KEY_PERMISSION, null);
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
		
  private void initializeSecurity(Recipients recipients) {
    if (recipients != null && recipients.isSingleRecipient() && KeyUtil.isSessionFor(this, recipients.getPrimaryRecipient())) {
      sendButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_lock_small, 0);
      sendButton.setCompoundDrawablePadding(10);
      this.sendEncrypted       = true;			
      this.characterCalculator = new EncryptedCharacterCalculator();
    } else {
      sendButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
      this.sendEncrypted       = false;
      this.characterCalculator = new CharacterCalculator();
    }		
		
    calculateCharactersRemaining();
  }
		
  private void initializeResources() {
    recipientsPanel     = (RecipientsPanel)findViewById(R.id.recipients);
    conversationView    = (ListView)findViewById(R.id.conversation);
    recipients          = getIntent().getParcelableExtra("recipients");
    threadId            = getIntent().getLongExtra("thread_id", -1);
    addContactButton    = (ImageButton)findViewById(R.id.contacts_button);
    sendButton          = (Button)findViewById(R.id.send_button);
    composeText         = (EditText)findViewById(R.id.embedded_text_editor);
    masterSecret        = (MasterSecret)getIntent().getParcelableExtra("master_secret");
    charactersLeft      = (TextView)findViewById(R.id.space_left);
    titleBar            = (TextView)findViewById(R.id.title_bar);
    greyLock            = findViewById(R.id.secure_indicator);
    redLock             = findViewById(R.id.secure_indicator_red);
		
    attachmentAdapter   = new AttachmentTypeSelectorAdapter(this);
    attachmentManager   = new AttachmentManager(this);
					
    AuthenticityCheckListener authenticityListener = new AuthenticityCheckListener();
    SendButtonListener sendButtonListener = new SendButtonListener();
		
    recipientsPanel.setPanelChangeListener(new RecipientsPanelChangeListener());
    sendButton.setOnClickListener(sendButtonListener);
    addContactButton.setOnClickListener(new AddRecipientButtonListener());
    composeText.setOnKeyListener(new ComposeKeyPressedListener());
    composeText.addTextChangedListener(new OnTextChangedListener());
    composeText.setOnEditorActionListener(sendButtonListener);
    greyLock.setOnClickListener(authenticityListener);
    redLock.setOnClickListener(authenticityListener);
    this.registerForContextMenu(conversationView);
    this.registerForContextMenu(sendButton);
		
    if (getIntent().getStringExtra("forwarded_message") != null)
      composeText.setText("FWD: " + getIntent().getStringExtra("forwarded_message"));
  }

  private void initializeTitleBarSecurity() {
    redLock.setVisibility(View.GONE);
    greyLock.setVisibility(View.GONE);
		
    if (recipients != null && recipients.isSingleRecipient() && KeyUtil.isSessionFor(this, recipients.getPrimaryRecipient())) {			
      Recipient recipient = recipients.getPrimaryRecipient();
      AuthenticityCalculator.setAuthenticityStatus(this, recipient, masterSecret, greyLock, redLock, titleBar);
    }
  }
	
  private void initializeTitleBar() {
    if (recipients != null && recipients.isSingleRecipient()) {
      String name = recipients.getPrimaryRecipient().getName();
			
      if (name == null || name.trim().length() == 0)
        name = recipients.getPrimaryRecipient().getNumber();
			
      titleBar.setText(name);
    } else {
      titleBar.setText("Compose Message");
    }		

    initializeTitleBarSecurity();
  }
	
  private void initializeColors() {
    if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(ApplicationPreferencesActivity.DARK_CONVERSATION_PREF, false)) {
      ((LinearLayout)findViewById(R.id.layout_container)).setBackgroundDrawable(getResources().getDrawable(R.drawable.softgrey_background));
    }
  }
	
  private void calculateCharactersRemaining() {
    int charactersSpent                               = composeText.getText().length();		
    CharacterCalculator.CharacterState characterState = characterCalculator.calculateCharacters(charactersSpent);		
    charactersLeft.setText(characterState.charactersRemaining + "/" + characterState.maxMessageSize + " (" + characterState.messagesSpent + ")");
  }
	
  private void initializeRecipientsInput() {
    recipientsPanel.setVisibility(View.VISIBLE);
        
    if (this.recipients != null) {
      recipientsPanel.addRecipients(this.recipients);
    }
  }
		
  private void initializeConversationAdapter() {
    Cursor cursor       = DatabaseFactory.getMmsSmsDatabase(this).getConversation(threadId);
    conversationAdapter = new ConversationAdapter(recipients, threadId, this, cursor, masterSecret, new FailedIconClickHandler());
    conversationView.setAdapter(conversationAdapter);
    conversationView.setItemsCanFocus(true);
    conversationView.setVisibility(View.VISIBLE);
		
    recipientsPanel.setVisibility(View.GONE);
    composeText.requestFocus();
  }
	
  private boolean isExistingConversation() {
    return this.recipients != null && this.threadId != -1;
  }
		
  private Recipients getRecipients() throws RecipientFormattingException {
    if (isExistingConversation()) return this.recipients;
    else						  return recipientsPanel.getRecipients();
  }
	
  private String getMessage() throws InvalidMessageException {
    SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
    String rawText       = composeText.getText().toString();
		
    if (rawText.length() < 1 && !attachmentManager.isAttachmentPresent()) 
      throw new InvalidMessageException("Message is empty!");
		
    if (!sendEncrypted && sp.getBoolean(ApplicationPreferencesActivity.WHITESPACE_PREF, true) && rawText.length() <= 145)
      rawText = rawText + "             ";

    return rawText;
  }
	
  private void sendComplete(Recipients recipients, long threadId) {
    attachmentManager.clear();
    recipientsPanel.disable();
    composeText.setText("");
		
    this.recipients = recipients;
    this.threadId   = threadId;
		
    if (this.conversationAdapter == null) {
      initializeConversationAdapter();
      this.recipientsPanel.setVisibility(View.GONE);
      initializeTitleBar();
    }		
  }
	
  private void sendMessage(boolean sendEncrypted) {
    try {
      Recipients recipients   = getRecipients();
      String message          = getMessage();				
      long allocatedThreadId;
			
      if (attachmentManager.isAttachmentPresent()) {
        allocatedThreadId = MessageSender.sendMms(ComposeMessageActivity.this, masterSecret, recipients, 
                                                  threadId, attachmentManager.getSlideDeck(), message, 
                                                  sendEncrypted);
      } else if (recipients.isEmailRecipient()) {
        allocatedThreadId = MessageSender.sendMms(ComposeMessageActivity.this, masterSecret, recipients, 
                                                  threadId, new SlideDeck(), message, sendEncrypted);
      } else {
        allocatedThreadId = MessageSender.send(ComposeMessageActivity.this, masterSecret, recipients, 
                                               threadId, message, sendEncrypted);
      }
			
      sendComplete(recipients, allocatedThreadId);
      MessageNotifier.updateNotification(ComposeMessageActivity.this, false);
    } catch (RecipientFormattingException ex) {
      Toast.makeText(ComposeMessageActivity.this, "Recipient is not a valid SMS or email address!", Toast.LENGTH_LONG).show();
      Log.w("compose", ex);
    } catch (InvalidMessageException ex) {
      Toast.makeText(ComposeMessageActivity.this, "Message is empty!", Toast.LENGTH_SHORT).show();
      Log.w("compose", ex);
    } catch (MmsException e) {
      Log.w("ComposeMessageActivity", e);
    }
  }

  // Listeners
	
  private class KillActivityReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      finish();
    }
  }
	
  private class SecurityUpdateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      if (intent.getLongExtra("thread_id", -1) == -1)
        return;
			
      if (intent.getLongExtra("thread_id", -1) == threadId) {
        initializeSecurity(recipients);
        initializeTitleBar();
        calculateCharactersRemaining();
      }
    }
  }
	
  private class InitiateSecureSessionListener implements DialogInterface.OnClickListener {
    public void onClick(DialogInterface dialogInterface, int which) {
      KeyExchangeInitiator.initiate(ComposeMessageActivity.this, masterSecret, recipients.getPrimaryRecipient(), true);
    }		
  }
	
  private class FailedIconClickHandler extends Handler {
    @Override
    public void handleMessage(android.os.Message message) {
      String failedMessageText = (String)message.obj;
      ComposeMessageActivity.this.composeText.setText(failedMessageText);
    }
  }
	
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
          if (PreferenceManager.getDefaultSharedPreferences(ComposeMessageActivity.this).getBoolean("pref_enter_sends", false)) {
            sendButton.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
            sendButton.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));					
            return true;
          }
        }
      }
      
      return false;
    }
  }
	
  private class AuthenticityCheckListener implements OnClickListener {
    public void onClick(View clicked) {
      String message = null;
			
      if      (clicked == greyLock)   message = "This session is verified to be authentic.";
      else if (clicked == redLock)    message = "Warning, this session has not yet been verified to be authentic.  You should verify your session or the identity key of the person you are communicating with.";
			
      AlertDialog.Builder builder = new AlertDialog.Builder(ComposeMessageActivity.this);
      builder.setTitle("Authenticity");
      builder.setIcon(android.R.drawable.ic_dialog_info);
      builder.setCancelable(false);
      builder.setMessage(message);
      builder.setPositiveButton("Ok", null);
      builder.show();
    }		
  }
	
  private class DeleteThreadListener implements DialogInterface.OnClickListener {
    public void onClick(DialogInterface dialog, int which) {
      if (threadId > 0) {
        DatabaseFactory.getThreadDatabase(ComposeMessageActivity.this).deleteConversation(threadId);
        finish();
      }		
    }		
  };
	
  private class DeleteMessageListener implements DialogInterface.OnClickListener {
    private final long messageId;
    private final String transport;
		
    public DeleteMessageListener(long messageId, String transport) {
      this.messageId = messageId;
      this.transport = transport;
    }
		
    public void onClick(DialogInterface dialog, int which) {
      Log.w("ComposeMessageActivity", "Calling delete on: " + messageId);
      if (transport.equals("mms"))
        DatabaseFactory.getMmsDatabase(ComposeMessageActivity.this).delete(messageId);
      else
        DatabaseFactory.getSmsDatabase(ComposeMessageActivity.this).deleteMessage(messageId);
    }
  }
	
  private class AbortSessionListener implements DialogInterface.OnClickListener {
    public void onClick(DialogInterface dialog, int which) {
      if (recipients != null && recipients.isSingleRecipient()) {
        KeyUtil.abortSessionFor(ComposeMessageActivity.this, recipients.getPrimaryRecipient());
        initializeSecurity(recipients);
        initializeTitleBar();
      }
    }
  }
	
  private class AddRecipientButtonListener implements OnClickListener {
    public void onClick(View v) {
      Intent intent = new Intent(ComposeMessageActivity.this, ContactSelectionActivity.class);
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
      initializeSecurity(recipients);
    }
  }
	
  private class SendButtonListener implements OnClickListener, TextView.OnEditorActionListener {		
    public void onClick(View v) {
      sendMessage(sendEncrypted);
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
	
}
