/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_processing/gain_control_impl.h"

#include "webrtc/base/constructormagic.h"
#include "webrtc/base/optional.h"
#include "webrtc/modules/audio_processing/audio_buffer.h"
#include "webrtc/modules/audio_processing/agc/legacy/gain_control.h"

namespace webrtc {

typedef void Handle;

namespace {
int16_t MapSetting(GainControl::Mode mode) {
  switch (mode) {
    case GainControl::kAdaptiveAnalog:
      return kAgcModeAdaptiveAnalog;
    case GainControl::kAdaptiveDigital:
      return kAgcModeAdaptiveDigital;
    case GainControl::kFixedDigital:
      return kAgcModeFixedDigital;
  }
  RTC_DCHECK(false);
  return -1;
}

// Maximum length that a frame of samples can have.
static const size_t kMaxAllowedValuesOfSamplesPerFrame = 160;
// Maximum number of frames to buffer in the render queue.
// TODO(peah): Decrease this once we properly handle hugely unbalanced
// reverse and forward call numbers.
static const size_t kMaxNumFramesToBuffer = 100;

}  // namespace

class GainControlImpl::GainController {
 public:
  explicit GainController() {
    state_ = WebRtcAgc_Create();
    RTC_CHECK(state_);
  }

  ~GainController() {
    RTC_DCHECK(state_);
    WebRtcAgc_Free(state_);
  }

  Handle* state() {
    RTC_DCHECK(state_);
    return state_;
  }

  void Initialize(int minimum_capture_level,
                  int maximum_capture_level,
                  Mode mode,
                  int sample_rate_hz,
                  int capture_level) {
    RTC_DCHECK(state_);
    int error =
        WebRtcAgc_Init(state_, minimum_capture_level, maximum_capture_level,
                       MapSetting(mode), sample_rate_hz);
    RTC_DCHECK_EQ(0, error);

    set_capture_level(capture_level);
  }

  void set_capture_level(int capture_level) {
    capture_level_ = rtc::Optional<int>(capture_level);
  }

  int get_capture_level() {
    RTC_DCHECK(capture_level_);
    return *capture_level_;
  }

 private:
  Handle* state_;
  // TODO(peah): Remove the optional once the initialization is moved into the
  // ctor.
  rtc::Optional<int> capture_level_;

