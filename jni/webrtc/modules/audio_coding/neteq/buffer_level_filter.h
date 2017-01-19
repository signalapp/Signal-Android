/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_NETEQ_BUFFER_LEVEL_FILTER_H_
#define WEBRTC_MODULES_AUDIO_CODING_NETEQ_BUFFER_LEVEL_FILTER_H_

#include "webrtc/base/constructormagic.h"

namespace webrtc {

class BufferLevelFilter {
 public:
  BufferLevelFilter();
  virtual ~BufferLevelFilter() {}
  virtual void Reset();

  // Updates the filter. Current buffer size is |buffer_size_packets| (Q0).
  // If |time_stretched_samples| is non-zero, the value is converted to the
  // corresponding number of packets, and is subtracted from the filtered
  // value (thus bypassing the filter operation). |packet_len_samples| is the
  // number of audio samples carried in each incoming packet.
  virtual void Update(int buffer_size_packets, int time_stretched_samples,
                      int packet_len_samples);

  // Set the current target buffer level (obtained from
  // DelayManager::base_target_level()). Used to select the appropriate
  // filter coefficient.
  virtual void SetTargetBufferLevel(int target_buffer_level);

  virtual int filtered_current_level() const { return filtered_current_level_; }

 private:
  int level_factor_;  // Filter factor for the buffer level filter in Q8.
  int filtered_current_level_;  // Filtered current buffer level in Q8.

  DISALLOW_COPY_AND_ASSIGN(BufferLevelFilter);
};

}  // namespace webrtc
#endif  // WEBRTC_MODULES_AUDIO_CODING_NETEQ_BUFFER_LEVEL_FILTER_H_
