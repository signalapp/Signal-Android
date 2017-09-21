package org.thoughtcrime.securesms.preferences;


import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MergeCursor;
import android.media.MediaScannerConnection;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.preference.PreferenceDialogFragmentCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.CursorAdapter;
import android.widget.HeaderViewListAdapter;
import android.widget.ListView;
import android.widget.Toast;

import org.thoughtcrime.securesms.R;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.concurrent.LinkedBlockingQueue;

import static android.app.Activity.RESULT_OK;

public class RingtonePreferenceDialogFragmentCompat extends PreferenceDialogFragmentCompat {
  private static final String TAG = "RingtonePrefDialog";

  private static final String CURSOR_DEFAULT_ID = "-2";
  private static final String CURSOR_NONE_ID = "-1";

  private int selectedIndex = -1;
  private Cursor cursor;

  private RingtoneManager ringtoneManager;
  private Ringtone defaultRingtone;

  public static RingtonePreferenceDialogFragmentCompat newInstance(String key) {
    RingtonePreferenceDialogFragmentCompat fragment = new RingtonePreferenceDialogFragmentCompat();
    Bundle b = new Bundle(1);
    b.putString(PreferenceDialogFragmentCompat.ARG_KEY, key);
    fragment.setArguments(b);
    return fragment;
  }

  private RingtonePreference getRingtonePreference() {
    return (RingtonePreference) getPreference();
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
  }

  @Override
  public void onPause() {
    super.onPause();

    stopPlaying();
  }


  private void stopPlaying() {
    if (defaultRingtone != null && defaultRingtone.isPlaying()) {
      defaultRingtone.stop();
    }

    if (ringtoneManager != null) {
      ringtoneManager.stopPreviousRingtone();
    }
  }

  @Override
  protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
    super.onPrepareDialogBuilder(builder);

    RingtonePreference ringtonePreference = getRingtonePreference();

    createCursor(ringtonePreference.getRingtone());

    String colTitle = cursor.getColumnName(RingtoneManager.TITLE_COLUMN_INDEX);

    final Context context = getContext();

    final int ringtoneType = ringtonePreference.getRingtoneType();
    final boolean showDefault = ringtonePreference.getShowDefault();
    final boolean showSilent = ringtonePreference.getShowSilent();
    final Uri defaultUri;

    if (showDefault) {
      defaultUri = RingtoneManager.getDefaultUri(ringtoneType);
    } else {
      defaultUri = null;
    }

