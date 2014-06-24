package org.thoughtcrime.securesms.database;

import org.whispersystems.textsecure.util.Util;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class XmlBackup {

  private static final String PROTOCOL       = "protocol";
  private static final String ADDRESS        = "address";
  private static final String DATE           = "date";
  private static final String TYPE           = "type";
  private static final String SUBJECT        = "subject";
  private static final String BODY           = "body";
  private static final String SERVICE_CENTER = "service_center";
  private static final String READ           = "read";
  private static final String STATUS         = "status";

  private final XmlPullParser parser;

  public XmlBackup(String path) throws XmlPullParserException, FileNotFoundException {
    XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
    parser = factory.newPullParser();
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
        else if (attributeName.equals(DATE          )) item.date          = Long.parseLong(parser.getAttributeValue(i));
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
    private long   date;
    private int    type;
    private String subject;
    private String body;
    private String serviceCenter;
    private int    read;
    private int    status;

    public XmlBackupItem() {}

    public XmlBackupItem(int protocol, String address, long date, int type, String subject,
                         String body, String serviceCenter, int read, int status)
    {
      this.protocol      = protocol;
      this.address       = address;
      this.date          = date;
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

    public long getDate() {
      return date;
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

    private static final String XML_HEADER      = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\" ?>";
    private static final String CREATED_BY      = "<!-- File Created By TextSecure -->";
    private static final String OPEN_TAG_SMSES  = "<smses count=\"%d\">";
    private static final String CLOSE_TAG_SMSES = "</smses>";
    private static final String OPEN_TAG_SMS    = " <sms ";
    private static final String CLOSE_EMPTYTAG  = "/>";
    private static final String OPEN_ATTRIBUTE  = "=\"";
    private static final String CLOSE_ATTRIBUTE = "\" ";
    private static final String TOA             = "toa=\"null\" ";
    private static final String SC_TOA          = "sc_toa=\"null\" ";
    private static final String LOCKED          = "locked=\"0\" ";

    private static final String ESCAPE_PATTERN = "[^\u0020-\uD7FF]";
    private static final Pattern pattern       = Pattern.compile(ESCAPE_PATTERN);
    private final BufferedWriter bufferedWriter;

    public Writer(String path, int count) throws IOException {
      bufferedWriter = new BufferedWriter(new FileWriter(path, false));

      bufferedWriter.write(XML_HEADER);
      bufferedWriter.newLine();
      bufferedWriter.write(CREATED_BY);
      bufferedWriter.newLine();
      bufferedWriter.write(String.format(OPEN_TAG_SMSES, count));
    }

    public void writeItem(XmlBackupItem item) throws IOException {
      StringBuilder stringBuilder = new StringBuilder();

      stringBuilder.append(OPEN_TAG_SMS);

      stringBuilder.append(PROTOCOL      ).append(OPEN_ATTRIBUTE).append(item.getProtocol()       ).append(CLOSE_ATTRIBUTE);
      stringBuilder.append(ADDRESS       ).append(OPEN_ATTRIBUTE).append(item.getAddress()        ).append(CLOSE_ATTRIBUTE);
      stringBuilder.append(DATE          ).append(OPEN_ATTRIBUTE).append(item.getDate()           ).append(CLOSE_ATTRIBUTE);
      stringBuilder.append(TYPE          ).append(OPEN_ATTRIBUTE).append(item.getType()           ).append(CLOSE_ATTRIBUTE);
      stringBuilder.append(SUBJECT       ).append(OPEN_ATTRIBUTE).append(String.valueOf(escapeXML(item.getSubject()))
                                                                                                  ).append(CLOSE_ATTRIBUTE);
      stringBuilder.append(BODY          ).append(OPEN_ATTRIBUTE).append(escapeXML(item.getBody())).append(CLOSE_ATTRIBUTE);
      stringBuilder.append(TOA           );
      stringBuilder.append(SC_TOA        );
      stringBuilder.append(SERVICE_CENTER).append(OPEN_ATTRIBUTE).append(item.getServiceCenter()  ).append(CLOSE_ATTRIBUTE);
      stringBuilder.append(READ          ).append(OPEN_ATTRIBUTE).append(item.getRead()           ).append(CLOSE_ATTRIBUTE);
      stringBuilder.append(STATUS        ).append(OPEN_ATTRIBUTE).append(item.getStatus()         ).append(CLOSE_ATTRIBUTE);
      stringBuilder.append(LOCKED        );

      stringBuilder.append(CLOSE_EMPTYTAG);

      bufferedWriter.newLine();
      bufferedWriter.write(stringBuilder.toString());
    }

    public void close() throws IOException {
      bufferedWriter.newLine();
      bufferedWriter.write(CLOSE_TAG_SMSES);
      bufferedWriter.close();
    }

    private String escapeXML(String s) {
      if (Util.isEmpty(s)) return s;

      Matcher matcher = pattern.matcher( s.replace("&",  "&amp;")
                                          .replace("<",  "&lt;")
                                          .replace(">",  "&gt;")
                                          .replace("\"", "&quot;")
                                          .replace("'",  "&apos;"));
      StringBuffer st = new StringBuffer();

      while (matcher.find()) {
        String escaped="";
        for (char ch: matcher.group(0).toCharArray())
          escaped += ("&#" + ((int)ch) + ";");
        matcher.appendReplacement(st, escaped);
      }
      matcher.appendTail(st);
      return st.toString();
    }

  }
}
