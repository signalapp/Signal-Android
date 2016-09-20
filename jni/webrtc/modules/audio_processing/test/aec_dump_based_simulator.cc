/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <iostream>

#include "webrtc/modules/audio_processing/test/aec_dump_based_simulator.h"

#include "webrtc/base/checks.h"
#include "webrtc/modules/audio_processing/test/protobuf_utils.h"
#include "webrtc/test/testsupport/trace_to_stderr.h"

namespace webrtc {
namespace test {
namespace {

// Verify output bitexactness for the fixed interface.
// TODO(peah): Check whether it would make sense to add a threshold
// to use for checking the bitexactness in a soft manner.
bool VerifyFixedBitExactness(const webrtc::audioproc::Stream& msg,
                             const AudioFrame& frame) {
  if ((sizeof(int16_t) * frame.samples_per_channel_ * frame.num_channels_) !=
      msg.output_data().size()) {
    return false;
  } else {
    for (size_t k = 0; k < frame.num_channels_ * frame.samples_per_channel_;
         ++k) {
      if (msg.output_data().data()[k] != frame.data_[k]) {
        return false;
      }
    }
  }
  return true;
}

// Verify output bitexactness for the float interface.
bool VerifyFloatBitExactness(const webrtc::audioproc::Stream& msg,
                             const StreamConfig& out_config,
                             const ChannelBuffer<float>& out_buf) {
  if (static_cast<size_t>(msg.output_channel_size()) !=
          out_config.num_channels() ||
      msg.output_channel(0).size() != out_config.num_frames()) {
    return false;
  } else {
    for (int ch = 0; ch < msg.output_channel_size(); ++ch) {
      for (size_t sample = 0; sample < out_config.num_frames(); ++sample) {
        if (msg.output_channel(ch).data()[sample] !=
            out_buf.channels()[ch][sample]) {
          return false;
        }
      }
    }
  }
  return true;
}

}  // namespace

void AecDumpBasedSimulator::PrepareProcessStreamCall(
    const webrtc::audioproc::Stream& msg) {
  if (msg.has_input_data()) {
    // Fixed interface processing.
    // Verify interface invariance.
    RTC_CHECK(interface_used_ == InterfaceType::kFixedInterface ||
              interface_used_ == InterfaceType::kNotSpecified);
    interface_used_ = InterfaceType::kFixedInterface;

    // Populate input buffer.
    RTC_CHECK_EQ(sizeof(fwd_frame_.data_[0]) * fwd_frame_.samples_per_channel_ *
                     fwd_frame_.num_channels_,
                 msg.input_data().size());
    memcpy(fwd_frame_.data_, msg.input_data().data(), msg.input_data().size());
  } else {
    // Float interface processing.
    // Verify interface invariance.
    RTC_CHECK(interface_used_ == InterfaceType::kFloatInterface ||
              interface_used_ == InterfaceType::kNotSpecified);
    interface_used_ = InterfaceType::kFloatInterface;

    RTC_CHECK_EQ(in_buf_->num_channels(),
                 static_cast<size_t>(msg.input_channel_size()));

    // Populate input buffer.
    for (int i = 0; i < msg.input_channel_size(); ++i) {
      RTC_CHECK_EQ(in_buf_->num_frames() * sizeof(*in_buf_->channels()[i]),
                   msg.input_channel(i).size());
      std::memcpy(in_buf_->channels()[i], msg.input_channel(i).data(),
                  msg.input_channel(i).size());
    }
  }

  if (!settings_.stream_delay) {
    if (msg.has_delay()) {
      RTC_CHECK_EQ(AudioProcessing::kNoError,
                   ap_->set_stream_delay_ms(msg.delay()));
    }
  } else {
    RTC_CHECK_EQ(AudioProcessing::kNoError,
                 ap_->set_stream_delay_ms(*settings_.stream_delay));
  }

  if (!settings_.stream_drift_samples) {
    if (msg.has_drift()) {
      ap_->echo_cancellation()->set_stream_drift_samples(msg.drift());
    }
  } else {
    ap_->echo_cancellation()->set_stream_drift_samples(
        *settings_.stream_drift_samples);
  }

  if (!settings_.use_ts) {
    if (msg.has_keypress()) {
      ap_->set_stream_key_pressed(msg.keypress());
    }
  } else {
    ap_->set_stream_key_pressed(*settings_.use_ts);
  }

  // TODO(peah): Add support for controlling the analog level via the
  // command-line.
  if (msg.has_level()) {
    RTC_CHECK_EQ(AudioProcessing::kNoError,
                 ap_->gain_control()->set_stream_analog_level(msg.level()));
  }
}

void AecDumpBasedSimulator::VerifyProcessStreamBitExactness(
    const webrtc::audioproc::Stream& msg) {
  if (bitexact_output_) {
    if (interface_used_ == InterfaceType::kFixedInterface) {
      bitexact_output_ = VerifyFixedBitExactness(msg, fwd_frame_);
    } else {
      bitexact_output_ = VerifyFloatBitExactness(msg, out_config_, *out_buf_);
    }
  }
}

void AecDumpBasedSimulator::PrepareReverseProcessStreamCall(
    const webrtc::audioproc::ReverseStream& msg) {
  if (msg.has_data()) {
    // Fixed interface processing.
    // Verify interface invariance.
    RTC_CHECK(interface_used_ == InterfaceType::kFixedInterface ||
              interface_used_ == InterfaceType::kNotSpecified);
    interface_used_ = InterfaceType::kFixedInterface;

    // Populate input buffer.
    RTC_CHECK_EQ(sizeof(int16_t) * rev_frame_.samples_per_channel_ *
                     rev_frame_.num_channels_,
                 msg.data().size());
    memcpy(rev_frame_.data_, msg.data().data(), msg.data().size());
  } else {
    // Float interface processing.
    // Verify interface invariance.
    RTC_CHECK(interface_used_ == InterfaceType::kFloatInterface ||
              interface_used_ == InterfaceType::kNotSpecified);
    interface_used_ = InterfaceType::kFloatInterface;

    RTC_CHECK_EQ(reverse_in_buf_->num_channels(),
                 static_cast<size_t>(msg.channel_size()));

    // Populate input buffer.
    for (int i = 0; i < msg.channel_size(); ++i) {
      RTC_CHECK_EQ(reverse_in_buf_->num_frames() *
                       sizeof(*reverse_in_buf_->channels()[i]),
                   msg.channel(i).size());
      std::memcpy(reverse_in_buf_->channels()[i], msg.channel(i).data(),
                  msg.channel(i).size());
    }
  }
}

void AecDumpBasedSimulator::Process() {
  std::unique_ptr<test::TraceToStderr> trace_to_stderr;
  if (settings_.use_verbose_logging) {
    trace_to_stderr.reset(new test::TraceToStderr(true));
  }

  CreateAudioProcessor();
  dump_input_file_ = OpenFile(settings_.aec_dump_input_filename->c_str(), "rb");

  webrtc::audioproc::Event event_msg;
  int num_forward_chunks_processed = 0;
  const float kOneBykChunksPerSecond =
      1.f / AudioProcessingSimulator::kChunksPerSecond;
  while (ReadMessageFromFile(dump_input_file_, &event_msg)) {
    switch (event_msg.type()) {
      case webrtc::audioproc::Event::INIT:
        RTC_CHECK(event_msg.has_init());
        HandleMessage(event_msg.init());
        break;
      case webrtc::audioproc::Event::STREAM:
        RTC_CHECK(event_msg.has_stream());
        HandleMessage(event_msg.stream());
        ++num_forward_chunks_processed;
        break;
      case webrtc::audioproc::Event::REVERSE_STREAM:
        RTC_CHECK(event_msg.has_reverse_stream());
        HandleMessage(event_msg.reverse_stream());
        break;
      case webrtc::audioproc::Event::CONFIG:
        RTC_CHECK(event_msg.has_config());
        HandleMessage(event_msg.config());
        break;
      default:
        RTC_CHECK(false);
    }
    if (trace_to_stderr) {
      trace_to_stderr->SetTimeSeconds(num_forward_chunks_processed *
                                      kOneBykChunksPerSecond);
    }
  }

  fclose(dump_input_file_);

  DestroyAudioProcessor();
}

void AecDumpBasedSimulator::HandleMessage(
    const webrtc::audioproc::Config& msg) {
  if (settings_.use_verbose_logging) {
    std::cout << "Config at frame:" << std::endl;
    std::cout << " Forward: " << get_num_process_stream_calls() << std::endl;
    std::cout << " Reverse: " << get_num_reverse_process_stream_calls()
              << std::endl;
  }

  if (!settings_.discard_all_settings_in_aecdump) {
    if (settings_.use_verbose_logging) {
      std::cout << "Setting used in config:" << std::endl;
    }
    Config config;

    if (msg.has_aec_enabled() || settings_.use_aec) {
      bool enable = settings_.use_aec ? *settings_.use_aec : msg.aec_enabled();
      RTC_CHECK_EQ(AudioProcessing::kNoError,
                   ap_->echo_cancellation()->Enable(enable));
      if (settings_.use_verbose_logging) {
        std::cout << " aec_enabled: " << (enable ? "true" : "false")
                  << std::endl;
      }
    }

    if (msg.has_aec_delay_agnostic_enabled() || settings_.use_delay_agnostic) {
      bool enable = settings_.use_delay_agnostic
                        ? *settings_.use_delay_agnostic
                        : msg.aec_delay_agnostic_enabled();
      config.Set<DelayAgnostic>(new DelayAgnostic(enable));
      if (settings_.use_verbose_logging) {
        std::cout << " aec_delay_agnostic_enabled: "
                  << (enable ? "true" : "false") << std::endl;
      }
    }

    if (msg.has_aec_drift_compensation_enabled() ||
        settings_.use_drift_compensation) {
      bool enable = settings_.use_drift_compensation
                        ? *settings_.use_drift_compensation
                        : msg.aec_drift_compensation_enabled();
      RTC_CHECK_EQ(AudioProcessing::kNoError,
                   ap_->echo_cancellation()->enable_drift_compensation(enable));
      if (settings_.use_verbose_logging) {
        std::cout << " aec_drift_compensation_enabled: "
                  << (enable ? "true" : "false") << std::endl;
      }
    }

    if (msg.has_aec_extended_filter_enabled() ||
        settings_.use_extended_filter) {
      bool enable = settings_.use_extended_filter
                        ? *settings_.use_extended_filter
                        : msg.aec_extended_filter_enabled();
      config.Set<ExtendedFilter>(new ExtendedFilter(enable));
      if (settings_.use_verbose_logging) {
        std::cout << " aec_extended_filter_enabled: "
                  << (enable ? "true" : "false") << std::endl;
      }
    }

    if (msg.has_aec_suppression_level() || settings_.aec_suppression_level) {
      int level = settings_.aec_suppression_level
                      ? *settings_.aec_suppression_level
                      : msg.aec_suppression_level();
      RTC_CHECK_EQ(
          AudioProcessing::kNoError,
          ap_->echo_cancellation()->set_suppression_level(
              static_cast<webrtc::EchoCancellation::SuppressionLevel>(level)));
      if (settings_.use_verbose_logging) {
        std::cout << " aec_suppression_level: " << level << std::endl;
      }
    }

    if (msg.has_aecm_enabled() || settings_.use_aecm) {
      bool enable =
          settings_.use_aecm ? *settings_.use_aecm : msg.aecm_enabled();
      RTC_CHECK_EQ(AudioProcessing::kNoError,
                   ap_->echo_control_mobile()->Enable(enable));
      if (settings_.use_verbose_logging) {
        std::cout << " aecm_enabled: " << (enable ? "true" : "false")
                  << std::endl;
      }
    }

    if (msg.has_aecm_comfort_noise_enabled() ||
        settings_.use_aecm_comfort_noise) {
      bool enable = settings_.use_aecm_comfort_noise
                        ? *settings_.use_aecm_comfort_noise
                        : msg.aecm_comfort_noise_enabled();
      RTC_CHECK_EQ(AudioProcessing::kNoError,
                   ap_->echo_control_mobile()->enable_comfort_noise(enable));
      if (settings_.use_verbose_logging) {
        std::cout << " aecm_comfort_noise_enabled: "
                  << (enable ? "true" : "false") << std::endl;
      }
    }

    if (msg.has_aecm_routing_mode() || settings_.aecm_routing_mode) {
      int routing_mode = settings_.aecm_routing_mode
                             ? *settings_.aecm_routing_mode
                             : msg.aecm_routing_mode();
      RTC_CHECK_EQ(AudioProcessing::kNoError,
                   ap_->echo_control_mobile()->set_routing_mode(
                       static_cast<webrtc::EchoControlMobile::RoutingMode>(
                           routing_mode)));
      if (settings_.use_verbose_logging) {
        std::cout << " aecm_routing_mode: " << routing_mode << std::endl;
      }
    }

    if (msg.has_agc_enabled() || settings_.use_agc) {
      bool enable = settings_.use_agc ? *settings_.use_agc : msg.agc_enabled();
      RTC_CHECK_EQ(AudioProcessing::kNoError,
                   ap_->gain_control()->Enable(enable));
      if (settings_.use_verbose_logging) {
        std::cout << " agc_enabled: " << (enable ? "true" : "false")
                  << std::endl;
      }
    }

    if (msg.has_agc_mode() || settings_.agc_mode) {
      int mode = settings_.agc_mode ? *settings_.agc_mode : msg.agc_mode();
      RTC_CHECK_EQ(AudioProcessing::kNoError,
                   ap_->gain_control()->set_mode(
                       static_cast<webrtc::GainControl::Mode>(mode)));
      if (settings_.use_verbose_logging) {
        std::cout << " agc_mode: " << mode << std::endl;
      }
    }

    if (msg.has_agc_limiter_enabled() || settings_.use_agc_limiter) {
      bool enable = settings_.use_agc_limiter ? *settings_.use_agc_limiter
                                              : msg.agc_limiter_enabled();
      RTC_CHECK_EQ(AudioProcessing::kNoError,
                   ap_->gain_control()->enable_limiter(enable));
      if (settings_.use_verbose_logging) {
        std::cout << " agc_limiter_enabled: " << (enable ? "true" : "false")
                  << std::endl;
      }
    }

    // TODO(peah): Add support for controlling the Experimental AGC from the
    // command line.
    if (msg.has_noise_robust_agc_enabled()) {
      config.Set<ExperimentalAgc>(
          new ExperimentalAgc(msg.noise_robust_agc_enabled()));
      if (settings_.use_verbose_logging) {
        std::cout << " noise_robust_agc_enabled: "
                  << (msg.noise_robust_agc_enabled() ? "true" : "false")
                  << std::endl;
      }
    }

    if (msg.has_transient_suppression_enabled() || settings_.use_ts) {
      bool enable = settings_.use_ts ? *settings_.use_ts
                                     : msg.transient_suppression_enabled();
      config.Set<ExperimentalNs>(new ExperimentalNs(enable));
      if (settings_.use_verbose_logging) {
        std::cout << " transient_suppression_enabled: "
                  << (enable ? "true" : "false") << std::endl;
      }
    }

    if (msg.has_intelligibility_enhancer_enabled() || settings_.use_ie) {
      bool enable = settings_.use_ie ? *settings_.use_ie
                                     : msg.intelligibility_enhancer_enabled();
      config.Set<Intelligibility>(new Intelligibility(enable));
      if (settings_.use_verbose_logging) {
        std::cout << " intelligibility_enhancer_enabled: "
                  << (enable ? "true" : "false") << std::endl;
      }
    }

    if (msg.has_hpf_enabled() || settings_.use_hpf) {
      bool enable = settings_.use_hpf ? *settings_.use_hpf : msg.hpf_enabled();
      RTC_CHECK_EQ(AudioProcessing::kNoError,
                   ap_->high_pass_filter()->Enable(enable));
      if (settings_.use_verbose_logging) {
        std::cout << " hpf_enabled: " << (enable ? "true" : "false")
                  << std::endl;
      }
    }

    if (msg.has_ns_enabled() || settings_.use_ns) {
      bool enable = settings_.use_ns ? *settings_.use_ns : msg.ns_enabled();
      RTC_CHECK_EQ(AudioProcessing::kNoError,
                   ap_->noise_suppression()->Enable(enable));
      if (settings_.use_verbose_logging) {
        std::cout << " ns_enabled: " << (enable ? "true" : "false")
                  << std::endl;
      }
    }

    if (msg.has_ns_level() || settings_.ns_level) {
      int level = settings_.ns_level ? *settings_.ns_level : msg.ns_level();
      RTC_CHECK_EQ(AudioProcessing::kNoError,
                   ap_->noise_suppression()->set_level(
                       static_cast<NoiseSuppression::Level>(level)));
      if (settings_.use_verbose_logging) {
        std::cout << " ns_level: " << level << std::endl;
      }
    }

    if (settings_.use_verbose_logging && msg.has_experiments_description() &&
        msg.experiments_description().size() > 0) {
      std::cout << " experiments not included by default in the simulation: "
                << msg.experiments_description() << std::endl;
    }

    if (settings_.use_refined_adaptive_filter) {
      config.Set<RefinedAdaptiveFilter>(
          new RefinedAdaptiveFilter(*settings_.use_refined_adaptive_filter));
    }

    if (settings_.use_aec3) {
      config.Set<EchoCanceller3>(new EchoCanceller3(*settings_.use_aec3));
    }

    ap_->SetExtraOptions(config);
  }
}

void AecDumpBasedSimulator::HandleMessage(const webrtc::audioproc::Init& msg) {
  RTC_CHECK(msg.has_sample_rate());
  RTC_CHECK(msg.has_num_input_channels());
  RTC_CHECK(msg.has_num_reverse_channels());
  RTC_CHECK(msg.has_reverse_sample_rate());

  if (settings_.use_verbose_logging) {
    std::cout << "Init at frame:" << std::endl;
    std::cout << " Forward: " << get_num_process_stream_calls() << std::endl;
    std::cout << " Reverse: " << get_num_reverse_process_stream_calls()
              << std::endl;
  }

  int num_output_channels;
  if (settings_.output_num_channels) {
    num_output_channels = *settings_.output_num_channels;
  } else {
    num_output_channels = msg.has_num_output_channels()
                              ? msg.num_output_channels()
                              : msg.num_input_channels();
  }

  int output_sample_rate;
  if (settings_.output_sample_rate_hz) {
    output_sample_rate = *settings_.output_sample_rate_hz;
  } else {
    output_sample_rate = msg.has_output_sample_rate() ? msg.output_sample_rate()
                                                      : msg.sample_rate();
  }

  int num_reverse_output_channels;
  if (settings_.reverse_output_num_channels) {
    num_reverse_output_channels = *settings_.reverse_output_num_channels;
  } else {
    num_reverse_output_channels = msg.has_num_reverse_output_channels()
                                      ? msg.num_reverse_output_channels()
                                      : msg.num_reverse_channels();
  }

  int reverse_output_sample_rate;
  if (settings_.reverse_output_sample_rate_hz) {
    reverse_output_sample_rate = *settings_.reverse_output_sample_rate_hz;
  } else {
    reverse_output_sample_rate = msg.has_reverse_output_sample_rate()
                                     ? msg.reverse_output_sample_rate()
                                     : msg.reverse_sample_rate();
  }

  SetupBuffersConfigsOutputs(
      msg.sample_rate(), output_sample_rate, msg.reverse_sample_rate(),
      reverse_output_sample_rate, msg.num_input_channels(), num_output_channels,
      msg.num_reverse_channels(), num_reverse_output_channels);
}

void AecDumpBasedSimulator::HandleMessage(
    const webrtc::audioproc::Stream& msg) {
  PrepareProcessStreamCall(msg);
  ProcessStream(interface_used_ == InterfaceType::kFixedInterface);
  VerifyProcessStreamBitExactness(msg);
}

void AecDumpBasedSimulator::HandleMessage(
    const webrtc::audioproc::ReverseStream& msg) {
  PrepareReverseProcessStreamCall(msg);
  ProcessReverseStream(interface_used_ == InterfaceType::kFixedInterface);
}

}  // namespace test
}  // namespace webrtc
