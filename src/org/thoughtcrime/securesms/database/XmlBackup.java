/**
 * Copyright (C) 2013-2014 Open WhisperSystems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.database;

import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;

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
    this.parser = Xml.newPullParser();
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
    parser.setInput(new FileInputStream(path), null);
  }

  public XmlBackupItem getNext() throws IOException, XmlPullParserException {
    while (parser.next() != XmlPullParser.END_DOCUMENT) {
      if (parser.getEventType() != XmlPullParser.START_TAG) {
        continue;
      }

      String name = parser.getName();

      if (!name.equals("sms")) {
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

    private BufferedWriter writer;
    private XmlSerializer serializer;

    public Writer(String path, int count) throws IOException {
      this.writer     = new BufferedWriter(new FileWriter(path));
      this.serializer = Xml.newSerializer();

      this.serializer.setOutput(writer);
      this.serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
      this.serializer.startDocument("UTF-8", true);
      this.serializer.startTag("", "smses");
      this.serializer.attribute("", "count", count+"");
    }

    public void writeItem(XmlBackupItem item) throws IOException {
      this.serializer.startTag("", "sms");
      this.serializer.attribute("", "protocol", item.getProtocol() + "");
      this.serializer.attribute("", "address", item.getAddress());
      this.serializer.attribute("", "date", item.getDate()+"");
      this.serializer.attribute("", "type", item.getType()+"");
      this.serializer.attribute("", "subject", item.getSubject()+"");
      try {
        this.serializer.attribute("", "body", item.getBody()+"");
      } catch (IllegalArgumentException ise) {
        // XXX - Fucking Android. Their serializer includes a bug that doesn't
        // handle some unicode characters correctly.
        Log.w("XmlBackup", ise);
      }
      this.serializer.attribute("", "toa", null+"");
      this.serializer.attribute("", "sc_toa", null+"");
      this.serializer.attribute("", "service_center", item.getServiceCenter()+"");
      this.serializer.attribute("", "read", item.getRead()+"");
      this.serializer.attribute("" , "status", item.getStatus()+"");
      this.serializer.attribute("", "locked", "0");
      this.serializer.endTag("", "sms");
    }

    public void close() throws IOException {
      this.serializer.endTag("", "smses");
      this.serializer.endDocument();
    }
  }
}
