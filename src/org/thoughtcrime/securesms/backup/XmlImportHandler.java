package org.thoughtcrime.securesms.backup;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.preference.PreferenceManager;
import android.util.Base64;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.RawDatabase;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Hashtable;

import static android.database.Cursor.FIELD_TYPE_BLOB;
import static android.database.Cursor.FIELD_TYPE_FLOAT;
import static android.database.Cursor.FIELD_TYPE_INTEGER;
import static android.database.Cursor.FIELD_TYPE_STRING;
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

public class XmlImportHandler extends DefaultHandler {

  private DecodeBase64Writer writer;
  private Context context = null;
  private SQLiteDatabase db;
  private Hashtable<String, Exporter.Mapping> draftsMappingHt;
  private Hashtable<String, Exporter.Mapping> groupsMappingHt;
  private Hashtable<String, Exporter.Mapping> identitiesMappingHt;
  private Hashtable<String, Exporter.Mapping> mmsMappingHt;
  private Hashtable<String, Exporter.Mapping> pushMappingHt;
  private Hashtable<String, Exporter.Mapping> recipientPreferencesMappingHt;
  private Hashtable<String, Exporter.Mapping> smsMappingHt;
  private Hashtable<String, Exporter.Mapping> threadsMappingHt;
  private Hashtable<String, Exporter.Mapping> attachmentMappingHt;

  XmlImportHandler(Context context) {
    super();

    RawDatabase rawDatabase = DatabaseFactory.getRawDatabase(context);
    this.context = context;
    this.db = rawDatabase.getWritableDatabase();

    cleanDatabase(db);

    this.draftsMappingHt = generateXmlToMappingHT(DraftsMapping.values());
    this.groupsMappingHt = generateXmlToMappingHT(GroupsMapping.values());
    this.identitiesMappingHt = generateXmlToMappingHT(IdentitiesMapping.values());
    this.mmsMappingHt = generateXmlToMappingHT(MmsMapping.values());
    this.pushMappingHt = generateXmlToMappingHT(PushMapping.values());
    this.recipientPreferencesMappingHt = generateXmlToMappingHT(RecipientPreferencesMapping.values());
    this.smsMappingHt = generateXmlToMappingHT(SmsMapping.values());
    this.threadsMappingHt = generateXmlToMappingHT(ThreadMapping.values());
    this.attachmentMappingHt = generateXmlToMappingHT(AttachmentMapping.values());
  }

  private static void cleanDatabase(SQLiteDatabase db) {
    db.delete(TABLE_NAME_DRAFTS, null, null);
    db.delete(TABLE_NAME_GROUPS, null, null);
    db.delete(TABLE_NAME_IDENTITIES, null, null);
    db.delete(TABLE_NAME_MMS, null, null);
    db.delete(TABLE_NAME_PUSH, null, null);
    db.delete(TABLE_NAME_RECIPIENT_PREFERENCES, null, null);
    db.delete(TABLE_NAME_SMS, null, null);
    db.delete(TABLE_NAME_THREAD, null, null);
  }

  @Override
  public void startElement(String uri, String localName,
                           String qName, Attributes attributes) {
    closeWriter();

    if (localName.equals(TAG_IDENTITIES)) {
      importTableRow(db, attributes, TABLE_NAME_IDENTITIES, identitiesMappingHt);
    } else if (localName.equals(TAG_SMS)) {
      importTableRow(db, attributes, TABLE_NAME_SMS, smsMappingHt);
    } else if (localName.equals(TAG_MMS)) {
      importTableRow(db, attributes, TABLE_NAME_MMS, mmsMappingHt);
    } else if (localName.equals(TAG_GROUPS)) {
      importTableRow(db, attributes, TABLE_NAME_GROUPS, groupsMappingHt);
    } else if (localName.equals(TAG_DRAFTS)) {
      importTableRow(db, attributes, TABLE_NAME_DRAFTS, draftsMappingHt);
    } else if (localName.equals(TAG_PUSH)) {
      importTableRow(db, attributes, TABLE_NAME_PUSH, pushMappingHt);
    } else if (localName.equals(TAG_RECIPIENT_PREFERENCES)) {
      importTableRow(db, attributes, TABLE_NAME_RECIPIENT_PREFERENCES, recipientPreferencesMappingHt);
    } else if (localName.equals(TAG_THREAD)) {
      importTableRow(db, attributes, TABLE_NAME_THREAD, threadsMappingHt);
    } else if (localName.equals(TAG_ATTACHMENT_ROW)) {
      importTableRow(db, attributes, TABLE_NAME_ATTACHMENT_ROW, attachmentMappingHt);
    } else if (localName.equals("SignalSharedPreference")) {
      importSharedPreference(context, attributes);
    } else if (localName.equals("SignalAttachment")) {
      createAttachmentStreams(context, attributes);
    } else if (localName.equals("SignalFile")) {
      createFileStreams(context, attributes);
    }
  }

  @Override
  public void endElement(String uri, String localName,
                         String qName) {
    closeWriter();
  }

