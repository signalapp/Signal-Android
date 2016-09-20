/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_processing/audio_processing_impl.h"

#include <assert.h>
#include <algorithm>

#include "webrtc/base/checks.h"
#include "webrtc/base/platform_file.h"
#include "webrtc/base/trace_event.h"
#include "webrtc/common_audio/audio_converter.h"
#include "webrtc/common_audio/channel_buffer.h"
#include "webrtc/common_audio/include/audio_util.h"
#include "webrtc/common_audio/signal_processing/include/signal_processing_library.h"
#include "webrtc/modules/audio_processing/aec/aec_core.h"
#include "webrtc/modules/audio_processing/agc/agc_manager_direct.h"
#include "webrtc/modules/audio_processing/audio_buffer.h"
#include "webrtc/modules/audio_processing/beamformer/nonlinear_beamformer.h"
#include "webrtc/modules/audio_processing/common.h"
#include "webrtc/modules/audio_processing/echo_cancellation_impl.h"
#include "webrtc/modules/audio_processing/echo_control_mobile_impl.h"
#include "webrtc/modules/audio_processing/gain_control_for_experimental_agc.h"
#include "webrtc/modules/audio_processing/gain_control_impl.h"
#include "webrtc/modules/audio_processing/high_pass_filter_impl.h"
#include "webrtc/modules/audio_processing/intelligibility/intelligibility_enhancer.h"
#include "webrtc/modules/audio_processing/level_estimator_impl.h"
#include "webrtc/modules/audio_processing/noise_suppression_impl.h"
#include "webrtc/modules/audio_processing/transient/transient_suppressor.h"
#include "webrtc/modules/audio_processing/voice_detection_impl.h"
#include "webrtc/modules/include/module_common_types.h"
#include "webrtc/system_wrappers/include/file_wrapper.h"
#include "webrtc/system_wrappers/include/logging.h"
#include "webrtc/system_wrappers/include/metrics.h"

#ifdef WEBRTC_AUDIOPROC_DEBUG_DUMP
// Files generated at build-time by the protobuf compiler.
#ifdef WEBRTC_ANDROID_PLATFORM_BUILD
#include "external/webrtc/webrtc/modules/audio_processing/debug.pb.h"
#else
#include "webrtc/modules/audio_processing/debug.pb.h"
#endif
#endif  // WEBRTC_AUDIOPROC_DEBUG_DUMP

#define RETURN_ON_ERR(expr) \
  do {                      \
    int err = (expr);       \
    if (err != kNoError) {  \
      return err;           \
    }                       \
  } while (0)

namespace webrtc {

const int AudioProcessing::kNativeSampleRatesHz[] = {
    AudioProcessing::kSampleRate8kHz,
    AudioProcessing::kSampleRate16kHz,
#ifdef WEBRTC_ARCH_ARM_FAMILY
    AudioProcessing::kSampleRate32kHz};
#else
    AudioProcessing::kSampleRate32kHz,
    AudioProcessing::kSampleRate48kHz};
#endif  // WEBRTC_ARCH_ARM_FAMILY
const size_t AudioProcessing::kNumNativeSampleRates =
    arraysize(AudioProcessing::kNativeSampleRatesHz);
const int AudioProcessing::kMaxNativeSampleRateHz = AudioProcessing::
    kNativeSampleRatesHz[AudioProcessing::kNumNativeSampleRates - 1];

namespace {

static bool LayoutHasKeyboard(AudioProcessing::ChannelLayout layout) {
  switch (layout) {
    case AudioProcessing::kMono:
    case AudioProcessing::kStereo:
      return false;
    case AudioProcessing::kMonoAndKeyboard:
    case AudioProcessing::kStereoAndKeyboard:
      return true;
  }

  assert(false);
  return false;
}

bool is_multi_band(int sample_rate_hz) {
  return sample_rate_hz == AudioProcessing::kSampleRate32kHz ||
         sample_rate_hz == AudioProcessing::kSampleRate48kHz;
}

int ClosestHigherNativeRate(int min_proc_rate) {
  for (int rate : AudioProcessing::kNativeSampleRatesHz) {
    if (rate >= min_proc_rate) {
      return rate;
    }
  }
  return AudioProcessing::kMaxNativeSampleRateHz;
}

}  // namespace

// Throughout webrtc, it's assumed that success is represented by zero.
static_assert(AudioProcessing::kNoError == 0, "kNoError must be zero");

struct AudioProcessingImpl::ApmPublicSubmodules {
  ApmPublicSubmodules() {}
  // Accessed externally of APM without any lock acquired.
  std::unique_ptr<EchoCancellationImpl> echo_cancellation;
  std::unique_ptr<EchoControlMobileImpl> echo_control_mobile;
  std::unique_ptr<GainControlImpl> gain_control;
  std::unique_ptr<HighPassFilterImpl> high_pass_filter;
  std::unique_ptr<LevelEstimatorImpl> level_estimator;
  std::unique_ptr<NoiseSuppressionImpl> noise_suppression;
  std::unique_ptr<VoiceDetectionImpl> voice_detection;
  std::unique_ptr<GainControlForExperimentalAgc>
      gain_control_for_experimental_agc;

  // Accessed internally from both render and capture.
  std::unique_ptr<TransientSuppressor> transient_suppressor;
  std::unique_ptr<IntelligibilityEnhancer> intelligibility_enhancer;
};

struct AudioProcessingImpl::ApmPrivateSubmodules {
  explicit ApmPrivateSubmodules(Beamformer<float>* beamformer)
      : beamformer(beamformer) {}
  // Accessed internally from capture or during initialization
  std::unique_ptr<Beamformer<float>> beamformer;
  std::unique_ptr<AgcManagerDirect> agc_manager;
};

AudioProcessing* AudioProcessing::Create() {
  Config config;
  return Create(config, nullptr);
}

AudioProcessing* AudioProcessing::Create(const Config& config) {
  return Create(config, nullptr);
}

AudioProcessing* AudioProcessing::Create(const Config& config,
                                         Beamformer<float>* beamformer) {
  AudioProcessingImpl* apm = new AudioProcessingImpl(config, beamformer);
  if (apm->Initialize() != kNoError) {
    delete apm;
    apm = nullptr;
  }

  return apm;
}

AudioProcessingImpl::AudioProcessingImpl(const Config& config)
    : AudioProcessingImpl(config, nullptr) {}

AudioProcessingImpl::AudioProcessingImpl(const Config& config,
                                         Beamformer<float>* beamformer)
    : public_submodules_(new ApmPublicSubmodules()),
      private_submodules_(new ApmPrivateSubmodules(beamformer)),
      constants_(config.Get<ExperimentalAgc>().startup_min_volume,
#if defined(WEBRTC_ANDROID) || defined(WEBRTC_IOS)
                 false),
#else
                 config.Get<ExperimentalAgc>().enabled),
