package org.thoughtcrime.securesms.util.movedocumentfiles;

import android.content.ContentResolver;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import org.thoughtcrime.securesms.util.movedocumentfiles.internal.FileCouldntBeCopied;
import org.thoughtcrime.securesms.util.movedocumentfiles.internal.InputStreamCouldntBeCreated;
import org.thoughtcrime.securesms.util.movedocumentfiles.internal.MoveDocumentFileException;
import org.thoughtcrime.securesms.util.movedocumentfiles.internal.OutputStreamCouldntBeCreated;
import org.thoughtcrime.securesms.util.movedocumentfiles.internal.TargetDirectoryNotWritable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class Util {
    private static final int BUFFER_SIZE = 1024;

    private static void validateWritable(DocumentFile directory) throws TargetDirectoryNotWritable {
        if (!directory.isDirectory()) {
            throw(new TargetDirectoryNotWritable());
        }
        if (!directory.canWrite()) {
            throw(new TargetDirectoryNotWritable());
        }
    }

    private static InputStream createInputStream(ContentResolver contentResolver,
                                                DocumentFile documentFile) throws InputStreamCouldntBeCreated {
        Uri uri = documentFile.getUri();
        try {
            return contentResolver.openInputStream(uri);
        } catch (FileNotFoundException e) {
            throw(new InputStreamCouldntBeCreated());
        }
    }

    private static OutputStream createOutputStream(ContentResolver contentResolver,
                                                  DocumentFile directory,
                                                  String mimeType,
                                                  String displayName) throws OutputStreamCouldntBeCreated {
        DocumentFile dest = directory.createFile(mimeType, displayName);
        if (dest == null) {
            throw(new OutputStreamCouldntBeCreated());
        }
        Uri uri = dest.getUri();
        try {
            return contentResolver.openOutputStream(uri);
        } catch (FileNotFoundException e) {
            throw(new OutputStreamCouldntBeCreated());
        }
    }

    private static void copy(Callback callback, InputStream inputStream, OutputStream outputStream) throws FileCouldntBeCopied {
        byte[] buffer = new byte[BUFFER_SIZE];
        try {
            int len = inputStream.read(buffer);
            while (len != -1) {
                outputStream.write(buffer, 0, len);
                callback.onProgress(len);
                len = inputStream.read(buffer);
            }
        } catch (IOException e) {
            throw(new FileCouldntBeCopied());
        }
    }

    private static void delete(DocumentFile documentFile) {
        documentFile.delete();
    }

    private static void moveDocumentFile(ContentResolver contentResolver,
                                        Callback callback,
                                        DocumentFile documentFile,
                                        DocumentFile targetDirectory) throws MoveDocumentFileException {
        String mimeType = documentFile.getType();
        String documentName = documentFile.getName();
        if (!documentFile.canRead()) {
            throw(new MoveDocumentFileException());
        }

        try (InputStream inputStream = createInputStream(contentResolver, documentFile);
             OutputStream outputStream = createOutputStream(contentResolver, targetDirectory, mimeType, documentName)) {
            copy(callback, inputStream, outputStream);
        } catch (IOException | InputStreamCouldntBeCreated | OutputStreamCouldntBeCreated | FileCouldntBeCopied e) {
            throw (new MoveDocumentFileException());
        }

        delete(documentFile);
    }

    private static long getLength(DocumentFile[] documentFiles) {
        long length = 0;
        for (DocumentFile documentFile : documentFiles) {
            length += documentFile.length();
        }
        return length;
    }

    public static void callOnFinish(Callback callback) {
        try {
            callback.onFinish();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void moveDocumentFiles(ContentResolver contentResolver,
                                         DocumentFile[] documentFiles,
                                         DocumentFile targetDirectory,
                                         Callback callback) {
        boolean atLeastOneFileMoved = false;
        try {
            validateWritable(targetDirectory);
        } catch (TargetDirectoryNotWritable e) {
            callback.onError(ErrorType.TARGET_DIRECTORY_NOT_WRITABLE);
            callOnFinish(callback);
            return;
        }
        try {
            long totalLength = getLength(documentFiles);
            callback.setTotalLength(totalLength);
            for (DocumentFile documentFile : documentFiles) {
                moveDocumentFile(contentResolver, callback, documentFile, targetDirectory);
                atLeastOneFileMoved = true;
            }
        } catch (MoveDocumentFileException e) {
            if (atLeastOneFileMoved) {
                callback.onPartialSuccess();
                callOnFinish(callback);
                return;
            }
            callback.onError(ErrorType.GENERIC);
            callOnFinish(callback);
            return;
        }
        callback.onSuccess();
        callOnFinish(callback);
    }
}
