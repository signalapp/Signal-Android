package androidx.documentfile.provider;

import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;

import org.signal.core.util.logging.Log;

/**
 * Located in androidx package as {@link TreeDocumentFile} is package protected.
 */
public class DocumentFileHelper {

  private static final String TAG = Log.tag(DocumentFileHelper.class);

  /**
   * System implementation swallows the exception and we are having problems with the rename. This inlines the
   * same call and logs the exception. Note this implementation does not update the passed in document file like
   * the system implementation. Do not use the provided document file after calling this method.
   *
   * @return true if rename successful
   */
  public static boolean renameTo(Context context, DocumentFile documentFile, String displayName) {
    if (documentFile instanceof TreeDocumentFile) {
      Log.d(TAG, "Renaming document directly");
      try {
        final Uri result = DocumentsContract.renameDocument(context.getContentResolver(), documentFile.getUri(), displayName);
        return result != null;
      } catch (Exception e) {
        Log.w(TAG, "Unable to rename document file", e);
        return false;
      }
    } else {
      Log.d(TAG, "Letting OS rename document: " + documentFile.getClass().getSimpleName());
      return documentFile.renameTo(displayName);
    }
  }
}
