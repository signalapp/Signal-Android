/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_processing/test/wav_based_simulator.h"

#include "webrtc/base/checks.h"
#include "webrtc/test/testsupport/trace_to_stderr.h"

namespace webrtc {
namespace test {

std::vector<WavBasedSimulator::SimulationEventType>
WavBasedSimulator::GetDefaultEventChain() const {
  std::vector<WavBasedSimulator::SimulationEventType> call_chain(2);
  call_chain[0] = SimulationEventType::kProcessStream;
  call_chain[1] = SimulationEventType::kProcessReverseStream;
  return call_chain;
}

void WavBasedSimulator::PrepareProcessStreamCall() {
  if (settings_.fixed_interface) {
    CopyToAudioFrame(*in_buf_, &fwd_frame_);
  }
  ap_->set_stream_key_pressed(settings_.use_ts && (*settings_.use_ts));

  RTC_CHECK_EQ(AudioProcessing::kNoError,
               ap_->set_stream_delay_ms(
                   settings_.stream_delay ? *settings_.stream_delay : 0));

  ap_->echo_cancellation()->set_stream_drift_samples(
      settings_.stream_drift_samples ? *settings_.stream_drift_samples : 0);

  RTC_CHECK_EQ(AudioProcessing::kNoError,
               ap_->gain_control()->set_stream_analog_level(
                   last_specified_microphone_level_));
}

void WavBasedSimulator::PrepareReverseProcessStreamCall() {
  if (settings_.fixed_interface) {
    CopyToAudioFrame(*reverse_in_buf_, &rev_frame_);
  }
}

void WavBasedSimulator::Process() {
  std::unique_ptr<test::TraceToStderr> trace_to_stderr;
  if (settings_.use_verbose_logging) {
    trace_to_stderr.reset(new test::TraceToStderr(true));
  }

  call_chain_ = GetDefaultEventChain();
  CreateAudioProcessor();

  Initialize();

  bool samples_left_to_process = true;
  int call_chain_index = 0;
  int num_forward_chunks_processed = 0;
  const int kOneBykChunksPerSecond =
      1.f / AudioProcessingSimulator::kChunksPerSecond;
  while (samples_left_to_process) {
    switch (call_chain_[call_chain_index]) {
      case SimulationEventType::kProcessStream:
        samples_left_to_process = HandleProcessStreamCall();
        ++num_forward_chunks_processed;
        break;
      case SimulationEventType::kProcessReverseStream:
        if (settings_.reverse_input_filename) {
          samples_left_to_process = HandleProcessReverseStreamCall();
        }
        break;
      default:
        RTC_CHECK(false);
    }

    call_chain_index = (call_chain_index + 1) % call_chain_.size();

    if (trace_to_stderr) {
      trace_to_stderr->SetTimeSeconds(num_forward_chunks_processed *
                                      kOneBykChunksPerSecond);
    }
  }

  DestroyAudioProcessor();
}

bool WavBasedSimulator::HandleProcessStreamCall() {
  bool samples_left_to_process = buffer_reader_->Read(in_buf_.get());
  if (samples_left_to_process) {
    PrepareProcessStreamCall();
    ProcessStream(settings_.fixed_interface);
    last_specified_microphone_level_ =
        ap_->gain_control()->stream_analog_level();
  }
  return samples_left_to_process;
}

bool WavBasedSimulator::HandleProcessReverseStreamCall() {
  bool samples_left_to_process =
      reverse_buffer_reader_->Read(reverse_in_buf_.get());
  if (samples_left_to_process) {
    PrepareReverseProcessStreamCall();
    ProcessReverseStream(settings_.fixed_interface);
  }
  return samples_left_to_process;
}

void WavBasedSimulator::Initialize() {
  std::unique_ptr<WavReader> in_file(
      new WavReader(settings_.input_filename->c_str()));
  int input_sample_rate_hz = in_file->sample_rate();
  int input_num_channels = in_file->num_channels();
  buffer_reader_.reset(new ChannelBufferWavReader(std::move(in_file)));

  int output_sample_rate_hz = settings_.output_sample_rate_hz
                                  ? *settings_.output_sample_rate_hz
                                  : input_sample_rate_hz;
  int output_num_channels = settings_.output_num_channels
                                ? *settings_.output_num_channels
                                : input_num_channels;

  int reverse_sample_rate_hz = 48000;
  int reverse_num_channels = 1;
  int reverse_output_sample_rate_hz = 48000;
  int reverse_output_num_channels = 1;
  if (settings_.reverse_input_filename) {
    std::unique_ptr<WavReader> reverse_in_file(
        new WavReader(settings_.reverse_input_filename->c_str()));
    reverse_sample_rate_hz = reverse_in_file->sample_rate();
    reverse_num_channels = reverse_in_file->num_channels();
    reverse_buffer_reader_.reset(
        new ChannelBufferWavReader(std::move(reverse_in_file)));

    reverse_output_sample_rate_hz =
        settings_.reverse_output_sample_rate_hz
            ? *settings_.reverse_output_sample_rate_hz
            : reverse_sample_rate_hz;
    reverse_output_num_channels = settings_.reverse_output_num_channels
                                      ? *settings_.reverse_output_num_channels
                                      : reverse_num_channels;
  }

  SetupBuffersConfigsOutputs(
      input_sample_rate_hz, output_sample_rate_hz, reverse_sample_rate_hz,
      reverse_output_sample_rate_hz, input_num_channels, output_num_channels,
      reverse_num_channels, reverse_output_num_channels);
}

}  // namespace test
}  // namespace webrtc
