/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_PROXYINFO_H__
#define WEBRTC_BASE_PROXYINFO_H__

#include <string>
#include "webrtc/base/socketaddress.h"
#include "webrtc/base/cryptstring.h"

namespace rtc {

enum ProxyType {
  PROXY_NONE,
  PROXY_HTTPS,
  PROXY_SOCKS5,
  PROXY_UNKNOWN
};
const char * ProxyToString(ProxyType proxy);

struct ProxyInfo {
  ProxyType type;
  SocketAddress address;
  std::string autoconfig_url;
  bool autodetect;
  std::string bypass_list;
  std::string username;
  CryptString password;

  ProxyInfo();
  ~ProxyInfo();
};

} // namespace rtc

#endif // WEBRTC_BASE_PROXYINFO_H__
