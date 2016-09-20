/*
 *  Copyright 2015 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/md5digest.h"

namespace rtc {

size_t Md5Digest::Size() const {
  return kSize;
}

void Md5Digest::Update(const void* buf, size_t len) {
  MD5Update(&ctx_, static_cast<const uint8_t*>(buf), len);
}

size_t Md5Digest::Finish(void* buf, size_t len) {
  if (len < kSize) {
    return 0;
  }
  MD5Final(&ctx_, static_cast<uint8_t*>(buf));
  MD5Init(&ctx_);  // Reset for next use.
  return kSize;
}

};  // namespace rtc
