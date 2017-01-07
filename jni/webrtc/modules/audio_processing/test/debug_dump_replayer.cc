/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_processing/test/debug_dump_replayer.h"

#include "webrtc/base/checks.h"
#include "webrtc/modules/audio_processing/test/protobuf_utils.h"


namespace webrtc {
namespace test {

namespace {

void MaybeResetBuffer(std::unique_ptr<ChannelBuffer<float>>* buffer,
                      const StreamConfig& config) {
  auto& buffer_ref = *buffer;
  if (!buffer_ref.get() || buffer_ref->num_frames() != config.num_frames() ||
      buffer_ref->num_channels() != config.num_channels()) {
    buffer_ref.reset(new ChannelBuffer<float>(config.num_frames(),
                                             config.num_channels()));
  }
}

}  // namespace

DebugDumpReplayer::DebugDumpReplayer()
    : input_(nullptr),  // will be created upon usage.
      reverse_(nullptr),
      output_(nullptr),
      apm_(nullptr),
      debug_file_(nullptr) {}

DebugDumpReplayer::~DebugDumpReplayer() {
  if (debug_file_)
    fclose(debug_file_);
}

bool DebugDumpReplayer::SetDumpFile(const std::string& filename) {
  debug_file_ = fopen(filename.c_str(), "rb");
  LoadNextMessage();
  return debug_file_;
}

// Get next event that has not run.
rtc::Optional<audioproc::Event> DebugDumpReplayer::GetNextEvent() const {
  if (!has_next_event_)
    return rtc::Optional<audioproc::Event>();
  else
    return rtc::Optional<audioproc::Event>(next_event_);
}

// Run the next event. Returns the event type.
bool DebugDumpReplayer::RunNextEvent() {
  if (!has_next_event_)
    return false;
  switch (next_event_.type()) {
    case audioproc::Event::INIT:
      OnInitEvent(next_event_.init());
      break;
    case audioproc::Event::STREAM:
      OnStreamEvent(next_event_.stream());
      break;
    case audioproc::Event::REVERSE_STREAM:
      OnReverseStreamEvent(next_event_.reverse_stream());
      break;
    case audioproc::Event::CONFIG:
      OnConfigEvent(next_event_.config());
      break;
    case audioproc::Event::UNKNOWN_EVENT:
      // We do not expect to receive UNKNOWN event.
      return false;
  }
  LoadNextMessage();
  return true;
}

const ChannelBuffer<float>* DebugDumpReplayer::GetOutput() const {
  return output_.get();
}

StreamConfig DebugDumpReplayer::GetOutputConfig() const {
  return output_config_;
}

// OnInitEvent reset the input/output/reserve channel format.
void DebugDumpReplayer::OnInitEvent(const audioproc::Init& msg) {
  RTC_CHECK(msg.has_num_input_channels());
  RTC_CHECK(msg.has_output_sample_rate());
  RTC_CHECK(msg.has_num_output_channels());
  RTC_CHECK(msg.has_reverse_sample_rate());
  RTC_CHECK(msg.has_num_reverse_channels());

  input_config_ = StreamConfig(msg.sample_rate(), msg.num_input_channels());
  output_config_ =
      StreamConfig(msg.output_sample_rate(), msg.num_output_channels());
  reverse_config_ =
      StreamConfig(msg.reverse_sample_rate(), msg.num_reverse_channels());

  MaybeResetBuffer(&input_, input_config_);
  MaybeResetBuffer(&output_, output_config_);
  MaybeResetBuffer(&reverse_, reverse_config_);
}

// OnStreamEvent replays an input signal and verifies the output.
void DebugDumpReplayer::OnStreamEvent(const audioproc::Stream& msg) {
  // APM should have been created.
  RTC_CHECK(apm_.get());

  RTC_CHECK_EQ(AudioProcessing::kNoError,
               apm_->gain_control()->set_stream_analog_level(msg.level()));
  RTC_CHECK_EQ(AudioProcessing::kNoError,
               apm_->set_stream_delay_ms(msg.delay()));

  apm_->echo_cancellation()->set_stream_drift_samples(msg.drift());
  if (msg.has_keypress()) {
    apm_->set_stream_key_pressed(msg.keypress());
  } else {
    apm_->set_stream_key_pressed(true);
  }

  RTC_CHECK_EQ(input_config_.num_channels(),
               static_cast<size_t>(msg.input_channel_size()));
  RTC_CHECK_EQ(input_config_.num_frames() * sizeof(float),
               msg.input_channel(0).size());

  for (int i = 0; i < msg.input_channel_size(); ++i) {
    memcpy(input_->channels()[i], msg.input_channel(i).data(),
           msg.input_channel(i).size());
  }

  RTC_CHECK_EQ(AudioProcessing::kNoError,
               apm_->ProcessStream(input_->channels(), input_config_,
                                   output_config_, output_->channels()));
}

void DebugDumpReplayer::OnReverseStreamEvent(
    const audioproc::ReverseStream& msg) {
  // APM should have been created.
  RTC_CHECK(apm_.get());

  RTC_CHECK_GT(msg.channel_size(), 0);
  RTC_CHECK_EQ(reverse_config_.num_channels(),
               static_cast<size_t>(msg.channel_size()));
  RTC_CHECK_EQ(reverse_config_.num_frames() * sizeof(float),
               msg.channel(0).size());

  for (int i = 0; i < msg.channel_size(); ++i) {
    memcpy(reverse_->channels()[i], msg.channel(i).data(),
           msg.channel(i).size());
  }

  RTC_CHECK_EQ(
      AudioProcessing::kNoError,
      apm_->ProcessReverseStream(reverse_->channels(), reverse_config_,
                                 reverse_config_, reverse_->channels()));
}

void DebugDumpReplayer::OnConfigEvent(const audioproc::Config& msg) {
  MaybeRecreateApm(msg);
  ConfigureApm(msg);
}

void DebugDumpReplayer::MaybeRecreateApm(const audioproc::Config& msg) {
  // These configurations cannot be changed on the fly.
  Config config;
  RTC_CHECK(msg.has_aec_delay_agnostic_enabled());
  config.Set<DelayAgnostic>(
      new DelayAgnostic(msg.aec_delay_agnostic_enabled()));

  RTC_CHECK(msg.has_noise_robust_agc_enabled());
  config.Set<ExperimentalAgc>(
      new ExperimentalAgc(msg.noise_robust_agc_enabled()));

  RTC_CHECK(msg.has_transient_suppression_enabled());
  config.Set<ExperimentalNs>(
      new ExperimentalNs(msg.transient_suppression_enabled()));

  RTC_CHECK(msg.has_aec_extended_filter_enabled());
  config.Set<ExtendedFilter>(
      new ExtendedFilter(msg.aec_extended_filter_enabled()));

  RTC_CHECK(msg.has_intelligibility_enhancer_enabled());
  config.Set<Intelligibility>(
      new Intelligibility(msg.intelligibility_enhancer_enabled()));

  // We only create APM once, since changes on these fields should not
  // happen in current implementation.
  if (!apm_.get()) {
    apm_.reset(AudioProcessing::Create(config));
  }
}

void DebugDumpReplayer::ConfigureApm(const audioproc::Config& msg) {
  // AEC configs.
  RTC_CHECK(msg.has_aec_enabled());
  RTC_CHECK_EQ(AudioProcessing::kNoError,
               apm_->echo_cancellation()->Enable(msg.aec_enabled()));

  RTC_CHECK(msg.has_aec_drift_compensation_enabled());
  RTC_CHECK_EQ(AudioProcessing::kNoError,
               apm_->echo_cancellation()->enable_drift_compensation(
                   msg.aec_drift_compensation_enabled()));

  RTC_CHECK(msg.has_aec_suppression_level());
  RTC_CHECK_EQ(AudioProcessing::kNoError,
               apm_->echo_cancellation()->set_suppression_level(
                   static_cast<EchoCancellation::SuppressionLevel>(
                       msg.aec_suppression_level())));

  // AECM configs.
  RTC_CHECK(msg.has_aecm_enabled());
  RTC_CHECK_EQ(AudioProcessing::kNoError,
               apm_->echo_control_mobile()->Enable(msg.aecm_enabled()));

  RTC_CHECK(msg.has_aecm_comfort_noise_enabled());
  RTC_CHECK_EQ(AudioProcessing::kNoError,
               apm_->echo_control_mobile()->enable_comfort_noise(
                   msg.aecm_comfort_noise_enabled()));

  RTC_CHECK(msg.has_aecm_routing_mode());
  RTC_CHECK_EQ(AudioProcessing::kNoError,
               apm_->echo_control_mobile()->set_routing_mode(
                   static_cast<EchoControlMobile::RoutingMode>(
                       msg.aecm_routing_mode())));

  // AGC configs.
  RTC_CHECK(msg.has_agc_enabled());
  RTC_CHECK_EQ(AudioProcessing::kNoError,
               apm_->gain_control()->Enable(msg.agc_enabled()));

  RTC_CHECK(msg.has_agc_mode());
  RTC_CHECK_EQ(AudioProcessing::kNoError,
               apm_->gain_control()->set_mode(
                   static_cast<GainControl::Mode>(msg.agc_mode())));

  RTC_CHECK(msg.has_agc_limiter_enabled());
  RTC_CHECK_EQ(AudioProcessing::kNoError,
               apm_->gain_control()->enable_limiter(msg.agc_limiter_enabled()));

  // HPF configs.
  RTC_CHECK(msg.has_hpf_enabled());
  RTC_CHECK_EQ(AudioProcessing::kNoError,
               apm_->high_pass_filter()->Enable(msg.hpf_enabled()));

  // NS configs.
  RTC_CHECK(msg.has_ns_enabled());
  RTC_CHECK_EQ(AudioProcessing::kNoError,
               apm_->noise_suppression()->Enable(msg.ns_enabled()));

  RTC_CHECK(msg.has_ns_level());
  RTC_CHECK_EQ(AudioProcessing::kNoError,
               apm_->noise_suppression()->set_level(
                   static_cast<NoiseSuppression::Level>(msg.ns_level())));
}

void DebugDumpReplayer::LoadNextMessage() {
  has_next_event_ =
      debug_file_ && ReadMessageFromFile(debug_file_, &next_event_);
}

}  // namespace test
}  // namespace webrtc
