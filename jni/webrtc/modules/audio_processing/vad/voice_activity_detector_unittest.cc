/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_processing/vad/voice_activity_detector.h"

#include <algorithm>
#include <vector>

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/test/testsupport/fileutils.h"

namespace webrtc {
namespace {

const int kStartTimeSec = 16;
const float kMeanSpeechProbability = 0.3f;
const float kMaxNoiseProbability = 0.1f;
const size_t kNumChunks = 300u;
const size_t kNumChunksPerIsacBlock = 3;

void GenerateNoise(std::vector<int16_t>* data) {
  for (size_t i = 0; i < data->size(); ++i) {
    // std::rand returns between 0 and RAND_MAX, but this will work because it
    // wraps into some random place.
    (*data)[i] = std::rand();
  }
}

}  // namespace

TEST(VoiceActivityDetectorTest, ConstructorSetsDefaultValues) {
  const float kDefaultVoiceValue = 1.f;

  VoiceActivityDetector vad;

  std::vector<double> p = vad.chunkwise_voice_probabilities();
  std::vector<double> rms = vad.chunkwise_rms();

  EXPECT_EQ(p.size(), 0u);
  EXPECT_EQ(rms.size(), 0u);

  EXPECT_FLOAT_EQ(vad.last_voice_probability(), kDefaultVoiceValue);
}

TEST(VoiceActivityDetectorTest, Speech16kHzHasHighVoiceProbabilities) {
  const int kSampleRateHz = 16000;
  const int kLength10Ms = kSampleRateHz / 100;

  VoiceActivityDetector vad;

  std::vector<int16_t> data(kLength10Ms);
  float mean_probability = 0.f;

  FILE* pcm_file =
      fopen(test::ResourcePath("audio_processing/transient/audio16kHz", "pcm")
                .c_str(),
            "rb");
  ASSERT_TRUE(pcm_file != nullptr);
  // The silences in the file are skipped to get a more robust voice probability
  // for speech.
  ASSERT_EQ(fseek(pcm_file, kStartTimeSec * kSampleRateHz * sizeof(data[0]),
                  SEEK_SET),
            0);

  size_t num_chunks = 0;
  while (fread(&data[0], sizeof(data[0]), data.size(), pcm_file) ==
         data.size()) {
    vad.ProcessChunk(&data[0], data.size(), kSampleRateHz);

    mean_probability += vad.last_voice_probability();

    ++num_chunks;
  }

  mean_probability /= num_chunks;

  EXPECT_GT(mean_probability, kMeanSpeechProbability);
}

TEST(VoiceActivityDetectorTest, Speech32kHzHasHighVoiceProbabilities) {
  const int kSampleRateHz = 32000;
  const int kLength10Ms = kSampleRateHz / 100;

  VoiceActivityDetector vad;

  std::vector<int16_t> data(kLength10Ms);
  float mean_probability = 0.f;

  FILE* pcm_file =
      fopen(test::ResourcePath("audio_processing/transient/audio32kHz", "pcm")
                .c_str(),
            "rb");
  ASSERT_TRUE(pcm_file != nullptr);
  // The silences in the file are skipped to get a more robust voice probability
  // for speech.
  ASSERT_EQ(fseek(pcm_file, kStartTimeSec * kSampleRateHz * sizeof(data[0]),
                  SEEK_SET),
            0);

  size_t num_chunks = 0;
  while (fread(&data[0], sizeof(data[0]), data.size(), pcm_file) ==
         data.size()) {
    vad.ProcessChunk(&data[0], data.size(), kSampleRateHz);

    mean_probability += vad.last_voice_probability();

    ++num_chunks;
  }

  mean_probability /= num_chunks;

  EXPECT_GT(mean_probability, kMeanSpeechProbability);
}

TEST(VoiceActivityDetectorTest, Noise16kHzHasLowVoiceProbabilities) {
  VoiceActivityDetector vad;

  std::vector<int16_t> data(kLength10Ms);
  float max_probability = 0.f;

  std::srand(42);

  for (size_t i = 0; i < kNumChunks; ++i) {
    GenerateNoise(&data);

    vad.ProcessChunk(&data[0], data.size(), kSampleRateHz);

    // Before the |vad has enough data to process an ISAC block it will return
    // the default value, 1.f, which would ruin the |max_probability| value.
    if (i > kNumChunksPerIsacBlock) {
      max_probability = std::max(max_probability, vad.last_voice_probability());
    }
  }

  EXPECT_LT(max_probability, kMaxNoiseProbability);
}

TEST(VoiceActivityDetectorTest, Noise32kHzHasLowVoiceProbabilities) {
  VoiceActivityDetector vad;

  std::vector<int16_t> data(2 * kLength10Ms);
  float max_probability = 0.f;

  std::srand(42);

  for (size_t i = 0; i < kNumChunks; ++i) {
    GenerateNoise(&data);

    vad.ProcessChunk(&data[0], data.size(), 2 * kSampleRateHz);

    // Before the |vad has enough data to process an ISAC block it will return
    // the default value, 1.f, which would ruin the |max_probability| value.
    if (i > kNumChunksPerIsacBlock) {
      max_probability = std::max(max_probability, vad.last_voice_probability());
    }
  }

  EXPECT_LT(max_probability, kMaxNoiseProbability);
}

}  // namespace webrtc
