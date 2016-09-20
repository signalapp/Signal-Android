/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_processing/audio_processing_impl.h"

#include <algorithm>
#include <memory>
#include <vector>

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/base/array_view.h"
#include "webrtc/base/criticalsection.h"
#include "webrtc/base/event.h"
#include "webrtc/base/platform_thread.h"
#include "webrtc/base/random.h"
#include "webrtc/config.h"
#include "webrtc/modules/audio_processing/test/test_utils.h"
#include "webrtc/modules/include/module_common_types.h"
#include "webrtc/system_wrappers/include/sleep.h"

namespace webrtc {

namespace {

class AudioProcessingImplLockTest;

// Type of the render thread APM API call to use in the test.
enum class RenderApiImpl {
  ProcessReverseStreamImpl1,
  ProcessReverseStreamImpl2,
  AnalyzeReverseStreamImpl
};

// Type of the capture thread APM API call to use in the test.
enum class CaptureApiImpl {
  ProcessStreamImpl1,
  ProcessStreamImpl2,
  ProcessStreamImpl3
};

// The runtime parameter setting scheme to use in the test.
enum class RuntimeParameterSettingScheme {
  SparseStreamMetadataChangeScheme,
  ExtremeStreamMetadataChangeScheme,
  FixedMonoStreamMetadataScheme,
  FixedStereoStreamMetadataScheme
};

// Variant of echo canceller settings to use in the test.
enum class AecType {
  BasicWebRtcAecSettings,
  AecTurnedOff,
  BasicWebRtcAecSettingsWithExtentedFilter,
  BasicWebRtcAecSettingsWithDelayAgnosticAec,
  BasicWebRtcAecSettingsWithAecMobile
};

// Thread-safe random number generator wrapper.
class RandomGenerator {
 public:
  RandomGenerator() : rand_gen_(42U) {}

  int RandInt(int min, int max) {
    rtc::CritScope cs(&crit_);
    return rand_gen_.Rand(min, max);
  }

  int RandInt(int max) {
    rtc::CritScope cs(&crit_);
    return rand_gen_.Rand(max);
  }

  float RandFloat() {
    rtc::CritScope cs(&crit_);
    return rand_gen_.Rand<float>();
  }

 private:
  rtc::CriticalSection crit_;
  Random rand_gen_ GUARDED_BY(crit_);
};

// Variables related to the audio data and formats.
struct AudioFrameData {
  explicit AudioFrameData(int max_frame_size) {
    // Set up the two-dimensional arrays needed for the APM API calls.
    input_framechannels.resize(2 * max_frame_size);
    input_frame.resize(2);
    input_frame[0] = &input_framechannels[0];
    input_frame[1] = &input_framechannels[max_frame_size];

    output_frame_channels.resize(2 * max_frame_size);
    output_frame.resize(2);
    output_frame[0] = &output_frame_channels[0];
    output_frame[1] = &output_frame_channels[max_frame_size];
  }

  AudioFrame frame;
  std::vector<float*> output_frame;
  std::vector<float> output_frame_channels;
  AudioProcessing::ChannelLayout output_channel_layout =
      AudioProcessing::ChannelLayout::kMono;
  int input_sample_rate_hz = 16000;
  int input_number_of_channels = -1;
  std::vector<float*> input_frame;
  std::vector<float> input_framechannels;
  AudioProcessing::ChannelLayout input_channel_layout =
      AudioProcessing::ChannelLayout::kMono;
  int output_sample_rate_hz = 16000;
  int output_number_of_channels = -1;
  StreamConfig input_stream_config;
  StreamConfig output_stream_config;
  int input_samples_per_channel = -1;
  int output_samples_per_channel = -1;
};

// The configuration for the test.
struct TestConfig {
  // Test case generator for the test configurations to use in the brief tests.
  static std::vector<TestConfig> GenerateBriefTestConfigs() {
    std::vector<TestConfig> test_configs;
    AecType aec_types[] = {AecType::BasicWebRtcAecSettingsWithDelayAgnosticAec,
                           AecType::BasicWebRtcAecSettingsWithAecMobile};
    for (auto aec_type : aec_types) {
      TestConfig test_config;
      test_config.aec_type = aec_type;

      test_config.min_number_of_calls = 300;

      // Perform tests only with the extreme runtime parameter setting scheme.
      test_config.runtime_parameter_setting_scheme =
          RuntimeParameterSettingScheme::ExtremeStreamMetadataChangeScheme;

      // Only test 16 kHz for this test suite.
      test_config.initial_sample_rate_hz = 16000;

      // Create test config for the second processing API function set.
      test_config.render_api_function =
          RenderApiImpl::ProcessReverseStreamImpl2;
      test_config.capture_api_function = CaptureApiImpl::ProcessStreamImpl2;

      // Create test config for the first processing API function set.
      test_configs.push_back(test_config);
      test_config.render_api_function =
          RenderApiImpl::AnalyzeReverseStreamImpl;
      test_config.capture_api_function = CaptureApiImpl::ProcessStreamImpl3;
      test_configs.push_back(test_config);
    }

    // Return the created test configurations.
    return test_configs;
  }

