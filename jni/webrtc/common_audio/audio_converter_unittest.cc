/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <cmath>
#include <algorithm>
#include <memory>
#include <vector>

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/base/arraysize.h"
#include "webrtc/base/format_macros.h"
#include "webrtc/common_audio/audio_converter.h"
#include "webrtc/common_audio/channel_buffer.h"
#include "webrtc/common_audio/resampler/push_sinc_resampler.h"

namespace webrtc {

typedef std::unique_ptr<ChannelBuffer<float>> ScopedBuffer;

// Sets the signal value to increase by |data| with every sample.
ScopedBuffer CreateBuffer(const std::vector<float>& data, size_t frames) {
  const size_t num_channels = data.size();
  ScopedBuffer sb(new ChannelBuffer<float>(frames, num_channels));
  for (size_t i = 0; i < num_channels; ++i)
    for (size_t j = 0; j < frames; ++j)
      sb->channels()[i][j] = data[i] * j;
  return sb;
}

void VerifyParams(const ChannelBuffer<float>& ref,
                  const ChannelBuffer<float>& test) {
  EXPECT_EQ(ref.num_channels(), test.num_channels());
  EXPECT_EQ(ref.num_frames(), test.num_frames());
}

// Computes the best SNR based on the error between |ref_frame| and
// |test_frame|. It searches around |expected_delay| in samples between the
// signals to compensate for the resampling delay.
float ComputeSNR(const ChannelBuffer<float>& ref,
                 const ChannelBuffer<float>& test,
                 size_t expected_delay) {
  VerifyParams(ref, test);
  float best_snr = 0;
  size_t best_delay = 0;

  // Search within one sample of the expected delay.
  for (size_t delay = std::max(expected_delay, static_cast<size_t>(1)) - 1;
       delay <= std::min(expected_delay + 1, ref.num_frames());
       ++delay) {
    float mse = 0;
    float variance = 0;
    float mean = 0;
    for (size_t i = 0; i < ref.num_channels(); ++i) {
      for (size_t j = 0; j < ref.num_frames() - delay; ++j) {
        float error = ref.channels()[i][j] - test.channels()[i][j + delay];
        mse += error * error;
        variance += ref.channels()[i][j] * ref.channels()[i][j];
        mean += ref.channels()[i][j];
      }
    }

    const size_t length = ref.num_channels() * (ref.num_frames() - delay);
    mse /= length;
    variance /= length;
    mean /= length;
    variance -= mean * mean;
    float snr = 100;  // We assign 100 dB to the zero-error case.
    if (mse > 0)
      snr = 10 * std::log10(variance / mse);
    if (snr > best_snr) {
      best_snr = snr;
      best_delay = delay;
    }
  }
  printf("SNR=%.1f dB at delay=%" PRIuS "\n", best_snr, best_delay);
  return best_snr;
}

// Sets the source to a linearly increasing signal for which we can easily
// generate a reference. Runs the AudioConverter and ensures the output has
// sufficiently high SNR relative to the reference.
void RunAudioConverterTest(size_t src_channels,
                           int src_sample_rate_hz,
                           size_t dst_channels,
                           int dst_sample_rate_hz) {
  const float kSrcLeft = 0.0002f;
  const float kSrcRight = 0.0001f;
  const float resampling_factor = (1.f * src_sample_rate_hz) /
      dst_sample_rate_hz;
  const float dst_left = resampling_factor * kSrcLeft;
  const float dst_right = resampling_factor * kSrcRight;
  const float dst_mono = (dst_left + dst_right) / 2;
  const size_t src_frames = static_cast<size_t>(src_sample_rate_hz / 100);
  const size_t dst_frames = static_cast<size_t>(dst_sample_rate_hz / 100);

  std::vector<float> src_data(1, kSrcLeft);
  if (src_channels == 2)
    src_data.push_back(kSrcRight);
  ScopedBuffer src_buffer = CreateBuffer(src_data, src_frames);

  std::vector<float> dst_data(1, 0);
  std::vector<float> ref_data;
  if (dst_channels == 1) {
    if (src_channels == 1)
      ref_data.push_back(dst_left);
    else
      ref_data.push_back(dst_mono);
  } else {
    dst_data.push_back(0);
    ref_data.push_back(dst_left);
    if (src_channels == 1)
      ref_data.push_back(dst_left);
    else
      ref_data.push_back(dst_right);
  }
  ScopedBuffer dst_buffer = CreateBuffer(dst_data, dst_frames);
  ScopedBuffer ref_buffer = CreateBuffer(ref_data, dst_frames);

  // The sinc resampler has a known delay, which we compute here.
  const size_t delay_frames = src_sample_rate_hz == dst_sample_rate_hz ? 0 :
      static_cast<size_t>(
          PushSincResampler::AlgorithmicDelaySeconds(src_sample_rate_hz) *
          dst_sample_rate_hz);
  // SNR reported on the same line later.
  printf("(%" PRIuS ", %d Hz) -> (%" PRIuS ", %d Hz) ",
         src_channels, src_sample_rate_hz, dst_channels, dst_sample_rate_hz);

  std::unique_ptr<AudioConverter> converter = AudioConverter::Create(
      src_channels, src_frames, dst_channels, dst_frames);
  converter->Convert(src_buffer->channels(), src_buffer->size(),
                     dst_buffer->channels(), dst_buffer->size());

  EXPECT_LT(43.f,
            ComputeSNR(*ref_buffer.get(), *dst_buffer.get(), delay_frames));
}

TEST(AudioConverterTest, ConversionsPassSNRThreshold) {
  const int kSampleRates[] = {8000, 16000, 32000, 44100, 48000};
  const size_t kChannels[] = {1, 2};
  for (size_t src_rate = 0; src_rate < arraysize(kSampleRates); ++src_rate) {
    for (size_t dst_rate = 0; dst_rate < arraysize(kSampleRates); ++dst_rate) {
      for (size_t src_channel = 0; src_channel < arraysize(kChannels);
           ++src_channel) {
        for (size_t dst_channel = 0; dst_channel < arraysize(kChannels);
             ++dst_channel) {
          RunAudioConverterTest(kChannels[src_channel], kSampleRates[src_rate],
                                kChannels[dst_channel], kSampleRates[dst_rate]);
        }
      }
    }
  }
}

}  // namespace webrtc
