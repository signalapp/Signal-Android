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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

/**
 * A multi-connect utility class.  Given a list of addresses, connect
 * to all of them simultaneously, and return the connection that completes
 * first.  Used as a client-centric form of server failover and discovery
 * of low-latency paths.
 *
 * @author Moxie Marlinspike
 *
 */
public class LowLatencySocketConnector {

  private static final int CONNECT_TIMEOUT_MILLIS = 10000;

  public static Socket connect(InetAddress[] addresses, int port) throws IOException {
    Selector selector                   = Selector.open();
    SocketChannel[] channels            = constructSocketChannels(selector, addresses.length);
    InetSocketAddress[] socketAddresses = constructSocketAddresses(addresses, port);

    assert(channels.length == socketAddresses.length);

    connectChannels(channels, socketAddresses);

    return waitForFirstChannel(selector);
  }

  private static Socket waitForFirstChannel(Selector selector) throws IOException {
    while (hasValidKeys(selector)) {
      int readyCount = selector.select(CONNECT_TIMEOUT_MILLIS);

      if (readyCount == 0)
        throw new IOException("Connect timed out!");

      Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();

      while (iterator.hasNext()) {
        SelectionKey key = iterator.next();
        iterator.remove();

        if (!key.isValid()) continue;

        SocketChannel channel = (SocketChannel)key.channel();
        boolean connected     = isChannelConnected(channel);

        if (connected) {
          closeAllButChannel(selector, channel);
          return channel.socket();
        } else {
          key.cancel();
        }
      }
    }

    throw new IOException("All connections failed!");
  }

  private static void closeAllButChannel(Selector selector, SocketChannel channel) {
    for (SelectionKey key : selector.keys()) {
      if (key.channel() != channel) {
        try {
          key.channel().close();
        } catch (IOException ioe) {}
      }
    }
  }

  private static boolean hasValidKeys(Selector selector) {
    for (SelectionKey key : selector.keys())
      if (key.isValid())
        return true;

    return false;
  }

  private static boolean isChannelConnected(SocketChannel channel) {
    try {
      return channel.finishConnect();
    } catch (IOException ioe) {
      Log.w("LowLatencySocketConnector", ioe);
      return false;
    }
  }

  private static void connectChannels(SocketChannel[] channels, InetSocketAddress[] addresses)
      throws IOException
  {
    for (int i=0;i<channels.length;i++) {
      channels[i].connect(addresses[i]);
    }
  }

  private static InetSocketAddress[] constructSocketAddresses(InetAddress[] addresses, int port) {
    InetSocketAddress[] socketAddresses = new InetSocketAddress[addresses.length];

    for (int i=0;i<socketAddresses.length;i++) {
      socketAddresses[i] = new InetSocketAddress(addresses[i], port);
    }

    return socketAddresses;
  }

  private static SocketChannel[] constructSocketChannels(Selector selector, int count)
      throws IOException
  {
    SocketChannel[] channels = new SocketChannel[count];

    for (int i=0;i<channels.length;i++) {
      channels[i] = SocketChannel.open();
      channels[i].configureBlocking(false);
      channels[i].register(selector, SelectionKey.OP_CONNECT);
    }

    return channels;
  }

}
