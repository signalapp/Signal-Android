package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.content.DialogInterface.OnClickListener;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import com.afollestad.materialdialogs.AlertDialogWrapper;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.mms.PartAuthority;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;

public class SaveAttachmentTask extends ProgressDialogAsyncTask<SaveAttachmentTask.Attachment, Void, Integer> {
  private static final String TAG = SaveAttachmentTask.class.getSimpleName();

  private static final int SUCCESS              = 0;
  private static final int FAILURE              = 1;
  private static final int WRITE_ACCESS_FAILURE = 2;

  private final WeakReference<Context> contextReference;
  private final WeakReference<MasterSecret> masterSecretReference;

  public SaveAttachmentTask(Context context, MasterSecret masterSecret) {
    super(context, R.string.ConversationFragment_saving_attachment, R.string.ConversationFragment_saving_attachment_to_storage);
    this.contextReference      = new WeakReference<>(context);
    this.masterSecretReference = new WeakReference<>(masterSecret);
  }

  @Override
  protected Integer doInBackground(SaveAttachmentTask.Attachment... attachments) {
    if (attachments == null || attachments.length != 1 || attachments[0] == null) {
      throw new AssertionError("must pass in exactly one attachment");
    }
    Attachment attachment = attachments[0];

    try {
      Context context           = contextReference.get();
      MasterSecret masterSecret = masterSecretReference.get();

      if (!Environment.getExternalStorageDirectory().canWrite()) {
        return WRITE_ACCESS_FAILURE;
      }

      if (context == null) {
        return FAILURE;
      }

      File        mediaFile   = constructOutputFile(attachment.contentType, attachment.date);
      InputStream inputStream = PartAuthority.getAttachmentStream(context, masterSecret, attachment.uri);

      if (inputStream == null) {
        return FAILURE;
      }

      OutputStream outputStream = new FileOutputStream(mediaFile);
      Util.copy(inputStream, outputStream);

      MediaScannerConnection.scanFile(context, new String[]{mediaFile.getAbsolutePath()},
                                      new String[]{attachment.contentType}, null);

      return SUCCESS;
    } catch (IOException ioe) {
      Log.w(TAG, ioe);
      return FAILURE;
    }
  }

  @Override
  protected void onPostExecute(Integer result) {
    super.onPostExecute(result);
    Context context = contextReference.get();
    if (context == null) return;

    switch (result) {
      case FAILURE:
        Toast.makeText(context, R.string.ConversationFragment_error_while_saving_attachment_to_storage,
            Toast.LENGTH_LONG).show();
        break;
      case SUCCESS:
        Toast.makeText(context, R.string.ConversationFragment_success_exclamation,
            Toast.LENGTH_LONG).show();
        break;
      case WRITE_ACCESS_FAILURE:
        Toast.makeText(context, R.string.ConversationFragment_unable_to_write_to_storage,
            Toast.LENGTH_LONG).show();
        break;
    }
  }

  private File constructOutputFile(String contentType, long timestamp) throws IOException {
    File sdCard = Environment.getExternalStorageDirectory();
    File outputDirectory;

    if (contentType.startsWith("video/")) {
      outputDirectory = new File(sdCard.getAbsoluteFile() + File.separator + Environment.DIRECTORY_MOVIES);
    } else if (contentType.startsWith("audio/")) {
      outputDirectory = new File(sdCard.getAbsolutePath() + File.separator + Environment.DIRECTORY_MUSIC);
    } else if (contentType.startsWith("image/")) {
      outputDirectory = new File(sdCard.getAbsolutePath() + File.separator + Environment.DIRECTORY_PICTURES);
    } else {
      outputDirectory = new File(sdCard.getAbsolutePath() + File.separator + Environment.DIRECTORY_DOWNLOADS);
    }

    if (!outputDirectory.mkdirs()) Log.w(TAG, "mkdirs() returned false, attempting to continue");

    MimeTypeMap       mimeTypeMap   = MimeTypeMap.getSingleton();
    String            extension     = mimeTypeMap.getExtensionFromMimeType(contentType);
    SimpleDateFormat  dateFormatter = new SimpleDateFormat("yyyy-MM-dd-HHmmss");
    String            base          = "textsecure-" + dateFormatter.format(timestamp);

    if (extension == null)
      extension = "attach";

    int i = 0;
    File file = new File(outputDirectory, base + "." + extension);
    while (file.exists()) {
      file = new File(outputDirectory, base + "-" + (++i) + "." + extension);
    }

    return file;
  }

  public static class Attachment {
    public Uri    uri;
    public String contentType;
    public long   date;

    public Attachment(Uri uri, String contentType, long date) {
      if (uri == null || contentType == null || date < 0) {
        throw new AssertionError("uri, content type, and date must all be specified");
      }
      this.uri         = uri;
      this.contentType = contentType;
      this.date        = date;
    }
  }

  public static void showWarningDialog(Context context, OnClickListener onAcceptListener) {
    AlertDialogWrapper.Builder builder = new AlertDialogWrapper.Builder(context);
    builder.setTitle(R.string.ConversationFragment_save_to_storage);
    builder.setIconAttribute(R.attr.dialog_alert_icon);
    builder.setCancelable(true);
    builder.setMessage(R.string.ConversationFragment_saving_this_media_to_storage_warning);
    builder.setPositiveButton(R.string.yes, onAcceptListener);
    builder.setNegativeButton(R.string.no, null);
    builder.show();
  }
}

