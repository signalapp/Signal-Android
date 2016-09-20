/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_PROCESSING_TEST_AUDIO_PROCESSING_SIMULATOR_H_
#define WEBRTC_MODULES_AUDIO_PROCESSING_TEST_AUDIO_PROCESSING_SIMULATOR_H_

#include <algorithm>
#include <limits>
#include <memory>
#include <string>

#include "webrtc/base/timeutils.h"
#include "webrtc/base/constructormagic.h"
#include "webrtc/base/optional.h"
#include "webrtc/common_audio/channel_buffer.h"
#include "webrtc/modules/audio_processing/include/audio_processing.h"
#include "webrtc/modules/audio_processing/test/test_utils.h"

namespace webrtc {
namespace test {

// Holds all the parameters available for controlling the simulation.
struct SimulationSettings {
  rtc::Optional<int> stream_delay;
  rtc::Optional<int> stream_drift_samples;
  rtc::Optional<int> output_sample_rate_hz;
  rtc::Optional<int> output_num_channels;
  rtc::Optional<int> reverse_output_sample_rate_hz;
  rtc::Optional<int> reverse_output_num_channels;
  rtc::Optional<std::string> microphone_positions;
  int target_angle_degrees = 90;
  rtc::Optional<std::string> output_filename;
  rtc::Optional<std::string> reverse_output_filename;
  rtc::Optional<std::string> input_filename;
  rtc::Optional<std::string> reverse_input_filename;
  rtc::Optional<bool> use_aec;
  rtc::Optional<bool> use_aecm;
  rtc::Optional<bool> use_agc;
  rtc::Optional<bool> use_hpf;
  rtc::Optional<bool> use_ns;
  rtc::Optional<bool> use_ts;
  rtc::Optional<bool> use_bf;
  rtc::Optional<bool> use_ie;
  rtc::Optional<bool> use_vad;
  rtc::Optional<bool> use_le;
  rtc::Optional<bool> use_all;
  rtc::Optional<int> aec_suppression_level;
  rtc::Optional<bool> use_delay_agnostic;
  rtc::Optional<bool> use_extended_filter;
  rtc::Optional<bool> use_drift_compensation;
  rtc::Optional<bool> use_aec3;
  rtc::Optional<int> aecm_routing_mode;
  rtc::Optional<bool> use_aecm_comfort_noise;
  rtc::Optional<int> agc_mode;
  rtc::Optional<int> agc_target_level;
  rtc::Optional<bool> use_agc_limiter;
  rtc::Optional<int> agc_compression_gain;
  rtc::Optional<int> vad_likelihood;
  rtc::Optional<int> ns_level;
  rtc::Optional<bool> use_refined_adaptive_filter;
  bool report_performance = false;
  bool report_bitexactness = false;
  bool use_verbose_logging = false;
  bool discard_all_settings_in_aecdump = true;
  rtc::Optional<std::string> aec_dump_input_filename;
  rtc::Optional<std::string> aec_dump_output_filename;
  bool fixed_interface = false;
  bool store_intermediate_output = false;
};

// Holds a few statistics about a series of TickIntervals.
struct TickIntervalStats {
  TickIntervalStats() : min(std::numeric_limits<int64_t>::max()) {}
  int64_t sum;
  int64_t max;
  int64_t min;
};

// Copies samples present in a ChannelBuffer into an AudioFrame.
void CopyToAudioFrame(const ChannelBuffer<float>& src, AudioFrame* dest);

// Provides common functionality for performing audioprocessing simulations.
class AudioProcessingSimulator {
 public:
  static const int kChunksPerSecond = 1000 / AudioProcessing::kChunkSizeMs;

  explicit AudioProcessingSimulator(const SimulationSettings& settings)
      : settings_(settings) {}
  virtual ~AudioProcessingSimulator() {}

  // Processes the data in the input.
  virtual void Process() = 0;

  // Returns the execution time of all AudioProcessing calls.
  const TickIntervalStats& proc_time() const { return proc_time_; }

  // Reports whether the processed recording was bitexact.
  bool OutputWasBitexact() { return bitexact_output_; }

  size_t get_num_process_stream_calls() { return num_process_stream_calls_; }
  size_t get_num_reverse_process_stream_calls() {
    return num_reverse_process_stream_calls_;
  }

 protected:
  // RAII class for execution time measurement. Updates the provided
  // TickIntervalStats based on the time between ScopedTimer creation and
  // leaving the enclosing scope.
  class ScopedTimer {
   public:
    explicit ScopedTimer(TickIntervalStats* proc_time)
        : proc_time_(proc_time), start_time_(rtc::TimeNanos()) {}

    ~ScopedTimer();

   private:
    TickIntervalStats* const proc_time_;
    int64_t start_time_;
  };

  TickIntervalStats* mutable_proc_time() { return &proc_time_; }
  void ProcessStream(bool fixed_interface);
  void ProcessReverseStream(bool fixed_interface);
  void CreateAudioProcessor();
  void DestroyAudioProcessor();
  void SetupBuffersConfigsOutputs(int input_sample_rate_hz,
                                  int output_sample_rate_hz,
                                  int reverse_input_sample_rate_hz,
                                  int reverse_output_sample_rate_hz,
                                  int input_num_channels,
                                  int output_num_channels,
                                  int reverse_input_num_channels,
                                  int reverse_output_num_channels);

  const SimulationSettings settings_;
  std::unique_ptr<AudioProcessing> ap_;

  std::unique_ptr<ChannelBuffer<float>> in_buf_;
  std::unique_ptr<ChannelBuffer<float>> out_buf_;
  std::unique_ptr<ChannelBuffer<float>> reverse_in_buf_;
  std::unique_ptr<ChannelBuffer<float>> reverse_out_buf_;
  StreamConfig in_config_;
  StreamConfig out_config_;
  StreamConfig reverse_in_config_;
  StreamConfig reverse_out_config_;
  std::unique_ptr<ChannelBufferWavReader> buffer_reader_;
  std::unique_ptr<ChannelBufferWavReader> reverse_buffer_reader_;
  AudioFrame rev_frame_;
  AudioFrame fwd_frame_;
  bool bitexact_output_ = true;

 private:
  void SetupOutput();

  size_t num_process_stream_calls_ = 0;
  size_t num_reverse_process_stream_calls_ = 0;
  size_t output_reset_counter_ = 0;
  std::unique_ptr<ChannelBufferWavWriter> buffer_writer_;
  std::unique_ptr<ChannelBufferWavWriter> reverse_buffer_writer_;
  TickIntervalStats proc_time_;

  RTC_DISALLOW_IMPLICIT_CONSTRUCTORS(AudioProcessingSimulator);
};

}  // namespace test
}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_PROCESSING_TEST_AUDIO_PROCESSING_SIMULATOR_H_
