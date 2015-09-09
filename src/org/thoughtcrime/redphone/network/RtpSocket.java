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

package org.thoughtcrime.redphone.network;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;

/**
 * RtpSocket wraps a {@link DatagramSocket}, allowing {@link RtpPacket}s to be sent a received.
 *
 * @author Stuart O. Anderson
 */
public class RtpSocket {

  private final byte [] buf = new byte[4096];

  private final String remoteIp;
  private final int    remotePort;

  private final DatagramSocket socket;

  public RtpSocket(int localPort, InetSocketAddress remoteAddress) throws SocketException {
    this.socket     = new DatagramSocket(localPort);
    this.remoteIp   = remoteAddress.getAddress().getHostAddress();
    this.remotePort = remoteAddress.getPort();

    socket.connect(new InetSocketAddress(remoteIp, remotePort));
    Log.d("RtpSocket", "Connected to: " + remoteIp);
  }

  public String getRemoteIp() {
    return remoteIp;
  }

  public int getRemotePort() {
    return remotePort;
  }

  public DatagramSocket getDatagramSocket() {
    return socket;
  }

  public void setTimeout(int timeoutMillis) {
    try {
      socket.setSoTimeout(timeoutMillis);
    } catch (SocketException e) {
      Log.w("RtpSocket", e);
    }
  }


  public void send(RtpPacket outPacket) throws IOException {
    try {
      socket.send(new DatagramPacket(outPacket.getPacket(), outPacket.getPacketLength()));
    } catch (IOException e) {
      if (!socket.isClosed()) {
        throw new IOException(e);
      }
    }
  }

  public RtpPacket receive() throws IOException {
    try {
      DatagramPacket dataPack = new DatagramPacket(buf, buf.length);
      socket.receive(dataPack);
      return  new RtpPacket(dataPack.getData(), dataPack.getLength());
    } catch( SocketTimeoutException e ) {
      //Do Nothing.
    } catch (IOException e) {
      if (!socket.isClosed()) {
        throw new IOException(e);
      }
    }
    return null;
  }

  public void close() {
    socket.close();
  }
}
