package org.thoughtcrime.securesms;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.text.ClipboardManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.CursorAdapter;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockListFragment;

import org.whispersystems.textsecure.util.FutureTaskListener;
import org.whispersystems.textsecure.util.ListenableFutureTask;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.loaders.ConversationLoader;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.util.Dialogs;
import org.thoughtcrime.securesms.util.DirectoryHelper;
import org.whispersystems.textsecure.crypto.MasterSecret;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Date;
import java.text.SimpleDateFormat;

public class ConversationFragment extends SherlockListFragment
  implements LoaderManager.LoaderCallbacks<Cursor>
{

  private ConversationFragmentListener listener;

  private MasterSecret masterSecret;
  private Recipients   recipients;
  private long         threadId;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
    return inflater.inflate(R.layout.conversation_fragment, container, false);
  }

  @Override
  public void onActivityCreated(Bundle bundle) {
    super.onActivityCreated(bundle);

    initializeResources();
    initializeListAdapter();
    registerForContextMenu(getListView());
  }

  @Override
  public void onCreateContextMenu (ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
    android.view.MenuInflater inflater = this.getSherlockActivity().getMenuInflater();
    menu.clear();

    inflater.inflate(R.menu.conversation_context, menu);

    MessageRecord messageRecord = getMessageRecord();
    if (messageRecord.isFailed()) {
      MenuItem resend = menu.findItem(R.id.menu_context_resend);
      resend.setVisible(true);
    }

    if (messageRecord instanceof MediaMmsMessageRecord) {
      inflater.inflate(R.menu.conversation_context_image, menu);
    }
  }

  @Override
  public boolean onContextItemSelected(android.view.MenuItem item) {
    final MessageRecord messageRecord = getMessageRecord();
    switch(item.getItemId()) {
    case R.id.menu_context_copy:           handleCopyMessage(messageRecord);     return true;
    case R.id.menu_context_delete_message: handleDeleteMessage(messageRecord);   return true;
    case R.id.menu_context_details:        handleDisplayDetails(messageRecord);  return true;
    case R.id.menu_context_forward:        handleForwardMessage(messageRecord);  return true;
    case R.id.menu_context_resend:         handleResendMessage(messageRecord);   return true;
    case R.id.menu_context_save_image:     handleSaveImage(messageRecord);       return true;
    }

    return false;
  }

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
    this.listener = (ConversationFragmentListener)activity;
  }

  private MessageRecord getMessageRecord() {
    Cursor cursor                     = ((CursorAdapter)getListAdapter()).getCursor();
    ConversationItem conversationItem = (ConversationItem)(((ConversationAdapter)getListAdapter()).newView(getActivity(), cursor, null));
    return conversationItem.getMessageRecord();
  }

  public void reload(Recipients recipients, long threadId) {
    this.recipients = recipients;
    this.threadId   = threadId;

    initializeListAdapter();
  }

  private void handleCopyMessage(MessageRecord message) {
    String body = message.getDisplayBody().toString();
    if (body == null) return;

    ClipboardManager clipboard = (ClipboardManager)getActivity()
        .getSystemService(Context.CLIPBOARD_SERVICE);
    clipboard.setText(body);
  }

  private void handleDeleteMessage(final MessageRecord message) {
    final long messageId   = message.getId();

    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    builder.setTitle(R.string.ConversationFragment_confirm_message_delete);
    builder.setIcon(Dialogs.resolveIcon(getActivity(), R.attr.dialog_alert_icon));
    builder.setCancelable(true);
    builder.setMessage(R.string.ConversationFragment_are_you_sure_you_want_to_permanently_delete_this_message);

    builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        if (message.isMms()) {
          DatabaseFactory.getMmsDatabase(getActivity()).delete(messageId);
        } else {
          DatabaseFactory.getSmsDatabase(getActivity()).deleteMessage(messageId);
        }
      }
    });

    builder.setNegativeButton(R.string.no, null);
    builder.show();
  }

  private void handleDisplayDetails(MessageRecord message) {
    long dateReceived = message.getDateReceived();
    long dateSent     = message.getDateSent();

    String transport;

    if      (message.isPending()) transport = "pending";
    else if (message.isPush())    transport = "push";
    else if (message.isMms())     transport = "mms";
    else                          transport = "sms";

    SimpleDateFormat dateFormatter = new SimpleDateFormat("EEE MMM d, yyyy 'at' hh:mm:ss a zzz");
    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    builder.setTitle(R.string.ConversationFragment_message_details);
    builder.setIcon(Dialogs.resolveIcon(getActivity(), R.attr.dialog_info_icon));
    builder.setCancelable(true);

    if (dateReceived == dateSent || message.isOutgoing()) {
      builder.setMessage(String.format(getSherlockActivity()
                                       .getString(R.string.ConversationFragment_transport_s_sent_received_s),
                                       transport.toUpperCase(),
                                       dateFormatter.format(new Date(dateSent))));
    } else {
      builder.setMessage(String.format(getSherlockActivity()
                                       .getString(R.string.ConversationFragment_sender_s_transport_s_sent_s_received_s),
                                       message.getIndividualRecipient().getNumber(),
                                       transport.toUpperCase(),
                                       dateFormatter.format(new Date(dateSent)),
                                       dateFormatter.format(new Date(dateReceived))));
    }

    builder.setPositiveButton(android.R.string.ok, null);
    builder.show();
  }

  private void handleForwardMessage(MessageRecord message) {
    Intent composeIntent = new Intent(getActivity(), ConversationActivity.class);
    composeIntent.putExtra("forwarded_message", message.getDisplayBody().toString());
    composeIntent.putExtra("master_secret", masterSecret);
    startActivity(composeIntent);
  }

  private void handleResendMessage(MessageRecord message) {
    long messageId = message.getId();
    final Activity activity = getActivity();
    MessageSender.resend(activity, messageId, message.isMms());
  }

  private void handleSaveImage(final MessageRecord message) {
    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    builder.setTitle(R.string.ConversationFragment_save_to_sd_card);
    builder.setIcon(Dialogs.resolveIcon(getActivity(), R.attr.dialog_alert_icon));
    builder.setCancelable(true);
    builder.setMessage(R.string.ConversationFragment_this_media_has_been_stored_in_an_encrypted_database_warning);
    builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
        ThumbnailSaveListener savelistener = new ThumbnailSaveListener((MediaMmsMessageRecord)message);
        savelistener.saveToSdCard();
      }
    });
    builder.setNegativeButton(R.string.no, null);
    builder.show();
  }

  private void initializeResources() {
    String recipientIds = this.getActivity().getIntent().getStringExtra("recipients");

    this.masterSecret = (MasterSecret)this.getActivity().getIntent()
                          .getParcelableExtra("master_secret");
    this.recipients   = RecipientFactory.getRecipientsForIds(getActivity(), recipientIds, true);
    this.threadId     = this.getActivity().getIntent().getLongExtra("thread_id", -1);
  }

  private void initializeListAdapter() {
    if (this.recipients != null && this.threadId != -1) {
      this.setListAdapter(new ConversationAdapter(getActivity(), masterSecret,
                                                  new FailedIconClickHandler(),
                                                  (!this.recipients.isSingleRecipient()) || this.recipients.isGroupRecipient(),
                                                  DirectoryHelper.isPushDestination(getActivity(), this.recipients.getPrimaryRecipient())));
      getListView().setRecyclerListener((ConversationAdapter)getListAdapter());
      getLoaderManager().initLoader(0, null, this);
    }
  }

  @Override
  public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
    return new ConversationLoader(getActivity(), threadId);
  }

  @Override
  public void onLoadFinished(Loader<Cursor> arg0, Cursor cursor) {
    ((CursorAdapter)getListAdapter()).changeCursor(cursor);
  }

  @Override
  public void onLoaderReset(Loader<Cursor> arg0) {
    ((CursorAdapter)getListAdapter()).changeCursor(null);
  }

  private class FailedIconClickHandler extends Handler {
    @Override
    public void handleMessage(android.os.Message message) {
      if (listener != null) {
        listener.setComposeText((String)message.obj);
      }
    }
  }

  public interface ConversationFragmentListener {
    public void setComposeText(String text);
  }


  private class ThumbnailSaveListener extends Handler implements Runnable, FutureTaskListener<SlideDeck>, MediaScannerConnection.MediaScannerConnectionClient {
    private static final int SUCCESS              = 0;
    private static final int FAILURE              = 1;
    private static final int WRITE_ACCESS_FAILURE = 2;

    private Slide slide;
    private ProgressDialog progressDialog;
    private MediaScannerConnection mediaScannerConnection;
    private File mediaFile;
    private Context context;

    public ThumbnailSaveListener(final MediaMmsMessageRecord message) {
      this.context = getActivity();

      ListenableFutureTask<SlideDeck> slideDeck = message.getSlideDeck();
      slideDeck.setListener(this);
    }

    @Override
    public void onSuccess(final SlideDeck result) {
      if (result == null)
        return;

      for (Slide slide : result.getSlides()) {
        if (slide.hasImage()) {
          // that's our slide
          this.slide = slide;
        }
      }
    }

    @Override
    public void onFailure(Throwable error) {}

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
        Log.w("ConversationFragment", ioe);
        this.obtainMessage(FAILURE).sendToTarget();
      }
    }

    private File constructOutputFile() throws IOException {
      File sdCard = Environment.getExternalStorageDirectory();
      File outputDirectory;

      if (slide == null) {
        throw new IOException();
      }

      if (slide.hasVideo())
        outputDirectory = new File(sdCard.getAbsoluteFile() + File.separator + "Movies");
      else if (slide.hasAudio())
        outputDirectory = new File(sdCard.getAbsolutePath() + File.separator + "Music");
      else
        outputDirectory = new File(sdCard.getAbsolutePath() + File.separator + "Pictures");
      outputDirectory.mkdirs();

      MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
      String extension = mimeTypeMap.getExtensionFromMimeType(slide.getContentType());
      if (extension == null)
          extension = "attach";

      return File.createTempFile("textsecure", "." + extension, outputDirectory);
    }

    public void saveToSdCard() {
      progressDialog = new ProgressDialog(context);
      progressDialog.setTitle(context.getString(R.string.ConversationFragment_saving_attachment));
      progressDialog.setMessage(context.getString(R.string.ConversationFragment_saving_attachment_to_sd_card));
      progressDialog.setCancelable(false);
      progressDialog.setIndeterminate(true);
      progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
      progressDialog.show();
      new Thread(this).start();
    }

    @Override
    public void handleMessage(Message message) {
      switch (message.what) {
      case FAILURE:
        Toast.makeText(context, R.string.ConversationFragment_error_while_saving_attachment_to_sd_card,
                       Toast.LENGTH_LONG).show();
        break;
      case SUCCESS:
        Toast.makeText(context, R.string.ConversationFragment_success_exclamation,
                       Toast.LENGTH_LONG).show();
        break;
      case WRITE_ACCESS_FAILURE:
        Toast.makeText(context, R.string.ConversationFragment_unable_to_write_to_sd_card_exclamation,
                       Toast.LENGTH_LONG).show();
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

}