#endif
#if defined(WEBRTC_ANDROID) || defined(WEBRTC_IOS)
      capture_(false,
#else
      capture_(config.Get<ExperimentalNs>().enabled,
#endif
               config.Get<Beamforming>().array_geometry,
               config.Get<Beamforming>().target_direction),
      capture_nonlocked_(config.Get<Beamforming>().enabled,
                         config.Get<Intelligibility>().enabled)
{
  {
    rtc::CritScope cs_render(&crit_render_);
    rtc::CritScope cs_capture(&crit_capture_);

    public_submodules_->echo_cancellation.reset(
        new EchoCancellationImpl(&crit_render_, &crit_capture_));
    public_submodules_->echo_control_mobile.reset(
        new EchoControlMobileImpl(&crit_render_, &crit_capture_));
    public_submodules_->gain_control.reset(
        new GainControlImpl(&crit_capture_, &crit_capture_));
    public_submodules_->high_pass_filter.reset(
        new HighPassFilterImpl(&crit_capture_));
    public_submodules_->level_estimator.reset(
        new LevelEstimatorImpl(&crit_capture_));
    public_submodules_->noise_suppression.reset(
        new NoiseSuppressionImpl(&crit_capture_));
    public_submodules_->voice_detection.reset(
        new VoiceDetectionImpl(&crit_capture_));
    public_submodules_->gain_control_for_experimental_agc.reset(
        new GainControlForExperimentalAgc(
            public_submodules_->gain_control.get(), &crit_capture_));
  }

  SetExtraOptions(config);
}

AudioProcessingImpl::~AudioProcessingImpl() {
  // Depends on gain_control_ and
  // public_submodules_->gain_control_for_experimental_agc.
  private_submodules_->agc_manager.reset();
  // Depends on gain_control_.
  public_submodules_->gain_control_for_experimental_agc.reset();

#ifdef WEBRTC_AUDIOPROC_DEBUG_DUMP
  debug_dump_.debug_file->CloseFile();
#endif
}

int AudioProcessingImpl::Initialize() {
  // Run in a single-threaded manner during initialization.
  rtc::CritScope cs_render(&crit_render_);
  rtc::CritScope cs_capture(&crit_capture_);
  return InitializeLocked();
}

int AudioProcessingImpl::Initialize(int input_sample_rate_hz,
                                    int output_sample_rate_hz,
                                    int reverse_sample_rate_hz,
                                    ChannelLayout input_layout,
                                    ChannelLayout output_layout,
                                    ChannelLayout reverse_layout) {
  const ProcessingConfig processing_config = {
      {{input_sample_rate_hz,
        ChannelsFromLayout(input_layout),
        LayoutHasKeyboard(input_layout)},
       {output_sample_rate_hz,
        ChannelsFromLayout(output_layout),
        LayoutHasKeyboard(output_layout)},
       {reverse_sample_rate_hz,
        ChannelsFromLayout(reverse_layout),
        LayoutHasKeyboard(reverse_layout)},
       {reverse_sample_rate_hz,
        ChannelsFromLayout(reverse_layout),
        LayoutHasKeyboard(reverse_layout)}}};

  return Initialize(processing_config);
}

int AudioProcessingImpl::Initialize(const ProcessingConfig& processing_config) {
  // Run in a single-threaded manner during initialization.
  rtc::CritScope cs_render(&crit_render_);
  rtc::CritScope cs_capture(&crit_capture_);
  return InitializeLocked(processing_config);
}

int AudioProcessingImpl::MaybeInitializeRender(
    const ProcessingConfig& processing_config) {
  return MaybeInitialize(processing_config);
}

int AudioProcessingImpl::MaybeInitializeCapture(
    const ProcessingConfig& processing_config) {
  return MaybeInitialize(processing_config);
}

// Calls InitializeLocked() if any of the audio parameters have changed from
// their current values (needs to be called while holding the crit_render_lock).
int AudioProcessingImpl::MaybeInitialize(
    const ProcessingConfig& processing_config) {
  // Called from both threads. Thread check is therefore not possible.
  if (processing_config == formats_.api_format) {
    return kNoError;
  }

  rtc::CritScope cs_capture(&crit_capture_);
  return InitializeLocked(processing_config);
}

int AudioProcessingImpl::InitializeLocked() {
  const int fwd_audio_buffer_channels =
      capture_nonlocked_.beamformer_enabled
          ? formats_.api_format.input_stream().num_channels()
          : formats_.api_format.output_stream().num_channels();
  const int rev_audio_buffer_out_num_frames =
      formats_.api_format.reverse_output_stream().num_frames() == 0
          ? formats_.rev_proc_format.num_frames()
          : formats_.api_format.reverse_output_stream().num_frames();
  if (formats_.api_format.reverse_input_stream().num_channels() > 0) {
    render_.render_audio.reset(new AudioBuffer(
        formats_.api_format.reverse_input_stream().num_frames(),
        formats_.api_format.reverse_input_stream().num_channels(),
        formats_.rev_proc_format.num_frames(),
        formats_.rev_proc_format.num_channels(),
        rev_audio_buffer_out_num_frames));
    if (rev_conversion_needed()) {
      render_.render_converter = AudioConverter::Create(
          formats_.api_format.reverse_input_stream().num_channels(),
          formats_.api_format.reverse_input_stream().num_frames(),
          formats_.api_format.reverse_output_stream().num_channels(),
          formats_.api_format.reverse_output_stream().num_frames());
    } else {
      render_.render_converter.reset(nullptr);
    }
  } else {
    render_.render_audio.reset(nullptr);
    render_.render_converter.reset(nullptr);
  }
  capture_.capture_audio.reset(
      new AudioBuffer(formats_.api_format.input_stream().num_frames(),
                      formats_.api_format.input_stream().num_channels(),
                      capture_nonlocked_.fwd_proc_format.num_frames(),
                      fwd_audio_buffer_channels,
                      formats_.api_format.output_stream().num_frames()));

  InitializeGainController();
  InitializeEchoCanceller();
  InitializeEchoControlMobile();
  InitializeExperimentalAgc();
  InitializeTransient();
  InitializeBeamformer();
  InitializeIntelligibility();
  InitializeHighPassFilter();
  InitializeNoiseSuppression();
  InitializeLevelEstimator();
  InitializeVoiceDetection();

#ifdef WEBRTC_AUDIOPROC_DEBUG_DUMP
  if (debug_dump_.debug_file->is_open()) {
    int err = WriteInitMessage();
    if (err != kNoError) {
      return err;
    }
  }
#endif

  return kNoError;
}

int AudioProcessingImpl::InitializeLocked(const ProcessingConfig& config) {
  for (const auto& stream : config.streams) {
    if (stream.num_channels() > 0 && stream.sample_rate_hz() <= 0) {
      return kBadSampleRateError;
    }
  }

  const size_t num_in_channels = config.input_stream().num_channels();
  const size_t num_out_channels = config.output_stream().num_channels();

  // Need at least one input channel.
  // Need either one output channel or as many outputs as there are inputs.
  if (num_in_channels == 0 ||
      !(num_out_channels == 1 || num_out_channels == num_in_channels)) {
    return kBadNumberChannelsError;
  }

  if (capture_nonlocked_.beamformer_enabled &&
      num_in_channels != capture_.array_geometry.size()) {
    return kBadNumberChannelsError;
  }

  formats_.api_format = config;

  capture_nonlocked_.fwd_proc_format = StreamConfig(ClosestHigherNativeRate(
      std::min(formats_.api_format.input_stream().sample_rate_hz(),
               formats_.api_format.output_stream().sample_rate_hz())));

  int rev_proc_rate = ClosestHigherNativeRate(std::min(
      formats_.api_format.reverse_input_stream().sample_rate_hz(),
      formats_.api_format.reverse_output_stream().sample_rate_hz()));
  // TODO(aluebs): Remove this restriction once we figure out why the 3-band
  // splitting filter degrades the AEC performance.
  if (rev_proc_rate > kSampleRate32kHz) {
    rev_proc_rate = is_rev_processed() ? kSampleRate32kHz : kSampleRate16kHz;
  }
  // If the forward sample rate is 8 kHz, the reverse stream is also processed
  // at this rate.
  if (capture_nonlocked_.fwd_proc_format.sample_rate_hz() == kSampleRate8kHz) {
    rev_proc_rate = kSampleRate8kHz;
  } else {
    rev_proc_rate = std::max(rev_proc_rate, static_cast<int>(kSampleRate16kHz));
  }

  // Always downmix the reverse stream to mono for analysis. This has been
  // demonstrated to work well for AEC in most practical scenarios.
  formats_.rev_proc_format = StreamConfig(rev_proc_rate, 1);

  if (capture_nonlocked_.fwd_proc_format.sample_rate_hz() == kSampleRate32kHz ||
      capture_nonlocked_.fwd_proc_format.sample_rate_hz() == kSampleRate48kHz) {
    capture_nonlocked_.split_rate = kSampleRate16kHz;
  } else {
    capture_nonlocked_.split_rate =
        capture_nonlocked_.fwd_proc_format.sample_rate_hz();
  }

  return InitializeLocked();
}

void AudioProcessingImpl::SetExtraOptions(const Config& config) {
  // Run in a single-threaded manner when setting the extra options.
  rtc::CritScope cs_render(&crit_render_);
  rtc::CritScope cs_capture(&crit_capture_);

  public_submodules_->echo_cancellation->SetExtraOptions(config);

  if (capture_.transient_suppressor_enabled !=
      config.Get<ExperimentalNs>().enabled) {
    capture_.transient_suppressor_enabled =
        config.Get<ExperimentalNs>().enabled;
    InitializeTransient();
  }

  if(capture_nonlocked_.intelligibility_enabled !=
     config.Get<Intelligibility>().enabled) {
    capture_nonlocked_.intelligibility_enabled =
        config.Get<Intelligibility>().enabled;
    InitializeIntelligibility();
  }

#ifdef WEBRTC_ANDROID_PLATFORM_BUILD
  if (capture_nonlocked_.beamformer_enabled !=
          config.Get<Beamforming>().enabled) {
    capture_nonlocked_.beamformer_enabled = config.Get<Beamforming>().enabled;
    if (config.Get<Beamforming>().array_geometry.size() > 1) {
      capture_.array_geometry = config.Get<Beamforming>().array_geometry;
    }
    capture_.target_direction = config.Get<Beamforming>().target_direction;
    InitializeBeamformer();
  }
#endif  // WEBRTC_ANDROID_PLATFORM_BUILD
}

int AudioProcessingImpl::proc_sample_rate_hz() const {
  // Used as callback from submodules, hence locking is not allowed.
  return capture_nonlocked_.fwd_proc_format.sample_rate_hz();
}

int AudioProcessingImpl::proc_split_sample_rate_hz() const {
  // Used as callback from submodules, hence locking is not allowed.
  return capture_nonlocked_.split_rate;
}

size_t AudioProcessingImpl::num_reverse_channels() const {
  // Used as callback from submodules, hence locking is not allowed.
  return formats_.rev_proc_format.num_channels();
}

size_t AudioProcessingImpl::num_input_channels() const {
  // Used as callback from submodules, hence locking is not allowed.
  return formats_.api_format.input_stream().num_channels();
}

size_t AudioProcessingImpl::num_proc_channels() const {
  // Used as callback from submodules, hence locking is not allowed.
  return capture_nonlocked_.beamformer_enabled ? 1 : num_output_channels();
}

size_t AudioProcessingImpl::num_output_channels() const {
  // Used as callback from submodules, hence locking is not allowed.
  return formats_.api_format.output_stream().num_channels();
}

void AudioProcessingImpl::set_output_will_be_muted(bool muted) {
  rtc::CritScope cs(&crit_capture_);
  capture_.output_will_be_muted = muted;
  if (private_submodules_->agc_manager.get()) {
    private_submodules_->agc_manager->SetCaptureMuted(
        capture_.output_will_be_muted);
  }
}


int AudioProcessingImpl::ProcessStream(const float* const* src,
                                       size_t samples_per_channel,
                                       int input_sample_rate_hz,
                                       ChannelLayout input_layout,
                                       int output_sample_rate_hz,
                                       ChannelLayout output_layout,
                                       float* const* dest) {
  TRACE_EVENT0("webrtc", "AudioProcessing::ProcessStream_ChannelLayout");
  StreamConfig input_stream;
  StreamConfig output_stream;
  {
    // Access the formats_.api_format.input_stream beneath the capture lock.
    // The lock must be released as it is later required in the call
    // to ProcessStream(,,,);
    rtc::CritScope cs(&crit_capture_);
    input_stream = formats_.api_format.input_stream();
    output_stream = formats_.api_format.output_stream();
  }

  input_stream.set_sample_rate_hz(input_sample_rate_hz);
  input_stream.set_num_channels(ChannelsFromLayout(input_layout));
  input_stream.set_has_keyboard(LayoutHasKeyboard(input_layout));
  output_stream.set_sample_rate_hz(output_sample_rate_hz);
  output_stream.set_num_channels(ChannelsFromLayout(output_layout));
  output_stream.set_has_keyboard(LayoutHasKeyboard(output_layout));

  if (samples_per_channel != input_stream.num_frames()) {
    return kBadDataLengthError;
  }
  return ProcessStream(src, input_stream, output_stream, dest);
}

int AudioProcessingImpl::ProcessStream(const float* const* src,
                                       const StreamConfig& input_config,
                                       const StreamConfig& output_config,
                                       float* const* dest) {
  TRACE_EVENT0("webrtc", "AudioProcessing::ProcessStream_StreamConfig");
  ProcessingConfig processing_config;
  {
    // Acquire the capture lock in order to safely call the function
    // that retrieves the render side data. This function accesses apm
    // getters that need the capture lock held when being called.
    rtc::CritScope cs_capture(&crit_capture_);
    public_submodules_->echo_cancellation->ReadQueuedRenderData();
    public_submodules_->echo_control_mobile->ReadQueuedRenderData();
    public_submodules_->gain_control->ReadQueuedRenderData();

    if (!src || !dest) {
      return kNullPointerError;
    }

    processing_config = formats_.api_format;
  }

  processing_config.input_stream() = input_config;
  processing_config.output_stream() = output_config;

  {
    // Do conditional reinitialization.
    rtc::CritScope cs_render(&crit_render_);
    RETURN_ON_ERR(MaybeInitializeCapture(processing_config));
  }
  rtc::CritScope cs_capture(&crit_capture_);
  assert(processing_config.input_stream().num_frames() ==
         formats_.api_format.input_stream().num_frames());

#ifdef WEBRTC_AUDIOPROC_DEBUG_DUMP
  if (debug_dump_.debug_file->is_open()) {
    RETURN_ON_ERR(WriteConfigMessage(false));

    debug_dump_.capture.event_msg->set_type(audioproc::Event::STREAM);
    audioproc::Stream* msg = debug_dump_.capture.event_msg->mutable_stream();
    const size_t channel_size =
        sizeof(float) * formats_.api_format.input_stream().num_frames();
    for (size_t i = 0; i < formats_.api_format.input_stream().num_channels();
         ++i)
      msg->add_input_channel(src[i], channel_size);
  }
#endif

  capture_.capture_audio->CopyFrom(src, formats_.api_format.input_stream());
  RETURN_ON_ERR(ProcessStreamLocked());
  capture_.capture_audio->CopyTo(formats_.api_format.output_stream(), dest);

#ifdef WEBRTC_AUDIOPROC_DEBUG_DUMP
  if (debug_dump_.debug_file->is_open()) {
    audioproc::Stream* msg = debug_dump_.capture.event_msg->mutable_stream();
    const size_t channel_size =
        sizeof(float) * formats_.api_format.output_stream().num_frames();
    for (size_t i = 0; i < formats_.api_format.output_stream().num_channels();
         ++i)
      msg->add_output_channel(dest[i], channel_size);
    RETURN_ON_ERR(WriteMessageToDebugFile(debug_dump_.debug_file.get(),
                                          &debug_dump_.num_bytes_left_for_log_,
                                          &crit_debug_, &debug_dump_.capture));
  }
#endif

  return kNoError;
}

int AudioProcessingImpl::ProcessStream(AudioFrame* frame) {
  TRACE_EVENT0("webrtc", "AudioProcessing::ProcessStream_AudioFrame");
  {
    // Acquire the capture lock in order to safely call the function
    // that retrieves the render side data. This function accesses apm
    // getters that need the capture lock held when being called.
    // The lock needs to be released as
    // public_submodules_->echo_control_mobile->is_enabled() aquires this lock
    // as well.
    rtc::CritScope cs_capture(&crit_capture_);
    public_submodules_->echo_cancellation->ReadQueuedRenderData();
    public_submodules_->echo_control_mobile->ReadQueuedRenderData();
    public_submodules_->gain_control->ReadQueuedRenderData();
  }

  if (!frame) {
    return kNullPointerError;
  }
  // Must be a native rate.
  if (frame->sample_rate_hz_ != kSampleRate8kHz &&
      frame->sample_rate_hz_ != kSampleRate16kHz &&
      frame->sample_rate_hz_ != kSampleRate32kHz &&
      frame->sample_rate_hz_ != kSampleRate48kHz) {
    return kBadSampleRateError;
  }

  ProcessingConfig processing_config;
  {
    // Aquire lock for the access of api_format.
    // The lock is released immediately due to the conditional
    // reinitialization.
    rtc::CritScope cs_capture(&crit_capture_);
    // TODO(ajm): The input and output rates and channels are currently
    // constrained to be identical in the int16 interface.
    processing_config = formats_.api_format;
  }
  processing_config.input_stream().set_sample_rate_hz(frame->sample_rate_hz_);
  processing_config.input_stream().set_num_channels(frame->num_channels_);
  processing_config.output_stream().set_sample_rate_hz(frame->sample_rate_hz_);
  processing_config.output_stream().set_num_channels(frame->num_channels_);

  {
    // Do conditional reinitialization.
    rtc::CritScope cs_render(&crit_render_);
    RETURN_ON_ERR(MaybeInitializeCapture(processing_config));
  }
  rtc::CritScope cs_capture(&crit_capture_);
  if (frame->samples_per_channel_ !=
      formats_.api_format.input_stream().num_frames()) {
    return kBadDataLengthError;
  }

#ifdef WEBRTC_AUDIOPROC_DEBUG_DUMP
  if (debug_dump_.debug_file->is_open()) {
    debug_dump_.capture.event_msg->set_type(audioproc::Event::STREAM);
    audioproc::Stream* msg = debug_dump_.capture.event_msg->mutable_stream();
    const size_t data_size =
        sizeof(int16_t) * frame->samples_per_channel_ * frame->num_channels_;
    msg->set_input_data(frame->data_, data_size);
  }
#endif

  capture_.capture_audio->DeinterleaveFrom(frame);
  RETURN_ON_ERR(ProcessStreamLocked());
  capture_.capture_audio->InterleaveTo(frame, output_copy_needed());

#ifdef WEBRTC_AUDIOPROC_DEBUG_DUMP
  if (debug_dump_.debug_file->is_open()) {
    audioproc::Stream* msg = debug_dump_.capture.event_msg->mutable_stream();
    const size_t data_size =
        sizeof(int16_t) * frame->samples_per_channel_ * frame->num_channels_;
    msg->set_output_data(frame->data_, data_size);
    RETURN_ON_ERR(WriteMessageToDebugFile(debug_dump_.debug_file.get(),
                                          &debug_dump_.num_bytes_left_for_log_,
                                          &crit_debug_, &debug_dump_.capture));
  }
#endif

  return kNoError;
}

int AudioProcessingImpl::ProcessStreamLocked() {
  // Ensure that not both the AEC and AECM are active at the same time.
  // TODO(peah): Simplify once the public API Enable functions for these
  // are moved to APM.
  RTC_DCHECK(!(public_submodules_->echo_cancellation->is_enabled() &&
               public_submodules_->echo_control_mobile->is_enabled()));

#ifdef WEBRTC_AUDIOPROC_DEBUG_DUMP
  if (debug_dump_.debug_file->is_open()) {
    audioproc::Stream* msg = debug_dump_.capture.event_msg->mutable_stream();
    msg->set_delay(capture_nonlocked_.stream_delay_ms);
    msg->set_drift(
        public_submodules_->echo_cancellation->stream_drift_samples());
    msg->set_level(gain_control()->stream_analog_level());
    msg->set_keypress(capture_.key_pressed);
  }
#endif

  MaybeUpdateHistograms();

  AudioBuffer* ca = capture_.capture_audio.get();  // For brevity.

  if (constants_.use_experimental_agc &&
      public_submodules_->gain_control->is_enabled()) {
    private_submodules_->agc_manager->AnalyzePreProcess(
        ca->channels()[0], ca->num_channels(),
        capture_nonlocked_.fwd_proc_format.num_frames());
  }

  if (fwd_analysis_needed()) {
    ca->SplitIntoFrequencyBands();
  }

  if (capture_nonlocked_.beamformer_enabled) {
    private_submodules_->beamformer->ProcessChunk(*ca->split_data_f(),
                                                  ca->split_data_f());
    ca->set_num_channels(1);
  }

  public_submodules_->high_pass_filter->ProcessCaptureAudio(ca);
  RETURN_ON_ERR(public_submodules_->gain_control->AnalyzeCaptureAudio(ca));
  public_submodules_->noise_suppression->AnalyzeCaptureAudio(ca);

  // Ensure that the stream delay was set before the call to the
  // AEC ProcessCaptureAudio function.
  if (public_submodules_->echo_cancellation->is_enabled() &&
      !was_stream_delay_set()) {
    return AudioProcessing::kStreamParameterNotSetError;
  }

  RETURN_ON_ERR(public_submodules_->echo_cancellation->ProcessCaptureAudio(
      ca, stream_delay_ms()));

  if (public_submodules_->echo_control_mobile->is_enabled() &&
      public_submodules_->noise_suppression->is_enabled()) {
    ca->CopyLowPassToReference();
  }
  public_submodules_->noise_suppression->ProcessCaptureAudio(ca);
  if (capture_nonlocked_.intelligibility_enabled) {
    RTC_DCHECK(public_submodules_->noise_suppression->is_enabled());
    int gain_db = public_submodules_->gain_control->is_enabled() ?
                  public_submodules_->gain_control->compression_gain_db() :
                  0;
    public_submodules_->intelligibility_enhancer->SetCaptureNoiseEstimate(
        public_submodules_->noise_suppression->NoiseEstimate(), gain_db);
  }

  // Ensure that the stream delay was set before the call to the
  // AECM ProcessCaptureAudio function.
  if (public_submodules_->echo_control_mobile->is_enabled() &&
      !was_stream_delay_set()) {
    return AudioProcessing::kStreamParameterNotSetError;
  }

  RETURN_ON_ERR(public_submodules_->echo_control_mobile->ProcessCaptureAudio(
      ca, stream_delay_ms()));

  public_submodules_->voice_detection->ProcessCaptureAudio(ca);

  if (constants_.use_experimental_agc &&
      public_submodules_->gain_control->is_enabled() &&
      (!capture_nonlocked_.beamformer_enabled ||
       private_submodules_->beamformer->is_target_present())) {
    private_submodules_->agc_manager->Process(
        ca->split_bands_const(0)[kBand0To8kHz], ca->num_frames_per_band(),
        capture_nonlocked_.split_rate);
  }
  RETURN_ON_ERR(public_submodules_->gain_control->ProcessCaptureAudio(
      ca, echo_cancellation()->stream_has_echo()));

  if (fwd_synthesis_needed()) {
    ca->MergeFrequencyBands();
  }

  // TODO(aluebs): Investigate if the transient suppression placement should be
  // before or after the AGC.
  if (capture_.transient_suppressor_enabled) {
    float voice_probability =
        private_submodules_->agc_manager.get()
            ? private_submodules_->agc_manager->voice_probability()
            : 1.f;

    public_submodules_->transient_suppressor->Suppress(
        ca->channels_f()[0], ca->num_frames(), ca->num_channels(),
        ca->split_bands_const_f(0)[kBand0To8kHz], ca->num_frames_per_band(),
        ca->keyboard_data(), ca->num_keyboard_frames(), voice_probability,
        capture_.key_pressed);
  }

  // The level estimator operates on the recombined data.
  public_submodules_->level_estimator->ProcessStream(ca);

  capture_.was_stream_delay_set = false;
  return kNoError;
}

int AudioProcessingImpl::AnalyzeReverseStream(const float* const* data,
                                              size_t samples_per_channel,
                                              int rev_sample_rate_hz,
                                              ChannelLayout layout) {
  TRACE_EVENT0("webrtc", "AudioProcessing::AnalyzeReverseStream_ChannelLayout");
  rtc::CritScope cs(&crit_render_);
  const StreamConfig reverse_config = {
      rev_sample_rate_hz, ChannelsFromLayout(layout), LayoutHasKeyboard(layout),
  };
  if (samples_per_channel != reverse_config.num_frames()) {
    return kBadDataLengthError;
  }
  return AnalyzeReverseStreamLocked(data, reverse_config, reverse_config);
}

int AudioProcessingImpl::ProcessReverseStream(
    const float* const* src,
    const StreamConfig& reverse_input_config,
    const StreamConfig& reverse_output_config,
    float* const* dest) {
  TRACE_EVENT0("webrtc", "AudioProcessing::ProcessReverseStream_StreamConfig");
  rtc::CritScope cs(&crit_render_);
  RETURN_ON_ERR(AnalyzeReverseStreamLocked(src, reverse_input_config,
                                           reverse_output_config));
  if (is_rev_processed()) {
    render_.render_audio->CopyTo(formats_.api_format.reverse_output_stream(),
                                 dest);
  } else if (render_check_rev_conversion_needed()) {
    render_.render_converter->Convert(src, reverse_input_config.num_samples(),
                                      dest,
                                      reverse_output_config.num_samples());
  } else {
    CopyAudioIfNeeded(src, reverse_input_config.num_frames(),
                      reverse_input_config.num_channels(), dest);
  }

  return kNoError;
}

int AudioProcessingImpl::AnalyzeReverseStreamLocked(
    const float* const* src,
    const StreamConfig& reverse_input_config,
    const StreamConfig& reverse_output_config) {
  if (src == nullptr) {
    return kNullPointerError;
  }

  if (reverse_input_config.num_channels() == 0) {
    return kBadNumberChannelsError;
  }

  ProcessingConfig processing_config = formats_.api_format;
  processing_config.reverse_input_stream() = reverse_input_config;
  processing_config.reverse_output_stream() = reverse_output_config;

  RETURN_ON_ERR(MaybeInitializeRender(processing_config));
  assert(reverse_input_config.num_frames() ==
         formats_.api_format.reverse_input_stream().num_frames());

#ifdef WEBRTC_AUDIOPROC_DEBUG_DUMP
  if (debug_dump_.debug_file->is_open()) {
    debug_dump_.render.event_msg->set_type(audioproc::Event::REVERSE_STREAM);
    audioproc::ReverseStream* msg =
        debug_dump_.render.event_msg->mutable_reverse_stream();
    const size_t channel_size =
        sizeof(float) * formats_.api_format.reverse_input_stream().num_frames();
    for (size_t i = 0;
         i < formats_.api_format.reverse_input_stream().num_channels(); ++i)
      msg->add_channel(src[i], channel_size);
    RETURN_ON_ERR(WriteMessageToDebugFile(debug_dump_.debug_file.get(),
                                          &debug_dump_.num_bytes_left_for_log_,
                                          &crit_debug_, &debug_dump_.render));
  }
#endif

  render_.render_audio->CopyFrom(src,
                                 formats_.api_format.reverse_input_stream());
  return ProcessReverseStreamLocked();
}

int AudioProcessingImpl::ProcessReverseStream(AudioFrame* frame) {
  TRACE_EVENT0("webrtc", "AudioProcessing::ProcessReverseStream_AudioFrame");
  rtc::CritScope cs(&crit_render_);
  if (frame == nullptr) {
    return kNullPointerError;
  }
  // Must be a native rate.
  if (frame->sample_rate_hz_ != kSampleRate8kHz &&
      frame->sample_rate_hz_ != kSampleRate16kHz &&
      frame->sample_rate_hz_ != kSampleRate32kHz &&
      frame->sample_rate_hz_ != kSampleRate48kHz) {
    return kBadSampleRateError;
  }

  if (frame->num_channels_ <= 0) {
    return kBadNumberChannelsError;
  }

  ProcessingConfig processing_config = formats_.api_format;
  processing_config.reverse_input_stream().set_sample_rate_hz(
      frame->sample_rate_hz_);
  processing_config.reverse_input_stream().set_num_channels(
      frame->num_channels_);
  processing_config.reverse_output_stream().set_sample_rate_hz(
      frame->sample_rate_hz_);
  processing_config.reverse_output_stream().set_num_channels(
      frame->num_channels_);

  RETURN_ON_ERR(MaybeInitializeRender(processing_config));
  if (frame->samples_per_channel_ !=
      formats_.api_format.reverse_input_stream().num_frames()) {
    return kBadDataLengthError;
  }

#ifdef WEBRTC_AUDIOPROC_DEBUG_DUMP
  if (debug_dump_.debug_file->is_open()) {
    debug_dump_.render.event_msg->set_type(audioproc::Event::REVERSE_STREAM);
    audioproc::ReverseStream* msg =
        debug_dump_.render.event_msg->mutable_reverse_stream();
    const size_t data_size =
        sizeof(int16_t) * frame->samples_per_channel_ * frame->num_channels_;
    msg->set_data(frame->data_, data_size);
    RETURN_ON_ERR(WriteMessageToDebugFile(debug_dump_.debug_file.get(),
                                          &debug_dump_.num_bytes_left_for_log_,
                                          &crit_debug_, &debug_dump_.render));
  }
#endif
  render_.render_audio->DeinterleaveFrom(frame);
  RETURN_ON_ERR(ProcessReverseStreamLocked());
  if (is_rev_processed()) {
    render_.render_audio->InterleaveTo(frame, true);
  }
  return kNoError;
}

int AudioProcessingImpl::ProcessReverseStreamLocked() {
  AudioBuffer* ra = render_.render_audio.get();  // For brevity.
  if (rev_analysis_needed()) {
    ra->SplitIntoFrequencyBands();
  }

  if (capture_nonlocked_.intelligibility_enabled) {
    public_submodules_->intelligibility_enhancer->ProcessRenderAudio(
        ra->split_channels_f(kBand0To8kHz), capture_nonlocked_.split_rate,
        ra->num_channels());
  }

  RETURN_ON_ERR(public_submodules_->echo_cancellation->ProcessRenderAudio(ra));
  RETURN_ON_ERR(
      public_submodules_->echo_control_mobile->ProcessRenderAudio(ra));
  if (!constants_.use_experimental_agc) {
    RETURN_ON_ERR(public_submodules_->gain_control->ProcessRenderAudio(ra));
  }

  if (rev_synthesis_needed()) {
    ra->MergeFrequencyBands();
  }

  return kNoError;
}

int AudioProcessingImpl::set_stream_delay_ms(int delay) {
  rtc::CritScope cs(&crit_capture_);
  Error retval = kNoError;
  capture_.was_stream_delay_set = true;
  delay += capture_.delay_offset_ms;

  if (delay < 0) {
    delay = 0;
    retval = kBadStreamParameterWarning;
  }

  // TODO(ajm): the max is rather arbitrarily chosen; investigate.
  if (delay > 500) {
    delay = 500;
    retval = kBadStreamParameterWarning;
  }

  capture_nonlocked_.stream_delay_ms = delay;
  return retval;
}

int AudioProcessingImpl::stream_delay_ms() const {
  // Used as callback from submodules, hence locking is not allowed.
  return capture_nonlocked_.stream_delay_ms;
}

bool AudioProcessingImpl::was_stream_delay_set() const {
  // Used as callback from submodules, hence locking is not allowed.
  return capture_.was_stream_delay_set;
}

void AudioProcessingImpl::set_stream_key_pressed(bool key_pressed) {
  rtc::CritScope cs(&crit_capture_);
  capture_.key_pressed = key_pressed;
}

void AudioProcessingImpl::set_delay_offset_ms(int offset) {
  rtc::CritScope cs(&crit_capture_);
  capture_.delay_offset_ms = offset;
}

int AudioProcessingImpl::delay_offset_ms() const {
  rtc::CritScope cs(&crit_capture_);
  return capture_.delay_offset_ms;
}

int AudioProcessingImpl::StartDebugRecording(
    const char filename[AudioProcessing::kMaxFilenameSize],
    int64_t max_log_size_bytes) {
  // Run in a single-threaded manner.
  rtc::CritScope cs_render(&crit_render_);
  rtc::CritScope cs_capture(&crit_capture_);
  static_assert(kMaxFilenameSize == FileWrapper::kMaxFileNameSize, "");

  if (filename == nullptr) {
    return kNullPointerError;
  }

#ifdef WEBRTC_AUDIOPROC_DEBUG_DUMP
  debug_dump_.num_bytes_left_for_log_ = max_log_size_bytes;
  // Stop any ongoing recording.
  debug_dump_.debug_file->CloseFile();

  if (!debug_dump_.debug_file->OpenFile(filename, false)) {
    return kFileError;
  }

  RETURN_ON_ERR(WriteConfigMessage(true));
  RETURN_ON_ERR(WriteInitMessage());
  return kNoError;
#else
  return kUnsupportedFunctionError;
#endif  // WEBRTC_AUDIOPROC_DEBUG_DUMP
}

int AudioProcessingImpl::StartDebugRecording(FILE* handle,
                                             int64_t max_log_size_bytes) {
  // Run in a single-threaded manner.
  rtc::CritScope cs_render(&crit_render_);
  rtc::CritScope cs_capture(&crit_capture_);

  if (handle == nullptr) {
    return kNullPointerError;
  }

#ifdef WEBRTC_AUDIOPROC_DEBUG_DUMP
  debug_dump_.num_bytes_left_for_log_ = max_log_size_bytes;

  // Stop any ongoing recording.
  debug_dump_.debug_file->CloseFile();

  if (!debug_dump_.debug_file->OpenFromFileHandle(handle)) {
    return kFileError;
  }

  RETURN_ON_ERR(WriteConfigMessage(true));
  RETURN_ON_ERR(WriteInitMessage());
  return kNoError;
#else
  return kUnsupportedFunctionError;
#endif  // WEBRTC_AUDIOPROC_DEBUG_DUMP
}

int AudioProcessingImpl::StartDebugRecordingForPlatformFile(
    rtc::PlatformFile handle) {
  // Run in a single-threaded manner.
  rtc::CritScope cs_render(&crit_render_);
  rtc::CritScope cs_capture(&crit_capture_);
  FILE* stream = rtc::FdopenPlatformFileForWriting(handle);
  return StartDebugRecording(stream, -1);
}

int AudioProcessingImpl::StopDebugRecording() {
  // Run in a single-threaded manner.
  rtc::CritScope cs_render(&crit_render_);
  rtc::CritScope cs_capture(&crit_capture_);

#ifdef WEBRTC_AUDIOPROC_DEBUG_DUMP
  // We just return if recording hasn't started.
  debug_dump_.debug_file->CloseFile();
  return kNoError;
#else
  return kUnsupportedFunctionError;
#endif  // WEBRTC_AUDIOPROC_DEBUG_DUMP
}

EchoCancellation* AudioProcessingImpl::echo_cancellation() const {
  // Adding a lock here has no effect as it allows any access to the submodule
  // from the returned pointer.
  return public_submodules_->echo_cancellation.get();
}

EchoControlMobile* AudioProcessingImpl::echo_control_mobile() const {
  // Adding a lock here has no effect as it allows any access to the submodule
  // from the returned pointer.
  return public_submodules_->echo_control_mobile.get();
}

GainControl* AudioProcessingImpl::gain_control() const {
  // Adding a lock here has no effect as it allows any access to the submodule
  // from the returned pointer.
  if (constants_.use_experimental_agc) {
    return public_submodules_->gain_control_for_experimental_agc.get();
  }
  return public_submodules_->gain_control.get();
}

HighPassFilter* AudioProcessingImpl::high_pass_filter() const {
  // Adding a lock here has no effect as it allows any access to the submodule
  // from the returned pointer.
  return public_submodules_->high_pass_filter.get();
}

LevelEstimator* AudioProcessingImpl::level_estimator() const {
  // Adding a lock here has no effect as it allows any access to the submodule
  // from the returned pointer.
  return public_submodules_->level_estimator.get();
}

NoiseSuppression* AudioProcessingImpl::noise_suppression() const {
  // Adding a lock here has no effect as it allows any access to the submodule
  // from the returned pointer.
  return public_submodules_->noise_suppression.get();
}

VoiceDetection* AudioProcessingImpl::voice_detection() const {
  // Adding a lock here has no effect as it allows any access to the submodule
  // from the returned pointer.
  return public_submodules_->voice_detection.get();
}

bool AudioProcessingImpl::is_fwd_processed() const {
  // The beamformer, noise suppressor and highpass filter
  // modify the data.
  if (capture_nonlocked_.beamformer_enabled ||
      public_submodules_->high_pass_filter->is_enabled() ||
      public_submodules_->noise_suppression->is_enabled() ||
      public_submodules_->echo_cancellation->is_enabled() ||
      public_submodules_->echo_control_mobile->is_enabled() ||
      public_submodules_->gain_control->is_enabled()) {
    return true;
  }

  // The capture data is otherwise unchanged.
  return false;
}

bool AudioProcessingImpl::output_copy_needed() const {
  // Check if we've upmixed or downmixed the audio.
  return ((formats_.api_format.output_stream().num_channels() !=
           formats_.api_format.input_stream().num_channels()) ||
          is_fwd_processed() || capture_.transient_suppressor_enabled);
}

bool AudioProcessingImpl::fwd_synthesis_needed() const {
  return (is_fwd_processed() &&
          is_multi_band(capture_nonlocked_.fwd_proc_format.sample_rate_hz()));
}

bool AudioProcessingImpl::fwd_analysis_needed() const {
  if (!is_fwd_processed() &&
      !public_submodules_->voice_detection->is_enabled() &&
      !capture_.transient_suppressor_enabled) {
    // Only public_submodules_->level_estimator is enabled.
    return false;
  } else if (is_multi_band(
                 capture_nonlocked_.fwd_proc_format.sample_rate_hz())) {
    // Something besides public_submodules_->level_estimator is enabled, and we
    // have super-wb.
    return true;
  }
  return false;
}

bool AudioProcessingImpl::is_rev_processed() const {
  return capture_nonlocked_.intelligibility_enabled;
}

bool AudioProcessingImpl::rev_synthesis_needed() const {
  return (is_rev_processed() &&
          is_multi_band(formats_.rev_proc_format.sample_rate_hz()));
}

bool AudioProcessingImpl::rev_analysis_needed() const {
  return is_multi_band(formats_.rev_proc_format.sample_rate_hz()) &&
         (is_rev_processed() ||
          public_submodules_->echo_cancellation
              ->is_enabled_render_side_query() ||
          public_submodules_->echo_control_mobile
              ->is_enabled_render_side_query() ||
          public_submodules_->gain_control->is_enabled_render_side_query());
}

bool AudioProcessingImpl::render_check_rev_conversion_needed() const {
  return rev_conversion_needed();
}

bool AudioProcessingImpl::rev_conversion_needed() const {
  return (formats_.api_format.reverse_input_stream() !=
          formats_.api_format.reverse_output_stream());
}

void AudioProcessingImpl::InitializeExperimentalAgc() {
  if (constants_.use_experimental_agc) {
    if (!private_submodules_->agc_manager.get()) {
      private_submodules_->agc_manager.reset(new AgcManagerDirect(
          public_submodules_->gain_control.get(),
          public_submodules_->gain_control_for_experimental_agc.get(),
          constants_.agc_startup_min_volume));
    }
    private_submodules_->agc_manager->Initialize();
    private_submodules_->agc_manager->SetCaptureMuted(
        capture_.output_will_be_muted);
  }
}

void AudioProcessingImpl::InitializeTransient() {
  if (capture_.transient_suppressor_enabled) {
    if (!public_submodules_->transient_suppressor.get()) {
      public_submodules_->transient_suppressor.reset(new TransientSuppressor());
    }
    public_submodules_->transient_suppressor->Initialize(
        capture_nonlocked_.fwd_proc_format.sample_rate_hz(),
        capture_nonlocked_.split_rate,
        num_proc_channels());
  }
}

void AudioProcessingImpl::InitializeBeamformer() {
  if (capture_nonlocked_.beamformer_enabled) {
    if (!private_submodules_->beamformer) {
      private_submodules_->beamformer.reset(new NonlinearBeamformer(
          capture_.array_geometry, capture_.target_direction));
    }
    private_submodules_->beamformer->Initialize(kChunkSizeMs,
                                                capture_nonlocked_.split_rate);
  }
}

void AudioProcessingImpl::InitializeIntelligibility() {
  if (capture_nonlocked_.intelligibility_enabled) {
    public_submodules_->intelligibility_enhancer.reset(
        new IntelligibilityEnhancer(capture_nonlocked_.split_rate,
                                    render_.render_audio->num_channels(),
                                    NoiseSuppressionImpl::num_noise_bins()));
  }
}

void AudioProcessingImpl::InitializeHighPassFilter() {
  public_submodules_->high_pass_filter->Initialize(num_proc_channels(),
                                                   proc_sample_rate_hz());
}

void AudioProcessingImpl::InitializeNoiseSuppression() {
  public_submodules_->noise_suppression->Initialize(num_proc_channels(),
                                                    proc_sample_rate_hz());
}

void AudioProcessingImpl::InitializeEchoCanceller() {
  public_submodules_->echo_cancellation->Initialize(
      proc_sample_rate_hz(), num_reverse_channels(), num_output_channels(),
      num_proc_channels());
}

void AudioProcessingImpl::InitializeGainController() {
  public_submodules_->gain_control->Initialize(num_proc_channels(),
                                               proc_sample_rate_hz());
}

void AudioProcessingImpl::InitializeEchoControlMobile() {
  public_submodules_->echo_control_mobile->Initialize(
      proc_split_sample_rate_hz(),
      num_reverse_channels(),
      num_output_channels());
}

void AudioProcessingImpl::InitializeLevelEstimator() {
  public_submodules_->level_estimator->Initialize();
}

void AudioProcessingImpl::InitializeVoiceDetection() {
  public_submodules_->voice_detection->Initialize(proc_split_sample_rate_hz());
}

void AudioProcessingImpl::MaybeUpdateHistograms() {
  static const int kMinDiffDelayMs = 60;

  if (echo_cancellation()->is_enabled()) {
    // Activate delay_jumps_ counters if we know echo_cancellation is runnning.
    // If a stream has echo we know that the echo_cancellation is in process.
    if (capture_.stream_delay_jumps == -1 &&
        echo_cancellation()->stream_has_echo()) {
      capture_.stream_delay_jumps = 0;
    }
    if (capture_.aec_system_delay_jumps == -1 &&
        echo_cancellation()->stream_has_echo()) {
      capture_.aec_system_delay_jumps = 0;
    }

    // Detect a jump in platform reported system delay and log the difference.
    const int diff_stream_delay_ms =
        capture_nonlocked_.stream_delay_ms - capture_.last_stream_delay_ms;
    if (diff_stream_delay_ms > kMinDiffDelayMs &&
        capture_.last_stream_delay_ms != 0) {
      RTC_HISTOGRAM_COUNTS("WebRTC.Audio.PlatformReportedStreamDelayJump",
                           diff_stream_delay_ms, kMinDiffDelayMs, 1000, 100);
      if (capture_.stream_delay_jumps == -1) {
        capture_.stream_delay_jumps = 0;  // Activate counter if needed.
      }
      capture_.stream_delay_jumps++;
    }
    capture_.last_stream_delay_ms = capture_nonlocked_.stream_delay_ms;

    // Detect a jump in AEC system delay and log the difference.
    const int samples_per_ms =
        rtc::CheckedDivExact(capture_nonlocked_.split_rate, 1000);
    RTC_DCHECK_LT(0, samples_per_ms);
    const int aec_system_delay_ms =
        public_submodules_->echo_cancellation->GetSystemDelayInSamples() /
        samples_per_ms;
    const int diff_aec_system_delay_ms =
        aec_system_delay_ms - capture_.last_aec_system_delay_ms;
    if (diff_aec_system_delay_ms > kMinDiffDelayMs &&
        capture_.last_aec_system_delay_ms != 0) {
      RTC_HISTOGRAM_COUNTS("WebRTC.Audio.AecSystemDelayJump",
                           diff_aec_system_delay_ms, kMinDiffDelayMs, 1000,
                           100);
      if (capture_.aec_system_delay_jumps == -1) {
        capture_.aec_system_delay_jumps = 0;  // Activate counter if needed.
      }
      capture_.aec_system_delay_jumps++;
    }
    capture_.last_aec_system_delay_ms = aec_system_delay_ms;
  }
}

void AudioProcessingImpl::UpdateHistogramsOnCallEnd() {
  // Run in a single-threaded manner.
  rtc::CritScope cs_render(&crit_render_);
  rtc::CritScope cs_capture(&crit_capture_);

  if (capture_.stream_delay_jumps > -1) {
    RTC_HISTOGRAM_ENUMERATION(
        "WebRTC.Audio.NumOfPlatformReportedStreamDelayJumps",
        capture_.stream_delay_jumps, 51);
  }
  capture_.stream_delay_jumps = -1;
  capture_.last_stream_delay_ms = 0;

  if (capture_.aec_system_delay_jumps > -1) {
    RTC_HISTOGRAM_ENUMERATION("WebRTC.Audio.NumOfAecSystemDelayJumps",
                              capture_.aec_system_delay_jumps, 51);
  }
  capture_.aec_system_delay_jumps = -1;
  capture_.last_aec_system_delay_ms = 0;
}

#ifdef WEBRTC_AUDIOPROC_DEBUG_DUMP
int AudioProcessingImpl::WriteMessageToDebugFile(
    FileWrapper* debug_file,
    int64_t* filesize_limit_bytes,
    rtc::CriticalSection* crit_debug,
    ApmDebugDumpThreadState* debug_state) {
  int32_t size = debug_state->event_msg->ByteSize();
  if (size <= 0) {
    return kUnspecifiedError;
  }
#if defined(WEBRTC_ARCH_BIG_ENDIAN)
// TODO(ajm): Use little-endian "on the wire". For the moment, we can be
//            pretty safe in assuming little-endian.
#endif

  if (!debug_state->event_msg->SerializeToString(&debug_state->event_str)) {
    return kUnspecifiedError;
  }

  {
    // Ensure atomic writes of the message.
    rtc::CritScope cs_debug(crit_debug);

    RTC_DCHECK(debug_file->is_open());
    // Update the byte counter.
    if (*filesize_limit_bytes >= 0) {
      *filesize_limit_bytes -=
          (sizeof(int32_t) + debug_state->event_str.length());
      if (*filesize_limit_bytes < 0) {
        // Not enough bytes are left to write this message, so stop logging.
        debug_file->CloseFile();
        return kNoError;
      }
    }
    // Write message preceded by its size.
    if (!debug_file->Write(&size, sizeof(int32_t))) {
      return kFileError;
    }
    if (!debug_file->Write(debug_state->event_str.data(),
                           debug_state->event_str.length())) {
      return kFileError;
    }
  }

  debug_state->event_msg->Clear();

  return kNoError;
}

int AudioProcessingImpl::WriteInitMessage() {
  debug_dump_.capture.event_msg->set_type(audioproc::Event::INIT);
  audioproc::Init* msg = debug_dump_.capture.event_msg->mutable_init();
  msg->set_sample_rate(formats_.api_format.input_stream().sample_rate_hz());

  msg->set_num_input_channels(static_cast<google::protobuf::int32>(
      formats_.api_format.input_stream().num_channels()));
  msg->set_num_output_channels(static_cast<google::protobuf::int32>(
      formats_.api_format.output_stream().num_channels()));
  msg->set_num_reverse_channels(static_cast<google::protobuf::int32>(
      formats_.api_format.reverse_input_stream().num_channels()));
  msg->set_reverse_sample_rate(
      formats_.api_format.reverse_input_stream().sample_rate_hz());
  msg->set_output_sample_rate(
      formats_.api_format.output_stream().sample_rate_hz());
  msg->set_reverse_output_sample_rate(
      formats_.api_format.reverse_output_stream().sample_rate_hz());
  msg->set_num_reverse_output_channels(
      formats_.api_format.reverse_output_stream().num_channels());

  RETURN_ON_ERR(WriteMessageToDebugFile(debug_dump_.debug_file.get(),
                                        &debug_dump_.num_bytes_left_for_log_,
                                        &crit_debug_, &debug_dump_.capture));
  return kNoError;
}

int AudioProcessingImpl::WriteConfigMessage(bool forced) {
  audioproc::Config config;

  config.set_aec_enabled(public_submodules_->echo_cancellation->is_enabled());
  config.set_aec_delay_agnostic_enabled(
      public_submodules_->echo_cancellation->is_delay_agnostic_enabled());
  config.set_aec_drift_compensation_enabled(
      public_submodules_->echo_cancellation->is_drift_compensation_enabled());
  config.set_aec_extended_filter_enabled(
      public_submodules_->echo_cancellation->is_extended_filter_enabled());
  config.set_aec_suppression_level(static_cast<int>(
      public_submodules_->echo_cancellation->suppression_level()));

  config.set_aecm_enabled(
      public_submodules_->echo_control_mobile->is_enabled());
  config.set_aecm_comfort_noise_enabled(
      public_submodules_->echo_control_mobile->is_comfort_noise_enabled());
  config.set_aecm_routing_mode(static_cast<int>(
      public_submodules_->echo_control_mobile->routing_mode()));

  config.set_agc_enabled(public_submodules_->gain_control->is_enabled());
  config.set_agc_mode(
      static_cast<int>(public_submodules_->gain_control->mode()));
  config.set_agc_limiter_enabled(
      public_submodules_->gain_control->is_limiter_enabled());
  config.set_noise_robust_agc_enabled(constants_.use_experimental_agc);

  config.set_hpf_enabled(public_submodules_->high_pass_filter->is_enabled());

  config.set_ns_enabled(public_submodules_->noise_suppression->is_enabled());
  config.set_ns_level(
      static_cast<int>(public_submodules_->noise_suppression->level()));

  config.set_transient_suppression_enabled(
      capture_.transient_suppressor_enabled);
  config.set_intelligibility_enhancer_enabled(
      capture_nonlocked_.intelligibility_enabled);

  std::string experiments_description =
      public_submodules_->echo_cancellation->GetExperimentsDescription();
  // TODO(peah): Add semicolon-separated concatenations of experiment
  // descriptions for other submodules.
  config.set_experiments_description(experiments_description);

  std::string serialized_config = config.SerializeAsString();
  if (!forced &&
      debug_dump_.capture.last_serialized_config == serialized_config) {
    return kNoError;
  }

  debug_dump_.capture.last_serialized_config = serialized_config;

  debug_dump_.capture.event_msg->set_type(audioproc::Event::CONFIG);
  debug_dump_.capture.event_msg->mutable_config()->CopyFrom(config);

  RETURN_ON_ERR(WriteMessageToDebugFile(debug_dump_.debug_file.get(),
                                        &debug_dump_.num_bytes_left_for_log_,
                                        &crit_debug_, &debug_dump_.capture));
  return kNoError;
}
#endif  // WEBRTC_AUDIOPROC_DEBUG_DUMP

}  // namespace webrtc
