/*
 *  Copyright 2012 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_SSLFINGERPRINT_H_
#define WEBRTC_BASE_SSLFINGERPRINT_H_

#include <string>

#include "webrtc/base/basictypes.h"
#include "webrtc/base/copyonwritebuffer.h"
#include "webrtc/base/sslidentity.h"

namespace rtc {

class SSLCertificate;

struct SSLFingerprint {
  static SSLFingerprint* Create(const std::string& algorithm,
                                const rtc::SSLIdentity* identity);

  static SSLFingerprint* Create(const std::string& algorithm,
                                const rtc::SSLCertificate* cert);

  static SSLFingerprint* CreateFromRfc4572(const std::string& algorithm,
                                           const std::string& fingerprint);

  SSLFingerprint(const std::string& algorithm,
                 const uint8_t* digest_in,
                 size_t digest_len);

  SSLFingerprint(const SSLFingerprint& from);

  bool operator==(const SSLFingerprint& other) const;

  std::string GetRfc4572Fingerprint() const;

  std::string ToString() const;

  std::string algorithm;
  rtc::CopyOnWriteBuffer digest;
};

}  // namespace rtc

#endif  // WEBRTC_BASE_SSLFINGERPRINT_H_
