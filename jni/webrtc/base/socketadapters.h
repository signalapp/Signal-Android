/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_SOCKETADAPTERS_H_
#define WEBRTC_BASE_SOCKETADAPTERS_H_

#include <map>
#include <string>

#include "webrtc/base/asyncsocket.h"
#include "webrtc/base/constructormagic.h"
#include "webrtc/base/cryptstring.h"
#include "webrtc/base/logging.h"

namespace rtc {

struct HttpAuthContext;
class ByteBufferReader;
class ByteBufferWriter;

///////////////////////////////////////////////////////////////////////////////

// Implements a socket adapter that can buffer and process data internally,
// as in the case of connecting to a proxy, where you must speak the proxy
// protocol before commencing normal socket behavior.
class BufferedReadAdapter : public AsyncSocketAdapter {
 public:
  BufferedReadAdapter(AsyncSocket* socket, size_t buffer_size);
  ~BufferedReadAdapter() override;

  int Send(const void* pv, size_t cb) override;
  int Recv(void* pv, size_t cb, int64_t* timestamp) override;

 protected:
  int DirectSend(const void* pv, size_t cb) {
    return AsyncSocketAdapter::Send(pv, cb);
  }

  void BufferInput(bool on = true);
  virtual void ProcessInput(char* data, size_t* len) = 0;

  void OnReadEvent(AsyncSocket* socket) override;

 private:
  char * buffer_;
  size_t buffer_size_, data_len_;
  bool buffering_;
  RTC_DISALLOW_COPY_AND_ASSIGN(BufferedReadAdapter);
};

///////////////////////////////////////////////////////////////////////////////

// Interface for implementing proxy server sockets.
class AsyncProxyServerSocket : public BufferedReadAdapter {
 public:
  AsyncProxyServerSocket(AsyncSocket* socket, size_t buffer_size);
  ~AsyncProxyServerSocket() override;
  sigslot::signal2<AsyncProxyServerSocket*,
                   const SocketAddress&>  SignalConnectRequest;
  virtual void SendConnectResult(int err, const SocketAddress& addr) = 0;
};

///////////////////////////////////////////////////////////////////////////////

// Implements a socket adapter that performs the client side of a
// fake SSL handshake. Used for "ssltcp" P2P functionality.
class AsyncSSLSocket : public BufferedReadAdapter {
 public:
  explicit AsyncSSLSocket(AsyncSocket* socket);

  int Connect(const SocketAddress& addr) override;

 protected:
  void OnConnectEvent(AsyncSocket* socket) override;
  void ProcessInput(char* data, size_t* len) override;
  RTC_DISALLOW_COPY_AND_ASSIGN(AsyncSSLSocket);
};

// Implements a socket adapter that performs the server side of a
// fake SSL handshake. Used when implementing a relay server that does "ssltcp".
class AsyncSSLServerSocket : public BufferedReadAdapter {
 public:
  explicit AsyncSSLServerSocket(AsyncSocket* socket);

 protected:
  void ProcessInput(char* data, size_t* len) override;
  RTC_DISALLOW_COPY_AND_ASSIGN(AsyncSSLServerSocket);
};

///////////////////////////////////////////////////////////////////////////////

// Implements a socket adapter that speaks the HTTP/S proxy protocol.
class AsyncHttpsProxySocket : public BufferedReadAdapter {
 public:
  AsyncHttpsProxySocket(AsyncSocket* socket, const std::string& user_agent,
    const SocketAddress& proxy,
    const std::string& username, const CryptString& password);
  ~AsyncHttpsProxySocket() override;

  // If connect is forced, the adapter will always issue an HTTP CONNECT to the
  // target address.  Otherwise, it will connect only if the destination port
  // is not port 80.
  void SetForceConnect(bool force) { force_connect_ = force; }

  int Connect(const SocketAddress& addr) override;
  SocketAddress GetRemoteAddress() const override;
  int Close() override;
  ConnState GetState() const override;

 protected:
  void OnConnectEvent(AsyncSocket* socket) override;
  void OnCloseEvent(AsyncSocket* socket, int err) override;
  void ProcessInput(char* data, size_t* len) override;

  bool ShouldIssueConnect() const;
  void SendRequest();
  void ProcessLine(char* data, size_t len);
  void EndResponse();
  void Error(int error);

