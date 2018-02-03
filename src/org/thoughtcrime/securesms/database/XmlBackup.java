package org.thoughtcrime.securesms.database;

import android.text.TextUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class XmlBackup {

  private static final String PROTOCOL       = "protocol";
  private static final String ADDRESS        = "address";
  private static final String CONTACT_NAME   = "contact_name";
  private static final String DATE           = "date";
  private static final String READABLE_DATE  = "readable_date";
  private static final String TYPE           = "type";
  private static final String SUBJECT        = "subject";
  private static final String BODY           = "body";
  private static final String SERVICE_CENTER = "service_center";
  private static final String READ           = "read";
  private static final String STATUS         = "status";
  private static final String TOA            = "toa";
  private static final String SC_TOA         = "sc_toa";
  private static final String LOCKED         = "locked";

  private static final SimpleDateFormat dateFormatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");

  private final XmlPullParser parser;

  public XmlBackup(String path) throws XmlPullParserException, FileNotFoundException {
    this.parser = XmlPullParserFactory.newInstance().newPullParser();
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
    parser.setInput(new FileInputStream(path), null);
  }

  public XmlBackupItem getNext() throws IOException, XmlPullParserException {
    while (parser.next() != XmlPullParser.END_DOCUMENT) {
      if (parser.getEventType() != XmlPullParser.START_TAG) {
        continue;
      }

      String name = parser.getName();

      if (!name.equalsIgnoreCase("sms")) {
        continue;
      }

      int attributeCount = parser.getAttributeCount();

      if (attributeCount <= 0) {
        continue;
      }

      XmlBackupItem item = new XmlBackupItem();

      for (int i=0;i<attributeCount;i++) {
        String attributeName = parser.getAttributeName(i);

        if      (attributeName.equals(PROTOCOL      )) item.protocol      = Integer.parseInt(parser.getAttributeValue(i));
        else if (attributeName.equals(ADDRESS       )) item.address       = parser.getAttributeValue(i);
        else if (attributeName.equals(CONTACT_NAME  )) item.contactName   = parser.getAttributeValue(i);
        else if (attributeName.equals(DATE          )) item.date          = Long.parseLong(parser.getAttributeValue(i));
        else if (attributeName.equals(READABLE_DATE )) item.readableDate  = parser.getAttributeValue(i);
        else if (attributeName.equals(TYPE          )) item.type          = Integer.parseInt(parser.getAttributeValue(i));
        else if (attributeName.equals(SUBJECT       )) item.subject       = parser.getAttributeValue(i);
        else if (attributeName.equals(BODY          )) item.body          = parser.getAttributeValue(i);
        else if (attributeName.equals(SERVICE_CENTER)) item.serviceCenter = parser.getAttributeValue(i);
        else if (attributeName.equals(READ          )) item.read          = Integer.parseInt(parser.getAttributeValue(i));
        else if (attributeName.equals(STATUS        )) item.status        = Integer.parseInt(parser.getAttributeValue(i));
      }

      return item;
    }

    return null;
  }

  public static class XmlBackupItem {
    private int    protocol;
    private String address;
    private String contactName;
    private long   date;
    private String readableDate;
    private int    type;
    private String subject;
    private String body;
    private String serviceCenter;
    private int    read;
    private int    status;

    public XmlBackupItem() {}

    public XmlBackupItem(int protocol, String address, String contactName, long date, int type,
                         String subject, String body, String serviceCenter, int read, int status)
    {
      this.protocol      = protocol;
      this.address       = address;
      this.contactName   = contactName;
      this.date          = date;
      this.readableDate  = dateFormatter.format(date);
      this.type          = type;
      this.subject       = subject;
      this.body          = body;
      this.serviceCenter = serviceCenter;
      this.read          = read;
      this.status        = status;
    }

    public int getProtocol() {
      return protocol;
    }

    public String getAddress() {
      return address;
    }

    public String getContactName() {
      return contactName;
    }

    public long getDate() {
      return date;
    }

    public String getReadableDate() {
      return readableDate;
    }

    public int getType() {
      return type;
    }

    public String getSubject() {
      return subject;
    }

    public String getBody() {
      return body;
    }

    public String getServiceCenter() {
      return serviceCenter;
    }

    public int getRead() {
      return read;
    }

    public int getStatus() {
      return status;
    }
  }

  public static class Writer {

    private static final String  XML_HEADER      = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\" ?>";
    private static final String  CREATED_BY      = "<!-- File Created By Signal -->";
    private static final String  OPEN_TAG_SMSES  = "<smses count=\"%d\">";
    private static final String  CLOSE_TAG_SMSES = "</smses>";
    private static final String  OPEN_TAG_SMS    = " <sms ";
    private static final String  CLOSE_EMPTYTAG  = "/>";
    private static final String  OPEN_ATTRIBUTE  = "=\"";
    private static final String  CLOSE_ATTRIBUTE = "\" ";

    private static final Pattern PATTERN         = Pattern.compile("[^\u0020-\uD7FF]");

    private final BufferedWriter bufferedWriter;

    public Writer(String path, int count) throws IOException {
      bufferedWriter = new BufferedWriter(new FileWriter(path, false));

      bufferedWriter.write(XML_HEADER);
      bufferedWriter.newLine();
      bufferedWriter.write(CREATED_BY);
      bufferedWriter.newLine();
      bufferedWriter.write(String.format(Locale.ROOT, OPEN_TAG_SMSES, count));
    }

    public void writeItem(XmlBackupItem item) throws IOException {
      StringBuilder stringBuilder = new StringBuilder();

      stringBuilder.append(OPEN_TAG_SMS);
      appendAttribute(stringBuilder, PROTOCOL, item.getProtocol());
      appendAttribute(stringBuilder, ADDRESS, escapeXML(item.getAddress()));
      appendAttribute(stringBuilder, CONTACT_NAME, escapeXML(item.getContactName()));
      appendAttribute(stringBuilder, DATE, item.getDate());
      appendAttribute(stringBuilder, READABLE_DATE, item.getReadableDate());
      appendAttribute(stringBuilder, TYPE, item.getType());
      appendAttribute(stringBuilder, SUBJECT, escapeXML(item.getSubject()));
      appendAttribute(stringBuilder, BODY, escapeXML(item.getBody()));
      appendAttribute(stringBuilder, TOA, "null");
      appendAttribute(stringBuilder, SC_TOA, "null");
      appendAttribute(stringBuilder, SERVICE_CENTER, item.getServiceCenter());
      appendAttribute(stringBuilder, READ, item.getRead());
      appendAttribute(stringBuilder, STATUS, item.getStatus());
      appendAttribute(stringBuilder, LOCKED, 0);
      stringBuilder.append(CLOSE_EMPTYTAG);

      bufferedWriter.newLine();
      bufferedWriter.write(stringBuilder.toString());
    }

    private <T> void appendAttribute(StringBuilder stringBuilder, String name, T value) {
      stringBuilder.append(name).append(OPEN_ATTRIBUTE).append(value).append(CLOSE_ATTRIBUTE);
    }

    public void close() throws IOException {
      bufferedWriter.newLine();
      bufferedWriter.write(CLOSE_TAG_SMSES);
      bufferedWriter.close();
    }

    private String escapeXML(String s) {
      if (TextUtils.isEmpty(s)) return s;

      Matcher matcher = PATTERN.matcher( s.replace("&",  "&amp;")
                                          .replace("<",  "&lt;")
                                          .replace(">",  "&gt;")
                                          .replace("\"", "&quot;")
                                          .replace("'",  "&apos;"));
      StringBuffer st = new StringBuffer();

      while (matcher.find()) {
        String escaped="";
        for (char ch: matcher.group(0).toCharArray()) {
          escaped += ("&#" + ((int) ch) + ";");
        }
        matcher.appendReplacement(st, escaped);
      }
      matcher.appendTail(st);
      return st.toString();
    }

  }
}
