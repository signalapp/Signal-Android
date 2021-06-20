package org.thoughtcrime.securesms.export;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;


import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.StorageUtil;
import org.thoughtcrime.securesms.util.task.ProgressDialogAsyncTask;
import org.whispersystems.libsignal.util.Pair;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * The ExportZipUtil Class allows to create a zip file and
 * attach to it all content and a chatToExport xml file, also
 * can include a html-viewer
 *
 * @author  @anlaji
 * @version 1.0
 * @since   2021-06-20
 */
public class ExportZipUtil extends ProgressDialogAsyncTask<ExportZipUtil.Attachment, Void, Pair<Integer, String>> {

    private static final String TAG = ExportZipUtil.class.getSimpleName ();

    static final int BUFFER = 1024;

    static final int SUCCESS                      = 0;
    private static final int FAILURE              = 1;
    private static final int WRITE_ACCESS_FAILURE = 2;


    private static final String HTML_VIEWER_PATH = "chatexport.htmlviewer/viewer.html";
    private static final String HTML_VIEWER_NAME = "viewer.html";
    private static final String HTML_VIEWER_JQUERY_PATH = "chatexport.htmlviewer/jquery-3.6.0.min.js";
    private static final String HTML_VIEWER_JQUERY_NAME = "jquery-3.6.0.min.js";
    private static final String HTML_VIEWER_ICON_PATH = "chatexport.htmlviewer/signal-app.png";
    private static final String HTML_VIEWER_ICON_NAME= "signal-app.png";
    private static final String XML_FILENAME = "chat.xml";

    private static final String STORAGE_DIRECTORY = Environment.DIRECTORY_DOWNLOADS;
    private static final String FILENAME_FORMAT = "/%s-%s_signalChatExport";

    @SuppressLint("SimpleDateFormat")
    static SimpleDateFormat dateFormatter = new SimpleDateFormat ("yyyy-MM-dd-HHmmss");


    private final WeakReference<Context> contextReference;

    private final int attachmentCount;
    HashMap<String, Uri> otherFiles;
    private final File zipFile;
    private final ZipOutputStream out;


    public ExportZipUtil (Context context, long threadId) throws IOException {
        this(context, 0, threadId, null);
    }

    public ExportZipUtil (Context context, int count, long threadId, HashMap<String, Uri> otherFiles) throws IOException {
        super (context,
                context.getResources ().getQuantityString (R.plurals.ExportZip_start_to_export, count, count),
                context.getResources ().getQuantityString (R.plurals.ExportZip_adding_n_attachments_to_media_folder, count, count));
        this.contextReference = new WeakReference<> (context);
        this.attachmentCount = count;
        this.otherFiles = otherFiles;
        this.zipFile = instantiateZipFile (threadId);
        this.out = getZipOutputStream ();
    }

    void startToExport(Context context, boolean hasViewer, String data) throws IOException {
        addXMLFile (XML_FILENAME, data);
        if(hasViewer) includeHTMLViewerToZip (context);
    }

    private void includeHTMLViewerToZip (Context context) throws IOException {
        addFile (HTML_VIEWER_NAME, context.getAssets().open(HTML_VIEWER_PATH));
        addFile (HTML_VIEWER_JQUERY_NAME, context.getAssets().open(HTML_VIEWER_JQUERY_PATH));
        addFile (HTML_VIEWER_ICON_NAME, context.getAssets().open(HTML_VIEWER_ICON_PATH));
    }

    private String getExternalPathToSaveZip () {
        File storage = Environment.getExternalStoragePublicDirectory(STORAGE_DIRECTORY);
        return storage.getAbsolutePath ();
    }

    private File instantiateZipFile (long threadId) {
        File root = new File(getExternalPathToSaveZip ());
        String groupName = DatabaseFactory.getThreadDatabase (getContext ()).getRecipientForThreadId (threadId).getDisplayName (getContext ());
        groupName = groupName.replaceAll("[^a-zA-Z0-9.\\-]", "_");
        String fileName = String.format(FILENAME_FORMAT,
                dateFormatter.format (new Date()),
                groupName);
        return new File(root.getAbsolutePath () + "/" + fileName + ".zip");
    }

    public ZipOutputStream getZipOutputStream() throws IOException {
        try {
            String zipPath = "";
            if(zipFile != null)
                zipPath = zipFile.getPath ();
            FileOutputStream dest = new FileOutputStream(zipPath+"/");
            return new ZipOutputStream(new BufferedOutputStream(dest));
        } catch (IOException e) {
            throw new IOException("Chat export file had an error.\"");
        }
    }

    public @NonNull
    static String getMediaStoreContentPathForType (@NonNull String contentType) {
        if (contentType.startsWith("video/")) {
            return "/Media/Signal Videos/";
        } else if (contentType.startsWith("audio/")) {
            return "/Media/Signal Audios/";
        } else if (contentType.startsWith("image/")) {
            if (contentType.endsWith ("gif")) return "/Media/Signal GIFs/";
            else if (contentType.endsWith ("webp")) return "/Media/Signal Stickers/";
            else return "/Media/Signal Images/";
        }
        else if (contentType.startsWith("application/")) {
            return "/Media/Signal Documents/";
        } else {
            return "/Media/Signal Other Things/";
        }
    }

    private String createOutputPath(@NonNull String outputUri, @NonNull String fileName)
            throws IOException {
        String[] fileParts = getFileNameParts (fileName);
        String base = fileParts[0];
        String extension = fileParts[1];

        File outputDirectory = new File (outputUri);
        File outputFile = new File (outputDirectory, base + "." + extension);

        int i = 0;
        while (outputFile.exists ()) {
            outputFile = new File (outputDirectory, base + "-" + (++i) + "." + extension);
        }

        if (outputFile.isHidden ()) {
            throw new IOException ("Specified name would not be visible");
        }
        return outputFile.getAbsolutePath ();
    }


