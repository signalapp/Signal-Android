/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_PROCESSING_INCLUDE_MOCK_AUDIO_PROCESSING_H_
#define WEBRTC_MODULES_AUDIO_PROCESSING_INCLUDE_MOCK_AUDIO_PROCESSING_H_

#include <memory>

#include "webrtc/modules/audio_processing/include/audio_processing.h"

namespace webrtc {

class MockEchoCancellation : public EchoCancellation {
 public:
  MOCK_METHOD1(Enable,
      int(bool enable));
  MOCK_CONST_METHOD0(is_enabled,
      bool());
  MOCK_METHOD1(enable_drift_compensation,
      int(bool enable));
  MOCK_CONST_METHOD0(is_drift_compensation_enabled,
      bool());
  MOCK_METHOD1(set_stream_drift_samples,
      void(int drift));
  MOCK_CONST_METHOD0(stream_drift_samples,
      int());
  MOCK_METHOD1(set_suppression_level,
      int(SuppressionLevel level));
  MOCK_CONST_METHOD0(suppression_level,
      SuppressionLevel());
  MOCK_CONST_METHOD0(stream_has_echo,
      bool());
  MOCK_METHOD1(enable_metrics,
      int(bool enable));
  MOCK_CONST_METHOD0(are_metrics_enabled,
      bool());
  MOCK_METHOD1(GetMetrics,
      int(Metrics* metrics));
  MOCK_METHOD1(enable_delay_logging,
      int(bool enable));
  MOCK_CONST_METHOD0(is_delay_logging_enabled,
      bool());
  MOCK_METHOD2(GetDelayMetrics,
      int(int* median, int* std));
  MOCK_METHOD3(GetDelayMetrics,
      int(int* median, int* std, float* fraction_poor_delays));
  MOCK_CONST_METHOD0(aec_core,
      struct AecCore*());
};

class MockEchoControlMobile : public EchoControlMobile {
 public:
  MOCK_METHOD1(Enable,
      int(bool enable));
  MOCK_CONST_METHOD0(is_enabled,
      bool());
  MOCK_METHOD1(set_routing_mode,
      int(RoutingMode mode));
  MOCK_CONST_METHOD0(routing_mode,
      RoutingMode());
  MOCK_METHOD1(enable_comfort_noise,
      int(bool enable));
  MOCK_CONST_METHOD0(is_comfort_noise_enabled,
      bool());
  MOCK_METHOD2(SetEchoPath,
      int(const void* echo_path, size_t size_bytes));
  MOCK_CONST_METHOD2(GetEchoPath,
      int(void* echo_path, size_t size_bytes));
};

class MockGainControl : public GainControl {
 public:
  MOCK_METHOD1(Enable,
      int(bool enable));
  MOCK_CONST_METHOD0(is_enabled,
      bool());
  MOCK_METHOD1(set_stream_analog_level,
      int(int level));
  MOCK_METHOD0(stream_analog_level,
      int());
  MOCK_METHOD1(set_mode,
      int(Mode mode));
  MOCK_CONST_METHOD0(mode,
      Mode());
  MOCK_METHOD1(set_target_level_dbfs,
      int(int level));
  MOCK_CONST_METHOD0(target_level_dbfs,
      int());
  MOCK_METHOD1(set_compression_gain_db,
      int(int gain));
  MOCK_CONST_METHOD0(compression_gain_db,
      int());
  MOCK_METHOD1(enable_limiter,
      int(bool enable));
  MOCK_CONST_METHOD0(is_limiter_enabled,
      bool());
  MOCK_METHOD2(set_analog_level_limits,
      int(int minimum, int maximum));
  MOCK_CONST_METHOD0(analog_level_minimum,
      int());
  MOCK_CONST_METHOD0(analog_level_maximum,
      int());
  MOCK_CONST_METHOD0(stream_is_saturated,
      bool());
};

class MockHighPassFilter : public HighPassFilter {
 public:
  MOCK_METHOD1(Enable,
      int(bool enable));
  MOCK_CONST_METHOD0(is_enabled,
      bool());
};

class MockLevelEstimator : public LevelEstimator {
 public:
  MOCK_METHOD1(Enable,
      int(bool enable));
  MOCK_CONST_METHOD0(is_enabled,
      bool());
  MOCK_METHOD0(RMS,
      int());
};

class MockNoiseSuppression : public NoiseSuppression {
 public:
  MOCK_METHOD1(Enable,
      int(bool enable));
  MOCK_CONST_METHOD0(is_enabled,
      bool());
  MOCK_METHOD1(set_level,
      int(Level level));
  MOCK_CONST_METHOD0(level,
      Level());
  MOCK_CONST_METHOD0(speech_probability,
      float());
  MOCK_METHOD0(NoiseEstimate, std::vector<float>());
};

class MockVoiceDetection : public VoiceDetection {
 public:
  MOCK_METHOD1(Enable,
      int(bool enable));
  MOCK_CONST_METHOD0(is_enabled,
      bool());
  MOCK_CONST_METHOD0(stream_has_voice,
      bool());
  MOCK_METHOD1(set_stream_has_voice,
      int(bool has_voice));
  MOCK_METHOD1(set_likelihood,
      int(Likelihood likelihood));
  MOCK_CONST_METHOD0(likelihood,
      Likelihood());
  MOCK_METHOD1(set_frame_size_ms,
      int(int size));
  MOCK_CONST_METHOD0(frame_size_ms,
      int());
};

class MockAudioProcessing : public AudioProcessing {
 public:
  MockAudioProcessing()
      : echo_cancellation_(new MockEchoCancellation),
        echo_control_mobile_(new MockEchoControlMobile),
        gain_control_(new MockGainControl),
        high_pass_filter_(new MockHighPassFilter),
        level_estimator_(new MockLevelEstimator),
        noise_suppression_(new MockNoiseSuppression),
        voice_detection_(new MockVoiceDetection) {
  }