  RTC_DISALLOW_COPY_AND_ASSIGN(GainController);
};

GainControlImpl::GainControlImpl(rtc::CriticalSection* crit_render,
                                 rtc::CriticalSection* crit_capture)
    : crit_render_(crit_render),
      crit_capture_(crit_capture),
      mode_(kAdaptiveAnalog),
      minimum_capture_level_(0),
      maximum_capture_level_(255),
      limiter_enabled_(true),
      target_level_dbfs_(3),
      compression_gain_db_(9),
      analog_capture_level_(0),
      was_analog_level_set_(false),
      stream_is_saturated_(false),
      render_queue_element_max_size_(0) {
  RTC_DCHECK(crit_render);
  RTC_DCHECK(crit_capture);
}

GainControlImpl::~GainControlImpl() {}

int GainControlImpl::ProcessRenderAudio(AudioBuffer* audio) {
  rtc::CritScope cs(crit_render_);
  if (!enabled_) {
    return AudioProcessing::kNoError;
  }

  RTC_DCHECK_GE(160u, audio->num_frames_per_band());

  render_queue_buffer_.resize(0);
  for (auto& gain_controller : gain_controllers_) {
    int err = WebRtcAgc_GetAddFarendError(gain_controller->state(),
                                          audio->num_frames_per_band());

    if (err != AudioProcessing::kNoError) {
      return AudioProcessing::kUnspecifiedError;
    }

    // Buffer the samples in the render queue.
    render_queue_buffer_.insert(
        render_queue_buffer_.end(), audio->mixed_low_pass_data(),
        (audio->mixed_low_pass_data() + audio->num_frames_per_band()));
  }

  // Insert the samples into the queue.
  if (!render_signal_queue_->Insert(&render_queue_buffer_)) {
    // The data queue is full and needs to be emptied.
    ReadQueuedRenderData();

    // Retry the insert (should always work).
    RTC_DCHECK_EQ(render_signal_queue_->Insert(&render_queue_buffer_), true);
  }

  return AudioProcessing::kNoError;
}

// Read chunks of data that were received and queued on the render side from
// a queue. All the data chunks are buffered into the farend signal of the AGC.
void GainControlImpl::ReadQueuedRenderData() {
  rtc::CritScope cs(crit_capture_);

  if (!enabled_) {
    return;
  }

  while (render_signal_queue_->Remove(&capture_queue_buffer_)) {
    size_t buffer_index = 0;
    RTC_DCHECK(num_proc_channels_);
    RTC_DCHECK_LT(0ul, *num_proc_channels_);
    const size_t num_frames_per_band =
        capture_queue_buffer_.size() / (*num_proc_channels_);
    for (auto& gain_controller : gain_controllers_) {
      WebRtcAgc_AddFarend(gain_controller->state(),
                          &capture_queue_buffer_[buffer_index],
                          num_frames_per_band);

      buffer_index += num_frames_per_band;
    }
  }
}

int GainControlImpl::AnalyzeCaptureAudio(AudioBuffer* audio) {
  rtc::CritScope cs(crit_capture_);

  if (!enabled_) {
    return AudioProcessing::kNoError;
  }

  RTC_DCHECK(num_proc_channels_);
  RTC_DCHECK_GE(160u, audio->num_frames_per_band());
  RTC_DCHECK_EQ(audio->num_channels(), *num_proc_channels_);
  RTC_DCHECK_LE(*num_proc_channels_, gain_controllers_.size());

  if (mode_ == kAdaptiveAnalog) {
    int capture_channel = 0;
    for (auto& gain_controller : gain_controllers_) {
      gain_controller->set_capture_level(analog_capture_level_);
      int err = WebRtcAgc_AddMic(
          gain_controller->state(), audio->split_bands(capture_channel),
          audio->num_bands(), audio->num_frames_per_band());

      if (err != AudioProcessing::kNoError) {
        return AudioProcessing::kUnspecifiedError;
      }
      ++capture_channel;
    }
  } else if (mode_ == kAdaptiveDigital) {
    int capture_channel = 0;
    for (auto& gain_controller : gain_controllers_) {
      int32_t capture_level_out = 0;
      int err = WebRtcAgc_VirtualMic(
          gain_controller->state(), audio->split_bands(capture_channel),
          audio->num_bands(), audio->num_frames_per_band(),
          analog_capture_level_, &capture_level_out);

      gain_controller->set_capture_level(capture_level_out);

      if (err != AudioProcessing::kNoError) {
        return AudioProcessing::kUnspecifiedError;
      }
      ++capture_channel;
    }
  }

  return AudioProcessing::kNoError;
}

int GainControlImpl::ProcessCaptureAudio(AudioBuffer* audio,
                                         bool stream_has_echo) {
  rtc::CritScope cs(crit_capture_);

  if (!enabled_) {
    return AudioProcessing::kNoError;
  }

  if (mode_ == kAdaptiveAnalog && !was_analog_level_set_) {
    return AudioProcessing::kStreamParameterNotSetError;
  }

  RTC_DCHECK(num_proc_channels_);
  RTC_DCHECK_GE(160u, audio->num_frames_per_band());
  RTC_DCHECK_EQ(audio->num_channels(), *num_proc_channels_);

  stream_is_saturated_ = false;
  int capture_channel = 0;
  for (auto& gain_controller : gain_controllers_) {
    int32_t capture_level_out = 0;
    uint8_t saturation_warning = 0;

    // The call to stream_has_echo() is ok from a deadlock perspective
    // as the capture lock is allready held.
    int err = WebRtcAgc_Process(
        gain_controller->state(), audio->split_bands_const(capture_channel),
        audio->num_bands(), audio->num_frames_per_band(),
        audio->split_bands(capture_channel),
        gain_controller->get_capture_level(), &capture_level_out,
        stream_has_echo, &saturation_warning);

    if (err != AudioProcessing::kNoError) {
      return AudioProcessing::kUnspecifiedError;
    }

    gain_controller->set_capture_level(capture_level_out);
    if (saturation_warning == 1) {
      stream_is_saturated_ = true;
    }

    ++capture_channel;
  }

  RTC_DCHECK_LT(0ul, *num_proc_channels_);
  if (mode_ == kAdaptiveAnalog) {
    // Take the analog level to be the average across the handles.
    analog_capture_level_ = 0;
    for (auto& gain_controller : gain_controllers_) {
      analog_capture_level_ += gain_controller->get_capture_level();
    }

    analog_capture_level_ /= (*num_proc_channels_);
  }

  was_analog_level_set_ = false;
  return AudioProcessing::kNoError;
}

int GainControlImpl::compression_gain_db() const {
  rtc::CritScope cs(crit_capture_);
  return compression_gain_db_;
}

// TODO(ajm): ensure this is called under kAdaptiveAnalog.
int GainControlImpl::set_stream_analog_level(int level) {
  rtc::CritScope cs(crit_capture_);

  was_analog_level_set_ = true;
  if (level < minimum_capture_level_ || level > maximum_capture_level_) {
    return AudioProcessing::kBadParameterError;
  }
  analog_capture_level_ = level;

  return AudioProcessing::kNoError;
}

int GainControlImpl::stream_analog_level() {
  rtc::CritScope cs(crit_capture_);
  // TODO(ajm): enable this assertion?
  //assert(mode_ == kAdaptiveAnalog);

  return analog_capture_level_;
}

int GainControlImpl::Enable(bool enable) {
  rtc::CritScope cs_render(crit_render_);
  rtc::CritScope cs_capture(crit_capture_);
  if (enable && !enabled_) {
    enabled_ = enable;  // Must be set before Initialize() is called.

    RTC_DCHECK(num_proc_channels_);
    RTC_DCHECK(sample_rate_hz_);
    Initialize(*num_proc_channels_, *sample_rate_hz_);
  } else {
    enabled_ = enable;
  }
  return AudioProcessing::kNoError;
}

bool GainControlImpl::is_enabled_render_side_query() const {
  // TODO(peah): Add threadchecker.
  rtc::CritScope cs(crit_render_);
  return enabled_;
}

bool GainControlImpl::is_enabled() const {
  rtc::CritScope cs(crit_capture_);
  return enabled_;
}

int GainControlImpl::set_mode(Mode mode) {
  rtc::CritScope cs_render(crit_render_);
  rtc::CritScope cs_capture(crit_capture_);
  if (MapSetting(mode) == -1) {
    return AudioProcessing::kBadParameterError;
  }

  mode_ = mode;
  RTC_DCHECK(num_proc_channels_);
  RTC_DCHECK(sample_rate_hz_);
  Initialize(*num_proc_channels_, *sample_rate_hz_);
  return AudioProcessing::kNoError;
}

GainControl::Mode GainControlImpl::mode() const {
  rtc::CritScope cs(crit_capture_);
  return mode_;
}

int GainControlImpl::set_analog_level_limits(int minimum,
                                             int maximum) {
  if (minimum < 0) {
    return AudioProcessing::kBadParameterError;
  }

  if (maximum > 65535) {
    return AudioProcessing::kBadParameterError;
  }

  if (maximum < minimum) {
    return AudioProcessing::kBadParameterError;
  }

  size_t num_proc_channels_local = 0u;
  int sample_rate_hz_local = 0;
  {
    rtc::CritScope cs(crit_capture_);

    minimum_capture_level_ = minimum;
    maximum_capture_level_ = maximum;

    RTC_DCHECK(num_proc_channels_);
    RTC_DCHECK(sample_rate_hz_);
    num_proc_channels_local = *num_proc_channels_;
    sample_rate_hz_local = *sample_rate_hz_;
  }
  Initialize(num_proc_channels_local, sample_rate_hz_local);
  return AudioProcessing::kNoError;
}

int GainControlImpl::analog_level_minimum() const {
  rtc::CritScope cs(crit_capture_);
  return minimum_capture_level_;
}

int GainControlImpl::analog_level_maximum() const {
  rtc::CritScope cs(crit_capture_);
  return maximum_capture_level_;
}

bool GainControlImpl::stream_is_saturated() const {
  rtc::CritScope cs(crit_capture_);
  return stream_is_saturated_;
}

int GainControlImpl::set_target_level_dbfs(int level) {
  if (level > 31 || level < 0) {
    return AudioProcessing::kBadParameterError;
  }
  {
    rtc::CritScope cs(crit_capture_);
    target_level_dbfs_ = level;
  }
  return Configure();
}

int GainControlImpl::target_level_dbfs() const {
  rtc::CritScope cs(crit_capture_);
  return target_level_dbfs_;
}

int GainControlImpl::set_compression_gain_db(int gain) {
  if (gain < 0 || gain > 90) {
    return AudioProcessing::kBadParameterError;
  }
  {
    rtc::CritScope cs(crit_capture_);
    compression_gain_db_ = gain;
  }
  return Configure();
}

int GainControlImpl::enable_limiter(bool enable) {
  {
    rtc::CritScope cs(crit_capture_);
    limiter_enabled_ = enable;
  }
  return Configure();
}

bool GainControlImpl::is_limiter_enabled() const {
  rtc::CritScope cs(crit_capture_);
  return limiter_enabled_;
}

void GainControlImpl::Initialize(size_t num_proc_channels, int sample_rate_hz) {
  rtc::CritScope cs_render(crit_render_);
  rtc::CritScope cs_capture(crit_capture_);

  num_proc_channels_ = rtc::Optional<size_t>(num_proc_channels);
  sample_rate_hz_ = rtc::Optional<int>(sample_rate_hz);

  if (!enabled_) {
    return;
  }

  gain_controllers_.resize(*num_proc_channels_);
  for (auto& gain_controller : gain_controllers_) {
    if (!gain_controller) {
      gain_controller.reset(new GainController());
    }
    gain_controller->Initialize(minimum_capture_level_, maximum_capture_level_,
                                mode_, *sample_rate_hz_, analog_capture_level_);
  }

  Configure();

  AllocateRenderQueue();
}

void GainControlImpl::AllocateRenderQueue() {
  rtc::CritScope cs_render(crit_render_);
  rtc::CritScope cs_capture(crit_capture_);

  RTC_DCHECK(num_proc_channels_);
  const size_t new_render_queue_element_max_size = std::max<size_t>(
      static_cast<size_t>(1),
      kMaxAllowedValuesOfSamplesPerFrame * (*num_proc_channels_));

  if (render_queue_element_max_size_ < new_render_queue_element_max_size) {
    render_queue_element_max_size_ = new_render_queue_element_max_size;
    std::vector<int16_t> template_queue_element(render_queue_element_max_size_);

    render_signal_queue_.reset(
        new SwapQueue<std::vector<int16_t>, RenderQueueItemVerifier<int16_t>>(
            kMaxNumFramesToBuffer, template_queue_element,
            RenderQueueItemVerifier<int16_t>(render_queue_element_max_size_)));

    render_queue_buffer_.resize(render_queue_element_max_size_);
    capture_queue_buffer_.resize(render_queue_element_max_size_);
  } else {
    render_signal_queue_->Clear();
  }
}

int GainControlImpl::Configure() {
  rtc::CritScope cs_render(crit_render_);
  rtc::CritScope cs_capture(crit_capture_);
  WebRtcAgcConfig config;
  // TODO(ajm): Flip the sign here (since AGC expects a positive value) if we
  //            change the interface.
  //assert(target_level_dbfs_ <= 0);
  //config.targetLevelDbfs = static_cast<int16_t>(-target_level_dbfs_);
  config.targetLevelDbfs = static_cast<int16_t>(target_level_dbfs_);
  config.compressionGaindB =
      static_cast<int16_t>(compression_gain_db_);
  config.limiterEnable = limiter_enabled_;

  int error = AudioProcessing::kNoError;
  for (auto& gain_controller : gain_controllers_) {
    const int handle_error =
        WebRtcAgc_set_config(gain_controller->state(), config);
    if (handle_error != AudioProcessing::kNoError) {
      error = handle_error;
    }
  }
  return error;
}
}  // namespace webrtc
