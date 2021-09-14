package org.thoughtcrime.securesms.export;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.AssetManager;
import android.net.Uri;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.NoExternalStorageException;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.StorageUtil;
import org.thoughtcrime.securesms.util.Util;
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
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * The ChatExportZipUtil Class allows to create a zip file and
 * attach to it all content and a chatToExport xml file, also
 * can include a html-viewer
 *
 * @author  @anlaji
 * @version 2.5 add_export_chats_feature
 * @since   2021-09-14
 */

public class ChatExportZipUtil extends ProgressDialogAsyncTask<ChatExportZipUtil.Attachment, Void, Pair<Integer, String>> {

    private static final String TAG = ChatExportZipUtil.class.getSimpleName ();

    private static final int BUFFER               = 1024;

    private static final int SUCCESS              = 0;
    private static final int FAILURE              = 1;
    private static final int WRITE_ACCESS_FAILURE = 2;


    private static final String HTML_VIEWER_PATH        = "chatexport.htmlviewer/viewer.html";
    private static final String HTML_VIEWER_NAME        = "viewer.html";
    private static final String HTML_VIEWER_JQUERY_PATH = "chatexport.htmlviewer/jquery-3.6.0.min.js";
    private static final String HTML_VIEWER_JQUERY_NAME = "jquery-3.6.0.min.js";
    private static final String HTML_VIEWER_ICON_PATH   = "chatexport.htmlviewer/signal-app.png";
    private static final String HTML_VIEWER_ICON_NAME   = "signal-app.png";
    private static final String XML_FILENAME            = "chat.xml";
    private static final String FILENAME_FORMAT         = "/%s-%s_signalChatExport";

    @SuppressLint("SimpleDateFormat")
    private static final SimpleDateFormat dateFormatter = new SimpleDateFormat ("yyyy-MM-dd-HH-mm-ss-SSS");



    private final WeakReference<Context> contextReference;
    private final int                    attachmentCount;
    private final Uri                    storageTreeUri;
    private Uri                          storageUri;
    private ZipOutputStream              out;


    public ChatExportZipUtil (Context context, long threadId) throws IOException, NoExternalStorageException {
        this(context, null, 0, threadId);
    }

    public ChatExportZipUtil (Context context, Uri storagePath, int count, long threadId) throws IOException, NoExternalStorageException {
        super (context,
                context.getResources ().getString (R.string.ExportZip_start_to_export),
                context.getResources ().getQuantityString (R.plurals.ExportZip_adding_n_attachments_to_media_folder, count, count));
        this.storageTreeUri = storagePath;
        this.setStorageUri(threadId);
        this.contextReference = new WeakReference<> (context);
        this.attachmentCount = count;
        this.setZipOutputStream(threadId);
    }

    public ZipOutputStream getZipOutputStream(long threadId) throws IOException {
        try {
            FileOutputStream dest = (FileOutputStream) getContext ().getContentResolver().openOutputStream (getStorageUri ());
            return new ZipOutputStream(new BufferedOutputStream(dest));
        } catch (IOException e) {
            e.printStackTrace ();
            throw new IOException("Chat export file had an error.\"");
        }
    }

    private DocumentFile instantiateZipFile (long threadId) {
        DocumentFile pickedDir = DocumentFile.fromTreeUri(getContext (), getStorageTreeUri ());
        String fileName = createFileName(threadId);
        return pickedDir.createFile("application/zip", fileName);
    }


    private String createFileName (long threadId) {
        ThreadDatabase db = DatabaseFactory.getThreadDatabase (getContext ());
        Recipient r = db.getRecipientForThreadId(threadId);
        String groupName = null;
        if (r != null) groupName = r.getDisplayName (getContext ());
        if (groupName != null) groupName = groupName.replaceAll("[^a-zA-Z0-9.\\-]", "_");
        return String.format(FILENAME_FORMAT,
                dateFormatter.format (new Date()),
                groupName);
    }

    protected void startToExport(Context context, boolean hasViewer, String data) {
        addXMLFile (XML_FILENAME, data);
        if(hasViewer) includeHTMLViewerToZip (context);
    }

