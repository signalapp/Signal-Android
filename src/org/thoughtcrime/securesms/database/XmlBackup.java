package org.thoughtcrime.securesms.database;

import android.util.Log;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import com.thoughtworks.xstream.io.naming.NoNameCoder;
import com.thoughtworks.xstream.io.xml.Xpp3DomDriver;

import org.thoughtcrime.securesms.crypto.AsymmetricMasterSecret;
import org.whispersystems.textsecure.crypto.IdentityKey;
import org.whispersystems.textsecure.crypto.IdentityKeyPair;
import org.whispersystems.textsecure.crypto.InvalidKeyException;
import org.whispersystems.textsecure.crypto.ecc.Curve;
import org.whispersystems.textsecure.crypto.ecc.DjbECPrivateKey;
import org.whispersystems.textsecure.crypto.ecc.DjbECPublicKey;
import org.whispersystems.textsecure.crypto.ecc.ECKeyPair;
import org.whispersystems.textsecure.crypto.ecc.ECPublicKey;
import org.whispersystems.textsecure.util.Hex;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.LinkedList;
import java.util.List;

public class XmlBackup {

  public XmlBackup(String path) throws XmlPullParserException, FileNotFoundException {
    XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
    parser.setInput(new FileInputStream(path), null);
  }

  public static class Child {}

  @XStreamAlias("sms")
  public static class Sms extends Child {
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

  @XStreamAlias("identity")
  public static class Identity extends Child {
    @XStreamAsAttribute  byte[] public_key;
    @XStreamAsAttribute  byte[] private_key;

    public Identity(IdentityKeyPair keyPair) {
      public_key  = keyPair.getPublicKey().serialize();
      private_key = keyPair.getPrivateKey().serialize();
    }

    public ECKeyPair toKeyPair() throws InvalidKeyException {
      final IdentityKeyPair identityKeyPair = new IdentityKeyPair(new IdentityKey(public_key, 0), Curve.decodePrivatePoint(private_key));
      return new ECKeyPair(identityKeyPair.getPublicKey().getPublicKey(), identityKeyPair.getPrivateKey());
    }
  }

  public static class Writer {

    private BufferedWriter     writer;
    private XStream            xstream;

    public Writer(BufferedWriter writer, int count) throws IOException {
      this.writer = writer;

      xstream = new XStream(new Xpp3DomDriver(new NoNameCoder()));
      xstream.autodetectAnnotations(true);
      writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
      writer.write(String.format("<smses count=\"%d\">\n", count));
    }

    public void writeItem(Child child) throws IOException {
      writer.write("  ");
      xstream.toXML(child, writer);
      writer.write("\n");
    }

    public void close() throws IOException {
      writer.write("</smses>");
      writer.close();
    }
  }
}
