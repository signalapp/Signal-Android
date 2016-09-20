/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <stddef.h>  // size_t

#include <memory>
#include <string>
#include <vector>

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/modules/audio_coding/neteq/tools/resample_input_audio_file.h"
#include "webrtc/modules/audio_processing/test/debug_dump_replayer.h"
#include "webrtc/modules/audio_processing/test/test_utils.h"
#include "webrtc/test/testsupport/fileutils.h"


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

class DebugDumpGenerator {
 public:
  DebugDumpGenerator(const std::string& input_file_name,
                     int input_file_rate_hz,
                     int input_channels,
                     const std::string& reverse_file_name,
                     int reverse_file_rate_hz,
                     int reverse_channels,
                     const Config& config,
                     const std::string& dump_file_name);

  // Constructor that uses default input files.
  explicit DebugDumpGenerator(const Config& config);

  ~DebugDumpGenerator();

  // Changes the sample rate of the input audio to the APM.
  void SetInputRate(int rate_hz);

  // Sets if converts stereo input signal to mono by discarding other channels.
  void ForceInputMono(bool mono);

  // Changes the sample rate of the reverse audio to the APM.
  void SetReverseRate(int rate_hz);

  // Sets if converts stereo reverse signal to mono by discarding other
  // channels.
  void ForceReverseMono(bool mono);

  // Sets the required sample rate of the APM output.
  void SetOutputRate(int rate_hz);

  // Sets the required channels of the APM output.
  void SetOutputChannels(int channels);

  std::string dump_file_name() const { return dump_file_name_; }

  void StartRecording();
  void Process(size_t num_blocks);
  void StopRecording();
  AudioProcessing* apm() const { return apm_.get(); }

 private:
  static void ReadAndDeinterleave(ResampleInputAudioFile* audio, int channels,
                                  const StreamConfig& config,
                                  float* const* buffer);

  // APM input/output settings.
  StreamConfig input_config_;
  StreamConfig reverse_config_;
  StreamConfig output_config_;

  // Input file format.
  const std::string input_file_name_;
  ResampleInputAudioFile input_audio_;
  const int input_file_channels_;

  // Reverse file format.
  const std::string reverse_file_name_;
  ResampleInputAudioFile reverse_audio_;
  const int reverse_file_channels_;

  // Buffer for APM input/output.
  std::unique_ptr<ChannelBuffer<float>> input_;
  std::unique_ptr<ChannelBuffer<float>> reverse_;
  std::unique_ptr<ChannelBuffer<float>> output_;

  std::unique_ptr<AudioProcessing> apm_;

  const std::string dump_file_name_;
};

DebugDumpGenerator::DebugDumpGenerator(const std::string& input_file_name,
                                       int input_rate_hz,
                                       int input_channels,
                                       const std::string& reverse_file_name,
                                       int reverse_rate_hz,
                                       int reverse_channels,
                                       const Config& config,
                                       const std::string& dump_file_name)
    : input_config_(input_rate_hz, input_channels),
      reverse_config_(reverse_rate_hz, reverse_channels),
      output_config_(input_rate_hz, input_channels),
      input_audio_(input_file_name, input_rate_hz, input_rate_hz),
      input_file_channels_(input_channels),
      reverse_audio_(reverse_file_name, reverse_rate_hz, reverse_rate_hz),
      reverse_file_channels_(reverse_channels),
      input_(new ChannelBuffer<float>(input_config_.num_frames(),
                                      input_config_.num_channels())),
      reverse_(new ChannelBuffer<float>(reverse_config_.num_frames(),
                                        reverse_config_.num_channels())),
      output_(new ChannelBuffer<float>(output_config_.num_frames(),
                                       output_config_.num_channels())),
      apm_(AudioProcessing::Create(config)),
      dump_file_name_(dump_file_name) {
}

DebugDumpGenerator::DebugDumpGenerator(const Config& config)
  : DebugDumpGenerator(ResourcePath("near32_stereo", "pcm"), 32000, 2,
                       ResourcePath("far32_stereo", "pcm"), 32000, 2,
                       config,
                       TempFilename(OutputPath(), "debug_aec")) {
}

DebugDumpGenerator::~DebugDumpGenerator() {
  remove(dump_file_name_.c_str());
}

void DebugDumpGenerator::SetInputRate(int rate_hz) {
  input_audio_.set_output_rate_hz(rate_hz);
  input_config_.set_sample_rate_hz(rate_hz);
  MaybeResetBuffer(&input_, input_config_);
}

void DebugDumpGenerator::ForceInputMono(bool mono) {
  const int channels = mono ? 1 : input_file_channels_;
  input_config_.set_num_channels(channels);
  MaybeResetBuffer(&input_, input_config_);
}

