/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_processing/echo_cancellation_impl.h"

#include <assert.h>
#include <string.h>

#include "webrtc/modules/audio_processing/aec/aec_core.h"
#include "webrtc/modules/audio_processing/aec/echo_cancellation.h"
#include "webrtc/modules/audio_processing/audio_buffer.h"

namespace webrtc {

namespace {
int16_t MapSetting(EchoCancellation::SuppressionLevel level) {
  switch (level) {
    case EchoCancellation::kLowSuppression:
      return kAecNlpConservative;
    case EchoCancellation::kModerateSuppression:
      return kAecNlpModerate;
    case EchoCancellation::kHighSuppression:
      return kAecNlpAggressive;
  }
  assert(false);
  return -1;
}

AudioProcessing::Error MapError(int err) {
  switch (err) {
    case AEC_UNSUPPORTED_FUNCTION_ERROR:
      return AudioProcessing::kUnsupportedFunctionError;
    case AEC_BAD_PARAMETER_ERROR:
      return AudioProcessing::kBadParameterError;
    case AEC_BAD_PARAMETER_WARNING:
      return AudioProcessing::kBadStreamParameterWarning;
    default:
      // AEC_UNSPECIFIED_ERROR
      // AEC_UNINITIALIZED_ERROR
      // AEC_NULL_POINTER_ERROR
      return AudioProcessing::kUnspecifiedError;
  }
}

// Maximum length that a frame of samples can have.
static const size_t kMaxAllowedValuesOfSamplesPerFrame = 160;
// Maximum number of frames to buffer in the render queue.
// TODO(peah): Decrease this once we properly handle hugely unbalanced
// reverse and forward call numbers.
static const size_t kMaxNumFramesToBuffer = 100;
}  // namespace

struct EchoCancellationImpl::StreamProperties {
  StreamProperties() = delete;
  StreamProperties(int sample_rate_hz,
                   size_t num_reverse_channels,
                   size_t num_output_channels,
                   size_t num_proc_channels)
      : sample_rate_hz(sample_rate_hz),
        num_reverse_channels(num_reverse_channels),
        num_output_channels(num_output_channels),
        num_proc_channels(num_proc_channels) {}

  const int sample_rate_hz;
  const size_t num_reverse_channels;
  const size_t num_output_channels;
  const size_t num_proc_channels;
};

class EchoCancellationImpl::Canceller {
 public:
  Canceller() {
    state_ = WebRtcAec_Create();
    RTC_DCHECK(state_);
  }

  ~Canceller() {
    RTC_CHECK(state_);
    WebRtcAec_Free(state_);
  }

  void* state() { return state_; }

  void Initialize(int sample_rate_hz) {
    // TODO(ajm): Drift compensation is disabled in practice. If restored, it
    // should be managed internally and not depend on the hardware sample rate.
    // For now, just hardcode a 48 kHz value.
    const int error = WebRtcAec_Init(state_, sample_rate_hz, 48000);
    RTC_DCHECK_EQ(0, error);
  }

