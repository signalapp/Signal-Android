/*
 *  Copyright 2007 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef _PROXYDETECT_H_
#define _PROXYDETECT_H_

#include "webrtc/base/proxyinfo.h"

namespace rtc {
// Auto-detect the proxy server.  Returns true if a proxy is configured,
// although hostname may be empty if the proxy is not required for
// the given URL.

bool GetProxySettingsForUrl(const char* agent, const char* url,
                            rtc::ProxyInfo* proxy,
                            bool long_operation = false);

}  // namespace rtc

#endif  // _PROXYDETECT_H_
