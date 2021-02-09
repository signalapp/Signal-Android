package org.whispersystems.signalservice.api.util;

import org.whispersystems.libsignal.util.guava.Optional;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.channels.SocketChannel;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import okhttp3.Dns;

public class TlsProxySocketFactory extends SocketFactory {

  private final SSLSocketFactory system;

  private final String        proxyHost;
  private final int           proxyPort;
  private final Optional<Dns> dns;

  public TlsProxySocketFactory(String proxyHost, int proxyPort, Optional<Dns> dns) {
    try {
      SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, null, null);

      this.system    = context.getSocketFactory();
      this.proxyHost = proxyHost;
      this.proxyPort = proxyPort;
      this.dns       = dns;
    } catch (NoSuchAlgorithmException | KeyManagementException e) {
      throw new AssertionError(e);
    }
  }

  @Override
  public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
    if (dns.isPresent()) {
      List<InetAddress> resolved = dns.get().lookup(host);

      if (resolved.size() > 0) {
        return createSocket(resolved.get(0), port);
      }
    }

    return new ProxySocket(system.createSocket(proxyHost, proxyPort));
  }

  @Override
  public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException, UnknownHostException {
    if (dns.isPresent()) {
      List<InetAddress> resolved = dns.get().lookup(host);

      if (resolved.size() > 0) {
        return createSocket(resolved.get(0), port, localHost, localPort);
      }
    }

    return new ProxySocket(system.createSocket(proxyHost, proxyPort, localHost, localPort));
  }

  @Override
  public Socket createSocket(InetAddress host, int port) throws IOException {
    return new ProxySocket(system.createSocket(proxyHost, proxyPort));
  }

  @Override
  public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
    return new ProxySocket(system.createSocket(proxyHost, proxyPort, localAddress, localPort));
  }

  @Override
  public Socket createSocket() throws IOException {
    SSLSocket socket = (SSLSocket)system.createSocket(proxyHost, proxyPort);
    socket.startHandshake();

    return new ProxySocket(socket);
  }

  private static class ProxySocket extends Socket {

    private final Socket delegate;

    private ProxySocket(Socket delegate) {
      this.delegate = delegate;
    }

    @Override
    public void bind(SocketAddress bindpoint) throws IOException {
      delegate.bind(bindpoint);
    }

    @Override
    public InetAddress getInetAddress() {
      return delegate.getInetAddress();
    }

    @Override
    public InetAddress getLocalAddress() {
      return delegate.getLocalAddress();
    }

    @Override
    public int getPort() {
      return delegate.getPort();
    }

    @Override
    public int getLocalPort() {
      return delegate.getLocalPort();
    }

    @Override
    public SocketAddress getRemoteSocketAddress() {
      return delegate.getRemoteSocketAddress();
    }

    @Override
    public SocketAddress getLocalSocketAddress() {
      return delegate.getLocalSocketAddress();
    }

    @Override
    public SocketChannel getChannel() {
      return delegate.getChannel();
    }

    @Override
    public InputStream getInputStream() throws IOException {
      return delegate.getInputStream();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
      return delegate.getOutputStream();
    }

    @Override
    public void setTcpNoDelay(boolean on) throws SocketException {
      delegate.setTcpNoDelay(on);
    }

    @Override
    public boolean getTcpNoDelay() throws SocketException {
      return delegate.getTcpNoDelay();
    }

    @Override
    public void setSoLinger(boolean on, int linger) throws SocketException {
      delegate.setSoLinger(on, linger);
    }

    @Override
    public int getSoLinger() throws SocketException {
      return delegate.getSoLinger();
    }

    @Override
    public void sendUrgentData(int data) throws IOException {
      delegate.sendUrgentData(data);
    }

    @Override
    public void setOOBInline(boolean on) throws SocketException {
      delegate.setOOBInline(on);
    }

    @Override
    public boolean getOOBInline() throws SocketException {
      return delegate.getOOBInline();
    }

    @Override
    public void setSoTimeout(int timeout) throws SocketException {
      delegate.setSoTimeout(timeout);
    }

    @Override
    public int getSoTimeout() throws SocketException {
      return delegate.getSoTimeout();
    }

    @Override
    public void setSendBufferSize(int size) throws SocketException {
      delegate.setSendBufferSize(size);
    }

    @Override
    public int getSendBufferSize() throws SocketException {
      return delegate.getSendBufferSize();
    }

    @Override
    public void setReceiveBufferSize(int size) throws SocketException {
      delegate.setReceiveBufferSize(size);
    }

    @Override
    public int getReceiveBufferSize() throws SocketException {
      return delegate.getReceiveBufferSize();
    }

    @Override
    public void setKeepAlive(boolean on) throws SocketException {
      delegate.setKeepAlive(on);
    }

    @Override
    public boolean getKeepAlive() throws SocketException {
      return delegate.getKeepAlive();
    }

    @Override
    public void setTrafficClass(int tc) throws SocketException {
      delegate.setTrafficClass(tc);
    }

    @Override
    public int getTrafficClass() throws SocketException {
      return delegate.getTrafficClass();
    }

    @Override
    public void setReuseAddress(boolean on) throws SocketException {
      delegate.setReuseAddress(on);
    }

    @Override
    public boolean getReuseAddress() throws SocketException {
      return delegate.getReuseAddress();
    }

    @Override
    public void close() throws IOException {
      delegate.close();
    }

    @Override
    public void shutdownInput() throws IOException {
      delegate.shutdownInput();
    }

    @Override
    public void shutdownOutput() throws IOException {
      delegate.shutdownOutput();
    }

    @Override
    public String toString() {
      return delegate.toString();
    }

    @Override
    public boolean isConnected() {
      return delegate.isConnected();
    }

    @Override
    public boolean isBound() {
      return delegate.isBound();
    }

    @Override
    public boolean isClosed() {
      return delegate.isClosed();
    }

    @Override
    public boolean isInputShutdown() {
      return delegate.isInputShutdown();
    }

    @Override
    public boolean isOutputShutdown() {
      return delegate.isOutputShutdown();
    }

    @Override
    public void setPerformancePreferences(int connectionTime, int latency, int bandwidth) {
      delegate.setPerformancePreferences(connectionTime, latency, bandwidth);
    }

    @Override
    public void connect(SocketAddress endpoint) throws IOException {
      // Already connected
    }

    @Override
    public void connect(SocketAddress endpoint, int timeout) throws IOException {
      // Already connected
    }
  }
}