  // Test case generator for the test configurations to use in the extensive
  // tests.
  static std::vector<TestConfig> GenerateExtensiveTestConfigs() {
    // Lambda functions for the test config generation.
    auto add_processing_apis = [](TestConfig test_config) {
      struct AllowedApiCallCombinations {
        RenderApiImpl render_api;
        CaptureApiImpl capture_api;
      };

      const AllowedApiCallCombinations api_calls[] = {
          {RenderApiImpl::ProcessReverseStreamImpl1,
           CaptureApiImpl::ProcessStreamImpl1},
          {RenderApiImpl::ProcessReverseStreamImpl2,
           CaptureApiImpl::ProcessStreamImpl2},
          {RenderApiImpl::ProcessReverseStreamImpl2,
           CaptureApiImpl::ProcessStreamImpl3},
          {RenderApiImpl::AnalyzeReverseStreamImpl,
           CaptureApiImpl::ProcessStreamImpl2},
          {RenderApiImpl::AnalyzeReverseStreamImpl,
           CaptureApiImpl::ProcessStreamImpl3}};
      std::vector<TestConfig> out;
      for (auto api_call : api_calls) {
        test_config.render_api_function = api_call.render_api;
        test_config.capture_api_function = api_call.capture_api;
        out.push_back(test_config);
      }
      return out;
    };

    auto add_aec_settings = [](const std::vector<TestConfig>& in) {
      std::vector<TestConfig> out;
      AecType aec_types[] = {
          AecType::BasicWebRtcAecSettings, AecType::AecTurnedOff,
          AecType::BasicWebRtcAecSettingsWithExtentedFilter,
          AecType::BasicWebRtcAecSettingsWithDelayAgnosticAec,
          AecType::BasicWebRtcAecSettingsWithAecMobile};
      for (auto test_config : in) {
        // Due to a VisualStudio 2015 compiler issue, the internal loop
        // variable here cannot override a previously defined name.
        // In other words "type" cannot be named "aec_type" here.
        // https://connect.microsoft.com/VisualStudio/feedback/details/2291755
        for (auto type : aec_types) {
          test_config.aec_type = type;
          out.push_back(test_config);
        }
      }
      return out;
    };

    auto add_settings_scheme = [](const std::vector<TestConfig>& in) {
      std::vector<TestConfig> out;
      RuntimeParameterSettingScheme schemes[] = {
          RuntimeParameterSettingScheme::SparseStreamMetadataChangeScheme,
          RuntimeParameterSettingScheme::ExtremeStreamMetadataChangeScheme,
          RuntimeParameterSettingScheme::FixedMonoStreamMetadataScheme,
          RuntimeParameterSettingScheme::FixedStereoStreamMetadataScheme};

      for (auto test_config : in) {
        for (auto scheme : schemes) {
          test_config.runtime_parameter_setting_scheme = scheme;
          out.push_back(test_config);
        }
      }
      return out;
    };

    auto add_sample_rates = [](const std::vector<TestConfig>& in) {
      const int sample_rates[] = {8000, 16000, 32000, 48000};

      std::vector<TestConfig> out;
      for (auto test_config : in) {
        auto available_rates =
            (test_config.aec_type ==
                     AecType::BasicWebRtcAecSettingsWithAecMobile
                 ? rtc::ArrayView<const int>(sample_rates, 2)
                 : rtc::ArrayView<const int>(sample_rates));

        for (auto rate : available_rates) {
          test_config.initial_sample_rate_hz = rate;
          out.push_back(test_config);
        }
      }
      return out;
    };

    // Generate test configurations of the relevant combinations of the
    // parameters to
    // test.
    TestConfig test_config;
    test_config.min_number_of_calls = 10000;
    return add_sample_rates(add_settings_scheme(
        add_aec_settings(add_processing_apis(test_config))));
  }

  RenderApiImpl render_api_function = RenderApiImpl::ProcessReverseStreamImpl2;
  CaptureApiImpl capture_api_function = CaptureApiImpl::ProcessStreamImpl2;
  RuntimeParameterSettingScheme runtime_parameter_setting_scheme =
      RuntimeParameterSettingScheme::ExtremeStreamMetadataChangeScheme;
  int initial_sample_rate_hz = 16000;
  AecType aec_type = AecType::BasicWebRtcAecSettingsWithDelayAgnosticAec;
  int min_number_of_calls = 300;
};

// Handler for the frame counters.
class FrameCounters {
 public:
  void IncreaseRenderCounter() {
    rtc::CritScope cs(&crit_);
    render_count++;
  }

  void IncreaseCaptureCounter() {
    rtc::CritScope cs(&crit_);
    capture_count++;
  }

  int GetCaptureCounter() const {
    rtc::CritScope cs(&crit_);
    return capture_count;
  }

  int GetRenderCounter() const {
    rtc::CritScope cs(&crit_);
    return render_count;
  }

  int CaptureMinusRenderCounters() const {
    rtc::CritScope cs(&crit_);
    return capture_count - render_count;
  }

  int RenderMinusCaptureCounters() const {
    return -CaptureMinusRenderCounters();
  }

  bool BothCountersExceedeThreshold(int threshold) {
    rtc::CritScope cs(&crit_);
    return (render_count > threshold && capture_count > threshold);
  }

 private:
  rtc::CriticalSection crit_;
  int render_count GUARDED_BY(crit_) = 0;
  int capture_count GUARDED_BY(crit_) = 0;
};

// Class for handling the capture side processing.
class CaptureProcessor {
 public:
  CaptureProcessor(int max_frame_size,
                   RandomGenerator* rand_gen,
                   rtc::Event* render_call_event,
                   rtc::Event* capture_call_event,
                   FrameCounters* shared_counters_state,
                   AudioProcessingImplLockTest* test_framework,
                   TestConfig* test_config,
                   AudioProcessing* apm);
  bool Process();

 private:
  static const int kMaxCallDifference = 10;
  static const float kCaptureInputFloatLevel;
  static const int kCaptureInputFixLevel = 1024;

