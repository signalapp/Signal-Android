package org.thoughtcrime.securesms.database;

import android.util.Xml;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import com.thoughtworks.xstream.io.naming.NoNameCoder;
import com.thoughtworks.xstream.io.xml.Xpp3DomDriver;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

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
  private static final String TOA            = "toa";
  private static final String SC_TOA         = "sc_toa";
  private static final String LOCKED         = "locked";

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

  @XStreamAlias("sms")
  public static class Sms {
    @XStreamAsAttribute private String address;
    @XStreamAsAttribute private long   date;
    @XStreamAsAttribute private int    type;
    @XStreamAsAttribute private String subject;
    @XStreamAsAttribute private String body;
    @XStreamAsAttribute private int    status;
    @XStreamAsAttribute private int    protocol       = 0;
    @XStreamAsAttribute private String toa            = "null";
    @XStreamAsAttribute private String sc_toa         = "null";
    @XStreamAsAttribute private String service_center = "null";
    @XStreamAsAttribute private int    read           = 1;
    @XStreamAsAttribute private int    locked         = 0;

    public Sms(String address, long date, int type, String subject, String body, int status) {
      this.address = address;
      this.date = date;
      this.type = type;
      this.subject = subject;
      this.body = body;
      this.status = status;
    }
  }

  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  @XStreamAlias("smses")
  public static class Smses {
    @XStreamImplicit    private List<Sms> smses;
    @XStreamAsAttribute private int       count;

    public Smses(int count) {
      this.count = count;
      this.smses = new LinkedList<Sms>();
    }

    public void addSms(Sms sms) {
      smses.add(sms);
    }
  }

  public static class Writer {

    private BufferedWriter writer;
    private XStream        xstream;
    private Smses          smses;

    public Writer(String path, int count) throws IOException {
      this.writer = new BufferedWriter(new FileWriter(path));

      xstream = new XStream(new Xpp3DomDriver(new NoNameCoder()));
      xstream.autodetectAnnotations(true);
      smses = new Smses(count);
    }

    public void writeItem(Sms sms) throws IOException {
      smses.addSms(sms);
    }

    public void close() throws IOException {
      xstream.toXML(smses, writer);
    }

    private String escapeXML(String s) {
      if (Util.isEmpty(s)) return s;

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
