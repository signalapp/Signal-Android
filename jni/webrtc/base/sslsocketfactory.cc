/*
 *  Copyright 2007 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <memory>

#include "webrtc/base/autodetectproxy.h"
#include "webrtc/base/httpcommon.h"
#include "webrtc/base/httpcommon-inl.h"
#include "webrtc/base/socketadapters.h"
#include "webrtc/base/ssladapter.h"
#include "webrtc/base/sslsocketfactory.h"

namespace rtc {

///////////////////////////////////////////////////////////////////////////////
// ProxySocketAdapter
// TODO: Consider combining AutoDetectProxy and ProxySocketAdapter.  I think
// the socket adapter is the more appropriate idiom for automatic proxy
// detection.  We may or may not want to combine proxydetect.* as well.
///////////////////////////////////////////////////////////////////////////////

class ProxySocketAdapter : public AsyncSocketAdapter {
 public:
  ProxySocketAdapter(SslSocketFactory* factory, int family, int type)
      : AsyncSocketAdapter(NULL), factory_(factory), family_(family),
        type_(type), detect_(NULL) {
  }
  ~ProxySocketAdapter() override {
    Close();
  }

  int Connect(const SocketAddress& addr) override {
    ASSERT(NULL == detect_);
    ASSERT(NULL == socket_);
    remote_ = addr;
    if (remote_.IsAnyIP() && remote_.hostname().empty()) {
      LOG_F(LS_ERROR) << "Empty address";
      return SOCKET_ERROR;
    }
    Url<char> url("/", remote_.HostAsURIString(), remote_.port());
    detect_ = new AutoDetectProxy(factory_->agent_);
    detect_->set_server_url(url.url());
    detect_->SignalWorkDone.connect(this,
        &ProxySocketAdapter::OnProxyDetectionComplete);
    detect_->Start();
    return SOCKET_ERROR;
  }
  int GetError() const override {
    if (socket_) {
      return socket_->GetError();
    }
    return detect_ ? EWOULDBLOCK : EADDRNOTAVAIL;
  }
  int Close() override {
    if (socket_) {
      return socket_->Close();
    }
    if (detect_) {
      detect_->Destroy(false);
      detect_ = NULL;
    }
    return 0;
  }
  ConnState GetState() const override {
    if (socket_) {
      return socket_->GetState();
    }
    return detect_ ? CS_CONNECTING : CS_CLOSED;
  }

private:
  // AutoDetectProxy Slots
  void OnProxyDetectionComplete(SignalThread* thread) {
    ASSERT(detect_ == thread);
    Attach(factory_->CreateProxySocket(detect_->proxy(), family_, type_));
    detect_->Release();
    detect_ = NULL;
    if (0 == AsyncSocketAdapter::Connect(remote_)) {
      SignalConnectEvent(this);
    } else if (!IsBlockingError(socket_->GetError())) {
      SignalCloseEvent(this, socket_->GetError());
    }
  }

  SslSocketFactory* factory_;
  int family_;
  int type_;
  SocketAddress remote_;
  AutoDetectProxy* detect_;
};

///////////////////////////////////////////////////////////////////////////////
// SslSocketFactory
///////////////////////////////////////////////////////////////////////////////

SslSocketFactory::SslSocketFactory(SocketFactory* factory,
                                   const std::string& user_agent)
    : factory_(factory),
      agent_(user_agent),
      autodetect_proxy_(true),
      force_connect_(false),
      logging_level_(LS_VERBOSE),
      binary_mode_(false),
      ignore_bad_cert_(false) {
}

SslSocketFactory::~SslSocketFactory() = default;

Socket* SslSocketFactory::CreateSocket(int type) {
  return CreateSocket(AF_INET, type);
}

Socket* SslSocketFactory::CreateSocket(int family, int type) {
  return factory_->CreateSocket(family, type);
}

AsyncSocket* SslSocketFactory::CreateAsyncSocket(int type) {
  return CreateAsyncSocket(AF_INET, type);
}

AsyncSocket* SslSocketFactory::CreateAsyncSocket(int family, int type) {
  if (autodetect_proxy_) {
    return new ProxySocketAdapter(this, family, type);
  } else {
    return CreateProxySocket(proxy_, family, type);
  }
}


AsyncSocket* SslSocketFactory::CreateProxySocket(const ProxyInfo& proxy,
                                                 int family,
                                                 int type) {
  AsyncSocket* socket = factory_->CreateAsyncSocket(family, type);
  if (!socket)
    return NULL;

  // Binary logging happens at the lowest level
  if (!logging_label_.empty() && binary_mode_) {
    socket = new LoggingSocketAdapter(socket, logging_level_,
                                      logging_label_.c_str(), binary_mode_);
  }

  if (proxy.type) {
    AsyncSocket* proxy_socket = 0;
    if (proxy_.type == PROXY_SOCKS5) {
      proxy_socket = new AsyncSocksProxySocket(socket, proxy.address,
                                               proxy.username, proxy.password);
    } else {
      // Note: we are trying unknown proxies as HTTPS currently
      AsyncHttpsProxySocket* http_proxy =
          new AsyncHttpsProxySocket(socket, agent_, proxy.address,
                                    proxy.username, proxy.password);
      http_proxy->SetForceConnect(force_connect_ || !hostname_.empty());
      proxy_socket = http_proxy;
    }
    if (!proxy_socket) {
      delete socket;
      return NULL;
    }
    socket = proxy_socket;  // for our purposes the proxy is now the socket
  }

  if (!hostname_.empty()) {
    std::unique_ptr<SSLAdapter> ssl_adapter(SSLAdapter::Create(socket));
    if (!ssl_adapter) {
      LOG_F(LS_ERROR) << "SSL unavailable";
      delete socket;
      return NULL;
    }

    ssl_adapter->set_ignore_bad_cert(ignore_bad_cert_);
    if (ssl_adapter->StartSSL(hostname_.c_str(), true) != 0) {
      LOG_F(LS_ERROR) << "SSL failed to start.";
      return NULL;
    }
    socket = ssl_adapter.release();
  }

  // Regular logging occurs at the highest level
  if (!logging_label_.empty() && !binary_mode_) {
    socket = new LoggingSocketAdapter(socket, logging_level_,
                                      logging_label_.c_str(), binary_mode_);
  }
  return socket;
}

///////////////////////////////////////////////////////////////////////////////

}  // namespace rtc
