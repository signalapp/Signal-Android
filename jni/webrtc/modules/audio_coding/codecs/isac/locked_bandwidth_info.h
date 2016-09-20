/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_CODECS_ISAC_LOCKED_BANDWIDTH_INFO_H_
#define WEBRTC_MODULES_AUDIO_CODING_CODECS_ISAC_LOCKED_BANDWIDTH_INFO_H_

#include "webrtc/base/atomicops.h"
#include "webrtc/base/criticalsection.h"
#include "webrtc/base/thread_annotations.h"
#include "webrtc/modules/audio_coding/codecs/isac/bandwidth_info.h"

namespace webrtc {

// An IsacBandwidthInfo that's safe to access from multiple threads because
// it's protected by a mutex.
class LockedIsacBandwidthInfo final {
 public:
  LockedIsacBandwidthInfo();
  ~LockedIsacBandwidthInfo();

  IsacBandwidthInfo Get() const {
    rtc::CritScope lock(&lock_);
    return bwinfo_;
  }

  void Set(const IsacBandwidthInfo& bwinfo) {
    rtc::CritScope lock(&lock_);
    bwinfo_ = bwinfo;
  }

  int AddRef() const { return rtc::AtomicOps::Increment(&ref_count_); }

  int Release() const {
    const int count = rtc::AtomicOps::Decrement(&ref_count_);
    if (count == 0) {
      delete this;
    }
    return count;
  }

 private:
  mutable volatile int ref_count_;
  rtc::CriticalSection lock_;
  IsacBandwidthInfo bwinfo_ GUARDED_BY(lock_);
};

}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_CODING_CODECS_ISAC_LOCKED_BANDWIDTH_INFO_H_