 private:
  SocketAddress proxy_, dest_;
  std::string agent_, user_, headers_;
  CryptString pass_;
  bool force_connect_;
  size_t content_length_;
  int defer_error_;
  bool expect_close_;
  enum ProxyState {
    PS_INIT, PS_LEADER, PS_AUTHENTICATE, PS_SKIP_HEADERS, PS_ERROR_HEADERS,
    PS_TUNNEL_HEADERS, PS_SKIP_BODY, PS_TUNNEL, PS_WAIT_CLOSE, PS_ERROR
  } state_;
  HttpAuthContext * context_;
  std::string unknown_mechanisms_;
  RTC_DISALLOW_COPY_AND_ASSIGN(AsyncHttpsProxySocket);
};

/* TODO: Implement this.
class AsyncHttpsProxyServerSocket : public AsyncProxyServerSocket {
 public:
  explicit AsyncHttpsProxyServerSocket(AsyncSocket* socket);

 private:
  virtual void ProcessInput(char * data, size_t& len);
  void Error(int error);
  RTC_DISALLOW_COPY_AND_ASSIGN(AsyncHttpsProxyServerSocket);
};
*/

///////////////////////////////////////////////////////////////////////////////

// Implements a socket adapter that speaks the SOCKS proxy protocol.
class AsyncSocksProxySocket : public BufferedReadAdapter {
 public:
  AsyncSocksProxySocket(AsyncSocket* socket, const SocketAddress& proxy,
    const std::string& username, const CryptString& password);
  ~AsyncSocksProxySocket() override;

  int Connect(const SocketAddress& addr) override;
  SocketAddress GetRemoteAddress() const override;
  int Close() override;
  ConnState GetState() const override;

 protected:
  void OnConnectEvent(AsyncSocket* socket) override;
  void ProcessInput(char* data, size_t* len) override;

  void SendHello();
  void SendConnect();
  void SendAuth();
  void Error(int error);

 private:
  enum State {
    SS_INIT, SS_HELLO, SS_AUTH, SS_CONNECT, SS_TUNNEL, SS_ERROR
  };
  State state_;
  SocketAddress proxy_, dest_;
  std::string user_;
  CryptString pass_;
  RTC_DISALLOW_COPY_AND_ASSIGN(AsyncSocksProxySocket);
};

// Implements a proxy server socket for the SOCKS protocol.
class AsyncSocksProxyServerSocket : public AsyncProxyServerSocket {
 public:
  explicit AsyncSocksProxyServerSocket(AsyncSocket* socket);

 private:
  void ProcessInput(char* data, size_t* len) override;
  void DirectSend(const ByteBufferWriter& buf);

  void HandleHello(ByteBufferReader* request);
  void SendHelloReply(uint8_t method);
  void HandleAuth(ByteBufferReader* request);
  void SendAuthReply(uint8_t result);
  void HandleConnect(ByteBufferReader* request);
  void SendConnectResult(int result, const SocketAddress& addr) override;

  void Error(int error);

  static const int kBufferSize = 1024;
  enum State {
    SS_HELLO, SS_AUTH, SS_CONNECT, SS_CONNECT_PENDING, SS_TUNNEL, SS_ERROR
  };
  State state_;
  RTC_DISALLOW_COPY_AND_ASSIGN(AsyncSocksProxyServerSocket);
};

///////////////////////////////////////////////////////////////////////////////

// Implements a socket adapter that logs everything that it sends and receives.
class LoggingSocketAdapter : public AsyncSocketAdapter {
 public:
  LoggingSocketAdapter(AsyncSocket* socket, LoggingSeverity level,
                 const char * label, bool hex_mode = false);

  int Send(const void* pv, size_t cb) override;
  int SendTo(const void* pv, size_t cb, const SocketAddress& addr) override;
  int Recv(void* pv, size_t cb, int64_t* timestamp) override;
  int RecvFrom(void* pv,
               size_t cb,
               SocketAddress* paddr,
               int64_t* timestamp) override;
  int Close() override;

 protected:
  void OnConnectEvent(AsyncSocket* socket) override;
  void OnCloseEvent(AsyncSocket* socket, int err) override;

 private:
  LoggingSeverity level_;
  std::string label_;
  bool hex_mode_;
  LogMultilineState lms_;
  RTC_DISALLOW_COPY_AND_ASSIGN(LoggingSocketAdapter);
};

///////////////////////////////////////////////////////////////////////////////

}  // namespace rtc

#endif  // WEBRTC_BASE_SOCKETADAPTERS_H_
