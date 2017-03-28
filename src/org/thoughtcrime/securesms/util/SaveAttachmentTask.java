package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.util.task.ProgressDialogAsyncTask;
import org.whispersystems.libsignal.util.Pair;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;

public class SaveAttachmentTask extends ProgressDialogAsyncTask<SaveAttachmentTask.Attachment, Void, Pair<Integer, File>> {
  private static final String TAG = SaveAttachmentTask.class.getSimpleName();

  private static final int SUCCESS              = 0;
  private static final int FAILURE              = 1;
  private static final int WRITE_ACCESS_FAILURE = 2;

  private final WeakReference<Context>      contextReference;
  private final WeakReference<MasterSecret> masterSecretReference;
  private final WeakReference<View>         view;

  private final int attachmentCount;

  public SaveAttachmentTask(Context context, MasterSecret masterSecret, View view) {
    this(context, masterSecret, view, 1);
  }

  public SaveAttachmentTask(Context context, MasterSecret masterSecret, View view, int count) {
    super(context,
          context.getResources().getQuantityString(R.plurals.ConversationFragment_saving_n_attachments, count, count),
          context.getResources().getQuantityString(R.plurals.ConversationFragment_saving_n_attachments_to_sd_card, count, count));
    this.contextReference      = new WeakReference<>(context);
    this.masterSecretReference = new WeakReference<>(masterSecret);
    this.view                  = new WeakReference<>(view);
    this.attachmentCount       = count;
  }

  @Override
  protected Pair<Integer, File> doInBackground(SaveAttachmentTask.Attachment... attachments) {
    if (attachments == null || attachments.length == 0) {
      throw new AssertionError("must pass in at least one attachment");
    }

    try {
      Context      context      = contextReference.get();
      MasterSecret masterSecret = masterSecretReference.get();
      File         directory    = null;

      if (!Environment.getExternalStorageDirectory().canWrite()) {
        return new Pair<>(WRITE_ACCESS_FAILURE, null);
      }

      if (context == null) {
        return new Pair<>(FAILURE, null);
      }

      for (Attachment attachment : attachments) {
        if (attachment != null) {
          directory = saveAttachment(context, masterSecret, attachment);
          if (directory == null) return new Pair<>(FAILURE, null);
        }
      }

      if (attachments.length > 1) return new Pair<>(SUCCESS, null);
      else                        return new Pair<>(SUCCESS, directory);
    } catch (IOException ioe) {
      Log.w(TAG, ioe);
      return new Pair<>(FAILURE, null);
    }
  }

  private @Nullable File saveAttachment(Context context, MasterSecret masterSecret, Attachment attachment)
      throws IOException
  {
    String      contentType = MediaUtil.getCorrectedMimeType(attachment.contentType);
    File        mediaFile   = constructOutputFile(attachment.fileName, contentType, attachment.date);
    InputStream inputStream = PartAuthority.getAttachmentStream(context, masterSecret, attachment.uri);

    if (inputStream == null) {
      return null;
    }

    OutputStream outputStream = new FileOutputStream(mediaFile);
    Util.copy(inputStream, outputStream);

    MediaScannerConnection.scanFile(context, new String[]{mediaFile.getAbsolutePath()},
                                    new String[]{contentType}, null);

    return mediaFile.getParentFile();
  }

  @Override
  protected void onPostExecute(final Pair<Integer, File> result) {
    super.onPostExecute(result);
    final Context context = contextReference.get();
    if (context == null) return;

    switch (result.first()) {
      case FAILURE:
        Toast.makeText(context,
                       context.getResources().getQuantityText(R.plurals.ConversationFragment_error_while_saving_attachments_to_sd_card,
                                                              attachmentCount),
                       Toast.LENGTH_LONG).show();
        break;
      case SUCCESS:
        Snackbar snackbar = Snackbar.make(view.get(),
                                          context.getResources().getQuantityText(R.plurals.ConversationFragment_files_saved_successfully, attachmentCount),
                                          Snackbar.LENGTH_SHORT);

        if (result.second() != null) {
          snackbar.setDuration(Snackbar.LENGTH_LONG);
          snackbar.setAction(R.string.SaveAttachmentTask_open_directory, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
              Intent intent = new Intent(Intent.ACTION_VIEW);
              intent.setDataAndType(Uri.fromFile(result.second()), "resource/folder");
              if (intent.resolveActivityInfo(context.getPackageManager(), 0) != null)
              {
                context.startActivity(intent);
              }
            }
          });
        }

        snackbar.show();
        break;
      case WRITE_ACCESS_FAILURE:
        Toast.makeText(context, R.string.ConversationFragment_unable_to_write_to_sd_card_exclamation,
            Toast.LENGTH_LONG).show();
        break;
    }
  }

  private File constructOutputFile(@Nullable String fileName, String contentType, long timestamp)
      throws IOException
  {
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

    if (fileName == null) {
      MimeTypeMap      mimeTypeMap   = MimeTypeMap.getSingleton();
      String           extension     = mimeTypeMap.getExtensionFromMimeType(contentType);
      SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd-HHmmss");
      String           base          = "signal-" + dateFormatter.format(timestamp);

      if (extension == null) extension = "attach";

      fileName = base + "." + extension;
    }

    int  i    = 0;
    File file = new File(outputDirectory, fileName);

    while (file.exists()) {
      String[] fileParts = getFileNameParts(fileName);
      file = new File(outputDirectory, fileParts[0] + "-" + (++i) + "." + fileParts[1]);
    }

    return file;
  }

  private String[] getFileNameParts(String fileName) {
    String[] result = new String[2];
    String[] tokens = fileName.split("\\.(?=[^\\.]+$)");

    result[0] = tokens[0];

    if (tokens.length > 1) result[1] = tokens[1];
    else                   result[1] = "";

    return result;
  }

  public static class Attachment {
    public Uri    uri;
    public String fileName;
    public String contentType;
    public long   date;

    public Attachment(@NonNull Uri uri, @NonNull String contentType,
                      long date, @Nullable String fileName)
    {
      if (uri == null || contentType == null || date < 0) {
        throw new AssertionError("uri, content type, and date must all be specified");
      }
      this.uri         = uri;
      this.fileName    = fileName;
      this.contentType = contentType;
      this.date        = date;
    }
  }

  public static void showWarningDialog(Context context, OnClickListener onAcceptListener) {
    showWarningDialog(context, onAcceptListener, 1);
  }

  public static void showWarningDialog(Context context, OnClickListener onAcceptListener, int count) {
    AlertDialog.Builder builder = new AlertDialog.Builder(context);
    builder.setTitle(R.string.ConversationFragment_save_to_sd_card);
    builder.setIconAttribute(R.attr.dialog_alert_icon);
    builder.setCancelable(true);
    builder.setMessage(context.getResources().getQuantityString(R.plurals.ConversationFragment_saving_n_media_to_storage_warning,
                                                                count, count));
    builder.setPositiveButton(R.string.yes, onAcceptListener);
    builder.setNegativeButton(R.string.no, null);
    builder.show();
  }
}

