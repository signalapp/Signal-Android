/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_NETEQ_DELAY_PEAK_DETECTOR_H_
#define WEBRTC_MODULES_AUDIO_CODING_NETEQ_DELAY_PEAK_DETECTOR_H_

#include <string.h>  // size_t

#include <list>
#include <memory>

#include "webrtc/base/constructormagic.h"
#include "webrtc/modules/audio_coding/neteq/tick_timer.h"

namespace webrtc {

class DelayPeakDetector {
 public:
  DelayPeakDetector(const TickTimer* tick_timer);
  virtual ~DelayPeakDetector();
  virtual void Reset();

  // Notifies the DelayPeakDetector of how much audio data is carried in each
  // packet.
  virtual void SetPacketAudioLength(int length_ms);

  // Returns true if peak-mode is active. That is, delay peaks were observed
  // recently.
  virtual bool peak_found();

  // Calculates and returns the maximum delay peak height. Returns -1 if no
  // delay peaks have been observed recently. The unit is number of packets.
  virtual int MaxPeakHeight() const;

  // Calculates and returns the maximum delay peak distance in ms (strictly
  // larger than 0), or 0 if no delay peaks have been observed recently.
  virtual uint64_t MaxPeakPeriod() const;

  // Updates the DelayPeakDetector with a new inter-arrival time (in packets)
  // and the current target buffer level (needed to decide if a peak is observed
  // or not). Returns true if peak-mode is active, false if not.
  virtual bool Update(int inter_arrival_time, int target_level);

 private:
  static const size_t kMaxNumPeaks = 8;
  static const size_t kMinPeaksToTrigger = 2;
  static const int kPeakHeightMs = 78;
  static const int kMaxPeakPeriodMs = 10000;

  typedef struct {
    uint64_t period_ms;
    int peak_height_packets;
  } Peak;

  bool CheckPeakConditions();

  std::list<Peak> peak_history_;
  bool peak_found_;
  int peak_detection_threshold_;
  const TickTimer* tick_timer_;
  std::unique_ptr<TickTimer::Stopwatch> peak_period_stopwatch_;

  RTC_DISALLOW_COPY_AND_ASSIGN(DelayPeakDetector);
};

}  // namespace webrtc
#endif  // WEBRTC_MODULES_AUDIO_CODING_NETEQ_DELAY_PEAK_DETECTOR_H_
