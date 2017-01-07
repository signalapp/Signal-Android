/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <math.h>
#include <stdio.h>
#include <string.h>
#ifdef WEBRTC_ANDROID
#include <sys/stat.h>
#endif

#include <algorithm>
#include <memory>

#include "webrtc/base/format_macros.h"
#include "webrtc/base/timeutils.h"
#include "webrtc/common.h"
#include "webrtc/modules/audio_processing/include/audio_processing.h"
#include "webrtc/modules/audio_processing/test/protobuf_utils.h"
#include "webrtc/modules/audio_processing/test/test_utils.h"
#include "webrtc/modules/include/module_common_types.h"
#include "webrtc/system_wrappers/include/cpu_features_wrapper.h"
#include "webrtc/test/testsupport/fileutils.h"
#include "webrtc/test/testsupport/perf_test.h"
#ifdef WEBRTC_ANDROID_PLATFORM_BUILD
#include "gtest/gtest.h"
#include "external/webrtc/webrtc/modules/audio_processing/debug.pb.h"
#else
#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/modules/audio_processing/debug.pb.h"
#endif

namespace webrtc {

using webrtc::audioproc::Event;
using webrtc::audioproc::Init;
using webrtc::audioproc::ReverseStream;
using webrtc::audioproc::Stream;

namespace {

void PrintStat(const AudioProcessing::Statistic& stat) {
  printf("%d, %d, %d\n", stat.average,
                         stat.maximum,
                         stat.minimum);
}

void usage() {
  printf(
  "Usage: process_test [options] [-pb PROTOBUF_FILE]\n"
  "  [-ir REVERSE_FILE] [-i PRIMARY_FILE] [-o OUT_FILE]\n");
  printf(
  "process_test is a test application for AudioProcessing.\n\n"
  "When a protobuf debug file is available, specify it with -pb. Alternately,\n"
  "when -ir or -i is used, the specified files will be processed directly in\n"
  "a simulation mode. Otherwise the full set of legacy test files is expected\n"
  "to be present in the working directory. OUT_FILE should be specified\n"
  "without extension to support both raw and wav output.\n\n");
  printf("Options\n");
  printf("General configuration (only used for the simulation mode):\n");
  printf("  -fs SAMPLE_RATE_HZ\n");
  printf("  -ch CHANNELS_IN CHANNELS_OUT\n");
  printf("  -rch REVERSE_CHANNELS\n");
  printf("\n");
  printf("Component configuration:\n");
  printf(
  "All components are disabled by default. Each block below begins with a\n"
  "flag to enable the component with default settings. The subsequent flags\n"
  "in the block are used to provide configuration settings.\n");
  printf("\n  -aec     Echo cancellation\n");
  printf("  --drift_compensation\n");
  printf("  --no_drift_compensation\n");
  printf("  --no_echo_metrics\n");
  printf("  --no_delay_logging\n");
  printf("  --aec_suppression_level LEVEL  [0 - 2]\n");
  printf("  --extended_filter\n");
  printf("  --no_reported_delay\n");
  printf("  --aec3\n");
  printf("  --refined_adaptive_filter\n");
  printf("\n  -aecm    Echo control mobile\n");
  printf("  --aecm_echo_path_in_file FILE\n");
  printf("  --aecm_echo_path_out_file FILE\n");
  printf("  --no_comfort_noise\n");
  printf("  --routing_mode MODE  [0 - 4]\n");
  printf("\n  -agc     Gain control\n");
  printf("  --analog\n");
  printf("  --adaptive_digital\n");
  printf("  --fixed_digital\n");
  printf("  --target_level LEVEL\n");
  printf("  --compression_gain GAIN\n");
  printf("  --limiter\n");
  printf("  --no_limiter\n");
  printf("\n  -hpf     High pass filter\n");
  printf("\n  -ns      Noise suppression\n");
  printf("  --ns_low\n");
  printf("  --ns_moderate\n");
  printf("  --ns_high\n");
  printf("  --ns_very_high\n");
  printf("  --ns_prob_file FILE\n");
  printf("\n  -vad     Voice activity detection\n");
  printf("  --vad_out_file FILE\n");
  printf("\n  -expns   Experimental noise suppression\n");
  printf("\n Level metrics (enabled by default)\n");
  printf("  --no_level_metrics\n");
  printf("\n");
  printf("Modifiers:\n");
  printf("  --noasm            Disable SSE optimization.\n");
  printf("  --add_delay DELAY  Add DELAY ms to input value.\n");
  printf("  --delay DELAY      Override input delay with DELAY ms.\n");
  printf("  --perf             Measure performance.\n");
  printf("  --quiet            Suppress text output.\n");
  printf("  --no_progress      Suppress progress.\n");
  printf("  --raw_output       Raw output instead of WAV file.\n");
  printf("  --debug_file FILE  Dump a debug recording.\n");
}

static float MicLevel2Gain(int level) {
  return pow(10.0f, ((level - 127.0f) / 128.0f * 40.0f) / 20.0f);
}

static void SimulateMic(int mic_level, AudioFrame* frame) {
  mic_level = std::min(std::max(mic_level, 0), 255);
  float mic_gain = MicLevel2Gain(mic_level);
  int num_samples = frame->samples_per_channel_ * frame->num_channels_;
  float v;
  for (int n = 0; n < num_samples; n++) {
    v = floor(frame->data_[n] * mic_gain + 0.5);
    v = std::max(std::min(32767.0f, v), -32768.0f);
    frame->data_[n] = static_cast<int16_t>(v);
  }
}

// void function for gtest.
void void_main(int argc, char* argv[]) {
  if (argc > 1 && strcmp(argv[1], "--help") == 0) {
    usage();
    return;
  }

  if (argc < 2) {
    printf("Did you mean to run without arguments?\n");
    printf("Try `process_test --help' for more information.\n\n");
  }

  std::unique_ptr<AudioProcessing> apm(AudioProcessing::Create());
  ASSERT_TRUE(apm.get() != NULL);

  const char* pb_filename = NULL;
  const char* far_filename = NULL;
  const char* near_filename = NULL;
  std::string out_filename;
  const char* vad_out_filename = NULL;
  const char* ns_prob_filename = NULL;
  const char* aecm_echo_path_in_filename = NULL;
  const char* aecm_echo_path_out_filename = NULL;

  int32_t sample_rate_hz = 16000;

  size_t num_capture_input_channels = 1;
  size_t num_capture_output_channels = 1;
  size_t num_render_channels = 1;

  int samples_per_channel = sample_rate_hz / 100;

  bool simulating = false;
  bool perf_testing = false;
  bool verbose = true;
  bool progress = true;
  bool raw_output = false;
  int extra_delay_ms = 0;
  int override_delay_ms = 0;
  Config config;

  ASSERT_EQ(apm->kNoError, apm->level_estimator()->Enable(true));
  for (int i = 1; i < argc; i++) {
    if (strcmp(argv[i], "-pb") == 0) {
      i++;
      ASSERT_LT(i, argc) << "Specify protobuf filename after -pb";
      pb_filename = argv[i];

    } else if (strcmp(argv[i], "-ir") == 0) {
      i++;
      ASSERT_LT(i, argc) << "Specify filename after -ir";
      far_filename = argv[i];
      simulating = true;

    } else if (strcmp(argv[i], "-i") == 0) {
      i++;
      ASSERT_LT(i, argc) << "Specify filename after -i";
      near_filename = argv[i];
      simulating = true;

    } else if (strcmp(argv[i], "-o") == 0) {
      i++;
      ASSERT_LT(i, argc) << "Specify filename without extension after -o";
      out_filename = argv[i];

    } else if (strcmp(argv[i], "-fs") == 0) {
      i++;
      ASSERT_LT(i, argc) << "Specify sample rate after -fs";
      ASSERT_EQ(1, sscanf(argv[i], "%d", &sample_rate_hz));
      samples_per_channel = sample_rate_hz / 100;

    } else if (strcmp(argv[i], "-ch") == 0) {
      i++;
      ASSERT_LT(i + 1, argc) << "Specify number of channels after -ch";
      ASSERT_EQ(1, sscanf(argv[i], "%" PRIuS, &num_capture_input_channels));
      i++;
      ASSERT_EQ(1, sscanf(argv[i], "%" PRIuS, &num_capture_output_channels));

    } else if (strcmp(argv[i], "-rch") == 0) {
      i++;
      ASSERT_LT(i, argc) << "Specify number of channels after -rch";
      ASSERT_EQ(1, sscanf(argv[i], "%" PRIuS, &num_render_channels));

    } else if (strcmp(argv[i], "-aec") == 0) {
      ASSERT_EQ(apm->kNoError, apm->echo_cancellation()->Enable(true));
      ASSERT_EQ(apm->kNoError,
                apm->echo_cancellation()->enable_metrics(true));
      ASSERT_EQ(apm->kNoError,
                apm->echo_cancellation()->enable_delay_logging(true));

    } else if (strcmp(argv[i], "--drift_compensation") == 0) {
      ASSERT_EQ(apm->kNoError, apm->echo_cancellation()->Enable(true));
      // TODO(ajm): this is enabled in the VQE test app by default. Investigate
      //            why it can give better performance despite passing zeros.
      ASSERT_EQ(apm->kNoError,
                apm->echo_cancellation()->enable_drift_compensation(true));
    } else if (strcmp(argv[i], "--no_drift_compensation") == 0) {
      ASSERT_EQ(apm->kNoError, apm->echo_cancellation()->Enable(true));
      ASSERT_EQ(apm->kNoError,
                apm->echo_cancellation()->enable_drift_compensation(false));

    } else if (strcmp(argv[i], "--no_echo_metrics") == 0) {
      ASSERT_EQ(apm->kNoError, apm->echo_cancellation()->Enable(true));
      ASSERT_EQ(apm->kNoError,
                apm->echo_cancellation()->enable_metrics(false));

    } else if (strcmp(argv[i], "--no_delay_logging") == 0) {
      ASSERT_EQ(apm->kNoError, apm->echo_cancellation()->Enable(true));
      ASSERT_EQ(apm->kNoError,
                apm->echo_cancellation()->enable_delay_logging(false));

    } else if (strcmp(argv[i], "--no_level_metrics") == 0) {
      ASSERT_EQ(apm->kNoError, apm->level_estimator()->Enable(false));

    } else if (strcmp(argv[i], "--aec_suppression_level") == 0) {
      i++;
      ASSERT_LT(i, argc) << "Specify level after --aec_suppression_level";
      int suppression_level;
      ASSERT_EQ(1, sscanf(argv[i], "%d", &suppression_level));
      ASSERT_EQ(apm->kNoError,
                apm->echo_cancellation()->set_suppression_level(
                    static_cast<webrtc::EchoCancellation::SuppressionLevel>(
                        suppression_level)));

    } else if (strcmp(argv[i], "--extended_filter") == 0) {
      config.Set<ExtendedFilter>(new ExtendedFilter(true));

    } else if (strcmp(argv[i], "--no_reported_delay") == 0) {
      config.Set<DelayAgnostic>(new DelayAgnostic(true));

    } else if (strcmp(argv[i], "--delay_agnostic") == 0) {
      config.Set<DelayAgnostic>(new DelayAgnostic(true));

    } else if (strcmp(argv[i], "--aec3") == 0) {
      config.Set<EchoCanceller3>(new EchoCanceller3(true));

    } else if (strcmp(argv[i], "--refined_adaptive_filter") == 0) {
      config.Set<RefinedAdaptiveFilter>(new RefinedAdaptiveFilter(true));

    } else if (strcmp(argv[i], "-aecm") == 0) {
      ASSERT_EQ(apm->kNoError, apm->echo_control_mobile()->Enable(true));

    } else if (strcmp(argv[i], "--aecm_echo_path_in_file") == 0) {
      i++;
      ASSERT_LT(i, argc) << "Specify filename after --aecm_echo_path_in_file";
      aecm_echo_path_in_filename = argv[i];

    } else if (strcmp(argv[i], "--aecm_echo_path_out_file") == 0) {
      i++;
      ASSERT_LT(i, argc) << "Specify filename after --aecm_echo_path_out_file";
      aecm_echo_path_out_filename = argv[i];

    } else if (strcmp(argv[i], "--no_comfort_noise") == 0) {
      ASSERT_EQ(apm->kNoError,
                apm->echo_control_mobile()->enable_comfort_noise(false));

    } else if (strcmp(argv[i], "--routing_mode") == 0) {
      i++;
      ASSERT_LT(i, argc) << "Specify mode after --routing_mode";
      int routing_mode;
      ASSERT_EQ(1, sscanf(argv[i], "%d", &routing_mode));
      ASSERT_EQ(apm->kNoError,
                apm->echo_control_mobile()->set_routing_mode(
                    static_cast<webrtc::EchoControlMobile::RoutingMode>(
                        routing_mode)));

    } else if (strcmp(argv[i], "-agc") == 0) {
      ASSERT_EQ(apm->kNoError, apm->gain_control()->Enable(true));

    } else if (strcmp(argv[i], "--analog") == 0) {
      ASSERT_EQ(apm->kNoError, apm->gain_control()->Enable(true));
      ASSERT_EQ(apm->kNoError,
                apm->gain_control()->set_mode(GainControl::kAdaptiveAnalog));

    } else if (strcmp(argv[i], "--adaptive_digital") == 0) {
      ASSERT_EQ(apm->kNoError, apm->gain_control()->Enable(true));
      ASSERT_EQ(apm->kNoError,
                apm->gain_control()->set_mode(GainControl::kAdaptiveDigital));

    } else if (strcmp(argv[i], "--fixed_digital") == 0) {
      ASSERT_EQ(apm->kNoError, apm->gain_control()->Enable(true));
      ASSERT_EQ(apm->kNoError,
                apm->gain_control()->set_mode(GainControl::kFixedDigital));

    } else if (strcmp(argv[i], "--target_level") == 0) {
      i++;
      int level;
      ASSERT_EQ(1, sscanf(argv[i], "%d", &level));

      ASSERT_EQ(apm->kNoError, apm->gain_control()->Enable(true));
      ASSERT_EQ(apm->kNoError,
                apm->gain_control()->set_target_level_dbfs(level));

    } else if (strcmp(argv[i], "--compression_gain") == 0) {
      i++;
      int gain;
      ASSERT_EQ(1, sscanf(argv[i], "%d", &gain));

      ASSERT_EQ(apm->kNoError, apm->gain_control()->Enable(true));
      ASSERT_EQ(apm->kNoError,
                apm->gain_control()->set_compression_gain_db(gain));

    } else if (strcmp(argv[i], "--limiter") == 0) {
      ASSERT_EQ(apm->kNoError, apm->gain_control()->Enable(true));
      ASSERT_EQ(apm->kNoError,
                apm->gain_control()->enable_limiter(true));

    } else if (strcmp(argv[i], "--no_limiter") == 0) {
      ASSERT_EQ(apm->kNoError, apm->gain_control()->Enable(true));
      ASSERT_EQ(apm->kNoError,
                apm->gain_control()->enable_limiter(false));

    } else if (strcmp(argv[i], "-hpf") == 0) {
      ASSERT_EQ(apm->kNoError, apm->high_pass_filter()->Enable(true));

    } else if (strcmp(argv[i], "-ns") == 0) {
      ASSERT_EQ(apm->kNoError, apm->noise_suppression()->Enable(true));

    } else if (strcmp(argv[i], "--ns_low") == 0) {
      ASSERT_EQ(apm->kNoError, apm->noise_suppression()->Enable(true));
      ASSERT_EQ(apm->kNoError,
          apm->noise_suppression()->set_level(NoiseSuppression::kLow));

    } else if (strcmp(argv[i], "--ns_moderate") == 0) {
      ASSERT_EQ(apm->kNoError, apm->noise_suppression()->Enable(true));
      ASSERT_EQ(apm->kNoError,
          apm->noise_suppression()->set_level(NoiseSuppression::kModerate));

    } else if (strcmp(argv[i], "--ns_high") == 0) {
      ASSERT_EQ(apm->kNoError, apm->noise_suppression()->Enable(true));
      ASSERT_EQ(apm->kNoError,
          apm->noise_suppression()->set_level(NoiseSuppression::kHigh));

    } else if (strcmp(argv[i], "--ns_very_high") == 0) {
      ASSERT_EQ(apm->kNoError, apm->noise_suppression()->Enable(true));
      ASSERT_EQ(apm->kNoError,
          apm->noise_suppression()->set_level(NoiseSuppression::kVeryHigh));

    } else if (strcmp(argv[i], "--ns_prob_file") == 0) {
      i++;
      ASSERT_LT(i, argc) << "Specify filename after --ns_prob_file";
      ns_prob_filename = argv[i];

    } else if (strcmp(argv[i], "-vad") == 0) {
      ASSERT_EQ(apm->kNoError, apm->voice_detection()->Enable(true));

    } else if (strcmp(argv[i], "--vad_very_low") == 0) {
      ASSERT_EQ(apm->kNoError, apm->voice_detection()->Enable(true));
      ASSERT_EQ(apm->kNoError,
          apm->voice_detection()->set_likelihood(
              VoiceDetection::kVeryLowLikelihood));

    } else if (strcmp(argv[i], "--vad_low") == 0) {
      ASSERT_EQ(apm->kNoError, apm->voice_detection()->Enable(true));
      ASSERT_EQ(apm->kNoError,
          apm->voice_detection()->set_likelihood(
              VoiceDetection::kLowLikelihood));

    } else if (strcmp(argv[i], "--vad_moderate") == 0) {
      ASSERT_EQ(apm->kNoError, apm->voice_detection()->Enable(true));
      ASSERT_EQ(apm->kNoError,
          apm->voice_detection()->set_likelihood(
              VoiceDetection::kModerateLikelihood));

    } else if (strcmp(argv[i], "--vad_high") == 0) {
      ASSERT_EQ(apm->kNoError, apm->voice_detection()->Enable(true));
      ASSERT_EQ(apm->kNoError,
          apm->voice_detection()->set_likelihood(
              VoiceDetection::kHighLikelihood));

    } else if (strcmp(argv[i], "--vad_out_file") == 0) {
      i++;
      ASSERT_LT(i, argc) << "Specify filename after --vad_out_file";
      vad_out_filename = argv[i];

    } else if (strcmp(argv[i], "-expns") == 0) {
      config.Set<ExperimentalNs>(new ExperimentalNs(true));

    } else if (strcmp(argv[i], "--noasm") == 0) {
      WebRtc_GetCPUInfo = WebRtc_GetCPUInfoNoASM;
      // We need to reinitialize here if components have already been enabled.
      ASSERT_EQ(apm->kNoError, apm->Initialize());

    } else if (strcmp(argv[i], "--add_delay") == 0) {
      i++;
      ASSERT_EQ(1, sscanf(argv[i], "%d", &extra_delay_ms));

    } else if (strcmp(argv[i], "--delay") == 0) {
      i++;
      ASSERT_EQ(1, sscanf(argv[i], "%d", &override_delay_ms));

    } else if (strcmp(argv[i], "--perf") == 0) {
      perf_testing = true;

    } else if (strcmp(argv[i], "--quiet") == 0) {
      verbose = false;
      progress = false;

    } else if (strcmp(argv[i], "--no_progress") == 0) {
      progress = false;

    } else if (strcmp(argv[i], "--raw_output") == 0) {
      raw_output = true;

    } else if (strcmp(argv[i], "--debug_file") == 0) {
      i++;
      ASSERT_LT(i, argc) << "Specify filename after --debug_file";
      ASSERT_EQ(apm->kNoError, apm->StartDebugRecording(argv[i], -1));
    } else {
      FAIL() << "Unrecognized argument " << argv[i];
    }
  }
  apm->SetExtraOptions(config);

  // If we're reading a protobuf file, ensure a simulation hasn't also
  // been requested (which makes no sense...)
  ASSERT_FALSE(pb_filename && simulating);

  if (verbose) {
    printf("Sample rate: %d Hz\n", sample_rate_hz);
    printf("Primary channels: %" PRIuS " (in), %" PRIuS " (out)\n",
           num_capture_input_channels,
           num_capture_output_channels);
    printf("Reverse channels: %" PRIuS "\n", num_render_channels);
  }

  const std::string out_path = webrtc::test::OutputPath();
  const char far_file_default[] = "apm_far.pcm";
  const char near_file_default[] = "apm_near.pcm";
  const char event_filename[] = "apm_event.dat";
  const char delay_filename[] = "apm_delay.dat";
  const char drift_filename[] = "apm_drift.dat";
  const std::string vad_file_default = out_path + "vad_out.dat";
  const std::string ns_prob_file_default = out_path + "ns_prob.dat";

  if (!simulating) {
    far_filename = far_file_default;
    near_filename = near_file_default;
  }

  if (out_filename.size() == 0) {
    out_filename = out_path + "out";
  }

  if (!vad_out_filename) {
    vad_out_filename = vad_file_default.c_str();
  }

  if (!ns_prob_filename) {
    ns_prob_filename = ns_prob_file_default.c_str();
  }

  FILE* pb_file = NULL;
  FILE* far_file = NULL;
  FILE* near_file = NULL;
  FILE* event_file = NULL;
  FILE* delay_file = NULL;
  FILE* drift_file = NULL;
  FILE* vad_out_file = NULL;
  FILE* ns_prob_file = NULL;
  FILE* aecm_echo_path_in_file = NULL;
  FILE* aecm_echo_path_out_file = NULL;

  std::unique_ptr<WavWriter> output_wav_file;
  std::unique_ptr<RawFile> output_raw_file;

  if (pb_filename) {
    pb_file = OpenFile(pb_filename, "rb");
  } else {
    if (far_filename) {
      far_file = OpenFile(far_filename, "rb");
    }

    near_file = OpenFile(near_filename, "rb");
    if (!simulating) {
      event_file = OpenFile(event_filename, "rb");
      delay_file = OpenFile(delay_filename, "rb");
      drift_file = OpenFile(drift_filename, "rb");
    }
  }

  int near_size_bytes = 0;
  if (pb_file) {
    struct stat st;
    stat(pb_filename, &st);
    // Crude estimate, but should be good enough.
    near_size_bytes = st.st_size / 3;
  } else {
    struct stat st;
    stat(near_filename, &st);
    near_size_bytes = st.st_size;
  }

  if (apm->voice_detection()->is_enabled()) {
    vad_out_file = OpenFile(vad_out_filename, "wb");
  }

  if (apm->noise_suppression()->is_enabled()) {
    ns_prob_file = OpenFile(ns_prob_filename, "wb");
  }

  if (aecm_echo_path_in_filename != NULL) {
    aecm_echo_path_in_file = OpenFile(aecm_echo_path_in_filename, "rb");

    const size_t path_size =
        apm->echo_control_mobile()->echo_path_size_bytes();
    std::unique_ptr<char[]> echo_path(new char[path_size]);
    ASSERT_EQ(path_size, fread(echo_path.get(),
                               sizeof(char),
                               path_size,
                               aecm_echo_path_in_file));
    EXPECT_EQ(apm->kNoError,
              apm->echo_control_mobile()->SetEchoPath(echo_path.get(),
                                                      path_size));
    fclose(aecm_echo_path_in_file);
    aecm_echo_path_in_file = NULL;
  }

  if (aecm_echo_path_out_filename != NULL) {
    aecm_echo_path_out_file = OpenFile(aecm_echo_path_out_filename, "wb");
  }

  size_t read_count = 0;
  int reverse_count = 0;
  int primary_count = 0;
  int near_read_bytes = 0;
  int64_t acc_nanos = 0;

  AudioFrame far_frame;
  AudioFrame near_frame;

  int delay_ms = 0;
  int drift_samples = 0;
  int capture_level = 127;
  int8_t stream_has_voice = 0;
  float ns_speech_prob = 0.0f;

  int64_t t0 = rtc::TimeNanos();
  int64_t t1 = t0;
  int64_t max_time_us = 0;
  int64_t max_time_reverse_us = 0;
  int64_t min_time_us = 1e6;
  int64_t min_time_reverse_us = 1e6;

  // TODO(ajm): Ideally we would refactor this block into separate functions,
  //            but for now we want to share the variables.
  if (pb_file) {
    Event event_msg;
    std::unique_ptr<ChannelBuffer<float> > reverse_cb;
    std::unique_ptr<ChannelBuffer<float> > primary_cb;
    int output_sample_rate = 32000;
    AudioProcessing::ChannelLayout output_layout = AudioProcessing::kMono;
    while (ReadMessageFromFile(pb_file, &event_msg)) {
      std::ostringstream trace_stream;
      trace_stream << "Processed frames: " << reverse_count << " (reverse), "
                   << primary_count << " (primary)";
      SCOPED_TRACE(trace_stream.str());

      if (event_msg.type() == Event::INIT) {
        ASSERT_TRUE(event_msg.has_init());
        const Init msg = event_msg.init();

        ASSERT_TRUE(msg.has_sample_rate());
        ASSERT_TRUE(msg.has_num_input_channels());
        ASSERT_TRUE(msg.has_num_output_channels());
        ASSERT_TRUE(msg.has_num_reverse_channels());
        int reverse_sample_rate = msg.sample_rate();
        if (msg.has_reverse_sample_rate()) {
          reverse_sample_rate = msg.reverse_sample_rate();
        }
        output_sample_rate = msg.sample_rate();
        if (msg.has_output_sample_rate()) {
          output_sample_rate = msg.output_sample_rate();
        }
        output_layout =
            LayoutFromChannels(static_cast<size_t>(msg.num_output_channels()));
        ASSERT_EQ(kNoErr,
                  apm->Initialize(
                      msg.sample_rate(),
                      output_sample_rate,
                      reverse_sample_rate,
                      LayoutFromChannels(
                          static_cast<size_t>(msg.num_input_channels())),
                      output_layout,
                      LayoutFromChannels(
                          static_cast<size_t>(msg.num_reverse_channels()))));

        samples_per_channel = msg.sample_rate() / 100;
        far_frame.sample_rate_hz_ = reverse_sample_rate;
        far_frame.samples_per_channel_ = reverse_sample_rate / 100;
        far_frame.num_channels_ = msg.num_reverse_channels();
        near_frame.sample_rate_hz_ = msg.sample_rate();
        near_frame.samples_per_channel_ = samples_per_channel;
        near_frame.num_channels_ = msg.num_input_channels();
        reverse_cb.reset(new ChannelBuffer<float>(
            far_frame.samples_per_channel_,
            msg.num_reverse_channels()));
        primary_cb.reset(new ChannelBuffer<float>(samples_per_channel,
                                                  msg.num_input_channels()));

        if (verbose) {
          printf("Init at frame: %d (primary), %d (reverse)\n",
              primary_count, reverse_count);
          printf("  Primary rates: %d Hz (in), %d Hz (out)\n",
                 msg.sample_rate(), output_sample_rate);
          printf("  Primary channels: %d (in), %d (out)\n",
                 msg.num_input_channels(),
                 msg.num_output_channels());
          printf("  Reverse rate: %d\n", reverse_sample_rate);
          printf("  Reverse channels: %d\n", msg.num_reverse_channels());
        }

        if (!raw_output) {
          // The WAV file needs to be reset every time, because it can't change
          // its sample rate or number of channels.
          output_wav_file.reset(new WavWriter(
              out_filename + ".wav", output_sample_rate,
              static_cast<size_t>(msg.num_output_channels())));
        }

      } else if (event_msg.type() == Event::REVERSE_STREAM) {
        ASSERT_TRUE(event_msg.has_reverse_stream());
        ReverseStream msg = event_msg.reverse_stream();
        reverse_count++;

        ASSERT_TRUE(msg.has_data() ^ (msg.channel_size() > 0));
        if (msg.has_data()) {
          ASSERT_EQ(sizeof(int16_t) * far_frame.samples_per_channel_ *
              far_frame.num_channels_, msg.data().size());
          memcpy(far_frame.data_, msg.data().data(), msg.data().size());
        } else {
          for (int i = 0; i < msg.channel_size(); ++i) {
            memcpy(reverse_cb->channels()[i],
                   msg.channel(i).data(),
                   reverse_cb->num_frames() *
                       sizeof(reverse_cb->channels()[i][0]));
          }
        }

        if (perf_testing) {
          t0 = rtc::TimeNanos();
        }

        if (msg.has_data()) {
          ASSERT_EQ(apm->kNoError,
                    apm->ProcessReverseStream(&far_frame));
        } else {
          ASSERT_EQ(apm->kNoError,
                    apm->AnalyzeReverseStream(
                        reverse_cb->channels(),
                        far_frame.samples_per_channel_,
                        far_frame.sample_rate_hz_,
                        LayoutFromChannels(far_frame.num_channels_)));
        }

        if (perf_testing) {
          t1 = rtc::TimeNanos();
          int64_t diff_nanos = t1 - t0;
          acc_nanos += diff_nanos;
          int64_t diff_us = diff_nanos / rtc::kNumNanosecsPerMicrosec;
          if (diff_us > max_time_reverse_us) {
            max_time_reverse_us = diff_us;
          }
          if (diff_us < min_time_reverse_us) {
            min_time_reverse_us = diff_us;
          }
        }

      } else if (event_msg.type() == Event::STREAM) {
        ASSERT_TRUE(event_msg.has_stream());
        const Stream msg = event_msg.stream();
        primary_count++;

        ASSERT_TRUE(msg.has_input_data() ^ (msg.input_channel_size() > 0));
        if (msg.has_input_data()) {
          ASSERT_EQ(sizeof(int16_t) * samples_per_channel *
              near_frame.num_channels_, msg.input_data().size());
          memcpy(near_frame.data_,
                 msg.input_data().data(),
                 msg.input_data().size());
          near_read_bytes += msg.input_data().size();
        } else {
          for (int i = 0; i < msg.input_channel_size(); ++i) {
            memcpy(primary_cb->channels()[i],
                   msg.input_channel(i).data(),
                   primary_cb->num_frames() *
                       sizeof(primary_cb->channels()[i][0]));
            near_read_bytes += msg.input_channel(i).size();
          }
        }

        if (progress && primary_count % 100 == 0) {
          near_read_bytes = std::min(near_read_bytes, near_size_bytes);
          printf("%.0f%% complete\r",
              (near_read_bytes * 100.0) / near_size_bytes);
          fflush(stdout);
        }

        if (perf_testing) {
          t0 = rtc::TimeNanos();
        }

        ASSERT_EQ(apm->kNoError,
                  apm->gain_control()->set_stream_analog_level(msg.level()));
        delay_ms = msg.delay() + extra_delay_ms;
        if (override_delay_ms) {
          delay_ms = override_delay_ms;
        }
        ASSERT_EQ(apm->kNoError,
                  apm->set_stream_delay_ms(delay_ms));
        apm->echo_cancellation()->set_stream_drift_samples(msg.drift());

        if (msg.has_keypress()) {
          apm->set_stream_key_pressed(msg.keypress());
        } else {
          apm->set_stream_key_pressed(true);
        }

        int err = apm->kNoError;
        if (msg.has_input_data()) {
          err = apm->ProcessStream(&near_frame);
          ASSERT_TRUE(near_frame.num_channels_ == apm->num_output_channels());
        } else {
          err = apm->ProcessStream(
              primary_cb->channels(),
              near_frame.samples_per_channel_,
              near_frame.sample_rate_hz_,
              LayoutFromChannels(near_frame.num_channels_),
              output_sample_rate,
              output_layout,
              primary_cb->channels());
        }

        if (err == apm->kBadStreamParameterWarning) {
          printf("Bad parameter warning. %s\n", trace_stream.str().c_str());
        }
        ASSERT_TRUE(err == apm->kNoError ||
                    err == apm->kBadStreamParameterWarning);

        stream_has_voice =
            static_cast<int8_t>(apm->voice_detection()->stream_has_voice());
        if (vad_out_file != NULL) {
          ASSERT_EQ(1u, fwrite(&stream_has_voice,
                               sizeof(stream_has_voice),
                               1,
                               vad_out_file));
        }

        if (ns_prob_file != NULL) {
          ns_speech_prob = apm->noise_suppression()->speech_probability();
          ASSERT_EQ(1u, fwrite(&ns_speech_prob,
                               sizeof(ns_speech_prob),
                               1,
                               ns_prob_file));
        }

        if (perf_testing) {
          t1 = rtc::TimeNanos();
          int64_t diff_nanos = t1 - t0;
          acc_nanos += diff_nanos;
          int64_t diff_us = diff_nanos / rtc::kNumNanosecsPerMicrosec;
          if (diff_us > max_time_us) {
            max_time_us = diff_us;
          }
          if (diff_us < min_time_us) {
            min_time_us = diff_us;
          }
        }

        const size_t samples_per_channel = output_sample_rate / 100;
        if (msg.has_input_data()) {
          if (raw_output && !output_raw_file) {
            output_raw_file.reset(new RawFile(out_filename + ".pcm"));
          }
          WriteIntData(near_frame.data_,
                       apm->num_output_channels() * samples_per_channel,
                       output_wav_file.get(),
                       output_raw_file.get());
        } else {
          if (raw_output && !output_raw_file) {
            output_raw_file.reset(new RawFile(out_filename + ".float"));
          }
          WriteFloatData(primary_cb->channels(),
                         samples_per_channel,
                         apm->num_output_channels(),
                         output_wav_file.get(),
                         output_raw_file.get());
        }
      }
    }

    ASSERT_TRUE(feof(pb_file));

  } else {
    enum Events {
      kInitializeEvent,
      kRenderEvent,
      kCaptureEvent,
      kResetEventDeprecated
    };
    int16_t event = 0;
    while (simulating || feof(event_file) == 0) {
      std::ostringstream trace_stream;
      trace_stream << "Processed frames: " << reverse_count << " (reverse), "
                   << primary_count << " (primary)";
      SCOPED_TRACE(trace_stream.str());

      if (simulating) {
        if (far_file == NULL) {
          event = kCaptureEvent;
        } else {
          event = (event == kCaptureEvent) ? kRenderEvent : kCaptureEvent;
        }
      } else {
        read_count = fread(&event, sizeof(event), 1, event_file);
        if (read_count != 1) {
          break;
        }
      }

      far_frame.sample_rate_hz_ = sample_rate_hz;
      far_frame.samples_per_channel_ = samples_per_channel;
      far_frame.num_channels_ = num_render_channels;
      near_frame.sample_rate_hz_ = sample_rate_hz;
      near_frame.samples_per_channel_ = samples_per_channel;

      if (event == kInitializeEvent || event == kResetEventDeprecated) {
        ASSERT_EQ(1u,
            fread(&sample_rate_hz, sizeof(sample_rate_hz), 1, event_file));
        samples_per_channel = sample_rate_hz / 100;

        int32_t unused_device_sample_rate_hz;
        ASSERT_EQ(1u,
            fread(&unused_device_sample_rate_hz,
                  sizeof(unused_device_sample_rate_hz),
                  1,
                  event_file));

        ASSERT_EQ(kNoErr, apm->Initialize(
                              sample_rate_hz,
                              sample_rate_hz,
                              sample_rate_hz,
                              LayoutFromChannels(num_capture_input_channels),
                              LayoutFromChannels(num_capture_output_channels),
                              LayoutFromChannels(num_render_channels)));

        far_frame.sample_rate_hz_ = sample_rate_hz;
        far_frame.samples_per_channel_ = samples_per_channel;
        far_frame.num_channels_ = num_render_channels;
        near_frame.sample_rate_hz_ = sample_rate_hz;
        near_frame.samples_per_channel_ = samples_per_channel;

        if (!raw_output) {
          // The WAV file needs to be reset every time, because it can't change
          // it's sample rate or number of channels.
          output_wav_file.reset(new WavWriter(out_filename + ".wav",
                                              sample_rate_hz,
                                              num_capture_output_channels));
        }

        if (verbose) {
          printf("Init at frame: %d (primary), %d (reverse)\n",
              primary_count, reverse_count);
          printf("  Sample rate: %d Hz\n", sample_rate_hz);
        }

      } else if (event == kRenderEvent) {
        reverse_count++;

        size_t size = samples_per_channel * num_render_channels;
        read_count = fread(far_frame.data_,
                           sizeof(int16_t),
                           size,
                           far_file);

        if (simulating) {
          if (read_count != size) {
            // Read an equal amount from the near file to avoid errors due to
            // not reaching end-of-file.
            EXPECT_EQ(0, fseek(near_file, read_count * sizeof(int16_t),
                      SEEK_CUR));
            break;  // This is expected.
          }
        } else {
          ASSERT_EQ(size, read_count);
        }

        if (perf_testing) {
          t0 = rtc::TimeNanos();
        }

        ASSERT_EQ(apm->kNoError,
                  apm->ProcessReverseStream(&far_frame));

        if (perf_testing) {
          t1 = rtc::TimeNanos();
          int64_t diff_nanos = t1 - t0;
          acc_nanos += diff_nanos;
          int64_t diff_us = diff_nanos / rtc::kNumNanosecsPerMicrosec;
          if (diff_us > max_time_reverse_us) {
            max_time_reverse_us = diff_us;
          }
          if (diff_us < min_time_reverse_us) {
            min_time_reverse_us = diff_us;
          }
        }

      } else if (event == kCaptureEvent) {
        primary_count++;
        near_frame.num_channels_ = num_capture_input_channels;

        size_t size = samples_per_channel * num_capture_input_channels;
        read_count = fread(near_frame.data_,
                           sizeof(int16_t),
                           size,
                           near_file);

        near_read_bytes += read_count * sizeof(int16_t);
        if (progress && primary_count % 100 == 0) {
          printf("%.0f%% complete\r",
              (near_read_bytes * 100.0) / near_size_bytes);
          fflush(stdout);
        }
        if (simulating) {
          if (read_count != size) {
            break;  // This is expected.
          }

          delay_ms = 0;
          drift_samples = 0;
        } else {
          ASSERT_EQ(size, read_count);

          // TODO(ajm): sizeof(delay_ms) for current files?
          ASSERT_EQ(1u,
              fread(&delay_ms, 2, 1, delay_file));
          ASSERT_EQ(1u,
              fread(&drift_samples, sizeof(drift_samples), 1, drift_file));
        }

        if (apm->gain_control()->is_enabled() &&
            apm->gain_control()->mode() == GainControl::kAdaptiveAnalog) {
          SimulateMic(capture_level, &near_frame);
        }

        if (perf_testing) {
          t0 = rtc::TimeNanos();
        }

        const int capture_level_in = capture_level;
        ASSERT_EQ(apm->kNoError,
                  apm->gain_control()->set_stream_analog_level(capture_level));
        delay_ms += extra_delay_ms;
        if (override_delay_ms) {
          delay_ms = override_delay_ms;
        }
        ASSERT_EQ(apm->kNoError,
                  apm->set_stream_delay_ms(delay_ms));
        apm->echo_cancellation()->set_stream_drift_samples(drift_samples);

        apm->set_stream_key_pressed(true);

        int err = apm->ProcessStream(&near_frame);
        if (err == apm->kBadStreamParameterWarning) {
          printf("Bad parameter warning. %s\n", trace_stream.str().c_str());
        }
        ASSERT_TRUE(err == apm->kNoError ||
                    err == apm->kBadStreamParameterWarning);
        ASSERT_TRUE(near_frame.num_channels_ == apm->num_output_channels());

        capture_level = apm->gain_control()->stream_analog_level();

        stream_has_voice =
            static_cast<int8_t>(apm->voice_detection()->stream_has_voice());
        if (vad_out_file != NULL) {
          ASSERT_EQ(1u, fwrite(&stream_has_voice,
                               sizeof(stream_has_voice),
                               1,
                               vad_out_file));
        }

        if (ns_prob_file != NULL) {
          ns_speech_prob = apm->noise_suppression()->speech_probability();
          ASSERT_EQ(1u, fwrite(&ns_speech_prob,
                               sizeof(ns_speech_prob),
                               1,
                               ns_prob_file));
        }

        if (apm->gain_control()->mode() != GainControl::kAdaptiveAnalog) {
          ASSERT_EQ(capture_level_in, capture_level);
        }

        if (perf_testing) {
          t1 = rtc::TimeNanos();
          int64_t diff_nanos = t1 - t0;
          acc_nanos += diff_nanos;
          int64_t diff_us = diff_nanos / rtc::kNumNanosecsPerMicrosec;
          if (diff_us > max_time_us) {
            max_time_us = diff_us;
          }
          if (diff_us < min_time_us) {
            min_time_us = diff_us;
          }
        }

        if (raw_output && !output_raw_file) {
          output_raw_file.reset(new RawFile(out_filename + ".pcm"));
        }
        if (!raw_output && !output_wav_file) {
          output_wav_file.reset(new WavWriter(out_filename + ".wav",
                                              sample_rate_hz,
                                              num_capture_output_channels));
        }
        WriteIntData(near_frame.data_,
                     size,
                     output_wav_file.get(),
                     output_raw_file.get());
      } else {
        FAIL() << "Event " << event << " is unrecognized";
      }
    }
  }
  if (progress) {
    printf("100%% complete\r");
  }

  if (aecm_echo_path_out_file != NULL) {
    const size_t path_size =
        apm->echo_control_mobile()->echo_path_size_bytes();
    std::unique_ptr<char[]> echo_path(new char[path_size]);
    apm->echo_control_mobile()->GetEchoPath(echo_path.get(), path_size);
    ASSERT_EQ(path_size, fwrite(echo_path.get(),
                                sizeof(char),
                                path_size,
                                aecm_echo_path_out_file));
    fclose(aecm_echo_path_out_file);
    aecm_echo_path_out_file = NULL;
  }

  if (verbose) {
    printf("\nProcessed frames: %d (primary), %d (reverse)\n",
        primary_count, reverse_count);

    if (apm->level_estimator()->is_enabled()) {
      printf("\n--Level metrics--\n");
      printf("RMS: %d dBFS\n", -apm->level_estimator()->RMS());
    }
    if (apm->echo_cancellation()->are_metrics_enabled()) {
      EchoCancellation::Metrics metrics;
      apm->echo_cancellation()->GetMetrics(&metrics);
      printf("\n--Echo metrics--\n");
      printf("(avg, max, min)\n");
      printf("ERL:  ");
      PrintStat(metrics.echo_return_loss);
      printf("ERLE: ");
      PrintStat(metrics.echo_return_loss_enhancement);
      printf("ANLP: ");
      PrintStat(metrics.a_nlp);
    }
    if (apm->echo_cancellation()->is_delay_logging_enabled()) {
      int median = 0;
      int std = 0;
      float fraction_poor_delays = 0;
      apm->echo_cancellation()->GetDelayMetrics(&median, &std,
                                                &fraction_poor_delays);
      printf("\n--Delay metrics--\n");
      printf("Median:             %3d\n", median);
      printf("Standard deviation: %3d\n", std);
      printf("Poor delay values:  %3.1f%%\n", fraction_poor_delays * 100);
    }
  }

  if (!pb_file) {
    int8_t temp_int8;
    if (far_file) {
      read_count = fread(&temp_int8, sizeof(temp_int8), 1, far_file);
      EXPECT_NE(0, feof(far_file)) << "Far-end file not fully processed";
    }

    read_count = fread(&temp_int8, sizeof(temp_int8), 1, near_file);
    EXPECT_NE(0, feof(near_file)) << "Near-end file not fully processed";

    if (!simulating) {
      read_count = fread(&temp_int8, sizeof(temp_int8), 1, event_file);
      EXPECT_NE(0, feof(event_file)) << "Event file not fully processed";
      read_count = fread(&temp_int8, sizeof(temp_int8), 1, delay_file);
      EXPECT_NE(0, feof(delay_file)) << "Delay file not fully processed";
      read_count = fread(&temp_int8, sizeof(temp_int8), 1, drift_file);
      EXPECT_NE(0, feof(drift_file)) << "Drift file not fully processed";
    }
  }

  if (perf_testing) {
    if (primary_count > 0) {
      int64_t exec_time = acc_nanos / rtc::kNumNanosecsPerMillisec;
      printf("\nTotal time: %.3f s, file time: %.2f s\n",
        exec_time * 0.001, primary_count * 0.01);
      printf("Time per frame: %.3f ms (average), %.3f ms (max),"
             " %.3f ms (min)\n",
          (exec_time * 1.0) / primary_count,
          (max_time_us + max_time_reverse_us) / 1000.0,
          (min_time_us + min_time_reverse_us) / 1000.0);
      // Record the results with Perf test tools.
      webrtc::test::PrintResult("audioproc", "", "time_per_10ms_frame",
          (exec_time * 1000) / primary_count, "us", false);
    } else {
      printf("Warning: no capture frames\n");
    }
  }
}

}  // namespace
}  // namespace webrtc

int main(int argc, char* argv[]) {
  webrtc::void_main(argc, argv);

  // Optional, but removes memory leak noise from Valgrind.
  google::protobuf::ShutdownProtobufLibrary();
  return 0;
}
