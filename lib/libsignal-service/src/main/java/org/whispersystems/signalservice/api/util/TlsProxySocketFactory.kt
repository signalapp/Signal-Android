package org.whispersystems.signalservice.api.util

import okhttp3.Dns
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketException
import java.net.UnknownHostException
import java.nio.channels.SocketChannel
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.util.Optional
import javax.net.SocketFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class TlsProxySocketFactory(
  private val proxyHost: String?,
  private val proxyPort: Int,
  private val dns: Optional<Dns>
) : SocketFactory() {

  private val system: SSLSocketFactory = try {
    val context = SSLContext.getInstance("TLS")
    context.init(null, null, null)

    context.socketFactory
  } catch (e: NoSuchAlgorithmException) {
    throw AssertionError(e)
  } catch (e: KeyManagementException) {
    throw AssertionError(e)
  }

  @Throws(IOException::class, UnknownHostException::class)
  override fun createSocket(host: String, port: Int): Socket {
    if (dns.isPresent) {
      val resolved = dns.get().lookup(host)

      if (resolved.isNotEmpty()) {
        return createSocket(resolved[0], port)
      }
    }

    return ProxySocket(system.createSocket(proxyHost, proxyPort))
  }

  @Throws(IOException::class, UnknownHostException::class)
  override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket {
    if (dns.isPresent) {
      val resolved = dns.get().lookup(host)

      if (resolved.isNotEmpty()) {
        return createSocket(resolved[0], port, localHost, localPort)
      }
    }

    return ProxySocket(system.createSocket(proxyHost, proxyPort, localHost, localPort))
  }

  @Throws(IOException::class)
  override fun createSocket(host: InetAddress, port: Int): Socket {
    return ProxySocket(system.createSocket(proxyHost, proxyPort))
  }

  @Throws(IOException::class)
  override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket {
    return ProxySocket(system.createSocket(proxyHost, proxyPort, localAddress, localPort))
  }

  @Throws(IOException::class)
  override fun createSocket(): Socket {
    val socket = system.createSocket(proxyHost, proxyPort) as SSLSocket
    socket.startHandshake()

    return ProxySocket(socket)
  }

  private class ProxySocket(private val delegate: Socket) : Socket() {
    @Throws(IOException::class)
    override fun connect(endpoint: SocketAddress) {
      // Already connected
    }

    @Throws(IOException::class)
    override fun connect(endpoint: SocketAddress, timeout: Int) {
      // Already connected
    }

    @Throws(IOException::class)
    override fun bind(bindpoint: SocketAddress) {
      delegate.bind(bindpoint)
    }

    override fun getInetAddress(): InetAddress {
      return delegate.inetAddress
    }

    override fun getLocalAddress(): InetAddress {
      return delegate.localAddress
    }

    override fun getPort(): Int {
      return delegate.port
    }

    override fun getLocalPort(): Int {
      return delegate.localPort
    }

    override fun getRemoteSocketAddress(): SocketAddress {
      return delegate.remoteSocketAddress
    }

    override fun getLocalSocketAddress(): SocketAddress {
      return delegate.localSocketAddress
    }

    override fun getChannel(): SocketChannel {
      return delegate.channel
    }

    @Throws(IOException::class)
    override fun getInputStream(): InputStream {
      return delegate.getInputStream()
    }

    @Throws(IOException::class)
    override fun getOutputStream(): OutputStream {
      return delegate.getOutputStream()
    }

    @Throws(SocketException::class)
    override fun setTcpNoDelay(on: Boolean) {
      delegate.tcpNoDelay = on
    }

    @Throws(SocketException::class)
    override fun getTcpNoDelay(): Boolean {
      return delegate.tcpNoDelay
    }

    @Throws(SocketException::class)
    override fun setSoLinger(on: Boolean, linger: Int) {
      delegate.setSoLinger(on, linger)
    }

    @Throws(SocketException::class)
    override fun getSoLinger(): Int {
      return delegate.soLinger
    }

    @Throws(IOException::class)
    override fun sendUrgentData(data: Int) {
      delegate.sendUrgentData(data)
    }

    @Throws(SocketException::class)
    override fun setOOBInline(on: Boolean) {
      delegate.oobInline = on
    }

    @Throws(SocketException::class)
    override fun getOOBInline(): Boolean {
      return delegate.oobInline
    }

    @Throws(SocketException::class)
    override fun setSoTimeout(timeout: Int) {
      delegate.soTimeout = timeout
    }

    @Throws(SocketException::class)
    override fun getSoTimeout(): Int {
      return delegate.soTimeout
    }

    @Throws(SocketException::class)
    override fun setSendBufferSize(size: Int) {
      delegate.sendBufferSize = size
    }

    @Throws(SocketException::class)
    override fun getSendBufferSize(): Int {
      return delegate.sendBufferSize
    }

    @Throws(SocketException::class)
    override fun setReceiveBufferSize(size: Int) {
      delegate.receiveBufferSize = size
    }

    @Throws(SocketException::class)
    override fun getReceiveBufferSize(): Int {
      return delegate.receiveBufferSize
    }

    @Throws(SocketException::class)
    override fun setKeepAlive(on: Boolean) {
      delegate.keepAlive = on
    }

    @Throws(SocketException::class)
    override fun getKeepAlive(): Boolean {
      return delegate.keepAlive
    }

    @Throws(SocketException::class)
    override fun setTrafficClass(tc: Int) {
      delegate.trafficClass = tc
    }

    @Throws(SocketException::class)
    override fun getTrafficClass(): Int {
      return delegate.trafficClass
    }

    @Throws(SocketException::class)
    override fun setReuseAddress(on: Boolean) {
      delegate.reuseAddress = on
    }

    @Throws(SocketException::class)
    override fun getReuseAddress(): Boolean {
      return delegate.reuseAddress
    }

    @Throws(IOException::class)
    override fun close() {
      delegate.close()
    }

    @Throws(IOException::class)
    override fun shutdownInput() {
      delegate.shutdownInput()
    }

    @Throws(IOException::class)
    override fun shutdownOutput() {
      delegate.shutdownOutput()
    }

    override fun toString(): String {
      return delegate.toString()
    }

    override fun isConnected(): Boolean {
      return delegate.isConnected
    }

    override fun isBound(): Boolean {
      return delegate.isBound
    }

    override fun isClosed(): Boolean {
      return delegate.isClosed
    }

    override fun isInputShutdown(): Boolean {
      return delegate.isInputShutdown
    }

    override fun isOutputShutdown(): Boolean {
      return delegate.isOutputShutdown
    }

    override fun setPerformancePreferences(connectionTime: Int, latency: Int, bandwidth: Int) {
      delegate.setPerformancePreferences(connectionTime, latency, bandwidth)
    }
  }
}