  virtual ~MockAudioProcessing() {
  }

  MOCK_METHOD0(Initialize,
      int());
  MOCK_METHOD6(Initialize,
      int(int sample_rate_hz,
          int output_sample_rate_hz,
          int reverse_sample_rate_hz,
          ChannelLayout input_layout,
          ChannelLayout output_layout,
          ChannelLayout reverse_layout));
  MOCK_METHOD1(Initialize,
      int(const ProcessingConfig& processing_config));
  MOCK_METHOD1(SetExtraOptions,
      void(const Config& config));
  MOCK_METHOD1(set_sample_rate_hz,
      int(int rate));
  MOCK_CONST_METHOD0(input_sample_rate_hz,
      int());
  MOCK_CONST_METHOD0(sample_rate_hz,
      int());
  MOCK_CONST_METHOD0(proc_sample_rate_hz,
      int());
  MOCK_CONST_METHOD0(proc_split_sample_rate_hz,
      int());
  MOCK_CONST_METHOD0(num_input_channels,
      size_t());
  MOCK_CONST_METHOD0(num_output_channels,
      size_t());
  MOCK_CONST_METHOD0(num_reverse_channels,
      size_t());
  MOCK_METHOD1(set_output_will_be_muted,
      void(bool muted));
  MOCK_CONST_METHOD0(output_will_be_muted,
      bool());
  MOCK_METHOD1(ProcessStream,
      int(AudioFrame* frame));
  MOCK_METHOD7(ProcessStream,
      int(const float* const* src,
          size_t samples_per_channel,
          int input_sample_rate_hz,
          ChannelLayout input_layout,
          int output_sample_rate_hz,
          ChannelLayout output_layout,
          float* const* dest));
  MOCK_METHOD4(ProcessStream,
               int(const float* const* src,
                   const StreamConfig& input_config,
                   const StreamConfig& output_config,
                   float* const* dest));
  MOCK_METHOD1(AnalyzeReverseStream,
      int(AudioFrame* frame));
  MOCK_METHOD1(ProcessReverseStream, int(AudioFrame* frame));
  MOCK_METHOD4(AnalyzeReverseStream,
      int(const float* const* data, size_t frames, int sample_rate_hz,
          ChannelLayout input_layout));
  MOCK_METHOD4(ProcessReverseStream,
               int(const float* const* src,
                   const StreamConfig& input_config,
                   const StreamConfig& output_config,
                   float* const* dest));
  MOCK_METHOD1(set_stream_delay_ms,
      int(int delay));
  MOCK_CONST_METHOD0(stream_delay_ms,
      int());
  MOCK_CONST_METHOD0(was_stream_delay_set,
      bool());
  MOCK_METHOD1(set_stream_key_pressed,
      void(bool key_pressed));
  MOCK_CONST_METHOD0(stream_key_pressed,
      bool());
  MOCK_METHOD1(set_delay_offset_ms,
      void(int offset));
  MOCK_CONST_METHOD0(delay_offset_ms,
      int());
  MOCK_METHOD2(StartDebugRecording,
               int(const char filename[kMaxFilenameSize],
                   int64_t max_log_size_bytes));
  MOCK_METHOD2(StartDebugRecording,
               int(FILE* handle, int64_t max_log_size_bytes));
  MOCK_METHOD0(StopDebugRecording,
      int());
  MOCK_METHOD0(UpdateHistogramsOnCallEnd, void());
  virtual MockEchoCancellation* echo_cancellation() const {
    return echo_cancellation_.get();
  }
  virtual MockEchoControlMobile* echo_control_mobile() const {
    return echo_control_mobile_.get();
  }
  virtual MockGainControl* gain_control() const {
    return gain_control_.get();
  }
  virtual MockHighPassFilter* high_pass_filter() const {
    return high_pass_filter_.get();
  }
  virtual MockLevelEstimator* level_estimator() const {
    return level_estimator_.get();
  }
  virtual MockNoiseSuppression* noise_suppression() const {
    return noise_suppression_.get();
  }
  virtual MockVoiceDetection* voice_detection() const {
    return voice_detection_.get();
  }

 private:
  std::unique_ptr<MockEchoCancellation> echo_cancellation_;
  std::unique_ptr<MockEchoControlMobile> echo_control_mobile_;
  std::unique_ptr<MockGainControl> gain_control_;
  std::unique_ptr<MockHighPassFilter> high_pass_filter_;
  std::unique_ptr<MockLevelEstimator> level_estimator_;
  std::unique_ptr<MockNoiseSuppression> noise_suppression_;
  std::unique_ptr<MockVoiceDetection> voice_detection_;
};

}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_PROCESSING_INCLUDE_MOCK_AUDIO_PROCESSING_H_
