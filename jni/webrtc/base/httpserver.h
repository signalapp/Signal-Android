/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_HTTPSERVER_H__
#define WEBRTC_BASE_HTTPSERVER_H__

#include <map>
#include <memory>

#include "webrtc/base/httpbase.h"

namespace rtc {

class AsyncSocket;
class HttpServer;
class SocketAddress;

//////////////////////////////////////////////////////////////////////
// HttpServer
//////////////////////////////////////////////////////////////////////

const int HTTP_INVALID_CONNECTION_ID = 0;

struct HttpServerTransaction : public HttpTransaction {
public:
  HttpServerTransaction(int id) : connection_id_(id) { }
  int connection_id() const { return connection_id_; }

private:
  int connection_id_;
};

class HttpServer {
public:
  HttpServer();
  virtual ~HttpServer();

  int HandleConnection(StreamInterface* stream);
  // Due to sigslot issues, we can't destroy some streams at an arbitrary time.
  sigslot::signal3<HttpServer*, int, StreamInterface*> SignalConnectionClosed;

  // This signal occurs when the HTTP request headers have been received, but
  // before the request body is written to the request document.  By default,
  // the request document is a MemoryStream.  By handling this signal, the
  // document can be overridden, in which case the third signal argument should
  // be set to true.  In the case where the request body should be ignored,
  // the document can be set to NULL.  Note that the transaction object is still
  // owened by the HttpServer at this point.  
  sigslot::signal3<HttpServer*, HttpServerTransaction*, bool*>
    SignalHttpRequestHeader;

  // An HTTP request has been made, and is available in the transaction object.
  // Populate the transaction's response, and then return the object via the
  // Respond method.  Note that during this time, ownership of the transaction
  // object is transferred, so it may be passed between threads, although
  // respond must be called on the server's active thread.
  sigslot::signal2<HttpServer*, HttpServerTransaction*> SignalHttpRequest;
  void Respond(HttpServerTransaction* transaction);

  // If you want to know when a request completes, listen to this event.
  sigslot::signal3<HttpServer*, HttpServerTransaction*, int>
    SignalHttpRequestComplete;

  // Stop processing the connection indicated by connection_id.
  // Unless force is true, the server will complete sending a response that is
  // in progress.
  void Close(int connection_id, bool force);
  void CloseAll(bool force);

  // After calling CloseAll, this event is signalled to indicate that all
  // outstanding connections have closed.
  sigslot::signal1<HttpServer*> SignalCloseAllComplete;

private:
  class Connection : private IHttpNotify {
  public:
    Connection(int connection_id, HttpServer* server);
    ~Connection() override;

    void BeginProcess(StreamInterface* stream);
    StreamInterface* EndProcess();
    
    void Respond(HttpServerTransaction* transaction);
    void InitiateClose(bool force);

    // IHttpNotify Interface
    HttpError onHttpHeaderComplete(bool chunked, size_t& data_size) override;
    void onHttpComplete(HttpMode mode, HttpError err) override;
    void onHttpClosed(HttpError err) override;

    int connection_id_;
    HttpServer* server_;
    HttpBase base_;
    HttpServerTransaction* current_;
    bool signalling_, close_;
  };

  Connection* Find(int connection_id);
  void Remove(int connection_id);

  friend class Connection;
  typedef std::map<int,Connection*> ConnectionMap;

  ConnectionMap connections_;
  int next_connection_id_;
  bool closing_;
};

//////////////////////////////////////////////////////////////////////

class HttpListenServer : public HttpServer, public sigslot::has_slots<> {
public:
  HttpListenServer();
  ~HttpListenServer() override;

  int Listen(const SocketAddress& address);
  bool GetAddress(SocketAddress* address) const;
  void StopListening();

private:
  void OnReadEvent(AsyncSocket* socket);
  void OnConnectionClosed(HttpServer* server, int connection_id,
                          StreamInterface* stream);

  std::unique_ptr<AsyncSocket> listener_;
};

//////////////////////////////////////////////////////////////////////

}  // namespace rtc

#endif // WEBRTC_BASE_HTTPSERVER_H__