  @Override
  public void characters(char[] ch, int start, int length) {
    if (this.writer != null) {
      try {
        this.writer.write(ch, start, length);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  @Override
  public void endDocument() {
    closeWriter();
    db.close();
    db = null;
    context = null;
  }

  private void createAttachmentStreams(Context context, Attributes attributes) {
    String dirname = "parts";
    String filename = null;
    if (attributes.getLength() < 1) {
      throw new RuntimeException("must have a filename");
    }
    filename = attributes.getValue(0);
    File outdir = context.getDir(dirname, Context.MODE_PRIVATE);
    File rawOutputFile = new File(outdir, filename);
    try {
      makeParentDirs(rawOutputFile);
    } catch (IOException e) {
      e.printStackTrace();
    } catch (ImportException e) {
      e.printStackTrace();
    }
    this.writer = createWriter(rawOutputFile);
  }

  private void createFileStreams(Context context, Attributes attributes) {
    String relativePath = null;
    for (int i = 0; i < attributes.getLength(); ++i) {
      if ("relativePath".equals(attributes.getLocalName(i))) {
        relativePath = attributes.getValue(i);
      }
    }
    if (relativePath == null) {
      throw new RuntimeException("must have a relativePath");
    }
    File filesDir = context.getFilesDir();
    File outfile = new File(filesDir, relativePath);
    try {
      makeParentDirs(outfile);
    } catch (ImportException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
    this.writer = createWriter(outfile);
  }

  private DecodeBase64Writer createWriter(File file) {
    FileOutputStream rawStream = null;
    try {
      rawStream = new FileOutputStream(file);
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }
    if (rawStream != null) {
      return new DecodeBase64Writer(rawStream);
    } else {
      return null;
    }
  }

  private static void makeParentDirs(File f) throws IOException, ImportException {
    File parentDir = f.getParentFile();
    if (parentDir != null) {
      parentDir.mkdirs();
    }
  }

  private void closeWriter() {
    if (this.writer != null) {
      try {
        this.writer.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    this.writer = null;
  }

  private static Hashtable<String, Exporter.Mapping> generateXmlToMappingHT(Exporter.Mapping[] mappings) {
    Hashtable<String, Exporter.Mapping> ht = new Hashtable<>();
    for (Exporter.Mapping m : mappings) {
      ht.put(m.xmlAttributeName(), m);
    }
    return ht;
  }

  private static void importTableRow(SQLiteDatabase db,
                                     Attributes attributes,
                                     String tableName,
                                     Hashtable<String, Exporter.Mapping> xmlNameToMappingHt) {
    int attributeCount = attributes.getLength();
    if (attributeCount <= 0) {
      return;
    }
    ContentValues contentValues = new ContentValues();
    for (int i = 0; i < attributeCount; ++i) {
      String key = attributes.getLocalName(i);
      String value = attributes.getValue(i);
      Exporter.Mapping mapping = xmlNameToMappingHt.get(key);
      if (mapping != null) {
        String sqliteName = mapping.sqliteColumnName();
        switch (mapping.type()) {
          case FIELD_TYPE_INTEGER:
            long t = Long.parseLong(value);
            contentValues.put(sqliteName, t);
            break;
          case FIELD_TYPE_STRING:
            contentValues.put(sqliteName, value);
            break;
          case FIELD_TYPE_BLOB:
            byte[] blob = Base64.decode(value, Base64.NO_WRAP);
            contentValues.put(sqliteName, blob);
            break;
          case FIELD_TYPE_FLOAT:
            float f = Float.parseFloat(value);
            contentValues.put(sqliteName, f);
            break;
        }
      }
    }
    db.insert(tableName, null, contentValues);
  }


  private static void importSharedPreference(Context context, Attributes attributes) {
    String sharedPreferenceFilename = null;
    String key = null;
    String value = null;
    String type = null;
    for (int i = 0; i < attributes.getLength(); ++i) {
      String k = attributes.getLocalName(i);
      String v = attributes.getValue(i);
      if (k.equals("sharedPreferencesFileName")) {
        sharedPreferenceFilename = v;
      } else if (k.equals("key")) {
        key = v;
      } else if (k.equals("value")) {
        value = v;
      } else if (k.equals("type")) {
        type = v;
      }
    }
    if ((key == null) || (value == null) || (type == null)) {
      return;
    }

    SharedPreferences sharedPrefs;
    if (sharedPreferenceFilename != null) {
      sharedPrefs = context.getSharedPreferences(sharedPreferenceFilename, 0);
    } else {
      sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
    }
    if ("class java.lang.String".equals(type)) {
      sharedPrefs.edit().putString(key, value).commit();
    } else if ("class java.lang.Boolean".equals(type)) {
      Boolean parsedValue = Boolean.parseBoolean(value);
      sharedPrefs.edit().putBoolean(key, parsedValue).commit();
    } else if ("class java.lang.Integer".equals(type)) {
      Integer parsedValue = Integer.parseInt(value);
      sharedPrefs.edit().putInt(key, parsedValue).commit();
    } else if ("class java.lang.Long".equals(type)) {
      Long parsedValue = Long.parseLong(value);
      sharedPrefs.edit().putLong(key, parsedValue).commit();
    }
  }
}
