/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_processing/gain_control_for_experimental_agc.h"

#include "webrtc/base/checks.h"
#include "webrtc/base/criticalsection.h"
#include "webrtc/modules/audio_processing/include/audio_processing.h"

namespace webrtc {

GainControlForExperimentalAgc::GainControlForExperimentalAgc(
    GainControl* gain_control,
    rtc::CriticalSection* crit_capture)
    : real_gain_control_(gain_control),
      volume_(0),
      crit_capture_(crit_capture) {}

int GainControlForExperimentalAgc::Enable(bool enable) {
  return real_gain_control_->Enable(enable);
}

bool GainControlForExperimentalAgc::is_enabled() const {
  return real_gain_control_->is_enabled();
}

int GainControlForExperimentalAgc::set_stream_analog_level(int level) {
  rtc::CritScope cs_capture(crit_capture_);
  volume_ = level;
  return AudioProcessing::kNoError;
}

int GainControlForExperimentalAgc::stream_analog_level() {
  rtc::CritScope cs_capture(crit_capture_);
  return volume_;
}

int GainControlForExperimentalAgc::set_mode(Mode mode) {
  return AudioProcessing::kNoError;
}

GainControl::Mode GainControlForExperimentalAgc::mode() const {
  return GainControl::kAdaptiveAnalog;
}

int GainControlForExperimentalAgc::set_target_level_dbfs(int level) {
  return AudioProcessing::kNoError;
}

int GainControlForExperimentalAgc::target_level_dbfs() const {
  return real_gain_control_->target_level_dbfs();
}

int GainControlForExperimentalAgc::set_compression_gain_db(int gain) {
  return AudioProcessing::kNoError;
}

int GainControlForExperimentalAgc::compression_gain_db() const {
  return real_gain_control_->compression_gain_db();
}

int GainControlForExperimentalAgc::enable_limiter(bool enable) {
  return AudioProcessing::kNoError;
}

bool GainControlForExperimentalAgc::is_limiter_enabled() const {
  return real_gain_control_->is_limiter_enabled();
}

int GainControlForExperimentalAgc::set_analog_level_limits(int minimum,
                                                           int maximum) {
  return AudioProcessing::kNoError;
}

int GainControlForExperimentalAgc::analog_level_minimum() const {
  return real_gain_control_->analog_level_minimum();
}

int GainControlForExperimentalAgc::analog_level_maximum() const {
  return real_gain_control_->analog_level_maximum();
}

bool GainControlForExperimentalAgc::stream_is_saturated() const {
  return real_gain_control_->stream_is_saturated();
}

void GainControlForExperimentalAgc::SetMicVolume(int volume) {
  rtc::CritScope cs_capture(crit_capture_);
  volume_ = volume;
}

int GainControlForExperimentalAgc::GetMicVolume() {
  rtc::CritScope cs_capture(crit_capture_);
  return volume_;
}

}  // namespace webrtc
