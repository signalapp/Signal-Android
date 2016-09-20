/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <cmath>
#include <cstring>
#include <memory>

#include "testing/gmock/include/gmock/gmock.h"
#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/base/timeutils.h"
#include "webrtc/common_audio/include/audio_util.h"
#include "webrtc/common_audio/resampler/push_sinc_resampler.h"
#include "webrtc/common_audio/resampler/sinusoidal_linear_chirp_source.h"
#include "webrtc/typedefs.h"

namespace webrtc {
namespace {

// Almost all conversions have an RMS error of around -14 dbFS.
const double kResamplingRMSError = -14.42;

// Used to convert errors to dbFS.
template <typename T>
T DBFS(T x) {
  return 20 * std::log10(x);
}

}  // namespace

class PushSincResamplerTest : public ::testing::TestWithParam<
    ::testing::tuple<int, int, double, double>> {
 public:
  PushSincResamplerTest()
      : input_rate_(::testing::get<0>(GetParam())),
        output_rate_(::testing::get<1>(GetParam())),
        rms_error_(::testing::get<2>(GetParam())),
        low_freq_error_(::testing::get<3>(GetParam())) {
  }

  ~PushSincResamplerTest() override {}

 protected:
  void ResampleBenchmarkTest(bool int_format);
  void ResampleTest(bool int_format);

  int input_rate_;
  int output_rate_;
  double rms_error_;
  double low_freq_error_;
};

class ZeroSource : public SincResamplerCallback {
 public:
  void Run(size_t frames, float* destination) {
    std::memset(destination, 0, sizeof(float) * frames);
  }
};

void PushSincResamplerTest::ResampleBenchmarkTest(bool int_format) {
  const size_t input_samples = static_cast<size_t>(input_rate_ / 100);
  const size_t output_samples = static_cast<size_t>(output_rate_ / 100);
  const int kResampleIterations = 500000;

  // Source for data to be resampled.
  ZeroSource resampler_source;

  std::unique_ptr<float[]> resampled_destination(new float[output_samples]);
  std::unique_ptr<float[]> source(new float[input_samples]);
  std::unique_ptr<int16_t[]> source_int(new int16_t[input_samples]);
  std::unique_ptr<int16_t[]> destination_int(new int16_t[output_samples]);

  resampler_source.Run(input_samples, source.get());
  for (size_t i = 0; i < input_samples; ++i) {
    source_int[i] = static_cast<int16_t>(floor(32767 * source[i] + 0.5));
  }

  printf("Benchmarking %d iterations of %d Hz -> %d Hz:\n",
         kResampleIterations, input_rate_, output_rate_);
  const double io_ratio = input_rate_ / static_cast<double>(output_rate_);
  SincResampler sinc_resampler(io_ratio, SincResampler::kDefaultRequestSize,
                               &resampler_source);
  int64_t start = rtc::TimeNanos();
  for (int i = 0; i < kResampleIterations; ++i) {
    sinc_resampler.Resample(output_samples, resampled_destination.get());
  }
  double total_time_sinc_us =
      (rtc::TimeNanos() - start) / rtc::kNumNanosecsPerMicrosec;
  printf("SincResampler took %.2f us per frame.\n",
         total_time_sinc_us / kResampleIterations);

  PushSincResampler resampler(input_samples, output_samples);
  start = rtc::TimeNanos();
  if (int_format) {
    for (int i = 0; i < kResampleIterations; ++i) {
      EXPECT_EQ(output_samples,
                resampler.Resample(source_int.get(),
                                   input_samples,
                                   destination_int.get(),
                                   output_samples));
    }
  } else {
    for (int i = 0; i < kResampleIterations; ++i) {
      EXPECT_EQ(output_samples,
                resampler.Resample(source.get(),
                                   input_samples,
                                   resampled_destination.get(),
                                   output_samples));
    }
  }
  double total_time_us =
      (rtc::TimeNanos() - start) / rtc::kNumNanosecsPerMicrosec;
  printf("PushSincResampler took %.2f us per frame; which is a %.1f%% overhead "
         "on SincResampler.\n\n", total_time_us / kResampleIterations,
         (total_time_us - total_time_sinc_us) / total_time_sinc_us * 100);
}

// Disabled because it takes too long to run routinely. Use for performance
// benchmarking when needed.
TEST_P(PushSincResamplerTest, DISABLED_BenchmarkInt) {
  ResampleBenchmarkTest(true);
}

TEST_P(PushSincResamplerTest, DISABLED_BenchmarkFloat) {
  ResampleBenchmarkTest(false);
}

// Tests resampling using a given input and output sample rate.
void PushSincResamplerTest::ResampleTest(bool int_format) {
  // Make comparisons using one second of data.
  static const double kTestDurationSecs = 1;
  // 10 ms blocks.
  const size_t kNumBlocks = static_cast<size_t>(kTestDurationSecs * 100);
  const size_t input_block_size = static_cast<size_t>(input_rate_ / 100);
  const size_t output_block_size = static_cast<size_t>(output_rate_ / 100);
  const size_t input_samples =
      static_cast<size_t>(kTestDurationSecs * input_rate_);
  const size_t output_samples =
      static_cast<size_t>(kTestDurationSecs * output_rate_);

  // Nyquist frequency for the input sampling rate.
  const double input_nyquist_freq = 0.5 * input_rate_;

  // Source for data to be resampled.
  SinusoidalLinearChirpSource resampler_source(
      input_rate_, input_samples, input_nyquist_freq, 0);

  PushSincResampler resampler(input_block_size, output_block_size);

  // TODO(dalecurtis): If we switch to AVX/SSE optimization, we'll need to
  // allocate these on 32-byte boundaries and ensure they're sized % 32 bytes.
  std::unique_ptr<float[]> resampled_destination(new float[output_samples]);
  std::unique_ptr<float[]> pure_destination(new float[output_samples]);
  std::unique_ptr<float[]> source(new float[input_samples]);
  std::unique_ptr<int16_t[]> source_int(new int16_t[input_block_size]);
  std::unique_ptr<int16_t[]> destination_int(new int16_t[output_block_size]);

  // The sinc resampler has an implicit delay of approximately half the kernel
  // size at the input sample rate. By moving to a push model, this delay
  // becomes explicit and is managed by zero-stuffing in PushSincResampler. We
  // deal with it in the test by delaying the "pure" source to match. It must be
  // checked before the first call to Resample(), because ChunkSize() will
  // change afterwards.
  const size_t output_delay_samples = output_block_size -
      resampler.get_resampler_for_testing()->ChunkSize();

  // Generate resampled signal.
  // With the PushSincResampler, we produce the signal block-by-10ms-block
  // rather than in a single pass, to exercise how it will be used in WebRTC.
  resampler_source.Run(input_samples, source.get());
  if (int_format) {
    for (size_t i = 0; i < kNumBlocks; ++i) {
      FloatToS16(&source[i * input_block_size], input_block_size,
               source_int.get());
      EXPECT_EQ(output_block_size,
                resampler.Resample(source_int.get(),
                                   input_block_size,
                                   destination_int.get(),
                                   output_block_size));
      S16ToFloat(destination_int.get(), output_block_size,
               &resampled_destination[i * output_block_size]);
    }
  } else {
    for (size_t i = 0; i < kNumBlocks; ++i) {
      EXPECT_EQ(
          output_block_size,
          resampler.Resample(&source[i * input_block_size],
                             input_block_size,
                             &resampled_destination[i * output_block_size],
                             output_block_size));
    }
  }

  // Generate pure signal.
  SinusoidalLinearChirpSource pure_source(
      output_rate_, output_samples, input_nyquist_freq, output_delay_samples);
  pure_source.Run(output_samples, pure_destination.get());

  // Range of the Nyquist frequency (0.5 * min(input rate, output_rate)) which
  // we refer to as low and high.
  static const double kLowFrequencyNyquistRange = 0.7;
  static const double kHighFrequencyNyquistRange = 0.9;

  // Calculate Root-Mean-Square-Error and maximum error for the resampling.
  double sum_of_squares = 0;
  double low_freq_max_error = 0;
  double high_freq_max_error = 0;
  int minimum_rate = std::min(input_rate_, output_rate_);
  double low_frequency_range = kLowFrequencyNyquistRange * 0.5 * minimum_rate;
  double high_frequency_range = kHighFrequencyNyquistRange * 0.5 * minimum_rate;

  for (size_t i = 0; i < output_samples; ++i) {
    double error = fabs(resampled_destination[i] - pure_destination[i]);

    if (pure_source.Frequency(i) < low_frequency_range) {
      if (error > low_freq_max_error)
        low_freq_max_error = error;
    } else if (pure_source.Frequency(i) < high_frequency_range) {
      if (error > high_freq_max_error)
        high_freq_max_error = error;
    }
    // TODO(dalecurtis): Sanity check frequencies > kHighFrequencyNyquistRange.

    sum_of_squares += error * error;
  }

  double rms_error = sqrt(sum_of_squares / output_samples);

  rms_error = DBFS(rms_error);
  // In order to keep the thresholds in this test identical to SincResamplerTest
  // we must account for the quantization error introduced by truncating from
  // float to int. This happens twice (once at input and once at output) and we
  // allow for the maximum possible error (1 / 32767) for each step.
  //
  // The quantization error is insignificant in the RMS calculation so does not
  // need to be accounted for there.
  low_freq_max_error = DBFS(low_freq_max_error - 2.0 / 32767);
  high_freq_max_error = DBFS(high_freq_max_error - 2.0 / 32767);

  EXPECT_LE(rms_error, rms_error_);
  EXPECT_LE(low_freq_max_error, low_freq_error_);

  // All conversions currently have a high frequency error around -6 dbFS.
  static const double kHighFrequencyMaxError = -6.02;
  EXPECT_LE(high_freq_max_error, kHighFrequencyMaxError);
}

TEST_P(PushSincResamplerTest, ResampleInt) { ResampleTest(true); }

TEST_P(PushSincResamplerTest, ResampleFloat) { ResampleTest(false); }

// Thresholds chosen arbitrarily based on what each resampling reported during
// testing.  All thresholds are in dbFS, http://en.wikipedia.org/wiki/DBFS.
INSTANTIATE_TEST_CASE_P(
    PushSincResamplerTest,
    PushSincResamplerTest,
    ::testing::Values(
        // First run through the rates tested in SincResamplerTest. The
        // thresholds are identical.
        //
        // We don't test rates which fail to provide an integer number of
        // samples in a 10 ms block (22050 and 11025 Hz). WebRTC doesn't support
        // these rates in any case (for the same reason).

        // To 44.1kHz
        ::testing::make_tuple(8000, 44100, kResamplingRMSError, -62.73),
        ::testing::make_tuple(16000, 44100, kResamplingRMSError, -62.54),
        ::testing::make_tuple(32000, 44100, kResamplingRMSError, -63.32),
        ::testing::make_tuple(44100, 44100, kResamplingRMSError, -73.53),
        ::testing::make_tuple(48000, 44100, -15.01, -64.04),
        ::testing::make_tuple(96000, 44100, -18.49, -25.51),
        ::testing::make_tuple(192000, 44100, -20.50, -13.31),

        // To 48kHz
        ::testing::make_tuple(8000, 48000, kResamplingRMSError, -63.43),
        ::testing::make_tuple(16000, 48000, kResamplingRMSError, -63.96),
        ::testing::make_tuple(32000, 48000, kResamplingRMSError, -64.04),
        ::testing::make_tuple(44100, 48000, kResamplingRMSError, -62.63),
        ::testing::make_tuple(48000, 48000, kResamplingRMSError, -73.52),
        ::testing::make_tuple(96000, 48000, -18.40, -28.44),
        ::testing::make_tuple(192000, 48000, -20.43, -14.11),

        // To 96kHz
        ::testing::make_tuple(8000, 96000, kResamplingRMSError, -63.19),
        ::testing::make_tuple(16000, 96000, kResamplingRMSError, -63.39),
        ::testing::make_tuple(32000, 96000, kResamplingRMSError, -63.95),
        ::testing::make_tuple(44100, 96000, kResamplingRMSError, -62.63),
        ::testing::make_tuple(48000, 96000, kResamplingRMSError, -73.52),
        ::testing::make_tuple(96000, 96000, kResamplingRMSError, -73.52),
        ::testing::make_tuple(192000, 96000, kResamplingRMSError, -28.41),

        // To 192kHz
        ::testing::make_tuple(8000, 192000, kResamplingRMSError, -63.10),
        ::testing::make_tuple(16000, 192000, kResamplingRMSError, -63.14),
        ::testing::make_tuple(32000, 192000, kResamplingRMSError, -63.38),
        ::testing::make_tuple(44100, 192000, kResamplingRMSError, -62.63),
        ::testing::make_tuple(48000, 192000, kResamplingRMSError, -73.44),
        ::testing::make_tuple(96000, 192000, kResamplingRMSError, -73.52),
        ::testing::make_tuple(192000, 192000, kResamplingRMSError, -73.52),

        // Next run through some additional cases interesting for WebRTC.
        // We skip some extreme downsampled cases (192 -> {8, 16}, 96 -> 8)
        // because they violate |kHighFrequencyMaxError|, which is not
        // unexpected. It's very unlikely that we'll see these conversions in
        // practice anyway.

        // To 8 kHz
        ::testing::make_tuple(8000, 8000, kResamplingRMSError, -75.50),
        ::testing::make_tuple(16000, 8000, -18.56, -28.79),
        ::testing::make_tuple(32000, 8000, -20.36, -14.13),
        ::testing::make_tuple(44100, 8000, -21.00, -11.39),
        ::testing::make_tuple(48000, 8000, -20.96, -11.04),

        // To 16 kHz
        ::testing::make_tuple(8000, 16000, kResamplingRMSError, -70.30),
        ::testing::make_tuple(16000, 16000, kResamplingRMSError, -75.51),
        ::testing::make_tuple(32000, 16000, -18.48, -28.59),
        ::testing::make_tuple(44100, 16000, -19.30, -19.67),
        ::testing::make_tuple(48000, 16000, -19.81, -18.11),
        ::testing::make_tuple(96000, 16000, -20.95, -10.96),

        // To 32 kHz
        ::testing::make_tuple(8000, 32000, kResamplingRMSError, -70.30),
        ::testing::make_tuple(16000, 32000, kResamplingRMSError, -75.51),
        ::testing::make_tuple(32000, 32000, kResamplingRMSError, -75.51),
        ::testing::make_tuple(44100, 32000, -16.44, -51.10),
        ::testing::make_tuple(48000, 32000, -16.90, -44.03),
        ::testing::make_tuple(96000, 32000, -19.61, -18.04),
        ::testing::make_tuple(192000, 32000, -21.02, -10.94)));

}  // namespace webrtc
