package org.whispersystems.signalservice.api.util;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Enables TLS v1.2 when creating SSLSockets.
 * <p/>
 * For some reason, android supports TLS v1.2 from API 16, but enables it by
 * default only from API 20.
 * @link https://developer.android.com/reference/javax/net/ssl/SSLSocket.html
 * @see SSLSocketFactory
 */
public class Tls12SocketFactory extends SSLSocketFactory {
  private static final String[] TLS_V12_V13_ONLY = {"TLSv1.3", "TLSv1.2"};

  final SSLSocketFactory delegate;

  public Tls12SocketFactory(SSLSocketFactory base) {
    this.delegate = base;
  }

  @Override
  public String[] getDefaultCipherSuites() {
    return delegate.getDefaultCipherSuites();
  }

  @Override
  public String[] getSupportedCipherSuites() {
    return delegate.getSupportedCipherSuites();
  }

  @Override
  public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
    return patch(delegate.createSocket(s, host, port, autoClose));
  }

  @Override
  public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
    return patch(delegate.createSocket(host, port));
  }

  @Override
  public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException, UnknownHostException {
    return patch(delegate.createSocket(host, port, localHost, localPort));
  }

  @Override
  public Socket createSocket(InetAddress host, int port) throws IOException {
    return patch(delegate.createSocket(host, port));
  }

  @Override
  public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
    return patch(delegate.createSocket(address, port, localAddress, localPort));
  }

  private Socket patch(Socket s) {
    if (s instanceof SSLSocket) {
      ((SSLSocket) s).setEnabledProtocols(TLS_V12_V13_ONLY);
    }
    return s;
  }
}