package org.whispersystems.textsecure.api;

import android.content.Context;

import org.whispersystems.libaxolotl.InvalidMessageException;
import org.whispersystems.libaxolotl.state.AxolotlStore;
import org.whispersystems.textsecure.api.messages.TextSecureAttachmentPointer;
import org.whispersystems.textsecure.api.crypto.AttachmentCipherInputStream;
import org.whispersystems.textsecure.push.PushServiceSocket;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class TextSecureMessageReceiver {

  private final String            signalingKey;
  private final AxolotlStore      axolotlStore;
  private final PushServiceSocket socket;


  public TextSecureMessageReceiver(Context context, String signalingKey, String url,
                                   PushServiceSocket.TrustStore trustStore,
                                   String user, String password,
                                   AxolotlStore axolotlStore)
  {
    this.axolotlStore = axolotlStore;
    this.signalingKey = signalingKey;
    this.socket       = new PushServiceSocket(context, url, trustStore, user, password);
  }

  public InputStream retrieveAttachment(TextSecureAttachmentPointer pointer, File destination)
      throws IOException, InvalidMessageException
  {
    socket.retrieveAttachment(pointer.getRelay().orNull(), pointer.getId(), destination);
    return new AttachmentCipherInputStream(destination, pointer.getKey());
  }

}
