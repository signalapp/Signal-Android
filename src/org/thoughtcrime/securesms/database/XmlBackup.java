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
  private final XmlPullParser parser;

  public XmlBackup(String path) throws XmlPullParserException, FileNotFoundException {
    this.parser = XmlPullParserFactory.newInstance().newPullParser();
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
    parser.setInput(new FileInputStream(path), null);
  }

  @XStreamAlias("sms")
  public static class Sms {
    @XStreamAsAttribute  String address;
    @XStreamAsAttribute  long   date;
    @XStreamAsAttribute  int    type;
    @XStreamAsAttribute  String subject;
    @XStreamAsAttribute  String body;
    @XStreamAsAttribute  int    status;
    @XStreamAsAttribute  int    protocol       = 0;
    @XStreamAsAttribute  String toa            = "null";
    @XStreamAsAttribute  String sc_toa         = "null";
    @XStreamAsAttribute  String service_center = "null";
    @XStreamAsAttribute  int    read           = 1;
    @XStreamAsAttribute  int    locked         = 0;

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
    @XStreamImplicit     List<Sms> smses;
    @XStreamAsAttribute  int       count;

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

      xstream = new XStream();
      xstream.autodetectAnnotations(true);
      smses = new Smses(count);
    }

    public void writeItem(Sms sms) throws IOException {
      smses.addSms(sms);
    }

    public void close() throws IOException {
      writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
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
