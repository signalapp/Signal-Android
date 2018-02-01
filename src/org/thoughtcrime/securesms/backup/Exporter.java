package org.thoughtcrime.securesms.backup;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Xml;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.NoExternalStorageException;
import org.thoughtcrime.securesms.database.RawDatabase;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Map;
import java.util.Stack;

import static android.database.Cursor.FIELD_TYPE_BLOB;
import static org.thoughtcrime.securesms.backup.AttachmentMapping.TABLE_NAME_ATTACHMENT_ROW;
import static org.thoughtcrime.securesms.backup.AttachmentMapping.TAG_ATTACHMENT_ROW;
import static org.thoughtcrime.securesms.backup.DraftsMapping.TABLE_NAME_DRAFTS;
import static org.thoughtcrime.securesms.backup.DraftsMapping.TAG_DRAFTS;
import static org.thoughtcrime.securesms.backup.GroupsMapping.TABLE_NAME_GROUPS;
import static org.thoughtcrime.securesms.backup.GroupsMapping.TAG_GROUPS;
import static org.thoughtcrime.securesms.backup.IdentitiesMapping.TABLE_NAME_IDENTITIES;
import static org.thoughtcrime.securesms.backup.IdentitiesMapping.TAG_IDENTITIES;
import static org.thoughtcrime.securesms.backup.MmsMapping.TABLE_NAME_MMS;
import static org.thoughtcrime.securesms.backup.MmsMapping.TAG_MMS;
import static org.thoughtcrime.securesms.backup.PushMapping.TABLE_NAME_PUSH;
import static org.thoughtcrime.securesms.backup.PushMapping.TAG_PUSH;
import static org.thoughtcrime.securesms.backup.RecipientPreferencesMapping.TABLE_NAME_RECIPIENT_PREFERENCES;
import static org.thoughtcrime.securesms.backup.RecipientPreferencesMapping.TAG_RECIPIENT_PREFERENCES;
import static org.thoughtcrime.securesms.backup.SmsMapping.TABLE_NAME_SMS;
import static org.thoughtcrime.securesms.backup.SmsMapping.TAG_SMS;
import static org.thoughtcrime.securesms.backup.ThreadMapping.TABLE_NAME_THREAD;
import static org.thoughtcrime.securesms.backup.ThreadMapping.TAG_THREAD;
import static org.thoughtcrime.securesms.util.Base64.ENCODE;

public class Exporter {

  public interface Mapping {
    String sqliteColumnName();

    String xmlAttributeName();

    int type();
  }

  static class ExportParams {
    final String tableName;
    final String tagName;
    final String idColumn;
    final Mapping[] mappingValues;

    ExportParams(String tableName, String tagName, String idColumn, Mapping[] mappingValues) {
      this.tableName = tableName;
      this.tagName = tagName;
      this.idColumn = idColumn;
      this.mappingValues = mappingValues;
    }
  }

  public static void exportEncrypted(Context context, String passphrase)
          throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, NoExternalStorageException {
    Writer writer = Utils.createEncryptingExportWriter(passphrase);
    try {
      exportWithWriter(context, writer);
    } finally {
      writer.close();
    }
  }

  private static void exportWithWriter(Context context, Writer writer)
          throws IOException {
    XmlSerializer xmlSerializer = Xml.newSerializer();
    xmlSerializer.setOutput(writer);
    xmlSerializer.startTag("", "SignalBackup");

    RawDatabase rawDatabase = DatabaseFactory.getRawDatabase(context);
    SQLiteDatabase db = rawDatabase.getWritableDatabase();


    ExportParams[] exportParams = {
            new ExportParams(TABLE_NAME_DRAFTS, TAG_DRAFTS,
                    DraftsMapping.ID.sqliteColumnName(), DraftsMapping.values()),
            new ExportParams(TABLE_NAME_GROUPS, TAG_GROUPS,
                    GroupsMapping.ID.sqliteColumnName(), GroupsMapping.values()),
            new ExportParams(TABLE_NAME_IDENTITIES, TAG_IDENTITIES,
                    IdentitiesMapping.ID.sqliteColumnName(), IdentitiesMapping.values()),
            new ExportParams(TABLE_NAME_MMS, TAG_MMS,
                    MmsMapping.ID.sqliteColumnName(), MmsMapping.values()),
            new ExportParams(TABLE_NAME_PUSH, TAG_PUSH,
                    PushMapping.ID.sqliteColumnName(), PushMapping.values()),
            new ExportParams(TABLE_NAME_RECIPIENT_PREFERENCES, TAG_RECIPIENT_PREFERENCES,
                    RecipientPreferencesMapping.ID.sqliteColumnName(),
                    RecipientPreferencesMapping.values()),
            new ExportParams(TABLE_NAME_SMS, TAG_SMS,
                    SmsMapping.ID.sqliteColumnName(), SmsMapping.values()),
            new ExportParams(TABLE_NAME_THREAD, TAG_THREAD,
                    ThreadMapping.ID.sqliteColumnName(), ThreadMapping.values()),
            new ExportParams(TABLE_NAME_ATTACHMENT_ROW, TAG_ATTACHMENT_ROW,
                    AttachmentMapping.ID.sqliteColumnName(), AttachmentMapping.values())
    };

    for (ExportParams ep : exportParams) {
      exportTable(db, xmlSerializer, ep.tableName, ep.idColumn,
              ep.tagName, ep.mappingValues);
    }

    exportAttachments(context, xmlSerializer);
    exportFilesDirectory(context, xmlSerializer);
    exportSharedPreferences(context, xmlSerializer);
    xmlSerializer.endTag("", "SignalBackup");
    xmlSerializer.flush();
  }

