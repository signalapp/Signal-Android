/*
 *  Copyright 2007 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_SSLSOCKETFACTORY_H__
#define WEBRTC_BASE_SSLSOCKETFACTORY_H__

#include "webrtc/base/proxyinfo.h"
#include "webrtc/base/socketserver.h"

namespace rtc {

///////////////////////////////////////////////////////////////////////////////
// SslSocketFactory
///////////////////////////////////////////////////////////////////////////////

class SslSocketFactory : public SocketFactory {
 public:
  SslSocketFactory(SocketFactory* factory, const std::string& user_agent);
  ~SslSocketFactory() override;

  void SetAutoDetectProxy() {
    autodetect_proxy_ = true;
  }
  void SetForceConnect(bool force) {
    force_connect_ = force;
  }
  void SetProxy(const ProxyInfo& proxy) {
    autodetect_proxy_ = false;
    proxy_ = proxy;
  }
  bool autodetect_proxy() const { return autodetect_proxy_; }
  const ProxyInfo& proxy() const { return proxy_; }

  void UseSSL(const char* hostname) { hostname_ = hostname; }
  void DisableSSL() { hostname_.clear(); }
  void SetIgnoreBadCert(bool ignore) { ignore_bad_cert_ = ignore; }
  bool ignore_bad_cert() const { return ignore_bad_cert_; }

  void SetLogging(LoggingSeverity level, const std::string& label, 
                  bool binary_mode = false) {
    logging_level_ = level;
    logging_label_ = label;
    binary_mode_ = binary_mode;
  }

  // SocketFactory Interface
  Socket* CreateSocket(int type) override;
  Socket* CreateSocket(int family, int type) override;

  AsyncSocket* CreateAsyncSocket(int type) override;
  AsyncSocket* CreateAsyncSocket(int family, int type) override;

 private:
  friend class ProxySocketAdapter;
  AsyncSocket* CreateProxySocket(const ProxyInfo& proxy, int family, int type);

  SocketFactory* factory_;
  std::string agent_;
  bool autodetect_proxy_, force_connect_;
  ProxyInfo proxy_;
  std::string hostname_, logging_label_;
  LoggingSeverity logging_level_;
  bool binary_mode_;
  bool ignore_bad_cert_;
};

///////////////////////////////////////////////////////////////////////////////

}  // namespace rtc

#endif  // WEBRTC_BASE_SSLSOCKETFACTORY_H__
