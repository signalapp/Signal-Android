package org.thoughtcrime.securesms.database;

import android.content.Context;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.io.naming.NoNameCoder;
import com.thoughtworks.xstream.io.xml.Xpp3DomDriver;

import org.thoughtcrime.securesms.crypto.AsymmetricMasterSecret;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.whispersystems.textsecure.crypto.MasterSecret;

import java.io.BufferedWriter;
import java.io.IOException;

public class IdentityExporter {

  public static void exportIdentity(Context context, MasterSecret masterSecret, BufferedWriter writer)
      throws IOException
  {
    new Writer(writer, MasterSecretUtil.getAsymmetricMasterSecret(context, masterSecret)).close();
  }

  @XStreamAlias("identity")
  public static class Identity {
    @XStreamAsAttribute  String type = "djb";
    @XStreamAsAttribute  byte[] public_key;
    @XStreamAsAttribute  byte[] private_key;

    public Identity(AsymmetricMasterSecret identity) {
      public_key  = identity.getDjbPublicKey().serialize();
      private_key = identity.getPrivateKey().serialize();
    }
  }

  public static class Writer {
    private BufferedWriter writer;
    private XStream        xstream;
    private Identity       identity;

    public Writer(BufferedWriter writer, AsymmetricMasterSecret identity) throws IOException {
      this.writer = writer;

      xstream = new XStream(new Xpp3DomDriver(new NoNameCoder()));
      xstream.autodetectAnnotations(true);
      this.identity = new Identity(identity);
    }

    public void close() throws IOException {
      writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
      xstream.toXML(identity, writer);
    }

  }
}
