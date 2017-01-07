/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_AUTODETECTPROXY_H_
#define WEBRTC_BASE_AUTODETECTPROXY_H_

#include <string>

#include "webrtc/base/constructormagic.h"
#include "webrtc/base/cryptstring.h"
#include "webrtc/base/proxydetect.h"
#include "webrtc/base/proxyinfo.h"
#include "webrtc/base/signalthread.h"

namespace rtc {

///////////////////////////////////////////////////////////////////////////////
// AutoDetectProxy
///////////////////////////////////////////////////////////////////////////////

class AsyncResolverInterface;
class AsyncSocket;

class AutoDetectProxy : public SignalThread {
 public:
  explicit AutoDetectProxy(const std::string& user_agent);

  const ProxyInfo& proxy() const { return proxy_; }

  void set_server_url(const std::string& url) {
    server_url_ = url;
  }
  void set_proxy(const SocketAddress& proxy) {
    proxy_.type = PROXY_UNKNOWN;
    proxy_.address = proxy;
  }
  void set_auth_info(bool use_auth, const std::string& username,
                     const CryptString& password) {
    if (use_auth) {
      proxy_.username = username;
      proxy_.password = password;
    }
  }
  // Default implementation of GetProxySettingsForUrl. Override for special
  // implementation.
  virtual bool GetProxyForUrl(const char* agent,
                              const char* url,
                              rtc::ProxyInfo* proxy);
  enum { MSG_TIMEOUT = SignalThread::ST_MSG_FIRST_AVAILABLE,
         MSG_UNRESOLVABLE,
         ADP_MSG_FIRST_AVAILABLE};

 protected:
  ~AutoDetectProxy() override;

  // SignalThread Interface
  void DoWork() override;
  void OnMessage(Message* msg) override;

  void Next();
  void Complete(ProxyType type);

  void OnConnectEvent(AsyncSocket * socket);
  void OnReadEvent(AsyncSocket * socket);
  void OnCloseEvent(AsyncSocket * socket, int error);
  void OnResolveResult(AsyncResolverInterface* resolver);
  bool DoConnect();

 private:
  std::string agent_;
  std::string server_url_;
  ProxyInfo proxy_;
  AsyncResolverInterface* resolver_;
  AsyncSocket* socket_;
  int next_;

  RTC_DISALLOW_IMPLICIT_CONSTRUCTORS(AutoDetectProxy);
};

}  // namespace rtc

#endif  // WEBRTC_BASE_AUTODETECTPROXY_H_
