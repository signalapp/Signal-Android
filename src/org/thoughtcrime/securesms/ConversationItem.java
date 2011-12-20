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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.MessageRecord;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.MmsMessageRecord;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.service.SendReceiveService;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.text.Spannable;
import android.text.format.DateUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * A view that displays an individual conversation item within a conversation
 * thread.  Used by ComposeMessageActivity's ListActivity via a ConversationAdapter.
 * 
 * @author Moxie Marlinspike
 *
 */

public class ConversationItem extends LinearLayout {

  private Handler       failedIconHandler;
  private MessageRecord messageRecord;
  private MasterSecret  masterSecret;

  private final TextView bodyText;
  private final TextView dateText;
  private final ImageView pendingImage;
  private final ImageView secureImage;
  private final ImageView failedImage;
  private final ImageView keyImage;
  private final ImageView contactPhoto;
	
  private final ImageView mmsThumbnail;
  private final Button    mmsDownloadButton;
  private final TextView  mmsDownloadingLabel;
		
  private final FailedIconClickListener failedIconClickListener   = new FailedIconClickListener();
  private final MmsDownloadClickListener mmsDownloadClickListener = new MmsDownloadClickListener();
  private final ClickListener clickListener                       = new ClickListener();
  private final Context context;
	
  public ConversationItem(Context context) {
    super(context);
		
    LayoutInflater li = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    li.inflate(R.layout.conversation_item, this);
		
    this.context             = context;
    this.bodyText            = (TextView) findViewById(R.id.conversation_item_body);
    this.dateText            = (TextView) findViewById(R.id.conversation_item_date);
    this.pendingImage        = (ImageView)findViewById(R.id.sms_pending_indicator);
    this.secureImage         = (ImageView)findViewById(R.id.sms_secure_indicator);
    this.failedImage         = (ImageView)findViewById(R.id.sms_failed_indicator);
    this.keyImage            = (ImageView)findViewById(R.id.key_exchange_indicator);
    this.mmsThumbnail        = (ImageView)findViewById(R.id.image_view);
    this.mmsDownloadButton   = (Button)   findViewById(R.id.mms_download_button);
    this.mmsDownloadingLabel = (TextView) findViewById(R.id.mms_label_downloading);
    this.contactPhoto        = (ImageView)findViewById(R.id.contact_photo);
		
    setOnClickListener(clickListener);
    this.failedImage.setOnClickListener(failedIconClickListener);
    this.mmsDownloadButton.setOnClickListener(mmsDownloadClickListener);
		
    if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean(ApplicationPreferencesActivity.DARK_CONVERSATION_PREF, false)) {
      this.bodyText.setTextColor(Color.WHITE);
      this.dateText.setTextColor(Color.LTGRAY);
    }		
  }
	
  public void set(MasterSecret masterSecret, MessageRecord messageRecord, Handler failedIconHandler)
  {
    this.messageRecord     = messageRecord;
    this.masterSecret      = masterSecret;
    this.failedIconHandler = failedIconHandler;
		
    // Double-dispatch back to methods below.
    messageRecord.setOnConversationItem(this);
  }
	
  public MessageRecord getMessageRecord() {
    return messageRecord;
  }
	
  public void setMessageRecord(MessageRecord messageRecord) {
    setBody(messageRecord);
    setDate(messageRecord.getDate());
		
    setStatusIcons(messageRecord);
		
    setEvents(messageRecord);		
  }
	
  public void setMessageRecord(MmsMessageRecord messageRecord) {
    setMessageRecord((MessageRecord)messageRecord);
		
    if (messageRecord.isNotification())
      setMmsNotificationAttributes(messageRecord);
    else
      setMmsMediaAttributes(messageRecord);
  }
	
  private void setMmsNotificationAttributes(MmsMessageRecord messageRecord) {
    String messageSize = "Message size: " + messageRecord.getMessageSize() + " KB";
    String expires     = "Expires: " + DateUtils.getRelativeTimeSpanString(getContext(), messageRecord.getExpiration(), false);
		
    dateText.setText(messageSize + "\n" + expires);
		
    if (MmsDatabase.Types.isDisplayDownloadButton(messageRecord.getStatus())) {
      mmsDownloadButton.setVisibility(View.VISIBLE);
      mmsDownloadingLabel.setVisibility(View.GONE);
    } else {
      mmsDownloadingLabel.setText(MmsDatabase.Types.getLabelForStatus(messageRecord.getStatus()));
      mmsDownloadButton.setVisibility(View.GONE);
      mmsDownloadingLabel.setVisibility(View.VISIBLE);
    }
		
    if (MmsDatabase.Types.isHardError(messageRecord.getStatus()))
      failedImage.setVisibility(View.VISIBLE);
  }
	
  private void setMmsMediaAttributes(MmsMessageRecord messageRecord) {
    SlideDeck slideDeck = messageRecord.getSlideDeck();
    List<Slide> slides  = slideDeck.getSlides();
		
    Iterator<Slide> iterator = slides.iterator();
		
    while (iterator.hasNext()) {
      Slide slide = iterator.next();
      if (slide.hasImage()) {
        mmsThumbnail.setImageBitmap(slide.getThumbnail());
        mmsThumbnail.setOnClickListener(new ThumbnailClickListener(slide));
        mmsThumbnail.setOnLongClickListener(new ThumbnailSaveListener(slide));
        mmsThumbnail.setVisibility(View.VISIBLE);
        return;
      }
    }

    mmsThumbnail.setVisibility(View.GONE);
  }
	
  public void setHandler(Handler failedIconHandler) {
    this.failedIconHandler = failedIconHandler;
  }
		
  private void checkForAutoInitiate(MessageRecord messageRecord) {
    if (AutoInitiateActivity.isValidAutoInitiateSituation(context, masterSecret, messageRecord.getRecipients().getPrimaryRecipient(), messageRecord.getBody(), messageRecord.getThreadId())) {
      AutoInitiateActivity.exemptThread(context, messageRecord.getThreadId());
      Intent intent = new Intent();
      intent.setClass(context, AutoInitiateActivity.class);
      intent.putExtra("threadId", messageRecord.getThreadId());
      intent.putExtra("masterSecret", masterSecret);
      intent.putExtra("recipient", messageRecord.getRecipients().getPrimaryRecipient());
			
      context.startActivity(intent);
    }
  }
	
  private void setBodyText(MessageRecord messageRecord) {
    String address      = "";
    Recipient recipient = messageRecord.getMessageRecipient();
    String body         = messageRecord.getBody();
		
    if      (messageRecord.isKeyExchange() && messageRecord.isOutgoing())           body    = "\nSent key exchange message";
    else if (messageRecord.isProcessedKeyExchange() && !messageRecord.isOutgoing()) body    = "\nReceived and processed key exchange message.";
    else if (messageRecord.isStaleKeyExchange())                                    body    = "\nError, received stale key exchange message.";
    else if (messageRecord.isKeyExchange() && !messageRecord.isOutgoing())          body    = "\nReceived key exchange message, click to process";
    else if (messageRecord.isOutgoing())                                            address = "Me: ";
    else                                                                            address = (recipient.getName() == null ? recipient.getNumber() : recipient.getName()) + ": ";
		
    bodyText.setText(address + body, TextView.BufferType.SPANNABLE);
		
    if (messageRecord.isKeyExchange() || messageRecord.getEmphasis()) {
      ((Spannable)bodyText.getText()).setSpan(new ForegroundColorSpan(context.getResources().getColor(android.R.color.darker_gray)), address.length(), address.length() + body.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      ((Spannable)bodyText.getText()).setSpan(new StyleSpan(android.graphics.Typeface.ITALIC), 0, address.length() + body.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    } else {
      ((Spannable)bodyText.getText()).setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, address.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);					
    }		
  }

  private void setBodyBackground(MessageRecord messageRecord) {
    if (!PreferenceManager.getDefaultSharedPreferences(context).getBoolean(ApplicationPreferencesActivity.DARK_CONVERSATION_PREF, false)) {
      if (messageRecord.isOutgoing()) setBackgroundResource(R.drawable.conversation_item_background_lightblue);
      else                            setBackgroundResource(R.drawable.conversation_item_background);
    } else {
      if (messageRecord.isOutgoing()) setBackgroundResource(R.drawable.conversation_item_background_black);
      else                            setBackgroundResource(R.drawable.conversation_item_background_lightgrey);			
    }
  }

  private void setContactPhotoForUserIdentity() {
    String configuredContact = PreferenceManager.getDefaultSharedPreferences(context).getString(ApplicationPreferencesActivity.IDENTITY_PREF, null);

    try {
      if (configuredContact != null) {
        Recipient recipient = RecipientFactory.getRecipientForUri(context, Uri.parse(configuredContact));
        if (recipient != null) {
          contactPhoto.setImageBitmap(recipient.getContactPhoto());
          return;
        }
      } 
			
      if (hasLocalNumber()) {
        contactPhoto.setImageBitmap(RecipientFactory.getRecipientsFromString(context, getLocalNumber()).getPrimaryRecipient().getContactPhoto());
      } else {
        contactPhoto.setImageDrawable(context.getResources().getDrawable(R.drawable.ic_contact_picture));
      }
    } catch (RecipientFormattingException rfe) {
      Log.w("ConversationItem", rfe);
    }
  }
	
  private void setBodyImage(MessageRecord messageRecord) {
    Recipient recipient = messageRecord.getMessageRecipient();
		
    if (!PreferenceManager.getDefaultSharedPreferences(context).getBoolean(ApplicationPreferencesActivity.CONVERSATION_ICONS_PREF, ApplicationPreferencesActivity.showIcon()) ||
        messageRecord.isKeyExchange()) 
    {
      contactPhoto.setVisibility(View.GONE);
      return;
    }
		
    if (!messageRecord.isOutgoing()) contactPhoto.setImageBitmap(recipient.getContactPhoto());
    else                             setContactPhotoForUserIdentity();
		
    contactPhoto.setVisibility(View.VISIBLE);
  }
	
  private void setBody(MessageRecord messageRecord) {
    setBodyText(messageRecord);
    setBodyBackground(messageRecord);
    setBodyImage(messageRecord);
  }
	
  private String getLocalNumber() {
    return ((TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE)).getLine1Number();		
  }
	
  private boolean hasLocalNumber() {
    String number = getLocalNumber();
    return (number != null) && (number.trim().length() > 0);
  }
	
  private void setDate(long date) {
    dateText.setText("Sent: " + DateUtils.getRelativeTimeSpanString(getContext(), date, false));
  }
	
  private void setStatusIcons(MessageRecord messageRecord) {
    pendingImage.setVisibility(messageRecord.isPending() ? View.VISIBLE : View.GONE);
    failedImage.setVisibility(messageRecord.isFailed() ? View.VISIBLE : View.GONE);
    secureImage.setVisibility(messageRecord.isSecure() ? View.VISIBLE : View.GONE);
    keyImage.setVisibility(messageRecord.isKeyExchange() ? View.VISIBLE : View.GONE);
		
    mmsThumbnail.setVisibility(View.GONE);
    mmsDownloadButton.setVisibility(View.GONE);
    mmsDownloadingLabel.setVisibility(View.GONE);
  }
	
  private void setEvents(MessageRecord messageRecord) {		
    setClickable(messageRecord.isKeyExchange() && !messageRecord.isOutgoing());

    if (!messageRecord.isOutgoing() && messageRecord.getRecipients().isSingleRecipient())
      checkForAutoInitiate(messageRecord);		
  }
	
  private void handleKeyExchangeClicked() {
    Intent intent = new Intent(context, ReceiveKeyActivity.class);
    intent.putExtra("recipient", messageRecord.getRecipients().getPrimaryRecipient());
    intent.putExtra("body", messageRecord.getBody());
    intent.putExtra("thread_id", messageRecord.getThreadId());
    intent.putExtra("master_secret", masterSecret);
    intent.putExtra("sent", messageRecord.isOutgoing());
    context.startActivity(intent);
  }
	
  private class ThumbnailSaveListener extends Handler implements View.OnLongClickListener, Runnable, MediaScannerConnection.MediaScannerConnectionClient {
    private static final int SUCCESS              = 0;
    private static final int FAILURE              = 1;
    private static final int WRITE_ACCESS_FAILURE = 2;
		
    private final Slide slide;
    private ProgressDialog progressDialog;
    private MediaScannerConnection mediaScannerConnection;
    private File mediaFile;
		
    public ThumbnailSaveListener(Slide slide) {
      this.slide = slide;
    }

    public void run() {
      if (!Environment.getExternalStorageDirectory().canWrite()) {
        this.obtainMessage(WRITE_ACCESS_FAILURE).sendToTarget();
        return;
      }

      try {
        mediaFile                 = constructOutputFile();
        InputStream inputStream   = slide.getPartDataInputStream();
        OutputStream outputStream = new FileOutputStream(mediaFile);
				
        byte[] buffer = new byte[4096];
        int read;
				
        while ((read = inputStream.read(buffer)) != -1) {
          outputStream.write(buffer, 0, read);
        }
				
        outputStream.close();
        inputStream.close();
				
        mediaScannerConnection = new MediaScannerConnection(context, this);
        mediaScannerConnection.connect();
      } catch (IOException ioe) {
        Log.w("ConversationItem", ioe);
        this.obtainMessage(FAILURE).sendToTarget();
      }			
    }
		
    private File constructOutputFile() throws IOException {
      File sdCard = Environment.getExternalStorageDirectory();
      File outputDirectory;
			
      if (slide.hasVideo())
        outputDirectory = new File(sdCard.getAbsoluteFile() + File.separator + "Movies");
      else if (slide.hasAudio())
        outputDirectory = new File(sdCard.getAbsolutePath() + File.separator + "Music");
      else
        outputDirectory = new File(sdCard.getAbsolutePath() + File.separator + "Pictures");
			
      outputDirectory.mkdirs();
      return File.createTempFile("textsecure", ".attach", outputDirectory);
    }
		
    private void saveToSdCard() {
      progressDialog = new ProgressDialog(context);
      progressDialog.setTitle("Saving Attachment");
      progressDialog.setMessage("Saving attachment to SD card...");
      progressDialog.setCancelable(false);
      progressDialog.setIndeterminate(true);
      progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
      progressDialog.show();
      new Thread(this).start();
    }
		
    public boolean onLongClick(View v) {
      AlertDialog.Builder builder = new AlertDialog.Builder(context);
      builder.setTitle("Save to SD Card?");
      builder.setIcon(android.R.drawable.ic_dialog_alert);
      builder.setCancelable(true);
      builder.setMessage("This media has been stored in an encrypted database. The version you save to the SD card will no longer be encrypted, would you like to continue?");
      builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
          saveToSdCard();
        }
      });
      builder.setNegativeButton(R.string.no, null);
      builder.show();
		    
      return true;
    }
		
    @Override
    public void handleMessage(Message message) {
      switch (message.what) {
      case FAILURE:
        Toast.makeText(context, "Error while saving attachment to SD card!", Toast.LENGTH_LONG);
        break;
      case SUCCESS:
        Toast.makeText(context, "Success!", Toast.LENGTH_LONG);
        break;
      case WRITE_ACCESS_FAILURE:
        Toast.makeText(context, "Unable to write to SD Card!", Toast.LENGTH_LONG);
        break;
      }
			
      progressDialog.dismiss();
    }

    public void onMediaScannerConnected() {
      mediaScannerConnection.scanFile(mediaFile.getAbsolutePath(), slide.getContentType());
    }

    public void onScanCompleted(String path, Uri uri) {
      mediaScannerConnection.disconnect();
      this.obtainMessage(SUCCESS).sendToTarget();			
    }
  }
	
  private class ThumbnailClickListener implements View.OnClickListener {
    private final Slide slide;
		
    public ThumbnailClickListener(Slide slide) {
      this.slide = slide;
    }

    private void fireIntent() {
      Log.w("ConversationItem", "Clicked: " + slide.getUri() + " , " + slide.getContentType());
      Intent intent = new Intent(Intent.ACTION_VIEW);
      intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      intent.setDataAndType(slide.getUri(), slide.getContentType());
      context.startActivity(intent);
    }
		
    public void onClick(View v) {
      AlertDialog.Builder builder = new AlertDialog.Builder(context);
      builder.setTitle("View secure media?");
      builder.setIcon(android.R.drawable.ic_dialog_alert);
      builder.setCancelable(true);
      builder.setMessage("This media has been stored in an encrypted database. Unfortunately, to view it with an external content viewer currently requires the data to be temporarily decrypted and written to disk.  Are you sure that you would like to do this?");
      builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
          fireIntent();
        }
      });
      builder.setNegativeButton(R.string.no, null);
      builder.show();
    }		
  }
	
  private class MmsDownloadClickListener implements View.OnClickListener {
    public void onClick(View v) {
      Log.w("MmsDownloadClickListener", "Content location: " + new String(((MmsMessageRecord)messageRecord).getContentLocation()));
      mmsDownloadButton.setVisibility(View.GONE);
      mmsDownloadingLabel.setVisibility(View.VISIBLE);
			
      Intent intent = new Intent(context, SendReceiveService.class);
      intent.putExtra("content_location", new String(((MmsMessageRecord)messageRecord).getContentLocation()));
      intent.putExtra("message_id", ((MmsMessageRecord)messageRecord).getId());
      intent.putExtra("transaction_id", ((MmsMessageRecord)messageRecord).getTransactionId());
      intent.putExtra("thread_id", ((MmsMessageRecord)messageRecord).getThreadId());
      intent.setAction(SendReceiveService.DOWNLOAD_MMS_ACTION);
      context.startService(intent);
    }
  }
	
  private class FailedIconClickListener implements View.OnClickListener {
    public void onClick(View v) {
      if (failedIconHandler != null && !messageRecord.isKeyExchange()) {
        Message message = Message.obtain();
        message.obj     = messageRecord.getBody();
        failedIconHandler.dispatchMessage(message);
      }
    }
  }
		
  private class ClickListener implements View.OnClickListener {
    public void onClick(View v) {
      if (messageRecord.isKeyExchange()           && 
          !messageRecord.isOutgoing()             && 
          !messageRecord.isProcessedKeyExchange() &&
          !messageRecord.isStaleKeyExchange())
        handleKeyExchangeClicked();
    }		
  }

}