    public static String generateOutputFileName (@NonNull String contentType, long date,@NonNull String uriPathSegment) {
        MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton ();
        String extension = mimeTypeMap.getExtensionFromMimeType (contentType);
        String base = "signal-" + dateFormatter.format (date) + uriPathSegment;
        if (extension == null) extension = "attach";

        return base + "." + extension;
    }


    public void addFile (String name, InputStream data) {
        try {
            byte[] buffer = new byte[1024];
            ZipEntry zipEntry = new ZipEntry(name);
            out.putNextEntry(zipEntry);

            BufferedInputStream origin = new BufferedInputStream (data, BUFFER);
            try {
                int len;
                while ((len = origin.read (buffer)) > 0) {
                    out.write (buffer, 0, len);
                }
                origin.close ();
            } finally {
                data.close ();
            }
            out.closeEntry ();

        } catch (IOException ex) {
            ex.printStackTrace ();
        }
    }

    private String saveAttachment (Context context, Attachment attachment) throws IOException{
        String      contentType = Objects.requireNonNull(MediaUtil.getCorrectedMimeType(attachment.contentType));
        String      fileName ;
        fileName = generateOutputFileName(contentType, attachment.date, attachment.uri.getPathSegments ().get (0));

        String           outputUri    = getMediaStoreContentPathForType(contentType);
        String           attachmentName     = createOutputPath( outputUri, fileName);

        InputStream inputStream = PartAuthority.getAttachmentStream(context, attachment.uri) ;
        addFile (attachmentName, inputStream);
        return outputUri;
    }

    public void addXMLFile (String name, String data) {
        ByteArrayInputStream bais = new ByteArrayInputStream(data.getBytes());
        addFile (name, bais);
    }

    public void closeZip () throws IOException {
        out.close();
    }


    private String[] getFileNameParts(String fileName) {
        String[] result = new String[2];
        String[] tokens = fileName.split("\\.(?=[^.]+$)");

        result[0] = tokens[0];

        if (tokens.length > 1) result[1] = tokens[1];
        else                   result[1] = "";

        return result;
    }

    @Override
    protected Pair<Integer, String> doInBackground(ExportZipUtil.Attachment... attachments) {
        if (attachments == null) {
            throw new AssertionError("must pass in at least one attachment");
        }

        try {
            Context      context      = contextReference.get();
            String       directory    = null;

            if (!StorageUtil.canWriteToMediaStore()) {
                return new Pair<>(WRITE_ACCESS_FAILURE, null);
            }

            if (context == null) {
                return new Pair<>(FAILURE, null);
            }
            for (ExportZipUtil.Attachment attachment : attachments) {
                if (attachment != null) {
                    directory = saveAttachment(context, attachment);
                    if (directory == null) return new Pair<>(FAILURE, null);
                }
            }

            if (attachments.length > 1) return new Pair<>(SUCCESS, null);
            else                        return new Pair<>(SUCCESS, directory);
        } catch (IOException ioe) {
            Log.w(TAG, ioe);
            return new Pair<>(FAILURE, null);
        } finally{
            try {
                out.close ();
            } catch (IOException e) {
                e.printStackTrace ();
            }
        }
    }
    @Override
    protected void onPostExecute(final Pair<Integer, String> result) {
        super.onPostExecute(result);
        final Context context = contextReference.get();
        if (context == null) return;

        switch (result.first()) {
            case FAILURE:
                Toast.makeText(context,
                        context.getResources().getQuantityText(R.plurals.ExportZip_error_while_adding_attachments_to_external_storage,
                                attachmentCount),
                        Toast.LENGTH_LONG).show();
                break;
            case SUCCESS:
                String message = !TextUtils.isEmpty(result.second())  ? context.getResources().getString(R.string.SaveAttachmentTask_saved_to, result.second())
                        : context.getResources().getString(R.string.SaveAttachmentTask_saved);
                Toast.makeText(context, message, Toast.LENGTH_LONG).show();
                break;
            case WRITE_ACCESS_FAILURE:
                Toast.makeText(context, R.string.ExportZip_unable_to_write_to_sd_card_exclamation,
                        Toast.LENGTH_LONG).show();
                break;
        }
        try {
            closeZip ();
        } catch (IOException e) {
            e.printStackTrace ();
        }
    }

    public static class Attachment {
        private final Uri    uri;
        private final String contentType;
        private final long   date;
        private final long   size;

        public Attachment(Uri uri, String contentType,
                          long date, @Nullable String fileName, long size)
        {
            if (uri == null || contentType == null || date < 0) {
                throw new AssertionError("uri, content type, and date must all be specified");
            }
            this.uri         = uri;
            this.contentType = contentType;
            this.date        = date;
            this.size        = size;
        }

        public long length () {
            return size;
        }
    }

    public static void showWarningDialog(Context context, DialogInterface.OnClickListener onAcceptListener, int count) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.ExportZip_save_to_external_storage);
        builder.setIcon(R.drawable.ic_warning);
        builder.setCancelable(true);
        builder.setMessage(context.getResources().getQuantityString(R.plurals.ExportZip_adding_n_media_to_storage_warning,
                count, count));
        builder.setPositiveButton(R.string.yes, onAcceptListener);
        builder.setNegativeButton(R.string.no, null);
        builder.show();
    }
}
