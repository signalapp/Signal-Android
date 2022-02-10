package org.thoughtcrime.securesms.util;


import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import androidx.documentfile.provider.DocumentFile;

import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.util.ByteUtil;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.backup.BackupPassphrase;
import org.thoughtcrime.securesms.database.NoExternalStorageException;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.permissions.Permissions;

import java.io.File;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class BackupUtil {

  private static final String TAG = Log.tag(BackupUtil.class);

  public static final int PASSPHRASE_LENGTH = 30;

  public static @NonNull String getLastBackupTime(@NonNull Context context, @NonNull Locale locale) {
    try {
      BackupInfo backup = getLatestBackup();

      if (backup == null) return context.getString(R.string.BackupUtil_never);
      else                return DateUtils.getExtendedRelativeTimeSpanString(context, locale, backup.getTimestamp());
    } catch (NoExternalStorageException e) {
      Log.w(TAG, e);
      return context.getString(R.string.BackupUtil_unknown);
    }
  }

  public static @NonNull String getLastChunkedBackupTime(@NonNull Context context, @NonNull Locale locale) {
    try {
      BackupInfo backup = getLatestChunkedBackup();

      if (backup == null) return context.getString(R.string.BackupUtil_never);
      else                return DateUtils.getExtendedRelativeTimeSpanString(context, locale, backup.getTimestamp());
    } catch (NoExternalStorageException e) {
      Log.w(TAG, e);
      return context.getString(R.string.BackupUtil_unknown);
    }
  }

  public static boolean isUserSelectionRequired(@NonNull Context context) {
    return Build.VERSION.SDK_INT >= 29 && !Permissions.hasAll(context, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE);
  }

  public static boolean canUserAccessBackupDirectory(@NonNull Context context) {
    if (isUserSelectionRequired(context)) {
      Uri backupDirectoryUri = SignalStore.settings().getSignalBackupDirectory();
      if (backupDirectoryUri == null) {
        return false;
      }

      DocumentFile backupDirectory = DocumentFile.fromTreeUri(context, backupDirectoryUri);
      return backupDirectory != null && backupDirectory.exists() && backupDirectory.canRead() && backupDirectory.canWrite();
    } else {
      return Permissions.hasAll(context, Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }
  }

  public static @Nullable BackupInfo getLatestBackup() throws NoExternalStorageException {
    List<BackupInfo> backups = getAllBackupsNewestFirst();

    return backups.isEmpty() ? null : backups.get(0);
  }

  public static @Nullable BackupInfo getLatestChunkedBackup() throws NoExternalStorageException {
    List<BackupInfo> backups = getAllChunkedBackupsNewestFirst();

    return backups.isEmpty() ? null : backups.get(0);
  }

  public static void deleteAllBackups() {
    Log.i(TAG, "Deleting all backups");

    try {
      List<BackupInfo> backups = getAllBackupsNewestFirst();

      for (BackupInfo backup : backups) {
        backup.delete();
      }
    } catch (NoExternalStorageException e) {
      Log.w(TAG, e);
    }
  }

  public static void deleteOldBackups() {
    Log.i(TAG, "Deleting older backups");

    try {
      List<BackupInfo> backups = getAllBackupsNewestFirst();

      for (int i = 2; i < backups.size(); i++) {
        backups.get(i).delete();
      }
    } catch (NoExternalStorageException e) {
      Log.w(TAG, e);
    }
  }

  public static void deleteOldChunkedBackups() {
    Log.i(TAG, "Deleting older backups");

    try {
      List<BackupInfo> backups = getAllChunkedBackupsNewestFirst();

      for (int i = 2; i < backups.size(); i++) {
        backups.get(i).delete();
      }
    } catch (NoExternalStorageException e) {
      Log.w(TAG, e);
    }
  }

  public static void disableBackups(@NonNull Context context) {
    BackupPassphrase.set(context, null);
    SignalStore.settings().setBackupEnabled(false);
    BackupUtil.deleteAllBackups();

    if (BackupUtil.isUserSelectionRequired(context)) {
      Uri backupLocationUri = SignalStore.settings().getSignalBackupDirectory();

      if (backupLocationUri == null) {
        return;
      }

      SignalStore.settings().clearSignalBackupDirectory();

      try {
        context.getContentResolver()
            .releasePersistableUriPermission(Objects.requireNonNull(backupLocationUri),
                                             Intent.FLAG_GRANT_READ_URI_PERMISSION |
                                             Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
      } catch (SecurityException e) {
        Log.w(TAG, "Could not release permissions", e);
      }
    }
  }

  private static List<BackupInfo> getAllBackupsNewestFirst() throws NoExternalStorageException {
    if (isUserSelectionRequired(ApplicationDependencies.getApplication())) {
      return getAllBackupsNewestFirstApi29();
    } else {
      return getAllBackupsNewestFirstLegacy();
    }
  }

  private static List<BackupInfo> getAllChunkedBackupsNewestFirst() throws NoExternalStorageException {
    if (isUserSelectionRequired(ApplicationDependencies.getApplication())) {
      return getAllChunkedBackupsNewestFirstApi29();
    } else {
      return getAllChunkedBackupsNewestFirstLegacy();
    }
  }

  @RequiresApi(29)
  private static List<BackupInfo> getAllBackupsNewestFirstApi29() {
    Uri backupDirectoryUri = SignalStore.settings().getSignalBackupDirectory();
    if (backupDirectoryUri == null) {
      Log.i(TAG, "Backup directory is not set. Returning an empty list.");
      return Collections.emptyList();
    }

    DocumentFile backupDirectory = DocumentFile.fromTreeUri(ApplicationDependencies.getApplication(), backupDirectoryUri);
    if (backupDirectory == null || !backupDirectory.exists() || !backupDirectory.canRead()) {
      Log.w(TAG, "Backup directory is inaccessible. Returning an empty list.");
      return Collections.emptyList();
    }

    DocumentFile[]   files   = backupDirectory.listFiles();
    List<BackupInfo> backups = new ArrayList<>(files.length);

    for (DocumentFile file : files) {
      if (file.isFile() && file.getName() != null && file.getName().endsWith(".backup")) {
        long backupTimestamp = getBackupTimestamp(file.getName());

        if (backupTimestamp != -1) {
          backups.add(new BackupInfo(backupTimestamp, file.length(), file.getUri()));
        }
      }
    }

    Collections.sort(backups, (a, b) -> Long.compare(b.timestamp, a.timestamp));

    return backups;
  }

  @RequiresApi(29)
  private static List<BackupInfo> getAllChunkedBackupsNewestFirstApi29() {
    Uri backupDirectoryUri = SignalStore.settings().getSignalBackupDirectory();
    if (backupDirectoryUri == null) {
      Log.i(TAG, "Backup directory is not set. Returning an empty list.");
      return Collections.emptyList();
    }

    DocumentFile backupDirectory = DocumentFile.fromTreeUri(ApplicationDependencies.getApplication(), backupDirectoryUri);
    if (backupDirectory == null || !backupDirectory.exists() || !backupDirectory.canRead()) {
      Log.w(TAG, "Backup directory is inaccessible. Returning an empty list.");
      return Collections.emptyList();
    }

    DocumentFile[]   files   = backupDirectory.listFiles();
    List<BackupInfo> backups = new ArrayList<>(files.length);

    HashMap<Long, List<DocumentFile>> m = new HashMap<>();

    for (DocumentFile file : files) {
      if (!file.isFile()) {
        continue;
      }
      String name = file.getName();
      long backupTimestamp = getBackupTimestampFromMultiFileBackup(name);
      if (backupTimestamp == -1) {
        continue;
      }
      if (!m.containsKey(backupTimestamp)) {
        m.put(backupTimestamp, new ArrayList<>());
      }
      m.get(backupTimestamp).add(file);
    }

    for (List<DocumentFile> l: m.values()) {
      Collections.sort(l, Comparator.comparing(DocumentFile::getName));
    }

    for (long backupTimestamp: m.keySet()) {
      long size = 0;
      List<DocumentFile> documentFiles = m.get(backupTimestamp);

      for (DocumentFile d: documentFiles) {
        size += d.length();
      }

      Uri[] uris = new Uri[documentFiles.size()];
      for (int i = 0; i < uris.length; ++i) {
        uris[i] = documentFiles.get(i).getUri();
      }
      BackupInfo backupInfo = new BackupInfo(backupTimestamp, size, uris);
      backups.add(backupInfo);
    }

    Collections.sort(backups, (a, b) -> Long.compare(b.timestamp, a.timestamp));

    return backups;
  }

  public static @Nullable BackupInfo getBackupInfoFromSingleUri(@NonNull Context context, @NonNull Uri singleUri) throws BackupFileException {
    DocumentFile documentFile = Objects.requireNonNull(DocumentFile.fromSingleUri(context, singleUri));

    return getBackupInfoFromSingleDocumentFile(documentFile);
  }

  @VisibleForTesting
  static @Nullable BackupInfo getBackupInfoFromSingleDocumentFile(@NonNull DocumentFile documentFile) throws BackupFileException {
    BackupFileState backupFileState = getBackupFileState(documentFile);

    if (backupFileState.isSuccess()) {
      long backupTimestamp = getBackupTimestamp(Objects.requireNonNull(documentFile.getName()));
      return new BackupInfo(backupTimestamp, documentFile.length(), documentFile.getUri());
    } else {
      Log.w(TAG, "Could not load backup info.");
      backupFileState.throwIfError();
      return null;
    }
  }

  public static @Nullable BackupInfo getBackupInfoFromMultiUris(@NonNull Context context, @NonNull Uri[] multiUris) throws BackupFileException {
    DocumentFile[] documentFiles = toDocumentFiles(context, multiUris);
    BackupFileState backupFileState = getBackupFileState(documentFiles);

    if (backupFileState.isSuccess()) {
      long backupTimestamp = getBackupTimestampFromMultiFileBackup(Objects.requireNonNull(documentFiles[0].getName()));
      return new BackupInfo(backupTimestamp, getLength(documentFiles), multiUris);
    }

    Log.w(TAG, "Could not load backup info.");
    backupFileState.throwIfError();
    return null;
  }

  private static List<BackupInfo> getAllBackupsNewestFirstLegacy() throws NoExternalStorageException {
    File             backupDirectory = StorageUtil.getOrCreateBackupDirectory();
    File[]           files           = backupDirectory.listFiles();
    List<BackupInfo> backups         = new ArrayList<>(files.length);

    for (File file : files) {
      if (file.isFile() && file.getAbsolutePath().endsWith(".backup")) {
        long backupTimestamp = getBackupTimestamp(file.getName());

        if (backupTimestamp != -1) {
          backups.add(new BackupInfo(backupTimestamp, file.length(), Uri.fromFile(file)));
        }
      }
    }

    Collections.sort(backups, (a, b) -> Long.compare(b.timestamp, a.timestamp));

    return backups;
  }

  private static List<BackupInfo> getAllChunkedBackupsNewestFirstLegacy() throws NoExternalStorageException {
    File backupDirectory = StorageUtil.getOrCreateBackupDirectory();
    File[] files = backupDirectory.listFiles();
    List<BackupInfo> backups = new ArrayList<>(files.length);

    HashMap<Long, List<File>> m = new HashMap<>();

    for (File file: files) {
      if (!file.isFile()) {
        continue;
      }
      String name = file.getName();
      long backupTimestamp = getBackupTimestampFromMultiFileBackup(name);
      if (backupTimestamp == -1) {
        continue;
      }
      if (!m.containsKey(backupTimestamp)) {
        m.put(backupTimestamp, new ArrayList<>());
      }
      m.get(backupTimestamp).add(file);
    }

    for (List<File> l: m.values()) {
      Collections.sort(l, Comparator.comparing(File::getName));
    }

    for (long backupTimestamp: m.keySet()) {
      long size = 0;
      List<File> filesList = m.get(backupTimestamp);

      for (File f: filesList) {
        size += f.length();
      }

      Uri[] uris = new Uri[filesList.size()];
      for (int i = 0; i < uris.length; ++i) {
        File f = filesList.get(i);
        uris[i] = Uri.fromFile(f);
      }
      BackupInfo backupInfo = new BackupInfo(backupTimestamp, size, uris);
      backups.add(backupInfo);
    }

    Collections.sort(backups, (a, b) -> Long.compare(b.timestamp, a.timestamp));

    return backups;
  }

  public static @NonNull String[] generateBackupPassphrase() {
    String[] result = new String[6];
    byte[]   random = new byte[30];

    new SecureRandom().nextBytes(random);

    for (int i=0;i<30;i+=5) {
      result[i/5] = String.format(Locale.ENGLISH,  "%05d", ByteUtil.byteArray5ToLong(random, i) % 100000);
    }

    return result;
  }

  public static boolean hasBackupFiles(@NonNull Context context) {
    if (Permissions.hasAll(context, Manifest.permission.READ_EXTERNAL_STORAGE)) {
      try {
        File directory = StorageUtil.getBackupDirectory();

        if (directory.exists() && directory.isDirectory()) {
          File[] files = directory.listFiles();
          return files != null && files.length > 0;
        } else {
          return false;
        }
      } catch (NoExternalStorageException e) {
        Log.w(TAG, "Failed to read storage!", e);
        return false;
      }
    } else {
      return false;
    }
  }

  public static boolean renameMulti(List<File> fromFiles, List<File> toFiles) {
    if (fromFiles.size() != toFiles.size()) {
      throw (new RuntimeException("fromFiles.size() doesn't match toFiles.size()"));
    }
    boolean success = true;
    for (int i = 0; i < fromFiles.size(); ++i) {
      File from = fromFiles.get(i);
      File to = toFiles.get(i);
      success = success && from.renameTo(to);
    }
    return success;
  }

  public static boolean renameMulti2(List<DocumentFile> fromFiles, List<String> toFiles) {
    if (fromFiles.size() != toFiles.size()) {
      throw (new RuntimeException("fromFiles.size() doesn't match toFiles.size()"));
    }
    boolean success = true;
    for (int i = 0; i < fromFiles.size(); ++i) {
      DocumentFile from = fromFiles.get(i);
      String to = toFiles.get(i);
      success = success && from.renameTo(to);
      fromFiles.set(i, null);
    }
    return success;
  }

  public static List<String> generateBackupFilenames(int n) {
    String timestamp = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).format(new Date());
    List<String> filenames = new ArrayList<>(n);
    for (int i = 0; i < n; ++i) {
      String filename  = String.format(Locale.ENGLISH, "signal-%s.backup.part%03d", timestamp, i);
      filenames.add(filename);
    }
    return filenames;
  }

  public static List<File> generateBackupFilenames2(File backupDirectory, int n) {
    List<String> filenames = generateBackupFilenames(n);
    List<File> result = new ArrayList<>(filenames.size());
    for (int i = 0; i < n; ++i) {
      File f = new File(backupDirectory, filenames.get(i));
      result.add(i, f);
    }
    return result;
  }

  private static long getBackupTimestamp(@NonNull String backupName) {
    String[] prefixSuffix = backupName.split("[.]");

    if (prefixSuffix.length == 2) {
      String[] parts = prefixSuffix[0].split("\\-");

      if (parts.length == 7) {
        try {
          Calendar calendar = Calendar.getInstance();
          calendar.set(Calendar.YEAR, Integer.parseInt(parts[1]));
          calendar.set(Calendar.MONTH, Integer.parseInt(parts[2]) - 1);
          calendar.set(Calendar.DAY_OF_MONTH, Integer.parseInt(parts[3]));
          calendar.set(Calendar.HOUR_OF_DAY, Integer.parseInt(parts[4]));
          calendar.set(Calendar.MINUTE, Integer.parseInt(parts[5]));
          calendar.set(Calendar.SECOND, Integer.parseInt(parts[6]));
          calendar.set(Calendar.MILLISECOND, 0);

          return calendar.getTimeInMillis();
        } catch (NumberFormatException e) {
          Log.w(TAG, e);
        }
      }
    }

    return -1;
  }

  private static BackupFileState getBackupFileState(@NonNull DocumentFile documentFile) {
    if (!documentFile.exists()) {
      return BackupFileState.NOT_FOUND;
    } else if (!documentFile.canRead()) {
      return BackupFileState.NOT_READABLE;
    } else if (Util.isEmpty(documentFile.getName()) || !documentFile.getName().endsWith(".backup")) {
      return BackupFileState.UNSUPPORTED_FILE_EXTENSION;
    } else {
        return BackupFileState.READABLE;
    }
  }

  static final String multiFileRegex = "^signal-\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{2}.backup.part\\d{3}$";

  private static BackupFileState getBackupFileState(@NonNull DocumentFile[] documentFiles) {
    if (documentFiles.length == 0) {
      return BackupFileState.NOT_FOUND;
    }
    for (DocumentFile documentFile: documentFiles) {
      if (!documentFile.exists()) {
        return BackupFileState.NOT_FOUND;
      } else if (!documentFile.canRead()) {
        return BackupFileState.NOT_READABLE;
      } else if (Util.isEmpty(documentFile.getName()) || !documentFile.getName().matches(multiFileRegex)) {
        return BackupFileState.UNSUPPORTED_FILE_EXTENSION;
      }
    }
    return BackupFileState.READABLE;
  }

  private static DocumentFile[] toDocumentFiles(Context context, @NonNull Uri[] uris) {
    DocumentFile[] documentFiles = new DocumentFile[uris.length];
    for (int i = 0; i < uris.length; i++) {
      documentFiles[i] = DocumentFile.fromSingleUri(context, uris[i]);
    }
    return documentFiles;
  }

  private static long getLength(@NonNull DocumentFile[] documentFiles) {
    long length = 0;
    for(DocumentFile documentFile: documentFiles) {
      length += documentFile.length();
    }
    return length;
  }

  private static long getBackupTimestampFromMultiFileBackup(@NonNull String filename) {
    if (!filename.matches(multiFileRegex)) {
      // this is no (correctly named) multifile backup
      return -1;
    }
    String s = filename.substring(0, filename.length() - ".partXXX".length());
    return getBackupTimestamp(s);
  }

  /**
   * Describes the validity of a backup file.
   */
  public enum BackupFileState {
    READABLE("The document at the specified Uri looks like a readable backup."),
    NOT_FOUND("The document at the specified Uri cannot be found."),
    NOT_READABLE("The document at the specified Uri cannot be read."),
    UNSUPPORTED_FILE_EXTENSION("The document at the specified Uri has an unsupported file extension.");

    private final String message;

    BackupFileState(String message) {
      this.message = message;
    }

    public boolean isSuccess() {
      return this == READABLE;
    }

    public void throwIfError() throws BackupFileException {
      if (!isSuccess()) {
        throw new BackupFileException(this);
      }
    }
  }

  /**
   * Wrapping exception for a non-successful BackupFileState.
   */
  public static class BackupFileException extends Exception {

    private final BackupFileState state;

    BackupFileException(BackupFileState backupFileState) {
      super(backupFileState.message);
      this.state = backupFileState;
    }

    public @NonNull BackupFileState getState() {
      return state;
    }
  }

  public static class BackupInfo {

    private final long timestamp;
    private final long size;
    private final Uri  uri;
    private final Uri[] uris;

    BackupInfo(long timestamp, long size, Uri uri) {
      this.timestamp = timestamp;
      this.size      = size;
      this.uri       = uri;
      this.uris      = null;
    }

    BackupInfo(long timestamp, long size, Uri[] uris) {
      this.timestamp = timestamp;
      this.size      = size;
      this.uri       = null;
      this.uris      = uris;
    }

    public boolean isSingleFileBackup() {
      return uri != null;
    }

    public boolean isMultiFileBackup() {
      return uris != null;
    }

    public long getTimestamp() {
      return timestamp;
    }

    public long getSize() {
      return size;
    }

    public Uri getUri() {
      return uri;
    }

    public Uri[] getUris() {
      return uris;
    }

    private static void delete(Uri uri) {
      File file = new File(Objects.requireNonNull(uri.getPath()));

      if (file.exists()) {
        Log.i(TAG, "Deleting File: " + file.getAbsolutePath());

        if (!file.delete()) {
          Log.w(TAG, "Delete failed: " + file.getAbsolutePath());
        }
      } else {
        DocumentFile document = DocumentFile.fromSingleUri(ApplicationDependencies.getApplication(), uri);
        if (document != null && document.exists()) {
          Log.i(TAG, "Deleting DocumentFile: " + uri);

          if (!document.delete()) {
            Log.w(TAG, "Delete failed: " + uri);
          }
        }
      }
    }

    private void delete() {
      if (uri != null) {
        delete(uri);
      }
      if (uris != null) {
        for (Uri uri: uris) {
          delete(uri);
        }
      }
    }
  }
}