  void PrepareFrame();
  void CallApmCaptureSide();
  void ApplyRuntimeSettingScheme();

  RandomGenerator* const rand_gen_ = nullptr;
  rtc::Event* const render_call_event_ = nullptr;
  rtc::Event* const capture_call_event_ = nullptr;
  FrameCounters* const frame_counters_ = nullptr;
  AudioProcessingImplLockTest* const test_ = nullptr;
  const TestConfig* const test_config_ = nullptr;
  AudioProcessing* const apm_ = nullptr;
  AudioFrameData frame_data_;
};

// Class for handling the stats processing.
class StatsProcessor {
 public:
  StatsProcessor(RandomGenerator* rand_gen,
                 TestConfig* test_config,
                 AudioProcessing* apm);
  bool Process();

 private:
  RandomGenerator* rand_gen_ = nullptr;
  TestConfig* test_config_ = nullptr;
  AudioProcessing* apm_ = nullptr;
};

// Class for handling the render side processing.
class RenderProcessor {
 public:
  RenderProcessor(int max_frame_size,
                  RandomGenerator* rand_gen,
                  rtc::Event* render_call_event,
                  rtc::Event* capture_call_event,
                  FrameCounters* shared_counters_state,
                  AudioProcessingImplLockTest* test_framework,
                  TestConfig* test_config,
                  AudioProcessing* apm);
  bool Process();

 private:
  static const int kMaxCallDifference = 10;
  static const int kRenderInputFixLevel = 16384;
  static const float kRenderInputFloatLevel;

  void PrepareFrame();
  void CallApmRenderSide();
  void ApplyRuntimeSettingScheme();

