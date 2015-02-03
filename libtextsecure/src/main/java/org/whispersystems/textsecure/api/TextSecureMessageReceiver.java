/**
 * Copyright (C) 2014 Open Whisper Systems
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
package org.whispersystems.textsecure.api;

import org.whispersystems.libaxolotl.InvalidMessageException;
import org.whispersystems.libaxolotl.InvalidVersionException;
import org.whispersystems.textsecure.api.crypto.AttachmentCipherInputStream;
import org.whispersystems.textsecure.api.messages.TextSecureAttachmentPointer;
import org.whispersystems.textsecure.api.messages.TextSecureEnvelope;
import org.whispersystems.textsecure.api.push.TrustStore;
import org.whispersystems.textsecure.api.util.CredentialsProvider;
import org.whispersystems.textsecure.internal.push.PushServiceSocket;
import org.whispersystems.textsecure.internal.util.StaticCredentialsProvider;
import org.whispersystems.textsecure.internal.websocket.WebSocketConnection;
import org.whispersystems.textsecure.internal.websocket.WebSocketProtos;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.whispersystems.textsecure.internal.websocket.WebSocketProtos.WebSocketRequestMessage;

public class TextSecureMessageReceiver {

  private final PushServiceSocket   socket;
  private final TrustStore          trustStore;
  private final String              url;
  private final CredentialsProvider credentialsProvider;

  public TextSecureMessageReceiver(String url, TrustStore trustStore,
                                   String user, String password, String signalingKey)
  {
    this(url, trustStore, new StaticCredentialsProvider(user, password, signalingKey));
  }

  public TextSecureMessageReceiver(String url, TrustStore trustStore, CredentialsProvider credentials) {
    this.url                 = url;
    this.trustStore          = trustStore;
    this.credentialsProvider = credentials;
    this.socket              = new PushServiceSocket(url, trustStore, credentials);
  }

  public InputStream retrieveAttachment(TextSecureAttachmentPointer pointer, File destination)
      throws IOException, InvalidMessageException
  {
    socket.retrieveAttachment(pointer.getRelay().orNull(), pointer.getId(), destination);
    return new AttachmentCipherInputStream(destination, pointer.getKey());
  }

  public TextSecureMessagePipe createMessagePipe() {
    WebSocketConnection webSocket = new WebSocketConnection(url, trustStore, credentialsProvider);
    return new TextSecureMessagePipe(webSocket, credentialsProvider);
  }

}