    private void includeHTMLViewerToZip (Context context) {
        try {
            AssetManager assetManager = context.getAssets();
            addFile (HTML_VIEWER_NAME, assetManager.open (HTML_VIEWER_PATH));
            addFile (HTML_VIEWER_JQUERY_NAME, assetManager.open (HTML_VIEWER_JQUERY_PATH));
            addFile (HTML_VIEWER_ICON_NAME, assetManager.open (HTML_VIEWER_ICON_PATH));
        } catch (IOException e) {
            e.printStackTrace();
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


    @NonNull
    public static String generateOutputFileName (@NonNull String contentType, long timestamp , @NonNull String uriPathSegment) {
        MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton ();
        String extension = mimeTypeMap.getExtensionFromMimeType (contentType);
        String base = "signal-" + dateFormatter.format (timestamp) + "-" + uriPathSegment;
        if (extension == null) extension = "attach";

        return base + "." + extension;
    }


    public void addFile (String name, InputStream data) {
        try {
            byte[] buffer = new byte[1024];
            ZipEntry zipEntry = new ZipEntry(name);
            getOut ().putNextEntry(zipEntry);

            BufferedInputStream origin = new BufferedInputStream (data, BUFFER);
            try {
                int len;
                while ((len = origin.read (buffer)) > 0) {
                    getOut ().write (buffer, 0, len);
                }
                origin.close ();
            } finally {
                data.close ();
            }
            getOut ().closeEntry ();

        } catch (IOException ex) {
            ex.printStackTrace ();
        }
    }

    @NonNull
    private String saveAttachment (Context context, Attachment attachment) throws IOException{
        String contentType, fileName, outputUri, attachmentName;

        contentType = getContentType (attachment);
        fileName = generateOutputFileName (contentType, attachment.date, attachment.uri.getPathSegments ().get (attachment.uri.getPathSegments ().size () - 1));
        outputUri = getMediaStoreContentPathForType (contentType);

        attachmentName = createOutputPath (outputUri, fileName);
        InputStream inputStream = PartAuthority.getAttachmentStream (context, attachment.uri);

        addFile (attachmentName, inputStream);
        return outputUri;
    }

    @NonNull
    private String getContentType (@NonNull Attachment attachment) {
        return Objects.requireNonNull (MediaUtil.getCorrectedMimeType (attachment.contentType));
    }

    public void addXMLFile (String name, @NonNull String data) {
        ByteArrayInputStream bais = new ByteArrayInputStream(data.getBytes());
        addFile (name, bais);
    }

    public void closeZip () throws IOException {
        getOut ().close();
    }


    @NonNull
    private String[] getFileNameParts(@NonNull String fileName) {
        String[] result = new String[2];
        String[] tokens = fileName.split("\\.(?=[^.]+$)");

        result[0] = tokens[0];

        if (tokens.length > 1) result[1] = tokens[1];
        else                   result[1] = "";

        return result;
    }

    @Override
    protected Pair<Integer, String> doInBackground(ChatExportZipUtil.Attachment... attachments) {
        if (attachments == null) {
            return new Pair<>(SUCCESS, null);
        }

        try {
            Context      context      = getContextReference ().get();
            String       directory    = null;

            if (!StorageUtil.canWriteToMediaStore()) {
                return new Pair<>(WRITE_ACCESS_FAILURE, null);
            }

            if (context == null) {
                return new Pair<>(FAILURE, null);
            }
            for (ChatExportZipUtil.Attachment attachment : attachments) {
                if (attachment != null) {
                    directory = saveAttachment(context, attachment);
                }
            }

            if (attachments.length > 1) return new Pair<>(SUCCESS, null);
            else                        return new Pair<>(SUCCESS, directory);
        } catch (IOException ioe) {
            Log.w(TAG, ioe);
            return new Pair<>(FAILURE, null);
        } finally{
            try {
                getOut ().close ();
            } catch (IOException e) {
                e.printStackTrace ();
            }
        }
    }
    @Override
    protected void onPostExecute(final Pair<Integer, String> result) {
        super.onPostExecute(result);
        final Context context = getContextReference ().get();
        if (context == null) return;

        switch (result.first()) {
            case FAILURE:
                if(getAttachmentCount () > 0)
                  Toast.makeText(context,
                        context.getResources().getQuantityText(R.plurals.ExportZip_error_while_adding_attachments_to_external_storage,
                                getAttachmentCount ()),
                        Toast.LENGTH_LONG).show();
                break;
            case SUCCESS:
                createFinishDialog(context);
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


    private void createFinishDialog (Context context) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);

        if(!Util.isEmpty (getStorageUri ().getPath ())) {
            if(getAttachmentCount () > 0)
                alertDialogBuilder.setMessage ("Chat export file saved to:\n" + getStorageTreeUri ().getEncodedPath ()
                    + "\n\nNumber of media files: " + getAttachmentCount () + "\n");
            else
                alertDialogBuilder.setMessage ("Chat export file saved to:\n" + getStorageTreeUri ().getEncodedPath ()
                        + "\n");
            alertDialogBuilder.setPositiveButton (R.string.open_export_directory,
                    (dialog, which) -> openDirectory (getStorageUri ()));
        }
        alertDialogBuilder.setTitle ("Export successful!");
        alertDialogBuilder.setIcon(R.drawable.ic_download_32);
        alertDialogBuilder.setCancelable(true);
        alertDialogBuilder.setNegativeButton(
                R.string.export_dialog_close,
                (dialog, which) -> dialog.cancel());
        AlertDialog alert = alertDialogBuilder.create();
        alert.show();
    }


    private void openDirectory (Uri storageDirectoryUri) {
        Intent intent = new Intent (Intent.ACTION_VIEW);
        intent.setDataAndType (storageDirectoryUri, "*/*");
        try{
            getContext ().startActivity (intent);
        }
        catch ( ActivityNotFoundException e) {
            e.printStackTrace ();
        }
    }

    public WeakReference<Context> getContextReference () {
        return contextReference;
    }

    public int getAttachmentCount () {
        return attachmentCount;
    }


    private Uri getStorageTreeUri () {
        return storageTreeUri;
    }

    public Uri getStorageUri () {
        return storageUri;
    }

    private void setStorageUri (Uri storageUri) {
        this.storageUri = storageUri;
    }

    private ZipOutputStream getOut () {
        return out;
    }

    private void setOut (ZipOutputStream out) {
        this.out = out;
    }

    private void setStorageUri (long threadId) {
        DocumentFile zipFile = instantiateZipFile(threadId);
        setStorageUri (zipFile.getUri ());
    }

    private void setZipOutputStream (long threadId) throws IOException {
        setOut (getZipOutputStream (threadId));
    }

    public static class Attachment {
        private final Uri    uri;
        private final String contentType;
        private final long   date;
        private final long   size;

        public Attachment(Uri uri, String contentType,
                          long date, long size)
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