  RandomGenerator* const rand_gen_ = nullptr;
  rtc::Event* const render_call_event_ = nullptr;
  rtc::Event* const capture_call_event_ = nullptr;
  FrameCounters* const frame_counters_ = nullptr;
  AudioProcessingImplLockTest* const test_ = nullptr;
  const TestConfig* const test_config_ = nullptr;
  AudioProcessing* const apm_ = nullptr;
  AudioFrameData frame_data_;
  bool first_render_call_ = true;
};

class AudioProcessingImplLockTest
    : public ::testing::TestWithParam<TestConfig> {
 public:
  AudioProcessingImplLockTest();
  bool RunTest();
  bool MaybeEndTest();

 private:
  static const int kTestTimeOutLimit = 10 * 60 * 1000;
  static const int kMaxFrameSize = 480;

  // ::testing::TestWithParam<> implementation
  void SetUp() override;
  void TearDown() override;

  // Thread callback for the render thread
  static bool RenderProcessorThreadFunc(void* context) {
    return reinterpret_cast<AudioProcessingImplLockTest*>(context)
        ->render_thread_state_.Process();
  }

  // Thread callback for the capture thread
  static bool CaptureProcessorThreadFunc(void* context) {
    return reinterpret_cast<AudioProcessingImplLockTest*>(context)
        ->capture_thread_state_.Process();
  }

  // Thread callback for the stats thread
  static bool StatsProcessorThreadFunc(void* context) {
    return reinterpret_cast<AudioProcessingImplLockTest*>(context)
        ->stats_thread_state_.Process();
  }

  // Tests whether all the required render and capture side calls have been
  // done.
  bool TestDone() {
    return frame_counters_.BothCountersExceedeThreshold(
        test_config_.min_number_of_calls);
  }

  // Start the threads used in the test.
  void StartThreads() {
    render_thread_.Start();
    render_thread_.SetPriority(rtc::kRealtimePriority);
    capture_thread_.Start();
    capture_thread_.SetPriority(rtc::kRealtimePriority);
    stats_thread_.Start();
    stats_thread_.SetPriority(rtc::kNormalPriority);
  }

  // Event handlers for the test.
  rtc::Event test_complete_;
  rtc::Event render_call_event_;
  rtc::Event capture_call_event_;

  // Thread related variables.
  rtc::PlatformThread render_thread_;
  rtc::PlatformThread capture_thread_;
  rtc::PlatformThread stats_thread_;
  mutable RandomGenerator rand_gen_;

  std::unique_ptr<AudioProcessing> apm_;
  TestConfig test_config_;
  FrameCounters frame_counters_;
  RenderProcessor render_thread_state_;
  CaptureProcessor capture_thread_state_;
  StatsProcessor stats_thread_state_;
};

// Sleeps a random time between 0 and max_sleep milliseconds.
void SleepRandomMs(int max_sleep, RandomGenerator* rand_gen) {
  int sleeptime = rand_gen->RandInt(0, max_sleep);
  SleepMs(sleeptime);
}

// Populates a float audio frame with random data.
void PopulateAudioFrame(float** frame,
                        float amplitude,
                        size_t num_channels,
                        size_t samples_per_channel,
                        RandomGenerator* rand_gen) {
  for (size_t ch = 0; ch < num_channels; ch++) {
    for (size_t k = 0; k < samples_per_channel; k++) {
      // Store random 16 bit quantized float number between +-amplitude.
      frame[ch][k] = amplitude * (2 * rand_gen->RandFloat() - 1);
    }
  }
}

// Populates an audioframe frame of AudioFrame type with random data.
void PopulateAudioFrame(AudioFrame* frame,
                        int16_t amplitude,
                        RandomGenerator* rand_gen) {
  ASSERT_GT(amplitude, 0);
  ASSERT_LE(amplitude, 32767);
  for (size_t ch = 0; ch < frame->num_channels_; ch++) {
    for (size_t k = 0; k < frame->samples_per_channel_; k++) {
      // Store random 16 bit number between -(amplitude+1) and
      // amplitude.
      frame->data_[k * ch] =
          rand_gen->RandInt(2 * amplitude + 1) - amplitude - 1;
    }
  }
}

AudioProcessingImplLockTest::AudioProcessingImplLockTest()
    : test_complete_(false, false),
      render_call_event_(false, false),
      capture_call_event_(false, false),
      render_thread_(RenderProcessorThreadFunc, this, "render"),
      capture_thread_(CaptureProcessorThreadFunc, this, "capture"),
      stats_thread_(StatsProcessorThreadFunc, this, "stats"),
      apm_(AudioProcessingImpl::Create()),
      render_thread_state_(kMaxFrameSize,
                           &rand_gen_,
                           &render_call_event_,
                           &capture_call_event_,
                           &frame_counters_,
                           this,
                           &test_config_,
                           apm_.get()),
      capture_thread_state_(kMaxFrameSize,
                            &rand_gen_,
                            &render_call_event_,
                            &capture_call_event_,
                            &frame_counters_,
                            this,
                            &test_config_,
                            apm_.get()),
      stats_thread_state_(&rand_gen_, &test_config_, apm_.get()) {}

// Run the test with a timeout.
bool AudioProcessingImplLockTest::RunTest() {
  StartThreads();
  return test_complete_.Wait(kTestTimeOutLimit);
}

bool AudioProcessingImplLockTest::MaybeEndTest() {
  if (HasFatalFailure() || TestDone()) {
    test_complete_.Set();
    return true;
  }
  return false;
}

// Setup of test and APM.
void AudioProcessingImplLockTest::SetUp() {
  test_config_ = static_cast<TestConfig>(GetParam());

  ASSERT_EQ(apm_->kNoError, apm_->level_estimator()->Enable(true));
  ASSERT_EQ(apm_->kNoError, apm_->gain_control()->Enable(true));

  ASSERT_EQ(apm_->kNoError,
            apm_->gain_control()->set_mode(GainControl::kAdaptiveDigital));
  ASSERT_EQ(apm_->kNoError, apm_->gain_control()->Enable(true));

  ASSERT_EQ(apm_->kNoError, apm_->noise_suppression()->Enable(true));
  ASSERT_EQ(apm_->kNoError, apm_->voice_detection()->Enable(true));

  Config config;
  if (test_config_.aec_type == AecType::AecTurnedOff) {
    ASSERT_EQ(apm_->kNoError, apm_->echo_control_mobile()->Enable(false));
    ASSERT_EQ(apm_->kNoError, apm_->echo_cancellation()->Enable(false));
  } else if (test_config_.aec_type ==
             AecType::BasicWebRtcAecSettingsWithAecMobile) {
    ASSERT_EQ(apm_->kNoError, apm_->echo_control_mobile()->Enable(true));
    ASSERT_EQ(apm_->kNoError, apm_->echo_cancellation()->Enable(false));
  } else {
    ASSERT_EQ(apm_->kNoError, apm_->echo_control_mobile()->Enable(false));
    ASSERT_EQ(apm_->kNoError, apm_->echo_cancellation()->Enable(true));
    ASSERT_EQ(apm_->kNoError, apm_->echo_cancellation()->enable_metrics(true));
    ASSERT_EQ(apm_->kNoError,
              apm_->echo_cancellation()->enable_delay_logging(true));

    config.Set<ExtendedFilter>(
        new ExtendedFilter(test_config_.aec_type ==
                           AecType::BasicWebRtcAecSettingsWithExtentedFilter));

    config.Set<DelayAgnostic>(
        new DelayAgnostic(test_config_.aec_type ==
                          AecType::BasicWebRtcAecSettingsWithDelayAgnosticAec));

    apm_->SetExtraOptions(config);
  }
}

void AudioProcessingImplLockTest::TearDown() {
  render_call_event_.Set();
  capture_call_event_.Set();
  render_thread_.Stop();
  capture_thread_.Stop();
  stats_thread_.Stop();
}

StatsProcessor::StatsProcessor(RandomGenerator* rand_gen,
                               TestConfig* test_config,
                               AudioProcessing* apm)
    : rand_gen_(rand_gen), test_config_(test_config), apm_(apm) {}

// Implements the callback functionality for the statistics
// collection thread.
bool StatsProcessor::Process() {
  SleepRandomMs(100, rand_gen_);

  EXPECT_EQ(apm_->echo_cancellation()->is_enabled(),
            ((test_config_->aec_type != AecType::AecTurnedOff) &&
             (test_config_->aec_type !=
              AecType::BasicWebRtcAecSettingsWithAecMobile)));
  apm_->echo_cancellation()->stream_drift_samples();
  EXPECT_EQ(apm_->echo_control_mobile()->is_enabled(),
            (test_config_->aec_type != AecType::AecTurnedOff) &&
                (test_config_->aec_type ==
                 AecType::BasicWebRtcAecSettingsWithAecMobile));
  EXPECT_TRUE(apm_->gain_control()->is_enabled());
  EXPECT_TRUE(apm_->noise_suppression()->is_enabled());

  // The below return values are not testable.
  apm_->noise_suppression()->speech_probability();
  apm_->voice_detection()->is_enabled();

  return true;
}

const float CaptureProcessor::kCaptureInputFloatLevel = 0.03125f;

CaptureProcessor::CaptureProcessor(int max_frame_size,
                                   RandomGenerator* rand_gen,
                                   rtc::Event* render_call_event,
                                   rtc::Event* capture_call_event,
                                   FrameCounters* shared_counters_state,
                                   AudioProcessingImplLockTest* test_framework,
                                   TestConfig* test_config,
                                   AudioProcessing* apm)
    : rand_gen_(rand_gen),
      render_call_event_(render_call_event),
      capture_call_event_(capture_call_event),
      frame_counters_(shared_counters_state),
      test_(test_framework),
      test_config_(test_config),
      apm_(apm),
      frame_data_(max_frame_size) {}

// Implements the callback functionality for the capture thread.
bool CaptureProcessor::Process() {
  // Sleep a random time to simulate thread jitter.
  SleepRandomMs(3, rand_gen_);

  // Check whether the test is done.
  if (test_->MaybeEndTest()) {
    return false;
  }

  // Ensure that the number of render and capture calls do not
  // differ too much.
  if (frame_counters_->CaptureMinusRenderCounters() > kMaxCallDifference) {
    render_call_event_->Wait(rtc::Event::kForever);
  }

  // Apply any specified capture side APM non-processing runtime calls.
  ApplyRuntimeSettingScheme();

  // Apply the capture side processing call.
  CallApmCaptureSide();

  // Increase the number of capture-side calls.
  frame_counters_->IncreaseCaptureCounter();

  // Flag to the render thread that another capture API call has occurred
  // by triggering this threads call event.
  capture_call_event_->Set();

  return true;
}

// Prepares a frame with relevant audio data and metadata.
void CaptureProcessor::PrepareFrame() {
  // Restrict to a common fixed sample rate if the AudioFrame
  // interface is used.
  if (test_config_->capture_api_function ==
      CaptureApiImpl::ProcessStreamImpl1) {
    frame_data_.input_sample_rate_hz = test_config_->initial_sample_rate_hz;
    frame_data_.output_sample_rate_hz = test_config_->initial_sample_rate_hz;
  }

  // Prepare the audioframe data and metadata.
  frame_data_.input_samples_per_channel =
      frame_data_.input_sample_rate_hz * AudioProcessing::kChunkSizeMs / 1000;
  frame_data_.frame.sample_rate_hz_ = frame_data_.input_sample_rate_hz;
  frame_data_.frame.num_channels_ = frame_data_.input_number_of_channels;
  frame_data_.frame.samples_per_channel_ =
      frame_data_.input_samples_per_channel;
  PopulateAudioFrame(&frame_data_.frame, kCaptureInputFixLevel, rand_gen_);

  // Prepare the float audio input data and metadata.
  frame_data_.input_stream_config.set_sample_rate_hz(
      frame_data_.input_sample_rate_hz);
  frame_data_.input_stream_config.set_num_channels(
      frame_data_.input_number_of_channels);
  frame_data_.input_stream_config.set_has_keyboard(false);
  PopulateAudioFrame(&frame_data_.input_frame[0], kCaptureInputFloatLevel,
                     frame_data_.input_number_of_channels,
                     frame_data_.input_samples_per_channel, rand_gen_);
  frame_data_.input_channel_layout =
      (frame_data_.input_number_of_channels == 1
           ? AudioProcessing::ChannelLayout::kMono
           : AudioProcessing::ChannelLayout::kStereo);

  // Prepare the float audio output data and metadata.
  frame_data_.output_samples_per_channel =
      frame_data_.output_sample_rate_hz * AudioProcessing::kChunkSizeMs / 1000;
  frame_data_.output_stream_config.set_sample_rate_hz(
      frame_data_.output_sample_rate_hz);
  frame_data_.output_stream_config.set_num_channels(
      frame_data_.output_number_of_channels);
  frame_data_.output_stream_config.set_has_keyboard(false);
  frame_data_.output_channel_layout =
      (frame_data_.output_number_of_channels == 1
           ? AudioProcessing::ChannelLayout::kMono
           : AudioProcessing::ChannelLayout::kStereo);
}

// Applies the capture side processing API call.
void CaptureProcessor::CallApmCaptureSide() {
  // Prepare a proper capture side processing API call input.
  PrepareFrame();

  // Set the stream delay.
  apm_->set_stream_delay_ms(30);

  // Set the analog level.
  apm_->gain_control()->set_stream_analog_level(80);

  // Call the specified capture side API processing method.
  int result = AudioProcessing::kNoError;
  switch (test_config_->capture_api_function) {
    case CaptureApiImpl::ProcessStreamImpl1:
      result = apm_->ProcessStream(&frame_data_.frame);
      break;
    case CaptureApiImpl::ProcessStreamImpl2:
      result = apm_->ProcessStream(
          &frame_data_.input_frame[0], frame_data_.input_samples_per_channel,
          frame_data_.input_sample_rate_hz, frame_data_.input_channel_layout,
          frame_data_.output_sample_rate_hz, frame_data_.output_channel_layout,
          &frame_data_.output_frame[0]);
      break;
    case CaptureApiImpl::ProcessStreamImpl3:
      result = apm_->ProcessStream(
          &frame_data_.input_frame[0], frame_data_.input_stream_config,
          frame_data_.output_stream_config, &frame_data_.output_frame[0]);
      break;
    default:
      FAIL();
  }

  // Retrieve the new analog level.
  apm_->gain_control()->stream_analog_level();

  // Check the return code for error.
  ASSERT_EQ(AudioProcessing::kNoError, result);
}

// Applies any runtime capture APM API calls and audio stream characteristics
// specified by the scheme for the test.
void CaptureProcessor::ApplyRuntimeSettingScheme() {
  const int capture_count_local = frame_counters_->GetCaptureCounter();

  // Update the number of channels and sample rates for the input and output.
  // Note that the counts frequencies for when to set parameters
  // are set using prime numbers in order to ensure that the
  // permutation scheme in the parameter setting changes.
  switch (test_config_->runtime_parameter_setting_scheme) {
    case RuntimeParameterSettingScheme::SparseStreamMetadataChangeScheme:
      if (capture_count_local == 0)
        frame_data_.input_sample_rate_hz = 16000;
      else if (capture_count_local % 11 == 0)
        frame_data_.input_sample_rate_hz = 32000;
      else if (capture_count_local % 73 == 0)
        frame_data_.input_sample_rate_hz = 48000;
      else if (capture_count_local % 89 == 0)
        frame_data_.input_sample_rate_hz = 16000;
      else if (capture_count_local % 97 == 0)
        frame_data_.input_sample_rate_hz = 8000;

      if (capture_count_local == 0)
        frame_data_.input_number_of_channels = 1;
      else if (capture_count_local % 4 == 0)
        frame_data_.input_number_of_channels =
            (frame_data_.input_number_of_channels == 1 ? 2 : 1);

      if (capture_count_local == 0)
        frame_data_.output_sample_rate_hz = 16000;
      else if (capture_count_local % 5 == 0)
        frame_data_.output_sample_rate_hz = 32000;
      else if (capture_count_local % 47 == 0)
        frame_data_.output_sample_rate_hz = 48000;
      else if (capture_count_local % 53 == 0)
        frame_data_.output_sample_rate_hz = 16000;
      else if (capture_count_local % 71 == 0)
        frame_data_.output_sample_rate_hz = 8000;

      if (capture_count_local == 0)
        frame_data_.output_number_of_channels = 1;
      else if (capture_count_local % 8 == 0)
        frame_data_.output_number_of_channels =
            (frame_data_.output_number_of_channels == 1 ? 2 : 1);
      break;
    case RuntimeParameterSettingScheme::ExtremeStreamMetadataChangeScheme:
      if (capture_count_local % 2 == 0) {
        frame_data_.input_number_of_channels = 1;
        frame_data_.input_sample_rate_hz = 16000;
        frame_data_.output_number_of_channels = 1;
        frame_data_.output_sample_rate_hz = 16000;
      } else {
        frame_data_.input_number_of_channels =
            (frame_data_.input_number_of_channels == 1 ? 2 : 1);
        if (frame_data_.input_sample_rate_hz == 8000)
          frame_data_.input_sample_rate_hz = 16000;
        else if (frame_data_.input_sample_rate_hz == 16000)
          frame_data_.input_sample_rate_hz = 32000;
        else if (frame_data_.input_sample_rate_hz == 32000)
          frame_data_.input_sample_rate_hz = 48000;
        else if (frame_data_.input_sample_rate_hz == 48000)
          frame_data_.input_sample_rate_hz = 8000;

        frame_data_.output_number_of_channels =
            (frame_data_.output_number_of_channels == 1 ? 2 : 1);
        if (frame_data_.output_sample_rate_hz == 8000)
          frame_data_.output_sample_rate_hz = 16000;
        else if (frame_data_.output_sample_rate_hz == 16000)
          frame_data_.output_sample_rate_hz = 32000;
        else if (frame_data_.output_sample_rate_hz == 32000)
          frame_data_.output_sample_rate_hz = 48000;
        else if (frame_data_.output_sample_rate_hz == 48000)
          frame_data_.output_sample_rate_hz = 8000;
      }
      break;
    case RuntimeParameterSettingScheme::FixedMonoStreamMetadataScheme:
      if (capture_count_local == 0) {
        frame_data_.input_sample_rate_hz = 16000;
        frame_data_.input_number_of_channels = 1;
        frame_data_.output_sample_rate_hz = 16000;
        frame_data_.output_number_of_channels = 1;
      }
      break;
    case RuntimeParameterSettingScheme::FixedStereoStreamMetadataScheme:
      if (capture_count_local == 0) {
        frame_data_.input_sample_rate_hz = 16000;
        frame_data_.input_number_of_channels = 2;
        frame_data_.output_sample_rate_hz = 16000;
        frame_data_.output_number_of_channels = 2;
      }
      break;
    default:
      FAIL();
  }

  // Call any specified runtime APM setter and
  // getter calls.
  switch (test_config_->runtime_parameter_setting_scheme) {
    case RuntimeParameterSettingScheme::SparseStreamMetadataChangeScheme:
    case RuntimeParameterSettingScheme::FixedMonoStreamMetadataScheme:
      break;
    case RuntimeParameterSettingScheme::ExtremeStreamMetadataChangeScheme:
    case RuntimeParameterSettingScheme::FixedStereoStreamMetadataScheme:
      if (capture_count_local % 2 == 0) {
        ASSERT_EQ(AudioProcessing::Error::kNoError,
                  apm_->set_stream_delay_ms(30));
        apm_->set_stream_key_pressed(true);
        apm_->set_delay_offset_ms(15);
        EXPECT_EQ(apm_->delay_offset_ms(), 15);
      } else {
        ASSERT_EQ(AudioProcessing::Error::kNoError,
                  apm_->set_stream_delay_ms(50));
        apm_->set_stream_key_pressed(false);
        apm_->set_delay_offset_ms(20);
        EXPECT_EQ(apm_->delay_offset_ms(), 20);
        apm_->delay_offset_ms();
      }
      break;
    default:
      FAIL();
  }

  // Restric the number of output channels not to exceed
  // the number of input channels.
  frame_data_.output_number_of_channels =
      std::min(frame_data_.output_number_of_channels,
               frame_data_.input_number_of_channels);
}

const float RenderProcessor::kRenderInputFloatLevel = 0.5f;

RenderProcessor::RenderProcessor(int max_frame_size,
                                 RandomGenerator* rand_gen,
                                 rtc::Event* render_call_event,
                                 rtc::Event* capture_call_event,
                                 FrameCounters* shared_counters_state,
                                 AudioProcessingImplLockTest* test_framework,
                                 TestConfig* test_config,
                                 AudioProcessing* apm)
    : rand_gen_(rand_gen),
      render_call_event_(render_call_event),
      capture_call_event_(capture_call_event),
      frame_counters_(shared_counters_state),
      test_(test_framework),
      test_config_(test_config),
      apm_(apm),
      frame_data_(max_frame_size) {}

// Implements the callback functionality for the render thread.
bool RenderProcessor::Process() {
  // Conditional wait to ensure that a capture call has been done
  // before the first render call is performed (implicitly
  // required by the APM API).
  if (first_render_call_) {
    capture_call_event_->Wait(rtc::Event::kForever);
    first_render_call_ = false;
  }

  // Sleep a random time to simulate thread jitter.
  SleepRandomMs(3, rand_gen_);

  // Check whether the test is done.
  if (test_->MaybeEndTest()) {
    return false;
  }

  // Ensure that the number of render and capture calls do not
  // differ too much.
  if (frame_counters_->RenderMinusCaptureCounters() > kMaxCallDifference) {
    capture_call_event_->Wait(rtc::Event::kForever);
  }

  // Apply any specified render side APM non-processing runtime calls.
  ApplyRuntimeSettingScheme();

  // Apply the render side processing call.
  CallApmRenderSide();

  // Increase the number of render-side calls.
  frame_counters_->IncreaseRenderCounter();

  // Flag to the capture thread that another render API call has occurred
  // by triggering this threads call event.
  render_call_event_->Set();
  return true;
}

// Prepares the render side frame and the accompanying metadata
// with the appropriate information.
void RenderProcessor::PrepareFrame() {
  // Restrict to a common fixed sample rate if the AudioFrame interface is
  // used.
  if ((test_config_->render_api_function ==
       RenderApiImpl::ProcessReverseStreamImpl1) ||
      (test_config_->aec_type !=
       AecType::BasicWebRtcAecSettingsWithAecMobile)) {
    frame_data_.input_sample_rate_hz = test_config_->initial_sample_rate_hz;
    frame_data_.output_sample_rate_hz = test_config_->initial_sample_rate_hz;
  }

  // Prepare the audioframe data and metadata
  frame_data_.input_samples_per_channel =
      frame_data_.input_sample_rate_hz * AudioProcessing::kChunkSizeMs / 1000;
  frame_data_.frame.sample_rate_hz_ = frame_data_.input_sample_rate_hz;
  frame_data_.frame.num_channels_ = frame_data_.input_number_of_channels;
  frame_data_.frame.samples_per_channel_ =
      frame_data_.input_samples_per_channel;
  PopulateAudioFrame(&frame_data_.frame, kRenderInputFixLevel, rand_gen_);

  // Prepare the float audio input data and metadata.
  frame_data_.input_stream_config.set_sample_rate_hz(
      frame_data_.input_sample_rate_hz);
  frame_data_.input_stream_config.set_num_channels(
      frame_data_.input_number_of_channels);
  frame_data_.input_stream_config.set_has_keyboard(false);
  PopulateAudioFrame(&frame_data_.input_frame[0], kRenderInputFloatLevel,
                     frame_data_.input_number_of_channels,
                     frame_data_.input_samples_per_channel, rand_gen_);
  frame_data_.input_channel_layout =
      (frame_data_.input_number_of_channels == 1
           ? AudioProcessing::ChannelLayout::kMono
           : AudioProcessing::ChannelLayout::kStereo);

  // Prepare the float audio output data and metadata.
  frame_data_.output_samples_per_channel =
      frame_data_.output_sample_rate_hz * AudioProcessing::kChunkSizeMs / 1000;
  frame_data_.output_stream_config.set_sample_rate_hz(
      frame_data_.output_sample_rate_hz);
  frame_data_.output_stream_config.set_num_channels(
      frame_data_.output_number_of_channels);
  frame_data_.output_stream_config.set_has_keyboard(false);
  frame_data_.output_channel_layout =
      (frame_data_.output_number_of_channels == 1
           ? AudioProcessing::ChannelLayout::kMono
           : AudioProcessing::ChannelLayout::kStereo);
}

// Makes the render side processing API call.
void RenderProcessor::CallApmRenderSide() {
  // Prepare a proper render side processing API call input.
  PrepareFrame();

  // Call the specified render side API processing method.
  int result = AudioProcessing::kNoError;
  switch (test_config_->render_api_function) {
    case RenderApiImpl::ProcessReverseStreamImpl1:
      result = apm_->ProcessReverseStream(&frame_data_.frame);
      break;
    case RenderApiImpl::ProcessReverseStreamImpl2:
      result = apm_->ProcessReverseStream(
          &frame_data_.input_frame[0], frame_data_.input_stream_config,
          frame_data_.output_stream_config, &frame_data_.output_frame[0]);
      break;
    case RenderApiImpl::AnalyzeReverseStreamImpl:
      result = apm_->AnalyzeReverseStream(
          &frame_data_.input_frame[0], frame_data_.input_samples_per_channel,
          frame_data_.input_sample_rate_hz, frame_data_.input_channel_layout);
      break;
    default:
      FAIL();
  }

  // Check the return code for error.
  ASSERT_EQ(AudioProcessing::kNoError, result);
}

// Applies any render capture side APM API calls and audio stream
// characteristics
// specified by the scheme for the test.
void RenderProcessor::ApplyRuntimeSettingScheme() {
  const int render_count_local = frame_counters_->GetRenderCounter();

  // Update the number of channels and sample rates for the input and output.
  // Note that the counts frequencies for when to set parameters
  // are set using prime numbers in order to ensure that the
  // permutation scheme in the parameter setting changes.
  switch (test_config_->runtime_parameter_setting_scheme) {
    case RuntimeParameterSettingScheme::SparseStreamMetadataChangeScheme:
      if (render_count_local == 0)
        frame_data_.input_sample_rate_hz = 16000;
      else if (render_count_local % 47 == 0)
        frame_data_.input_sample_rate_hz = 32000;
      else if (render_count_local % 71 == 0)
        frame_data_.input_sample_rate_hz = 48000;
      else if (render_count_local % 79 == 0)
        frame_data_.input_sample_rate_hz = 16000;
      else if (render_count_local % 83 == 0)
        frame_data_.input_sample_rate_hz = 8000;

      if (render_count_local == 0)
        frame_data_.input_number_of_channels = 1;
      else if (render_count_local % 4 == 0)
        frame_data_.input_number_of_channels =
            (frame_data_.input_number_of_channels == 1 ? 2 : 1);

      if (render_count_local == 0)
        frame_data_.output_sample_rate_hz = 16000;
      else if (render_count_local % 17 == 0)
        frame_data_.output_sample_rate_hz = 32000;
      else if (render_count_local % 19 == 0)
        frame_data_.output_sample_rate_hz = 48000;
      else if (render_count_local % 29 == 0)
        frame_data_.output_sample_rate_hz = 16000;
      else if (render_count_local % 61 == 0)
        frame_data_.output_sample_rate_hz = 8000;

      if (render_count_local == 0)
        frame_data_.output_number_of_channels = 1;
      else if (render_count_local % 8 == 0)
        frame_data_.output_number_of_channels =
            (frame_data_.output_number_of_channels == 1 ? 2 : 1);
      break;
    case RuntimeParameterSettingScheme::ExtremeStreamMetadataChangeScheme:
      if (render_count_local == 0) {
        frame_data_.input_number_of_channels = 1;
        frame_data_.input_sample_rate_hz = 16000;
        frame_data_.output_number_of_channels = 1;
        frame_data_.output_sample_rate_hz = 16000;
      } else {
        frame_data_.input_number_of_channels =
            (frame_data_.input_number_of_channels == 1 ? 2 : 1);
        if (frame_data_.input_sample_rate_hz == 8000)
          frame_data_.input_sample_rate_hz = 16000;
        else if (frame_data_.input_sample_rate_hz == 16000)
          frame_data_.input_sample_rate_hz = 32000;
        else if (frame_data_.input_sample_rate_hz == 32000)
          frame_data_.input_sample_rate_hz = 48000;
        else if (frame_data_.input_sample_rate_hz == 48000)
          frame_data_.input_sample_rate_hz = 8000;

        frame_data_.output_number_of_channels =
            (frame_data_.output_number_of_channels == 1 ? 2 : 1);
        if (frame_data_.output_sample_rate_hz == 8000)
          frame_data_.output_sample_rate_hz = 16000;
        else if (frame_data_.output_sample_rate_hz == 16000)
          frame_data_.output_sample_rate_hz = 32000;
        else if (frame_data_.output_sample_rate_hz == 32000)
          frame_data_.output_sample_rate_hz = 48000;
        else if (frame_data_.output_sample_rate_hz == 48000)
          frame_data_.output_sample_rate_hz = 8000;
      }
      break;
    case RuntimeParameterSettingScheme::FixedMonoStreamMetadataScheme:
      if (render_count_local == 0) {
        frame_data_.input_sample_rate_hz = 16000;
        frame_data_.input_number_of_channels = 1;
        frame_data_.output_sample_rate_hz = 16000;
        frame_data_.output_number_of_channels = 1;
      }
      break;
    case RuntimeParameterSettingScheme::FixedStereoStreamMetadataScheme:
      if (render_count_local == 0) {
        frame_data_.input_sample_rate_hz = 16000;
        frame_data_.input_number_of_channels = 2;
        frame_data_.output_sample_rate_hz = 16000;
        frame_data_.output_number_of_channels = 2;
      }
      break;
    default:
      FAIL();
  }

  // Restric the number of output channels not to exceed
  // the number of input channels.
  frame_data_.output_number_of_channels =
      std::min(frame_data_.output_number_of_channels,
               frame_data_.input_number_of_channels);
}

}  // anonymous namespace

TEST_P(AudioProcessingImplLockTest, LockTest) {
  // Run test and verify that it did not time out.
  ASSERT_TRUE(RunTest());
}

// Instantiate tests from the extreme test configuration set.
INSTANTIATE_TEST_CASE_P(
    DISABLED_AudioProcessingImplLockExtensive,
    AudioProcessingImplLockTest,
    ::testing::ValuesIn(TestConfig::GenerateExtensiveTestConfigs()));

INSTANTIATE_TEST_CASE_P(
    AudioProcessingImplLockBrief,
    AudioProcessingImplLockTest,
    ::testing::ValuesIn(TestConfig::GenerateBriefTestConfigs()));

}  // namespace webrtc