    builder
        .setSingleChoiceItems(cursor, selectedIndex, colTitle, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialogInterface, int i) {
            if (i < cursor.getCount()) {
              selectedIndex = i;

              int realIdx = i - (showDefault ? 1 : 0) - (showSilent ? 1 : 0);

              stopPlaying();

              if (showDefault && i == 0) {
                if (defaultRingtone != null) {
                  defaultRingtone.play();
                } else {
                  defaultRingtone = RingtoneManager.getRingtone(context, defaultUri);
                  if (defaultRingtone != null) {
                    defaultRingtone.play();
                  }
                }
              } else if (((showDefault && i == 1) || (!showDefault && i == 0)) && showSilent) {
                ringtoneManager.stopPreviousRingtone(); // "playing" silence
              } else {
                Ringtone ringtone = ringtoneManager.getRingtone(realIdx);
                ringtone.play();
              }
            } else {
              newRingtone();
            }
          }
        })
        .setOnDismissListener(new DialogInterface.OnDismissListener() {
          @Override
          public void onDismiss(DialogInterface dialogInterface) {
            if (defaultRingtone != null) {
              defaultRingtone.stop();
            }

            RingtonePreferenceDialogFragmentCompat.this.onDismiss(dialogInterface);
          }
        })
        .setNegativeButton(android.R.string.cancel, this)
        .setPositiveButton(android.R.string.ok, this);
  }

  @NonNull
  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    AlertDialog dialog = (AlertDialog) super.onCreateDialog(savedInstanceState);

    if (getRingtonePreference().shouldShowAdd()) {
      ListView listView = dialog.getListView();
      View addRingtoneView = LayoutInflater.from(listView.getContext()).inflate(R.layout.add_ringtone_item, listView, false);
      listView.addFooterView(addRingtoneView);
    }

    return dialog;
  }

  @Override
  public void onDialogClosed(boolean positiveResult) {
    stopPlaying();

    defaultRingtone = null;

    final RingtonePreference preference = getRingtonePreference();
    final boolean showDefault = preference.getShowDefault();
    final boolean showSilent = preference.getShowSilent();

    if (positiveResult && selectedIndex >= 0) {
      final Uri uri;

      if (showDefault && selectedIndex == 0) {
        uri = RingtoneManager.getDefaultUri(preference.getRingtoneType());
      } else if (((showDefault && selectedIndex == 1) || (!showDefault && selectedIndex == 0)) && showSilent) {
        uri = null;
      } else {
        uri = ringtoneManager.getRingtoneUri(selectedIndex - (showDefault ? 1 : 0) - (showSilent ? 1 : 0));
      }

      if (preference.callChangeListener(uri)) {
        preference.setRingtone(uri);
      }
    }
  }

  @NonNull
  private Cursor createCursor(Uri ringtoneUri) {
    RingtonePreference ringtonePreference = getRingtonePreference();
    ringtoneManager = new RingtoneManager(getContext());

    ringtoneManager.setType(ringtonePreference.getRingtoneType());
    ringtoneManager.setStopPreviousRingtone(true);

    Cursor ringtoneCursor = ringtoneManager.getCursor();

    String colId = ringtoneCursor.getColumnName(RingtoneManager.ID_COLUMN_INDEX);
    String colTitle = ringtoneCursor.getColumnName(RingtoneManager.TITLE_COLUMN_INDEX);

    MatrixCursor extras = new MatrixCursor(new String[]{colId, colTitle});

    final int ringtoneType = ringtonePreference.getRingtoneType();
    final boolean showDefault = ringtonePreference.getShowDefault();
    final boolean showSilent = ringtonePreference.getShowSilent();

    if (showDefault) {
      switch (ringtoneType) {
        case RingtoneManager.TYPE_ALARM:
          extras.addRow(new String[]{CURSOR_DEFAULT_ID, getString(R.string.RingtonePreference_alarm_sound_default)});
          break;
        case RingtoneManager.TYPE_NOTIFICATION:
          extras.addRow(new String[]{CURSOR_DEFAULT_ID, getString(R.string.RingtonePreference_notification_sound_default)});
          break;
        case RingtoneManager.TYPE_RINGTONE:
        case RingtoneManager.TYPE_ALL:
        default:
          extras.addRow(new String[]{CURSOR_DEFAULT_ID, getString(R.string.RingtonePreference_ringtone_default)});
          break;
      }
    }

    if (showSilent) {
      extras.addRow(new String[]{CURSOR_NONE_ID, getString(R.string.RingtonePreference_ringtone_silent)});
    }

    selectedIndex = ringtoneManager.getRingtonePosition(ringtoneUri);
    if (selectedIndex >= 0) {
      selectedIndex += (showDefault ? 1 : 0) + (showSilent ? 1 : 0);
    }

    if (selectedIndex < 0 && showDefault) {
      if (RingtoneManager.getDefaultType(ringtoneUri) != -1) {
        selectedIndex = 0;
      }
    }

    if (selectedIndex < 0 && showSilent) {
      selectedIndex = showDefault ? 1 : 0;
    }

    Cursor[] cursors = {extras, ringtoneCursor};
    return this.cursor = new MergeCursor(cursors);
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == getRingtonePreference().getCustomRingtoneRequestCode()) {
      if (resultCode == RESULT_OK) {
        final Uri fileUri = data.getData();
        final Context context = getContext();

        final RingtonePreference ringtonePreference = getRingtonePreference();
        final int ringtoneType = ringtonePreference.getRingtoneType();

        // FIXME static field leak
        @SuppressLint("StaticFieldLeak") final AsyncTask<Uri, Void, Cursor> installTask = new AsyncTask<Uri, Void, Cursor>() {
          @Override
          protected Cursor doInBackground(Uri... params) {
            try {
              Uri newUri = addCustomExternalRingtone(context, params[0], ringtoneType);

              return createCursor(newUri);
            } catch (IOException | IllegalArgumentException e) {
              Log.e(TAG, "Unable to add new ringtone: ", e);
            }
            return null;
          }

          @Override
          protected void onPostExecute(final Cursor newCursor) {
            if (newCursor != null) {
              final ListView listView = ((AlertDialog) getDialog()).getListView();
              final CursorAdapter adapter = ((CursorAdapter) ((HeaderViewListAdapter) listView.getAdapter()).getWrappedAdapter());
              adapter.changeCursor(newCursor);

              listView.setItemChecked(selectedIndex, true);
              listView.setSelection(selectedIndex);
              listView.clearFocus();
            } else {
              Toast.makeText(context, getString(R.string.RingtonePreference_unable_to_add_ringtone), Toast.LENGTH_SHORT).show();
            }
          }
        };
        installTask.execute(fileUri);
      } else {
        ListView listView = ((AlertDialog) getDialog()).getListView();
        listView.setItemChecked(selectedIndex, true);
      }
    } else {
      super.onActivityResult(requestCode, resultCode, data);
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    if (requestCode == getRingtonePreference().getPermissionRequestCode() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
      newRingtone();
    } else {
      super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
  }

  private void newRingtone() {
    boolean hasPerm = ContextCompat.checkSelfPermission(getContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    if (hasPerm) {
      final Intent chooseFile = new Intent(Intent.ACTION_GET_CONTENT);
      chooseFile.setType("audio/*");
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        chooseFile.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"audio/*", "application/ogg"});
      }
      startActivityForResult(chooseFile, getRingtonePreference().getCustomRingtoneRequestCode());
    } else {
      requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, getRingtonePreference().getPermissionRequestCode());
    }
  }

  @WorkerThread
  public static Uri addCustomExternalRingtone(Context context, @NonNull Uri fileUri, final int type)
      throws IOException {
    if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
      throw new IOException("External storage is not mounted. Unable to install ringtones.");
    }

    if (ContentResolver.SCHEME_FILE.equals(fileUri.getScheme())) {
      fileUri = Uri.fromFile(new File(fileUri.getPath()));
    }

    String mimeType = context.getContentResolver().getType(fileUri);

    if (mimeType == null) {
      String fileExtension = MimeTypeMap.getFileExtensionFromUrl(fileUri
                                                                     .toString());
      mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
          fileExtension.toLowerCase());
    }

    if (mimeType == null || !(mimeType.startsWith("audio/") || mimeType.equals("application/ogg"))) {
      throw new IllegalArgumentException("Ringtone file must have MIME type \"audio/*\"."
                                             + " Given file has MIME type \"" + mimeType + "\"");
    }

    final String subdirectory = getDirForType(type);

    final File outFile = getUniqueExternalFile(context, subdirectory, getFileDisplayNameFromUri(context, fileUri), mimeType);

    if (outFile != null) {
      final InputStream input = context.getContentResolver().openInputStream(fileUri);
      final OutputStream output = new FileOutputStream(outFile);

      if (input != null) {
        byte[] buffer = new byte[10240];

        for (int len; (len = input.read(buffer)) != -1; ) {
          output.write(buffer, 0, len);
        }

        input.close();
      }

      output.close();

      NewRingtoneScanner scanner = null;
      try {
        scanner = new NewRingtoneScanner(context, outFile);
        return scanner.take();
      } catch (InterruptedException e) {
        throw new IOException("Audio file failed to scan as a ringtone", e);
      } finally {
        if (scanner != null) {
          scanner.close();
        }
      }
    } else {
      return null;
    }
  }

  private static String getDirForType(int type) {
    switch (type) {
      case RingtoneManager.TYPE_ALL:
      case RingtoneManager.TYPE_RINGTONE:
        return Environment.DIRECTORY_RINGTONES;
      case RingtoneManager.TYPE_NOTIFICATION:
        return Environment.DIRECTORY_NOTIFICATIONS;
      case RingtoneManager.TYPE_ALARM:
        return Environment.DIRECTORY_ALARMS;
      default:
        throw new IllegalArgumentException("Unsupported ringtone type: " + type);
    }
  }

  private static String getFileDisplayNameFromUri(Context context, Uri uri) {
    String scheme = uri.getScheme();

    if (ContentResolver.SCHEME_FILE.equals(scheme)) {
      return uri.getLastPathSegment();
    } else if (ContentResolver.SCHEME_CONTENT.equals(scheme)) {

      String[] projection = {OpenableColumns.DISPLAY_NAME};

      Cursor cursor = null;
      try {
        cursor = context.getContentResolver().query(uri, projection, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
          return cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
        }
      } finally {
        if (cursor != null) {
          cursor.close();
        }
      }
    }

    // This will only happen if the Uri isn't either SCHEME_CONTENT or SCHEME_FILE, so we assume
    // it already represents the file's name.
    return uri.toString();
  }

  /**
   * Creates a unique file in the specified external storage with the desired name. If the name is
   * taken, the new file's name will have '(%d)' to avoid overwriting files.
   *
   * @param context      {@link Context} to query the file name from.
   * @param subdirectory One of the directories specified in {@link android.os.Environment}
   * @param fileName     desired name for the file.
   * @param mimeType     MIME type of the file to create.
   * @return the File object in the storage, or null if an error occurs.
   */
  @Nullable
  private static File getUniqueExternalFile(Context context, String subdirectory, String fileName,
                                            String mimeType) {
    File externalStorage = Environment.getExternalStoragePublicDirectory(subdirectory);
    // Make sure the storage subdirectory exists
    //noinspection ResultOfMethodCallIgnored
    externalStorage.mkdirs();

    File outFile;
    try {
      // Ensure the file has a unique name, as to not override any existing file
      outFile = buildUniqueFile(externalStorage, mimeType, fileName);
    } catch (FileNotFoundException e) {
      // This might also be reached if the number of repeated files gets too high
      Log.e(TAG, "Unable to get a unique file name: " + e);
      return null;
    }
    return outFile;
  }

  @NonNull
  private static File buildUniqueFile(File externalStorage, String mimeType, String fileName) throws FileNotFoundException {
    final String[] parts = splitFileName(mimeType, fileName);

    String name = parts[0];
    String ext = (parts[1] != null) ? "." + parts[1] : "";

    File file = new File(externalStorage, name + ext);
    SecureRandom random = new SecureRandom();

    int n = 0;
    while (file.exists()) {
      if (n++ >= 32) {
        n = random.nextInt();
      }
      file = new File(externalStorage, name + " (" + n + ")" + ext);
    }

    return file;
  }

  @NonNull
  public static String[] splitFileName(String mimeType, String displayName) {
    String name;
    String ext;

    String mimeTypeFromExt;

    // Extract requested extension from display name
    final int lastDot = displayName.lastIndexOf('.');
    if (lastDot >= 0) {
      name = displayName.substring(0, lastDot);
      ext = displayName.substring(lastDot + 1);
      mimeTypeFromExt = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
          ext.toLowerCase());
    } else {
      name = displayName;
      ext = null;
      mimeTypeFromExt = null;
    }

    if (mimeTypeFromExt == null) {
      mimeTypeFromExt = "application/octet-stream";
    }

    final String extFromMimeType = MimeTypeMap.getSingleton().getExtensionFromMimeType(
        mimeType);
    //noinspection StatementWithEmptyBody
    if (TextUtils.equals(mimeType, mimeTypeFromExt) || TextUtils.equals(ext, extFromMimeType)) {
      // Extension maps back to requested MIME type; allow it
    } else {
      // No match; insist that create file matches requested MIME
      name = displayName;
      ext = extFromMimeType;
    }


    if (ext == null) {
      ext = "";
    }

    return new String[]{name, ext};
  }

  /**
   * Creates a {@link android.media.MediaScannerConnection} to scan a ringtone file and add its
   * information to the internal database.
   * <p>
   * It uses a {@link java.util.concurrent.LinkedBlockingQueue} so that the caller can block until
   * the scan is completed.
   */
  private static class NewRingtoneScanner implements Closeable, MediaScannerConnection.MediaScannerConnectionClient {
    private MediaScannerConnection mMediaScannerConnection;
    private File mFile;
    private LinkedBlockingQueue<Uri> mQueue = new LinkedBlockingQueue<>(1);

    private NewRingtoneScanner(Context context, File file) {
      mFile = file;
      mMediaScannerConnection = new MediaScannerConnection(context, this);
      mMediaScannerConnection.connect();
    }

    @Override
    public void close() {
      mMediaScannerConnection.disconnect();
    }

    @Override
    public void onMediaScannerConnected() {
      mMediaScannerConnection.scanFile(mFile.getAbsolutePath(), null);
    }

    @Override
    public void onScanCompleted(String path, Uri uri) {
      if (uri == null) {
        // There was some issue with scanning. Delete the copied file so it is not oprhaned.
        //noinspection ResultOfMethodCallIgnored
        mFile.delete();
        return;
      }
      try {
        mQueue.put(uri);
      } catch (InterruptedException e) {
        Log.e(TAG, "Unable to put new ringtone Uri in queue", e);
      }
    }

    private Uri take() throws InterruptedException {
      return mQueue.take();
    }
  }
}