  private static void exportSharedPreferences(Context context,
                                              XmlSerializer xmlSerializer) throws IOException {
    exportSharedPreferencesFile(context, xmlSerializer, "SecureSMS-Preferences");
    SharedPreferences defaultSharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
    exportSharedPreferences(context, xmlSerializer, null, defaultSharedPrefs);
  }

  private static void exportSharedPreferencesFile(Context context,
                                                  XmlSerializer xmlSerializer,
                                                  String sharedPrefsName) throws IOException {
    SharedPreferences sharedPrefs = context.getSharedPreferences(sharedPrefsName, 0);
    exportSharedPreferences(context, xmlSerializer, sharedPrefsName, sharedPrefs);
  }

  private static void exportSharedPreferences(Context context,
                                              XmlSerializer xmlSerializer,
                                              String sharedPrefsName,
                                              SharedPreferences sharedPrefs) throws IOException {
    Map<String, ?> allPrefs = sharedPrefs.getAll();
    for (String key : allPrefs.keySet()) {
      xmlSerializer.startTag("", "SignalSharedPreference");
      if (sharedPrefsName != null) {
        xmlSerializer.attribute("", "sharedPreferencesFileName", sharedPrefsName);
      }
      xmlSerializer.attribute("", "key", key);
      xmlSerializer.attribute("", "value", allPrefs.get(key).toString());
      xmlSerializer.attribute("", "type", allPrefs.get(key).getClass().toString());
      xmlSerializer.endTag("", "SignalSharedPreference");
      xmlSerializer.flush();
    }
  }

  private static void exportAttachments(Context context,
                                        XmlSerializer xmlSerializer) throws IOException {
    String tagName = "SignalAttachment";
    File dir = context.getDir("parts", Context.MODE_PRIVATE);
    exportDirectory(xmlSerializer, tagName, dir);
  }

  private static void exportFilesDirectory(Context context,
                                           XmlSerializer xmlSerializer) throws IOException {
    String tagName = "SignalFile";
    File dir = context.getFilesDir();
    exportDirectory(xmlSerializer, tagName, dir);
  }

  private static void exportDirectory(XmlSerializer xmlSerializer,
                                      String tagName,
                                      File dir) throws IOException {
    Stack<File> stack = new Stack<>();
    stack.push(dir);
    String parentPath = dir.getPath() + File.separator;
    while (!stack.empty()) {
      File d = stack.pop();
      File[] files = d.listFiles();
      for (File f : files) {
        if (f.isDirectory()) {
          stack.push(f);
        } else {
          String relativePath = f.getPath();
          relativePath = relativePath.substring(parentPath.length());
          xmlSerializer.startTag("", tagName);
          xmlSerializer.attribute("", "relativePath", relativePath);
          writeBlob(xmlSerializer, f);
          xmlSerializer.endTag("", tagName);
          xmlSerializer.flush();
        }
      }
    }
  }

  private static void writeBlob(XmlSerializer xmlSerializer, File f) throws IOException {
    FileInputStream rawInput = null;
    try {
      rawInput = new FileInputStream(f);
      InputStream encodedInput = new org.thoughtcrime.securesms.util.Base64.InputStream(rawInput, ENCODE);
      try {
        while (true) {
          int read = encodedInput.read();
          if (read == -1) {
            break;
          } else {
            xmlSerializer.text("" + ((char) read));
          }
        }
      } finally {
        encodedInput.close();
      }
    } finally {
      if (rawInput != null) {
        rawInput.close();
      }
    }
  }

  private static void exportTable(SQLiteDatabase db,
                                  XmlSerializer xmlSerializer,
                                  String tableName,
                                  String keyColumnName,
                                  String tagName,
                                  Mapping[] mappings)
          throws IOException {
    String[] columns = new String[mappings.length];
    for (int i = 0; i < columns.length; ++i) {
      columns[i] = mappings[i].sqliteColumnName();
    }
    Cursor cursor = db.query(tableName, columns, null, null, null, null, keyColumnName, null);
    if (cursor.getCount() > 0) {
      while (cursor.moveToNext()) {
        xmlSerializer.startTag("", tagName);
        for (int i = 0; i < columns.length; ++i) {
          Mapping m = mappings[i];
          String xmlKey = m.xmlAttributeName();
          String xmlValue = null;
          if (m.type() == FIELD_TYPE_BLOB) {
            byte[] blob = cursor.getBlob(i);
            if (blob != null) {
              xmlValue = Base64.encodeToString(blob, Base64.NO_WRAP);
            }
          } else {
            xmlValue = cursor.getString(i);
          }
          if (xmlValue != null) {
            xmlSerializer.attribute("", xmlKey, xmlValue);
          }
        }
        xmlSerializer.endTag("", tagName);
        xmlSerializer.flush();
      }
      cursor.close();
    }
  }

}