void DebugDumpGenerator::SetReverseRate(int rate_hz) {
  reverse_audio_.set_output_rate_hz(rate_hz);
  reverse_config_.set_sample_rate_hz(rate_hz);
  MaybeResetBuffer(&reverse_, reverse_config_);
}

void DebugDumpGenerator::ForceReverseMono(bool mono) {
  const int channels = mono ? 1 : reverse_file_channels_;
  reverse_config_.set_num_channels(channels);
  MaybeResetBuffer(&reverse_, reverse_config_);
}

void DebugDumpGenerator::SetOutputRate(int rate_hz) {
  output_config_.set_sample_rate_hz(rate_hz);
  MaybeResetBuffer(&output_, output_config_);
}

void DebugDumpGenerator::SetOutputChannels(int channels) {
  output_config_.set_num_channels(channels);
  MaybeResetBuffer(&output_, output_config_);
}

void DebugDumpGenerator::StartRecording() {
  apm_->StartDebugRecording(dump_file_name_.c_str(), -1);
}

void DebugDumpGenerator::Process(size_t num_blocks) {
  for (size_t i = 0; i < num_blocks; ++i) {
    ReadAndDeinterleave(&reverse_audio_, reverse_file_channels_,
                        reverse_config_, reverse_->channels());
    ReadAndDeinterleave(&input_audio_, input_file_channels_, input_config_,
                        input_->channels());
    RTC_CHECK_EQ(AudioProcessing::kNoError, apm_->set_stream_delay_ms(100));
    apm_->set_stream_key_pressed(i % 10 == 9);
    RTC_CHECK_EQ(AudioProcessing::kNoError,
                 apm_->ProcessStream(input_->channels(), input_config_,
                                     output_config_, output_->channels()));

    RTC_CHECK_EQ(AudioProcessing::kNoError,
                 apm_->ProcessReverseStream(reverse_->channels(),
                                            reverse_config_,
                                            reverse_config_,
                                            reverse_->channels()));
  }
}

void DebugDumpGenerator::StopRecording() {
  apm_->StopDebugRecording();
}

void DebugDumpGenerator::ReadAndDeinterleave(ResampleInputAudioFile* audio,
                                             int channels,
                                             const StreamConfig& config,
                                             float* const* buffer) {
  const size_t num_frames = config.num_frames();
  const int out_channels = config.num_channels();

  std::vector<int16_t> signal(channels * num_frames);

  audio->Read(num_frames * channels, &signal[0]);

  // We only allow reducing number of channels by discarding some channels.
  RTC_CHECK_LE(out_channels, channels);
  for (int channel = 0; channel < out_channels; ++channel) {
    for (size_t i = 0; i < num_frames; ++i) {
      buffer[channel][i] = S16ToFloat(signal[i * channels + channel]);
    }
  }
}

}  // namespace

class DebugDumpTest : public ::testing::Test {
 public:
  // VerifyDebugDump replays a debug dump using APM and verifies that the result
  // is bit-exact-identical to the output channel in the dump. This is only
  // guaranteed if the debug dump is started on the first frame.
  void VerifyDebugDump(const std::string& dump_file_name);

 private:
  DebugDumpReplayer debug_dump_replayer_;
};

void DebugDumpTest::VerifyDebugDump(const std::string& in_filename) {
  ASSERT_TRUE(debug_dump_replayer_.SetDumpFile(in_filename));

  while (const rtc::Optional<audioproc::Event> event =
      debug_dump_replayer_.GetNextEvent()) {
    debug_dump_replayer_.RunNextEvent();
    if (event->type() == audioproc::Event::STREAM) {
      const audioproc::Stream* msg = &event->stream();
      const StreamConfig output_config = debug_dump_replayer_.GetOutputConfig();
      const ChannelBuffer<float>* output = debug_dump_replayer_.GetOutput();
      // Check that output of APM is bit-exact to the output in the dump.
      ASSERT_EQ(output_config.num_channels(),
                static_cast<size_t>(msg->output_channel_size()));
      ASSERT_EQ(output_config.num_frames() * sizeof(float),
                msg->output_channel(0).size());
      for (int i = 0; i < msg->output_channel_size(); ++i) {
        ASSERT_EQ(0, memcmp(output->channels()[i],
                            msg->output_channel(i).data(),
                            msg->output_channel(i).size()));
      }
    }
  }
}

TEST_F(DebugDumpTest, SimpleCase) {
  Config config;
  DebugDumpGenerator generator(config);
  generator.StartRecording();
  generator.Process(100);
  generator.StopRecording();
  VerifyDebugDump(generator.dump_file_name());
}