 private:
  void* state_;
};

EchoCancellationImpl::EchoCancellationImpl(rtc::CriticalSection* crit_render,
                                           rtc::CriticalSection* crit_capture)
    : crit_render_(crit_render),
      crit_capture_(crit_capture),
      drift_compensation_enabled_(false),
      metrics_enabled_(false),
      suppression_level_(kModerateSuppression),
      stream_drift_samples_(0),
      was_stream_drift_set_(false),
      stream_has_echo_(false),
      delay_logging_enabled_(false),
      extended_filter_enabled_(false),
      delay_agnostic_enabled_(false),
      aec3_enabled_(false),
      render_queue_element_max_size_(0) {
  RTC_DCHECK(crit_render);
  RTC_DCHECK(crit_capture);
}

EchoCancellationImpl::~EchoCancellationImpl() {}

int EchoCancellationImpl::ProcessRenderAudio(const AudioBuffer* audio) {
  rtc::CritScope cs_render(crit_render_);
  if (!enabled_) {
    return AudioProcessing::kNoError;
  }

  RTC_DCHECK(stream_properties_);
  RTC_DCHECK_GE(160u, audio->num_frames_per_band());
  RTC_DCHECK_EQ(audio->num_channels(),
                stream_properties_->num_reverse_channels);
  RTC_DCHECK_GE(cancellers_.size(), stream_properties_->num_output_channels *
                                        audio->num_channels());

  int err = AudioProcessing::kNoError;

  // The ordering convention must be followed to pass to the correct AEC.
  size_t handle_index = 0;
  render_queue_buffer_.clear();
  for (size_t i = 0; i < stream_properties_->num_output_channels; i++) {
    for (size_t j = 0; j < audio->num_channels(); j++) {
      // Retrieve any error code produced by the buffering of the farend
      // signal.
      err = WebRtcAec_GetBufferFarendError(
          cancellers_[handle_index++]->state(),
          audio->split_bands_const_f(j)[kBand0To8kHz],
          audio->num_frames_per_band());

      if (err != AudioProcessing::kNoError) {
        return MapError(err);  // TODO(ajm): warning possible?
      }

      // Buffer the samples in the render queue.
      render_queue_buffer_.insert(render_queue_buffer_.end(),
                                  audio->split_bands_const_f(j)[kBand0To8kHz],
                                  (audio->split_bands_const_f(j)[kBand0To8kHz] +
                                   audio->num_frames_per_band()));
    }
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
// a queue. All the data chunks are buffered into the farend signal of the AEC.
void EchoCancellationImpl::ReadQueuedRenderData() {
  rtc::CritScope cs_capture(crit_capture_);
  if (!enabled_) {
    return;
  }

  RTC_DCHECK(stream_properties_);
  while (render_signal_queue_->Remove(&capture_queue_buffer_)) {
    size_t handle_index = 0;
    size_t buffer_index = 0;
    const size_t num_frames_per_band =
        capture_queue_buffer_.size() /
        (stream_properties_->num_output_channels *
         stream_properties_->num_reverse_channels);
    for (size_t i = 0; i < stream_properties_->num_output_channels; i++) {
      for (size_t j = 0; j < stream_properties_->num_reverse_channels; j++) {
        WebRtcAec_BufferFarend(cancellers_[handle_index++]->state(),
                               &capture_queue_buffer_[buffer_index],
                               num_frames_per_band);

        buffer_index += num_frames_per_band;
      }
    }
  }
}

int EchoCancellationImpl::ProcessCaptureAudio(AudioBuffer* audio,
                                              int stream_delay_ms) {
  rtc::CritScope cs_capture(crit_capture_);
  if (!enabled_) {
    return AudioProcessing::kNoError;
  }

  if (drift_compensation_enabled_ && !was_stream_drift_set_) {
    return AudioProcessing::kStreamParameterNotSetError;
  }

  RTC_DCHECK(stream_properties_);
  RTC_DCHECK_GE(160u, audio->num_frames_per_band());
  RTC_DCHECK_EQ(audio->num_channels(), stream_properties_->num_proc_channels);

  int err = AudioProcessing::kNoError;

  // The ordering convention must be followed to pass to the correct AEC.
  size_t handle_index = 0;
  stream_has_echo_ = false;
  for (size_t i = 0; i < audio->num_channels(); i++) {
    for (size_t j = 0; j < stream_properties_->num_reverse_channels; j++) {
      err = WebRtcAec_Process(
          cancellers_[handle_index]->state(), audio->split_bands_const_f(i),
          audio->num_bands(), audio->split_bands_f(i),
          audio->num_frames_per_band(), stream_delay_ms, stream_drift_samples_);

      if (err != AudioProcessing::kNoError) {
        err = MapError(err);
        // TODO(ajm): Figure out how to return warnings properly.
        if (err != AudioProcessing::kBadStreamParameterWarning) {
          return err;
        }
      }

      int status = 0;
      err = WebRtcAec_get_echo_status(cancellers_[handle_index]->state(),
                                      &status);
      if (err != AudioProcessing::kNoError) {
        return MapError(err);
      }

      if (status == 1) {
        stream_has_echo_ = true;
      }

      handle_index++;
    }
  }

  was_stream_drift_set_ = false;
  return AudioProcessing::kNoError;
}

int EchoCancellationImpl::Enable(bool enable) {
  // Run in a single-threaded manner.
  rtc::CritScope cs_render(crit_render_);
  rtc::CritScope cs_capture(crit_capture_);

  if (enable && !enabled_) {
    enabled_ = enable;  // Must be set before Initialize() is called.

    // TODO(peah): Simplify once the Enable function has been removed from
    // the public APM API.
    RTC_DCHECK(stream_properties_);
    Initialize(stream_properties_->sample_rate_hz,
               stream_properties_->num_reverse_channels,
               stream_properties_->num_output_channels,
               stream_properties_->num_proc_channels);
  } else {
    enabled_ = enable;
  }
  return AudioProcessing::kNoError;
}

bool EchoCancellationImpl::is_enabled_render_side_query() const {
  // TODO(peah): Add threadchecker.
  rtc::CritScope cs_render(crit_render_);
  return enabled_;
}

bool EchoCancellationImpl::is_enabled() const {
  rtc::CritScope cs(crit_capture_);
  return enabled_;
}

int EchoCancellationImpl::set_suppression_level(SuppressionLevel level) {
  {
    if (MapSetting(level) == -1) {
      return AudioProcessing::kBadParameterError;
    }
    rtc::CritScope cs(crit_capture_);
    suppression_level_ = level;
  }
  return Configure();
}

EchoCancellation::SuppressionLevel EchoCancellationImpl::suppression_level()
    const {
  rtc::CritScope cs(crit_capture_);
  return suppression_level_;
}

int EchoCancellationImpl::enable_drift_compensation(bool enable) {
  {
    rtc::CritScope cs(crit_capture_);
    drift_compensation_enabled_ = enable;
  }
  return Configure();
}

bool EchoCancellationImpl::is_drift_compensation_enabled() const {
  rtc::CritScope cs(crit_capture_);
  return drift_compensation_enabled_;
}

void EchoCancellationImpl::set_stream_drift_samples(int drift) {
  rtc::CritScope cs(crit_capture_);
  was_stream_drift_set_ = true;
  stream_drift_samples_ = drift;
}

int EchoCancellationImpl::stream_drift_samples() const {
  rtc::CritScope cs(crit_capture_);
  return stream_drift_samples_;
}

int EchoCancellationImpl::enable_metrics(bool enable) {
  {
    rtc::CritScope cs(crit_capture_);
    metrics_enabled_ = enable;
  }
  return Configure();
}

bool EchoCancellationImpl::are_metrics_enabled() const {
  rtc::CritScope cs(crit_capture_);
  return metrics_enabled_;
}

// TODO(ajm): we currently just use the metrics from the first AEC. Think more
//            aboue the best way to extend this to multi-channel.
int EchoCancellationImpl::GetMetrics(Metrics* metrics) {
  rtc::CritScope cs(crit_capture_);
  if (metrics == NULL) {
    return AudioProcessing::kNullPointerError;
  }

  if (!enabled_ || !metrics_enabled_) {
    return AudioProcessing::kNotEnabledError;
  }

  AecMetrics my_metrics;
  memset(&my_metrics, 0, sizeof(my_metrics));
  memset(metrics, 0, sizeof(Metrics));

  const int err = WebRtcAec_GetMetrics(cancellers_[0]->state(), &my_metrics);
  if (err != AudioProcessing::kNoError) {
    return MapError(err);
  }

  metrics->residual_echo_return_loss.instant = my_metrics.rerl.instant;
  metrics->residual_echo_return_loss.average = my_metrics.rerl.average;
  metrics->residual_echo_return_loss.maximum = my_metrics.rerl.max;
  metrics->residual_echo_return_loss.minimum = my_metrics.rerl.min;

  metrics->echo_return_loss.instant = my_metrics.erl.instant;
  metrics->echo_return_loss.average = my_metrics.erl.average;
  metrics->echo_return_loss.maximum = my_metrics.erl.max;
  metrics->echo_return_loss.minimum = my_metrics.erl.min;

  metrics->echo_return_loss_enhancement.instant = my_metrics.erle.instant;
  metrics->echo_return_loss_enhancement.average = my_metrics.erle.average;
  metrics->echo_return_loss_enhancement.maximum = my_metrics.erle.max;
  metrics->echo_return_loss_enhancement.minimum = my_metrics.erle.min;

  metrics->a_nlp.instant = my_metrics.aNlp.instant;
  metrics->a_nlp.average = my_metrics.aNlp.average;
  metrics->a_nlp.maximum = my_metrics.aNlp.max;
  metrics->a_nlp.minimum = my_metrics.aNlp.min;

  metrics->divergent_filter_fraction = my_metrics.divergent_filter_fraction;
  return AudioProcessing::kNoError;
}

bool EchoCancellationImpl::stream_has_echo() const {
  rtc::CritScope cs(crit_capture_);
  return stream_has_echo_;
}

int EchoCancellationImpl::enable_delay_logging(bool enable) {
  {
    rtc::CritScope cs(crit_capture_);
    delay_logging_enabled_ = enable;
  }
  return Configure();
}

bool EchoCancellationImpl::is_delay_logging_enabled() const {
  rtc::CritScope cs(crit_capture_);
  return delay_logging_enabled_;
}

bool EchoCancellationImpl::is_delay_agnostic_enabled() const {
  rtc::CritScope cs(crit_capture_);
  return delay_agnostic_enabled_;
}

bool EchoCancellationImpl::is_aec3_enabled() const {
  rtc::CritScope cs(crit_capture_);
  return aec3_enabled_;
}

std::string EchoCancellationImpl::GetExperimentsDescription() {
  rtc::CritScope cs(crit_capture_);
  std::string description = (aec3_enabled_ ? "AEC3;" : "");
  if (refined_adaptive_filter_enabled_) {
    description += "RefinedAdaptiveFilter;";
  }
  return description;
}

bool EchoCancellationImpl::is_refined_adaptive_filter_enabled() const {
  rtc::CritScope cs(crit_capture_);
  return refined_adaptive_filter_enabled_;
}

bool EchoCancellationImpl::is_extended_filter_enabled() const {
  rtc::CritScope cs(crit_capture_);
  return extended_filter_enabled_;
}

// TODO(bjornv): How should we handle the multi-channel case?
int EchoCancellationImpl::GetDelayMetrics(int* median, int* std) {
  rtc::CritScope cs(crit_capture_);
  float fraction_poor_delays = 0;
  return GetDelayMetrics(median, std, &fraction_poor_delays);
}

int EchoCancellationImpl::GetDelayMetrics(int* median, int* std,
                                          float* fraction_poor_delays) {
  rtc::CritScope cs(crit_capture_);
  if (median == NULL) {
    return AudioProcessing::kNullPointerError;
  }
  if (std == NULL) {
    return AudioProcessing::kNullPointerError;
  }

  if (!enabled_ || !delay_logging_enabled_) {
    return AudioProcessing::kNotEnabledError;
  }

  const int err = WebRtcAec_GetDelayMetrics(cancellers_[0]->state(), median,
                                            std, fraction_poor_delays);
  if (err != AudioProcessing::kNoError) {
    return MapError(err);
  }

  return AudioProcessing::kNoError;
}

struct AecCore* EchoCancellationImpl::aec_core() const {
  rtc::CritScope cs(crit_capture_);
  if (!enabled_) {
    return NULL;
  }
  return WebRtcAec_aec_core(cancellers_[0]->state());
}

void EchoCancellationImpl::Initialize(int sample_rate_hz,
                                      size_t num_reverse_channels,
                                      size_t num_output_channels,
                                      size_t num_proc_channels) {
  rtc::CritScope cs_render(crit_render_);
  rtc::CritScope cs_capture(crit_capture_);

  stream_properties_.reset(
      new StreamProperties(sample_rate_hz, num_reverse_channels,
                           num_output_channels, num_proc_channels));

  if (!enabled_) {
    return;
  }

  if (NumCancellersRequired() > cancellers_.size()) {
    const size_t cancellers_old_size = cancellers_.size();
    cancellers_.resize(NumCancellersRequired());

    for (size_t i = cancellers_old_size; i < cancellers_.size(); ++i) {
      cancellers_[i].reset(new Canceller());
    }
  }

  for (auto& canceller : cancellers_) {
    canceller->Initialize(sample_rate_hz);
  }

  Configure();

  AllocateRenderQueue();
}

int EchoCancellationImpl::GetSystemDelayInSamples() const {
  rtc::CritScope cs(crit_capture_);
  RTC_DCHECK(enabled_);
  // Report the delay for the first AEC component.
  return WebRtcAec_system_delay(
      WebRtcAec_aec_core(cancellers_[0]->state()));
}

void EchoCancellationImpl::AllocateRenderQueue() {
  const size_t new_render_queue_element_max_size = std::max<size_t>(
      static_cast<size_t>(1),
      kMaxAllowedValuesOfSamplesPerFrame * NumCancellersRequired());

  rtc::CritScope cs_render(crit_render_);
  rtc::CritScope cs_capture(crit_capture_);

  // Reallocate the queue if the queue item size is too small to fit the
  // data to put in the queue.
  if (render_queue_element_max_size_ < new_render_queue_element_max_size) {
    render_queue_element_max_size_ = new_render_queue_element_max_size;

    std::vector<float> template_queue_element(render_queue_element_max_size_);

    render_signal_queue_.reset(
        new SwapQueue<std::vector<float>, RenderQueueItemVerifier<float>>(
            kMaxNumFramesToBuffer, template_queue_element,
            RenderQueueItemVerifier<float>(render_queue_element_max_size_)));

    render_queue_buffer_.resize(render_queue_element_max_size_);
    capture_queue_buffer_.resize(render_queue_element_max_size_);
  } else {
    render_signal_queue_->Clear();
  }
}

void EchoCancellationImpl::SetExtraOptions(const Config& config) {
  {
    rtc::CritScope cs(crit_capture_);
    extended_filter_enabled_ = config.Get<ExtendedFilter>().enabled;
    delay_agnostic_enabled_ = config.Get<DelayAgnostic>().enabled;
    refined_adaptive_filter_enabled_ =
        config.Get<RefinedAdaptiveFilter>().enabled;
    aec3_enabled_ = config.Get<EchoCanceller3>().enabled;
  }
  Configure();
}

int EchoCancellationImpl::Configure() {
  rtc::CritScope cs_render(crit_render_);
  rtc::CritScope cs_capture(crit_capture_);
  AecConfig config;
  config.metricsMode = metrics_enabled_;
  config.nlpMode = MapSetting(suppression_level_);
  config.skewMode = drift_compensation_enabled_;
  config.delay_logging = delay_logging_enabled_;

  int error = AudioProcessing::kNoError;
  for (auto& canceller : cancellers_) {
    WebRtcAec_enable_extended_filter(WebRtcAec_aec_core(canceller->state()),
                                     extended_filter_enabled_ ? 1 : 0);
    WebRtcAec_enable_delay_agnostic(WebRtcAec_aec_core(canceller->state()),
                                    delay_agnostic_enabled_ ? 1 : 0);
    WebRtcAec_enable_aec3(WebRtcAec_aec_core(canceller->state()),
                          aec3_enabled_ ? 1 : 0);
    WebRtcAec_enable_refined_adaptive_filter(
        WebRtcAec_aec_core(canceller->state()),
        refined_adaptive_filter_enabled_);
    const int handle_error = WebRtcAec_set_config(canceller->state(), config);
    if (handle_error != AudioProcessing::kNoError) {
      error = AudioProcessing::kNoError;
    }
  }
  return error;
}

size_t EchoCancellationImpl::NumCancellersRequired() const {
  RTC_DCHECK(stream_properties_);
  return stream_properties_->num_output_channels *
         stream_properties_->num_reverse_channels;
}

}  // namespace webrtc
