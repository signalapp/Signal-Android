/*
 * Copyright (C) 2011 Whisper Systems
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

package org.privatechats.redphone.signaling;

import android.util.Log;

import org.privatechats.redphone.signaling.signals.OpenPortSignal;
import org.privatechats.redphone.signaling.signals.Signal;
import org.privatechats.redphone.util.LineReader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.Map;

/**
 * Responsible for getting the UDP connection flow started.
 *
 * We start by sending a "hello signal" to this call's remote UDP port,
 * which the server responds to.  This should effectively punch open a
 * bidirectional UDP flow in any potential NAT devices.
 *
 * @author Moxie Marlinspke
 *
 */

public class NetworkConnector {

  private static final String TAG = NetworkConnector.class.getSimpleName();

  private       DatagramSocket socket;
  private final long           sessionId;
  private final String         server;
  private final int            port;

  public NetworkConnector(long sessionId, String server, int port) {
    Log.w(TAG, "Opening up port: " + server + " , " + port);
    this.sessionId = sessionId;
    this.server    = server;
    this.port      = port;
  }

  public int makeConnection() throws SessionInitiationFailureException {
    int result = -1;
    int timeout = 1000;
    for (int attempts = 0; attempts < 5; attempts++) {
      Log.d(TAG, "attempting connection");
      result = attemptConnection( timeout );
      if (result != -1)
        break;
      timeout *= 2;
      if( timeout > 10000 ) timeout = 10000;
    }

    if (result == -1)
      throw new SessionInitiationFailureException("Could not connect to server.");

    return result;
  }

  private int attemptConnection( int timeout ) {
    try {
      socket = new DatagramSocket();
      socket.connect(new InetSocketAddress(server, port));
      socket.setSoTimeout(timeout);
      sendSignal(new OpenPortSignal(sessionId));

      SignalResponse response = readSignalResponse();

      if (response.getStatusCode() != 200) {
        Log.e(TAG, "Bad response from server.");
        socket.close();
        return -1;
      }

      int localPort = socket.getLocalPort();
      socket.close();
      return localPort;

    } catch (IOException | SignalingException e) {
      Log.w(TAG, e);
    }
    return -1;
  }

  private void sendSignal(Signal signal) throws IOException {
    byte[] signalBytes    = signal.serialize().getBytes();
    DatagramPacket packet = new DatagramPacket(signalBytes, signalBytes.length);
    socket.send(packet);
  }

  private SignalResponse readSignalResponse() throws SignalingException, IOException {
    byte[] responseBuffer   = new byte[2048];
    DatagramPacket response = new DatagramPacket(responseBuffer, responseBuffer.length);

    socket.receive(response);

    ByteArrayInputStream bais           = new ByteArrayInputStream(responseBuffer);
    LineReader lineReader               = new LineReader(bais);
    SignalResponseReader responseReader = new SignalResponseReader(lineReader);

    int statusCode              = responseReader.readSignalResponseCode();
    Map<String, String> headers = responseReader.readSignalHeaders();
    byte[] body                 = responseReader.readSignalBody(headers);

    return new SignalResponse(statusCode, headers, body);
  }
}