TEST_F(DebugDumpTest, ChangeInputFormat) {
  Config config;
  DebugDumpGenerator generator(config);
  generator.StartRecording();
  generator.Process(100);
  generator.SetInputRate(48000);

  generator.ForceInputMono(true);
  // Number of output channel should not be larger than that of input. APM will
  // fail otherwise.
  generator.SetOutputChannels(1);

  generator.Process(100);
  generator.StopRecording();
  VerifyDebugDump(generator.dump_file_name());
}

TEST_F(DebugDumpTest, ChangeReverseFormat) {
  Config config;
  DebugDumpGenerator generator(config);
  generator.StartRecording();
  generator.Process(100);
  generator.SetReverseRate(48000);
  generator.ForceReverseMono(true);
  generator.Process(100);
  generator.StopRecording();
  VerifyDebugDump(generator.dump_file_name());
}

TEST_F(DebugDumpTest, ChangeOutputFormat) {
  Config config;
  DebugDumpGenerator generator(config);
  generator.StartRecording();
  generator.Process(100);
  generator.SetOutputRate(48000);
  generator.SetOutputChannels(1);
  generator.Process(100);
  generator.StopRecording();
  VerifyDebugDump(generator.dump_file_name());
}

TEST_F(DebugDumpTest, ToggleAec) {
  Config config;
  DebugDumpGenerator generator(config);
  generator.StartRecording();
  generator.Process(100);

  EchoCancellation* aec = generator.apm()->echo_cancellation();
  EXPECT_EQ(AudioProcessing::kNoError, aec->Enable(!aec->is_enabled()));

  generator.Process(100);
  generator.StopRecording();
  VerifyDebugDump(generator.dump_file_name());
}

TEST_F(DebugDumpTest, ToggleDelayAgnosticAec) {
  Config config;
  config.Set<DelayAgnostic>(new DelayAgnostic(true));
  DebugDumpGenerator generator(config);
  generator.StartRecording();
  generator.Process(100);

  EchoCancellation* aec = generator.apm()->echo_cancellation();
  EXPECT_EQ(AudioProcessing::kNoError, aec->Enable(!aec->is_enabled()));

  generator.Process(100);
  generator.StopRecording();
  VerifyDebugDump(generator.dump_file_name());
}

TEST_F(DebugDumpTest, VerifyRefinedAdaptiveFilterExperimentalString) {
  Config config;
  config.Set<RefinedAdaptiveFilter>(new RefinedAdaptiveFilter(true));
  DebugDumpGenerator generator(config);
  generator.StartRecording();
  generator.Process(100);
  generator.StopRecording();

  DebugDumpReplayer debug_dump_replayer_;

  ASSERT_TRUE(debug_dump_replayer_.SetDumpFile(generator.dump_file_name()));

  while (const rtc::Optional<audioproc::Event> event =
             debug_dump_replayer_.GetNextEvent()) {
    debug_dump_replayer_.RunNextEvent();
    if (event->type() == audioproc::Event::CONFIG) {
      const audioproc::Config* msg = &event->config();
      ASSERT_TRUE(msg->has_experiments_description());
      EXPECT_PRED_FORMAT2(testing::IsSubstring, "RefinedAdaptiveFilter",
                          msg->experiments_description().c_str());
    }
  }
}

TEST_F(DebugDumpTest, VerifyCombinedExperimentalStringInclusive) {
  Config config;
  config.Set<RefinedAdaptiveFilter>(new RefinedAdaptiveFilter(true));
  config.Set<EchoCanceller3>(new EchoCanceller3(true));
  DebugDumpGenerator generator(config);
  generator.StartRecording();
  generator.Process(100);
  generator.StopRecording();

  DebugDumpReplayer debug_dump_replayer_;

  ASSERT_TRUE(debug_dump_replayer_.SetDumpFile(generator.dump_file_name()));

  while (const rtc::Optional<audioproc::Event> event =
             debug_dump_replayer_.GetNextEvent()) {
    debug_dump_replayer_.RunNextEvent();
    if (event->type() == audioproc::Event::CONFIG) {
      const audioproc::Config* msg = &event->config();
      ASSERT_TRUE(msg->has_experiments_description());
      EXPECT_PRED_FORMAT2(testing::IsSubstring, "RefinedAdaptiveFilter",
                          msg->experiments_description().c_str());
      EXPECT_PRED_FORMAT2(testing::IsSubstring, "AEC3",
                          msg->experiments_description().c_str());
    }
  }
}

