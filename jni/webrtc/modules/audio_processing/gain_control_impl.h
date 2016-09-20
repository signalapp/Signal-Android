/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_PROCESSING_GAIN_CONTROL_IMPL_H_
#define WEBRTC_MODULES_AUDIO_PROCESSING_GAIN_CONTROL_IMPL_H_

#include <memory>
#include <vector>

#include "webrtc/base/constructormagic.h"
#include "webrtc/base/criticalsection.h"
#include "webrtc/base/swap_queue.h"
#include "webrtc/base/thread_annotations.h"
#include "webrtc/modules/audio_processing/include/audio_processing.h"
#include "webrtc/modules/audio_processing/render_queue_item_verifier.h"

namespace webrtc {

class AudioBuffer;

class GainControlImpl : public GainControl {
 public:
  GainControlImpl(rtc::CriticalSection* crit_render,
                  rtc::CriticalSection* crit_capture);
  ~GainControlImpl() override;

  int ProcessRenderAudio(AudioBuffer* audio);
  int AnalyzeCaptureAudio(AudioBuffer* audio);
  int ProcessCaptureAudio(AudioBuffer* audio, bool stream_has_echo);

  void Initialize(size_t num_proc_channels, int sample_rate_hz);

  // GainControl implementation.
  bool is_enabled() const override;
  int stream_analog_level() override;
  bool is_limiter_enabled() const override;
  Mode mode() const override;

  // Checks whether the module is enabled. Must only be
  // called from the render side of APM as otherwise
  // deadlocks may occur.
  bool is_enabled_render_side_query() const;

  // Reads render side data that has been queued on the render call.
  void ReadQueuedRenderData();

  int compression_gain_db() const override;

 private:
  class GainController;

  // GainControl implementation.
  int Enable(bool enable) override;
  int set_stream_analog_level(int level) override;
  int set_mode(Mode mode) override;
  int set_target_level_dbfs(int level) override;
  int target_level_dbfs() const override;
  int set_compression_gain_db(int gain) override;
  int enable_limiter(bool enable) override;
  int set_analog_level_limits(int minimum, int maximum) override;
  int analog_level_minimum() const override;
  int analog_level_maximum() const override;
  bool stream_is_saturated() const override;

  void AllocateRenderQueue();
  int Configure();

  rtc::CriticalSection* const crit_render_ ACQUIRED_BEFORE(crit_capture_);
  rtc::CriticalSection* const crit_capture_;

  bool enabled_ = false;

  Mode mode_ GUARDED_BY(crit_capture_);
  int minimum_capture_level_ GUARDED_BY(crit_capture_);
  int maximum_capture_level_ GUARDED_BY(crit_capture_);
  bool limiter_enabled_ GUARDED_BY(crit_capture_);
  int target_level_dbfs_ GUARDED_BY(crit_capture_);
  int compression_gain_db_ GUARDED_BY(crit_capture_);
  int analog_capture_level_ GUARDED_BY(crit_capture_);
  bool was_analog_level_set_ GUARDED_BY(crit_capture_);
  bool stream_is_saturated_ GUARDED_BY(crit_capture_);

  size_t render_queue_element_max_size_ GUARDED_BY(crit_render_)
      GUARDED_BY(crit_capture_);
  std::vector<int16_t> render_queue_buffer_ GUARDED_BY(crit_render_);
  std::vector<int16_t> capture_queue_buffer_ GUARDED_BY(crit_capture_);

  // Lock protection not needed.
  std::unique_ptr<
      SwapQueue<std::vector<int16_t>, RenderQueueItemVerifier<int16_t>>>
      render_signal_queue_;

  std::vector<std::unique_ptr<GainController>> gain_controllers_;

  rtc::Optional<size_t> num_proc_channels_ GUARDED_BY(crit_capture_);
  rtc::Optional<int> sample_rate_hz_ GUARDED_BY(crit_capture_);

  RTC_DISALLOW_IMPLICIT_CONSTRUCTORS(GainControlImpl);
};
}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_PROCESSING_GAIN_CONTROL_IMPL_H_
