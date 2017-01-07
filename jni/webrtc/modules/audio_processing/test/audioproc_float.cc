/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <iostream>
#include <memory>

#include <string.h>

#include "gflags/gflags.h"
#include "webrtc/modules/audio_processing/include/audio_processing.h"
#include "webrtc/modules/audio_processing/test/aec_dump_based_simulator.h"
#include "webrtc/modules/audio_processing/test/audio_processing_simulator.h"
#include "webrtc/modules/audio_processing/test/wav_based_simulator.h"

namespace webrtc {
namespace test {
namespace {

const int kParameterNotSpecifiedValue = -10000;

const char kUsageDescription[] =
    "Usage: audioproc_f [options] -i <input.wav>\n"
    "                   or\n"
    "       audioproc_f [options] -dump_input <aec_dump>\n"
    "\n\n"
    "Command-line tool to simulate a call using the audio "
    "processing module, either based on wav files or "
    "protobuf debug dump recordings.";

DEFINE_string(dump_input, "", "Aec dump input filename");
DEFINE_string(dump_output, "", "Aec dump output filename");
DEFINE_string(i, "", "Forward stream input wav filename");
DEFINE_string(o, "", "Forward stream output wav filename");
DEFINE_string(ri, "", "Reverse stream input wav filename");
DEFINE_string(ro, "", "Reverse stream output wav filename");
DEFINE_int32(output_num_channels,
             kParameterNotSpecifiedValue,
             "Number of forward stream output channels");
DEFINE_int32(reverse_output_num_channels,
             kParameterNotSpecifiedValue,
             "Number of Reverse stream output channels");
DEFINE_int32(output_sample_rate_hz,
             kParameterNotSpecifiedValue,
             "Forward stream output sample rate in Hz");
DEFINE_int32(reverse_output_sample_rate_hz,
             kParameterNotSpecifiedValue,
             "Reverse stream output sample rate in Hz");
DEFINE_string(mic_positions,
              "",
              "Space delimited cartesian coordinates of microphones in "
              "meters. The coordinates of each point are contiguous. For a "
              "two element array: \"x1 y1 z1 x2 y2 z2\"");
DEFINE_int32(target_angle_degrees,
             90,
             "The azimuth of the target in degrees (0-359). Only applies to "
             "beamforming.");
DEFINE_bool(fixed_interface,
            false,
            "Use the fixed interface when operating on wav files");
DEFINE_int32(aec,
             kParameterNotSpecifiedValue,
             "Activate (1) or deactivate(0) the echo canceller");
DEFINE_int32(aecm,
             kParameterNotSpecifiedValue,
             "Activate (1) or deactivate(0) the mobile echo controller");
DEFINE_int32(agc,
             kParameterNotSpecifiedValue,
             "Activate (1) or deactivate(0) the AGC");
DEFINE_int32(hpf,
             kParameterNotSpecifiedValue,
             "Activate (1) or deactivate(0) the high-pass filter");
DEFINE_int32(ns,
             kParameterNotSpecifiedValue,
             "Activate (1) or deactivate(0) the noise suppressor");
DEFINE_int32(ts,
             kParameterNotSpecifiedValue,
             "Activate (1) or deactivate(0) the transient suppressor");
DEFINE_int32(bf,
             kParameterNotSpecifiedValue,
             "Activate (1) or deactivate(0) the beamformer");
DEFINE_int32(ie,
             kParameterNotSpecifiedValue,
             "Activate (1) or deactivate(0) the intelligibility enhancer");
DEFINE_int32(vad,
             kParameterNotSpecifiedValue,
             "Activate (1) or deactivate(0) the voice activity detector");
DEFINE_int32(le,
             kParameterNotSpecifiedValue,
             "Activate (1) or deactivate(0) the level estimator");
DEFINE_bool(all_default,
            false,
            "Activate all of the default components (will be overridden by any "
            "other settings)");
DEFINE_int32(aec_suppression_level,
             kParameterNotSpecifiedValue,
             "Set the aec suppression level (0-2)");
DEFINE_int32(delay_agnostic,
             kParameterNotSpecifiedValue,
             "Activate (1) or deactivate(0) the AEC delay agnostic mode");
DEFINE_int32(extended_filter,
             kParameterNotSpecifiedValue,
             "Activate (1) or deactivate(0) the AEC extended filter mode");
DEFINE_int32(drift_compensation,
             kParameterNotSpecifiedValue,
             "Activate (1) or deactivate(0) the drift compensation");
DEFINE_int32(aec3,
             kParameterNotSpecifiedValue,
             "Activate (1) or deactivate(0) the experimental AEC mode AEC3");
DEFINE_int32(
    refined_adaptive_filter,
    kParameterNotSpecifiedValue,
    "Activate (1) or deactivate(0) the refined adaptive filter functionality");
DEFINE_int32(aecm_routing_mode,
             kParameterNotSpecifiedValue,
             "Specify the AECM routing mode (0-4)");
DEFINE_int32(aecm_comfort_noise,
             kParameterNotSpecifiedValue,
             "Activate (1) or deactivate(0) the AECM comfort noise");
DEFINE_int32(agc_mode,
             kParameterNotSpecifiedValue,
             "Specify the AGC mode (0-2)");
DEFINE_int32(agc_target_level,
             kParameterNotSpecifiedValue,
             "Specify the AGC target level (0-31)");
DEFINE_int32(agc_limiter,
             kParameterNotSpecifiedValue,
             "Activate (1) or deactivate(0) the level estimator");
DEFINE_int32(agc_compression_gain,
             kParameterNotSpecifiedValue,
             "Specify the AGC compression gain (0-90)");
DEFINE_int32(vad_likelihood,
             kParameterNotSpecifiedValue,
             "Specify the VAD likelihood (0-3)");
DEFINE_int32(ns_level,
             kParameterNotSpecifiedValue,
             "Specify the NS level (0-3)");
DEFINE_int32(stream_delay,
             kParameterNotSpecifiedValue,
             "Specify the stream delay in ms to use");
DEFINE_int32(stream_drift_samples,
             kParameterNotSpecifiedValue,
             "Specify the number of stream drift samples to use");
DEFINE_bool(performance_report, false, "Report the APM performance ");
DEFINE_bool(verbose, false, "Produce verbose output");
DEFINE_bool(bitexactness_report,
            false,
            "Report bitexactness for aec dump result reproduction");
DEFINE_bool(discard_settings_in_aecdump,
            false,
            "Discard any config settings specified in the aec dump");
DEFINE_bool(store_intermediate_output,
            false,
            "Creates new output files after each init");

void SetSettingIfSpecified(const std::string value,
                           rtc::Optional<std::string>* parameter) {
  if (value.compare("") != 0) {
    *parameter = rtc::Optional<std::string>(value);
  }
}

void SetSettingIfSpecified(int value, rtc::Optional<int>* parameter) {
  if (value != kParameterNotSpecifiedValue) {
    *parameter = rtc::Optional<int>(value);
  }
}

void SetSettingIfFlagSet(int32_t flag, rtc::Optional<bool>* parameter) {
  if (flag == 0) {
    *parameter = rtc::Optional<bool>(false);
  } else if (flag == 1) {
    *parameter = rtc::Optional<bool>(true);
  }
}

SimulationSettings CreateSettings() {
  SimulationSettings settings;
  if (FLAGS_all_default) {
    settings.use_le = rtc::Optional<bool>(true);
    settings.use_vad = rtc::Optional<bool>(true);
    settings.use_ie = rtc::Optional<bool>(false);
    settings.use_bf = rtc::Optional<bool>(false);
    settings.use_ts = rtc::Optional<bool>(true);
    settings.use_ns = rtc::Optional<bool>(true);
    settings.use_hpf = rtc::Optional<bool>(true);
    settings.use_agc = rtc::Optional<bool>(true);
    settings.use_aec = rtc::Optional<bool>(true);
    settings.use_aecm = rtc::Optional<bool>(false);
  }
  SetSettingIfSpecified(FLAGS_dump_input, &settings.aec_dump_input_filename);
  SetSettingIfSpecified(FLAGS_dump_output, &settings.aec_dump_output_filename);
  SetSettingIfSpecified(FLAGS_i, &settings.input_filename);
  SetSettingIfSpecified(FLAGS_o, &settings.output_filename);
  SetSettingIfSpecified(FLAGS_ri, &settings.reverse_input_filename);
  SetSettingIfSpecified(FLAGS_ro, &settings.reverse_output_filename);
  SetSettingIfSpecified(FLAGS_output_num_channels,
                        &settings.output_num_channels);
  SetSettingIfSpecified(FLAGS_reverse_output_num_channels,
                        &settings.reverse_output_num_channels);
  SetSettingIfSpecified(FLAGS_output_sample_rate_hz,
                        &settings.output_sample_rate_hz);
  SetSettingIfSpecified(FLAGS_reverse_output_sample_rate_hz,
                        &settings.reverse_output_sample_rate_hz);
  SetSettingIfSpecified(FLAGS_mic_positions, &settings.microphone_positions);
  settings.target_angle_degrees = FLAGS_target_angle_degrees;
  SetSettingIfFlagSet(FLAGS_aec, &settings.use_aec);
  SetSettingIfFlagSet(FLAGS_aecm, &settings.use_aecm);
  SetSettingIfFlagSet(FLAGS_agc, &settings.use_agc);
  SetSettingIfFlagSet(FLAGS_hpf, &settings.use_hpf);
  SetSettingIfFlagSet(FLAGS_ns, &settings.use_ns);
  SetSettingIfFlagSet(FLAGS_ts, &settings.use_ts);
  SetSettingIfFlagSet(FLAGS_bf, &settings.use_bf);
  SetSettingIfFlagSet(FLAGS_ie, &settings.use_ie);
  SetSettingIfFlagSet(FLAGS_vad, &settings.use_vad);
  SetSettingIfFlagSet(FLAGS_le, &settings.use_le);
  SetSettingIfSpecified(FLAGS_aec_suppression_level,
                        &settings.aec_suppression_level);
  SetSettingIfFlagSet(FLAGS_delay_agnostic, &settings.use_delay_agnostic);
  SetSettingIfFlagSet(FLAGS_extended_filter, &settings.use_extended_filter);
  SetSettingIfFlagSet(FLAGS_drift_compensation,
                      &settings.use_drift_compensation);
  SetSettingIfFlagSet(FLAGS_refined_adaptive_filter,
                      &settings.use_refined_adaptive_filter);

  SetSettingIfFlagSet(FLAGS_aec3, &settings.use_aec3);
  SetSettingIfSpecified(FLAGS_aecm_routing_mode, &settings.aecm_routing_mode);
  SetSettingIfFlagSet(FLAGS_aecm_comfort_noise,
                      &settings.use_aecm_comfort_noise);
  SetSettingIfSpecified(FLAGS_agc_mode, &settings.agc_mode);
  SetSettingIfSpecified(FLAGS_agc_target_level, &settings.agc_target_level);
  SetSettingIfFlagSet(FLAGS_agc_limiter, &settings.use_agc_limiter);
  SetSettingIfSpecified(FLAGS_agc_compression_gain,
                        &settings.agc_compression_gain);
  SetSettingIfSpecified(FLAGS_vad_likelihood, &settings.vad_likelihood);
  SetSettingIfSpecified(FLAGS_ns_level, &settings.ns_level);
  SetSettingIfSpecified(FLAGS_stream_delay, &settings.stream_delay);
  SetSettingIfSpecified(FLAGS_stream_drift_samples,
                        &settings.stream_drift_samples);
  settings.report_performance = FLAGS_performance_report;
  settings.use_verbose_logging = FLAGS_verbose;
  settings.report_bitexactness = FLAGS_bitexactness_report;
  settings.discard_all_settings_in_aecdump = FLAGS_discard_settings_in_aecdump;
  settings.fixed_interface = FLAGS_fixed_interface;
  settings.store_intermediate_output = FLAGS_store_intermediate_output;

  return settings;
}

void ReportConditionalErrorAndExit(bool condition, std::string message) {
  if (condition) {
    std::cerr << message << std::endl;
    exit(1);
  }
}

void PerformBasicParameterSanityChecks(const SimulationSettings& settings) {
  if (settings.input_filename || settings.reverse_input_filename) {
    ReportConditionalErrorAndExit(!!settings.aec_dump_input_filename,
                                  "Error: The aec dump cannot be specified "
                                  "together with input wav files!\n");

    ReportConditionalErrorAndExit(!settings.input_filename,
                                  "Error: When operating at wav files, the "
                                  "input wav filename must be "
                                  "specified!\n");

    ReportConditionalErrorAndExit(
        settings.reverse_output_filename && !settings.reverse_input_filename,
        "Error: When operating at wav files, the reverse input wav filename "
        "must be specified if the reverse output wav filename is specified!\n");
  } else {
    ReportConditionalErrorAndExit(!settings.aec_dump_input_filename,
                                  "Error: Either the aec dump or the wav "
                                  "input files must be specified!\n");
  }

  ReportConditionalErrorAndExit(
      settings.use_aec && *settings.use_aec && settings.use_aecm &&
          *settings.use_aecm,
      "Error: The AEC and the AECM cannot be activated at the same time!\n");

  ReportConditionalErrorAndExit(
      settings.output_sample_rate_hz && *settings.output_sample_rate_hz <= 0,
      "Error: --output_sample_rate_hz must be positive!\n");

  ReportConditionalErrorAndExit(
      settings.reverse_output_sample_rate_hz &&
          settings.output_sample_rate_hz &&
          *settings.output_sample_rate_hz <= 0,
      "Error: --reverse_output_sample_rate_hz must be positive!\n");

  ReportConditionalErrorAndExit(
      settings.output_num_channels && *settings.output_num_channels <= 0,
      "Error: --output_num_channels must be positive!\n");

  ReportConditionalErrorAndExit(
      settings.reverse_output_num_channels &&
          *settings.reverse_output_num_channels <= 0,
      "Error: --reverse_output_num_channels must be positive!\n");

  ReportConditionalErrorAndExit(
      settings.use_bf && *settings.use_bf && !settings.microphone_positions,
      "Error: --mic_positions must be specified when the beamformer is "
      "activated.\n");

  ReportConditionalErrorAndExit(
      settings.target_angle_degrees < 0 || settings.target_angle_degrees > 359,
      "Error: -target_angle_degrees must be specified between 0 and 359.\n");

  ReportConditionalErrorAndExit(
      settings.aec_suppression_level &&
          ((*settings.aec_suppression_level) < 0 ||
           (*settings.aec_suppression_level) > 2),
      "Error: --aec_suppression_level must be specified between 0 and 2.\n");

  ReportConditionalErrorAndExit(
      settings.aecm_routing_mode && ((*settings.aecm_routing_mode) < 0 ||
                                     (*settings.aecm_routing_mode) > 4),
      "Error: --aecm_routing_mode must be specified between 0 and 4.\n");

  ReportConditionalErrorAndExit(
      settings.agc_target_level && ((*settings.agc_target_level) < 0 ||
                                    (*settings.agc_target_level) > 31),
      "Error: --agc_target_level must be specified between 0 and 31.\n");

  ReportConditionalErrorAndExit(
      settings.agc_compression_gain && ((*settings.agc_compression_gain) < 0 ||
                                        (*settings.agc_compression_gain) > 90),
      "Error: --agc_compression_gain must be specified between 0 and 90.\n");

  ReportConditionalErrorAndExit(
      settings.vad_likelihood &&
          ((*settings.vad_likelihood) < 0 || (*settings.vad_likelihood) > 3),
      "Error: --vad_likelihood must be specified between 0 and 3.\n");

  ReportConditionalErrorAndExit(
      settings.ns_level &&
          ((*settings.ns_level) < 0 || (*settings.ns_level) > 3),
      "Error: --ns_level must be specified between 0 and 3.\n");

  ReportConditionalErrorAndExit(
      settings.report_bitexactness && !settings.aec_dump_input_filename,
      "Error: --bitexactness_report can only be used when operating on an "
      "aecdump\n");

  auto valid_wav_name = [](const std::string& wav_file_name) {
    if (wav_file_name.size() < 5) {
      return false;
    }
    if ((wav_file_name.compare(wav_file_name.size() - 4, 4, ".wav") == 0) ||
        (wav_file_name.compare(wav_file_name.size() - 4, 4, ".WAV") == 0)) {
      return true;
    }
    return false;
  };

  ReportConditionalErrorAndExit(
      settings.input_filename && (!valid_wav_name(*settings.input_filename)),
      "Error: --i must be a valid .wav file name.\n");

  ReportConditionalErrorAndExit(
      settings.output_filename && (!valid_wav_name(*settings.output_filename)),
      "Error: --o must be a valid .wav file name.\n");

  ReportConditionalErrorAndExit(
      settings.reverse_input_filename &&
          (!valid_wav_name(*settings.reverse_input_filename)),
      "Error: --ri must be a valid .wav file name.\n");

  ReportConditionalErrorAndExit(
      settings.reverse_output_filename &&
          (!valid_wav_name(*settings.reverse_output_filename)),
      "Error: --ro must be a valid .wav file name.\n");
}

}  // namespace

int main(int argc, char* argv[]) {
  google::SetUsageMessage(kUsageDescription);
  google::ParseCommandLineFlags(&argc, &argv, true);

  SimulationSettings settings = CreateSettings();
  PerformBasicParameterSanityChecks(settings);
  std::unique_ptr<AudioProcessingSimulator> processor;

  if (settings.aec_dump_input_filename) {
    processor.reset(new AecDumpBasedSimulator(settings));
  } else {
    processor.reset(new WavBasedSimulator(settings));
  }

  processor->Process();

  if (settings.report_performance) {
    const auto& proc_time = processor->proc_time();
    int64_t exec_time_us = proc_time.sum / rtc::kNumNanosecsPerMicrosec;
    std::cout << std::endl
              << "Execution time: " << exec_time_us * 1e-6 << " s, File time: "
              << processor->get_num_process_stream_calls() * 1.f /
                     AudioProcessingSimulator::kChunksPerSecond
              << std::endl
              << "Time per fwd stream chunk (mean, max, min): " << std::endl
              << exec_time_us * 1.f / processor->get_num_process_stream_calls()
              << " us, " << 1.f * proc_time.max / rtc::kNumNanosecsPerMicrosec
              << " us, " << 1.f * proc_time.min / rtc::kNumNanosecsPerMicrosec
              << " us" << std::endl;
  }

  if (settings.report_bitexactness && settings.aec_dump_input_filename) {
    if (processor->OutputWasBitexact()) {
      std::cout << "The processing was bitexact.";
    } else {
      std::cout << "The processing was not bitexact.";
    }
  }

  return 0;
}

}  // namespace test
}  // namespace webrtc

int main(int argc, char* argv[]) {
  return webrtc::test::main(argc, argv);
}