TEST_F(DebugDumpTest, VerifyCombinedExperimentalStringExclusive) {
  Config config;
  config.Set<RefinedAdaptiveFilter>(new RefinedAdaptiveFilter(true));
  DebugDumpGenerator generator(config);
  generator.StartRecording();
  generator.Process(100);
  generator.StopRecording();

  DebugDumpReplayer debug_dump_replayer_;

  ASSERT_TRUE(debug_dump_replayer_.SetDumpFile(generator.dump_file_name()));

  while (const rtc::Optional<audioproc::Event> event =
             debug_dump_replayer_.GetNextEvent()) {
    debug_dump_replayer_.RunNextEvent();
    if (event->type() == audioproc::Event::CONFIG) {
      const audioproc::Config* msg = &event->config();
      ASSERT_TRUE(msg->has_experiments_description());
      EXPECT_PRED_FORMAT2(testing::IsSubstring, "RefinedAdaptiveFilter",
                          msg->experiments_description().c_str());
      EXPECT_PRED_FORMAT2(testing::IsNotSubstring, "AEC3",
                          msg->experiments_description().c_str());
    }
  }
}

TEST_F(DebugDumpTest, VerifyAec3ExperimentalString) {
  Config config;
  config.Set<EchoCanceller3>(new EchoCanceller3(true));
  DebugDumpGenerator generator(config);
  generator.StartRecording();
  generator.Process(100);
  generator.StopRecording();

  DebugDumpReplayer debug_dump_replayer_;

  ASSERT_TRUE(debug_dump_replayer_.SetDumpFile(generator.dump_file_name()));

  while (const rtc::Optional<audioproc::Event> event =
             debug_dump_replayer_.GetNextEvent()) {
    debug_dump_replayer_.RunNextEvent();
    if (event->type() == audioproc::Event::CONFIG) {
      const audioproc::Config* msg = &event->config();
      ASSERT_TRUE(msg->has_experiments_description());
      EXPECT_PRED_FORMAT2(testing::IsSubstring, "AEC3",
                          msg->experiments_description().c_str());
    }
  }
}

TEST_F(DebugDumpTest, VerifyEmptyExperimentalString) {
  Config config;
  DebugDumpGenerator generator(config);
  generator.StartRecording();
  generator.Process(100);
  generator.StopRecording();

  DebugDumpReplayer debug_dump_replayer_;

  ASSERT_TRUE(debug_dump_replayer_.SetDumpFile(generator.dump_file_name()));

  while (const rtc::Optional<audioproc::Event> event =
             debug_dump_replayer_.GetNextEvent()) {
    debug_dump_replayer_.RunNextEvent();
    if (event->type() == audioproc::Event::CONFIG) {
      const audioproc::Config* msg = &event->config();
      ASSERT_TRUE(msg->has_experiments_description());
      EXPECT_EQ(0u, msg->experiments_description().size());
    }
  }
}

TEST_F(DebugDumpTest, ToggleAecLevel) {
  Config config;
  DebugDumpGenerator generator(config);
  EchoCancellation* aec = generator.apm()->echo_cancellation();
  EXPECT_EQ(AudioProcessing::kNoError, aec->Enable(true));
  EXPECT_EQ(AudioProcessing::kNoError,
            aec->set_suppression_level(EchoCancellation::kLowSuppression));
  generator.StartRecording();
  generator.Process(100);

  EXPECT_EQ(AudioProcessing::kNoError,
            aec->set_suppression_level(EchoCancellation::kHighSuppression));
  generator.Process(100);
  generator.StopRecording();
  VerifyDebugDump(generator.dump_file_name());
}

#if defined(WEBRTC_ANDROID)
// AGC may not be supported on Android.
#define MAYBE_ToggleAgc DISABLED_ToggleAgc
#else
#define MAYBE_ToggleAgc ToggleAgc
#endif
TEST_F(DebugDumpTest, MAYBE_ToggleAgc) {
  Config config;
  DebugDumpGenerator generator(config);
  generator.StartRecording();
  generator.Process(100);

  GainControl* agc = generator.apm()->gain_control();
  EXPECT_EQ(AudioProcessing::kNoError, agc->Enable(!agc->is_enabled()));

  generator.Process(100);
  generator.StopRecording();
  VerifyDebugDump(generator.dump_file_name());
}

TEST_F(DebugDumpTest, ToggleNs) {
  Config config;
  DebugDumpGenerator generator(config);
  generator.StartRecording();
  generator.Process(100);

  NoiseSuppression* ns = generator.apm()->noise_suppression();
  EXPECT_EQ(AudioProcessing::kNoError, ns->Enable(!ns->is_enabled()));

  generator.Process(100);
  generator.StopRecording();
  VerifyDebugDump(generator.dump_file_name());
}

TEST_F(DebugDumpTest, TransientSuppressionOn) {
  Config config;
  config.Set<ExperimentalNs>(new ExperimentalNs(true));
  DebugDumpGenerator generator(config);
  generator.StartRecording();
  generator.Process(100);
  generator.StopRecording();
  VerifyDebugDump(generator.dump_file_name());
}

}  // namespace test
}  // namespace webrtc
