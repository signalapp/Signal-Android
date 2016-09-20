/*
 *  Copyright (c) 2016 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_TIMESTAMPALIGNER_H_
#define WEBRTC_BASE_TIMESTAMPALIGNER_H_

#include "webrtc/base/basictypes.h"
#include "webrtc/base/constructormagic.h"
#include "webrtc/base/optional.h"

namespace rtc {

class TimestampAligner {
 public:
  TimestampAligner();
  ~TimestampAligner();

 public:
  // Update the estimated offset between camera time and system monotonic time.
  int64_t UpdateOffset(int64_t camera_time_us, int64_t system_time_us);

  int64_t ClipTimestamp(int64_t filtered_time_us, int64_t system_time_us);

 private:
  // State for the timestamp translation.
  int frames_seen_;
  // Estimated offset between camera time and system monotonic time.
  int64_t offset_us_;

  // State for timestamp clipping, applied after the filter, to ensure
  // that translated timestamps are monotonic and not in the future.
  // Subtracted from the translated timestamps.
  int64_t clip_bias_us_;
  rtc::Optional<int64_t> prev_translated_time_us_;
  RTC_DISALLOW_COPY_AND_ASSIGN(TimestampAligner);
};

}  // namespace rtc

#endif  // WEBRTC_BASE_TIMESTAMPALIGNER_H_
