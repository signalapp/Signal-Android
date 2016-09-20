/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_processing/agc/agc_manager_direct.h"

#include <cassert>
#include <cmath>

#ifdef WEBRTC_AGC_DEBUG_DUMP
#include <cstdio>
#endif

#include "webrtc/modules/audio_processing/agc/gain_map_internal.h"
#include "webrtc/modules/audio_processing/gain_control_impl.h"
#include "webrtc/modules/include/module_common_types.h"
#include "webrtc/system_wrappers/include/logging.h"

namespace webrtc {

namespace {

// Lowest the microphone level can be lowered due to clipping.
const int kClippedLevelMin = 170;
// Amount the microphone level is lowered with every clipping event.
const int kClippedLevelStep = 15;
// Proportion of clipped samples required to declare a clipping event.
const float kClippedRatioThreshold = 0.1f;
// Time in frames to wait after a clipping event before checking again.
const int kClippedWaitFrames = 300;

// Amount of error we tolerate in the microphone level (presumably due to OS
// quantization) before we assume the user has manually adjusted the microphone.
const int kLevelQuantizationSlack = 25;

const int kDefaultCompressionGain = 7;
const int kMaxCompressionGain = 12;
const int kMinCompressionGain = 2;
// Controls the rate of compression changes towards the target.
const float kCompressionGainStep = 0.05f;

const int kMaxMicLevel = 255;
static_assert(kGainMapSize > kMaxMicLevel, "gain map too small");
const int kMinMicLevel = 12;

// Prevent very large microphone level changes.
const int kMaxResidualGainChange = 15;

// Maximum additional gain allowed to compensate for microphone level
// restrictions from clipping events.
const int kSurplusCompressionGain = 6;

int ClampLevel(int mic_level) {
  return std::min(std::max(kMinMicLevel, mic_level), kMaxMicLevel);
}

int LevelFromGainError(int gain_error, int level) {
  assert(level >= 0 && level <= kMaxMicLevel);
  if (gain_error == 0) {
    return level;
  }
  // TODO(ajm): Could be made more efficient with a binary search.
  int new_level = level;
  if (gain_error > 0) {
    while (kGainMap[new_level] - kGainMap[level] < gain_error &&
          new_level < kMaxMicLevel) {
      ++new_level;
    }
  } else {
    while (kGainMap[new_level] - kGainMap[level] > gain_error &&
          new_level > kMinMicLevel) {
      --new_level;
    }
  }
  return new_level;
}

}  // namespace

// Facility for dumping debug audio files. All methods are no-ops in the
// default case where WEBRTC_AGC_DEBUG_DUMP is undefined.
class DebugFile {
#ifdef WEBRTC_AGC_DEBUG_DUMP
 public:
  explicit DebugFile(const char* filename)
      : file_(fopen(filename, "wb")) {
    assert(file_);
  }
  ~DebugFile() {
    fclose(file_);
  }
  void Write(const int16_t* data, size_t length_samples) {
    fwrite(data, 1, length_samples * sizeof(int16_t), file_);
  }
 private:
  FILE* file_;
#else
 public:
  explicit DebugFile(const char* filename) {
  }
  ~DebugFile() {
  }
  void Write(const int16_t* data, size_t length_samples) {
  }
#endif  // WEBRTC_AGC_DEBUG_DUMP
};

AgcManagerDirect::AgcManagerDirect(GainControl* gctrl,
                                   VolumeCallbacks* volume_callbacks,
                                   int startup_min_level)
    : agc_(new Agc()),
      gctrl_(gctrl),
      volume_callbacks_(volume_callbacks),
      frames_since_clipped_(kClippedWaitFrames),
      level_(0),
      max_level_(kMaxMicLevel),
      max_compression_gain_(kMaxCompressionGain),
      target_compression_(kDefaultCompressionGain),
      compression_(target_compression_),
      compression_accumulator_(compression_),
      capture_muted_(false),
      check_volume_on_next_process_(true),  // Check at startup.
      startup_(true),
      startup_min_level_(ClampLevel(startup_min_level)),
      file_preproc_(new DebugFile("agc_preproc.pcm")),
      file_postproc_(new DebugFile("agc_postproc.pcm")) {
}

AgcManagerDirect::AgcManagerDirect(Agc* agc,
                                   GainControl* gctrl,
                                   VolumeCallbacks* volume_callbacks,
                                   int startup_min_level)
    : agc_(agc),
      gctrl_(gctrl),
      volume_callbacks_(volume_callbacks),
      frames_since_clipped_(kClippedWaitFrames),
      level_(0),
      max_level_(kMaxMicLevel),
      max_compression_gain_(kMaxCompressionGain),
      target_compression_(kDefaultCompressionGain),
      compression_(target_compression_),
      compression_accumulator_(compression_),
      capture_muted_(false),
      check_volume_on_next_process_(true),  // Check at startup.
      startup_(true),
      startup_min_level_(ClampLevel(startup_min_level)),
      file_preproc_(new DebugFile("agc_preproc.pcm")),
      file_postproc_(new DebugFile("agc_postproc.pcm")) {
}

AgcManagerDirect::~AgcManagerDirect() {}

int AgcManagerDirect::Initialize() {
  max_level_ = kMaxMicLevel;
  max_compression_gain_ = kMaxCompressionGain;
  target_compression_ = kDefaultCompressionGain;
  compression_ = target_compression_;
  compression_accumulator_ = compression_;
  capture_muted_ = false;
  check_volume_on_next_process_ = true;
  // TODO(bjornv): Investigate if we need to reset |startup_| as well. For
  // example, what happens when we change devices.

  if (gctrl_->set_mode(GainControl::kFixedDigital) != 0) {
    LOG(LS_ERROR) << "set_mode(GainControl::kFixedDigital) failed.";
    return -1;
  }
  if (gctrl_->set_target_level_dbfs(2) != 0) {
    LOG(LS_ERROR) << "set_target_level_dbfs(2) failed.";
    return -1;
  }
  if (gctrl_->set_compression_gain_db(kDefaultCompressionGain) != 0) {
    LOG(LS_ERROR) << "set_compression_gain_db(kDefaultCompressionGain) failed.";
    return -1;
  }
  if (gctrl_->enable_limiter(true) != 0) {
    LOG(LS_ERROR) << "enable_limiter(true) failed.";
    return -1;
  }
  return 0;
}

void AgcManagerDirect::AnalyzePreProcess(int16_t* audio,
                                         int num_channels,
                                         size_t samples_per_channel) {
  size_t length = num_channels * samples_per_channel;
  if (capture_muted_) {
    return;
  }

  file_preproc_->Write(audio, length);

  if (frames_since_clipped_ < kClippedWaitFrames) {
    ++frames_since_clipped_;
    return;
  }

  // Check for clipped samples, as the AGC has difficulty detecting pitch
  // under clipping distortion. We do this in the preprocessing phase in order
  // to catch clipped echo as well.
  //
  // If we find a sufficiently clipped frame, drop the current microphone level
  // and enforce a new maximum level, dropped the same amount from the current
  // maximum. This harsh treatment is an effort to avoid repeated clipped echo
  // events. As compensation for this restriction, the maximum compression
  // gain is increased, through SetMaxLevel().
  float clipped_ratio = agc_->AnalyzePreproc(audio, length);
  if (clipped_ratio > kClippedRatioThreshold) {
    LOG(LS_INFO) << "[agc] Clipping detected. clipped_ratio="
                 << clipped_ratio;
    // Always decrease the maximum level, even if the current level is below
    // threshold.
    SetMaxLevel(std::max(kClippedLevelMin, max_level_ - kClippedLevelStep));
    if (level_ > kClippedLevelMin) {
      // Don't try to adjust the level if we're already below the limit. As
      // a consequence, if the user has brought the level above the limit, we
      // will still not react until the postproc updates the level.
      SetLevel(std::max(kClippedLevelMin, level_ - kClippedLevelStep));
      // Reset the AGC since the level has changed.
      agc_->Reset();
    }
    frames_since_clipped_ = 0;
  }
}

void AgcManagerDirect::Process(const int16_t* audio,
                               size_t length,
                               int sample_rate_hz) {
  if (capture_muted_) {
    return;
  }

  if (check_volume_on_next_process_) {
    check_volume_on_next_process_ = false;
    // We have to wait until the first process call to check the volume,
    // because Chromium doesn't guarantee it to be valid any earlier.
    CheckVolumeAndReset();
  }

  if (agc_->Process(audio, length, sample_rate_hz) != 0) {
    LOG(LS_ERROR) << "Agc::Process failed";
    assert(false);
  }

  UpdateGain();
  UpdateCompressor();

  file_postproc_->Write(audio, length);
}

void AgcManagerDirect::SetLevel(int new_level) {
  int voe_level = volume_callbacks_->GetMicVolume();
  if (voe_level < 0) {
    return;
  }
  if (voe_level == 0) {
    LOG(LS_INFO) << "[agc] VolumeCallbacks returned level=0, taking no action.";
    return;
  }
  if (voe_level > kMaxMicLevel) {
    LOG(LS_ERROR) << "VolumeCallbacks returned an invalid level=" << voe_level;
    return;
  }

  if (voe_level > level_ + kLevelQuantizationSlack ||
      voe_level < level_ - kLevelQuantizationSlack) {
    LOG(LS_INFO) << "[agc] Mic volume was manually adjusted. Updating "
                 << "stored level from " << level_ << " to " << voe_level;
    level_ = voe_level;
    // Always allow the user to increase the volume.
    if (level_ > max_level_) {
      SetMaxLevel(level_);
    }
    // Take no action in this case, since we can't be sure when the volume
    // was manually adjusted. The compressor will still provide some of the
    // desired gain change.
    agc_->Reset();
    return;
  }

  new_level = std::min(new_level, max_level_);
  if (new_level == level_) {
    return;
  }

  volume_callbacks_->SetMicVolume(new_level);
  LOG(LS_INFO) << "[agc] voe_level=" << voe_level << ", "
               << "level_=" << level_ << ", "
               << "new_level=" << new_level;
  level_ = new_level;
}

void AgcManagerDirect::SetMaxLevel(int level) {
  assert(level >= kClippedLevelMin);
  max_level_ = level;
  // Scale the |kSurplusCompressionGain| linearly across the restricted
  // level range.
  max_compression_gain_ = kMaxCompressionGain + std::floor(
      (1.f * kMaxMicLevel - max_level_) / (kMaxMicLevel - kClippedLevelMin) *
      kSurplusCompressionGain + 0.5f);
  LOG(LS_INFO) << "[agc] max_level_=" << max_level_
               << ", max_compression_gain_="  << max_compression_gain_;
}

void AgcManagerDirect::SetCaptureMuted(bool muted) {
  if (capture_muted_ == muted) {
    return;
  }
  capture_muted_ = muted;

  if (!muted) {
    // When we unmute, we should reset things to be safe.
    check_volume_on_next_process_ = true;
  }
}

float AgcManagerDirect::voice_probability() {
  return agc_->voice_probability();
}

int AgcManagerDirect::CheckVolumeAndReset() {
  int level = volume_callbacks_->GetMicVolume();
  if (level < 0) {
    return -1;
  }
  // Reasons for taking action at startup:
  // 1) A person starting a call is expected to be heard.
  // 2) Independent of interpretation of |level| == 0 we should raise it so the
  // AGC can do its job properly.
  if (level == 0 && !startup_) {
    LOG(LS_INFO) << "[agc] VolumeCallbacks returned level=0, taking no action.";
    return 0;
  }
  if (level > kMaxMicLevel) {
    LOG(LS_ERROR) << "VolumeCallbacks returned an invalid level=" << level;
    return -1;
  }
  LOG(LS_INFO) << "[agc] Initial GetMicVolume()=" << level;

  int minLevel = startup_ ? startup_min_level_ : kMinMicLevel;
  if (level < minLevel) {
    level = minLevel;
    LOG(LS_INFO) << "[agc] Initial volume too low, raising to " << level;
    volume_callbacks_->SetMicVolume(level);
  }
  agc_->Reset();
  level_ = level;
  startup_ = false;
  return 0;
}

// Requests the RMS error from AGC and distributes the required gain change
// between the digital compression stage and volume slider. We use the
// compressor first, providing a slack region around the current slider
// position to reduce movement.
//
// If the slider needs to be moved, we check first if the user has adjusted
// it, in which case we take no action and cache the updated level.
void AgcManagerDirect::UpdateGain() {
  int rms_error = 0;
  if (!agc_->GetRmsErrorDb(&rms_error)) {
    // No error update ready.
    return;
  }
  // The compressor will always add at least kMinCompressionGain. In effect,
  // this adjusts our target gain upward by the same amount and rms_error
  // needs to reflect that.
  rms_error += kMinCompressionGain;

  // Handle as much error as possible with the compressor first.
  int raw_compression = std::max(std::min(rms_error, max_compression_gain_),
                                 kMinCompressionGain);
  // Deemphasize the compression gain error. Move halfway between the current
  // target and the newly received target. This serves to soften perceptible
  // intra-talkspurt adjustments, at the cost of some adaptation speed.
  if ((raw_compression == max_compression_gain_ &&
      target_compression_ == max_compression_gain_ - 1) ||
      (raw_compression == kMinCompressionGain &&
      target_compression_ == kMinCompressionGain + 1)) {
    // Special case to allow the target to reach the endpoints of the
    // compression range. The deemphasis would otherwise halt it at 1 dB shy.
    target_compression_ = raw_compression;
  } else {
    target_compression_ = (raw_compression - target_compression_) / 2
        + target_compression_;
  }

  // Residual error will be handled by adjusting the volume slider. Use the
  // raw rather than deemphasized compression here as we would otherwise
  // shrink the amount of slack the compressor provides.
  int residual_gain = rms_error - raw_compression;
  residual_gain = std::min(std::max(residual_gain, -kMaxResidualGainChange),
      kMaxResidualGainChange);
  LOG(LS_INFO) << "[agc] rms_error=" << rms_error << ", "
               << "target_compression=" << target_compression_ << ", "
               << "residual_gain=" << residual_gain;
  if (residual_gain == 0)
    return;

  SetLevel(LevelFromGainError(residual_gain, level_));
}

void AgcManagerDirect::UpdateCompressor() {
  if (compression_ == target_compression_) {
    return;
  }

  // Adapt the compression gain slowly towards the target, in order to avoid
  // highly perceptible changes.
  if (target_compression_ > compression_) {
    compression_accumulator_ += kCompressionGainStep;
  } else {
    compression_accumulator_ -= kCompressionGainStep;
  }

  // The compressor accepts integer gains in dB. Adjust the gain when
  // we've come within half a stepsize of the nearest integer.  (We don't
  // check for equality due to potential floating point imprecision).
  int new_compression = compression_;
  int nearest_neighbor = std::floor(compression_accumulator_ + 0.5);
  if (std::fabs(compression_accumulator_ - nearest_neighbor) <
      kCompressionGainStep / 2) {
    new_compression = nearest_neighbor;
  }

  // Set the new compression gain.
  if (new_compression != compression_) {
    compression_ = new_compression;
    compression_accumulator_ = new_compression;
    if (gctrl_->set_compression_gain_db(compression_) != 0) {
      LOG(LS_ERROR) << "set_compression_gain_db(" << compression_
                    << ") failed.";
    }
  }
}

}  // namespace webrtc
