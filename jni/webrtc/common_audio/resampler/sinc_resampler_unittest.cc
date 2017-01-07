/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// Modified from the Chromium original:
// src/media/base/sinc_resampler_unittest.cc

// MSVC++ requires this to be set before any other includes to get M_PI.
#define _USE_MATH_DEFINES

#include <math.h>

#include <memory>

#include "testing/gmock/include/gmock/gmock.h"
#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/base/timeutils.h"
#include "webrtc/common_audio/resampler/sinc_resampler.h"
#include "webrtc/common_audio/resampler/sinusoidal_linear_chirp_source.h"
#include "webrtc/system_wrappers/include/cpu_features_wrapper.h"
#include "webrtc/system_wrappers/include/stringize_macros.h"
#include "webrtc/test/test_suite.h"

using testing::_;

namespace webrtc {

static const double kSampleRateRatio = 192000.0 / 44100.0;
static const double kKernelInterpolationFactor = 0.5;

// Helper class to ensure ChunkedResample() functions properly.
class MockSource : public SincResamplerCallback {
 public:
  MOCK_METHOD2(Run, void(size_t frames, float* destination));
};

ACTION(ClearBuffer) {
  memset(arg1, 0, arg0 * sizeof(float));
}

ACTION(FillBuffer) {
  // Value chosen arbitrarily such that SincResampler resamples it to something
  // easily representable on all platforms; e.g., using kSampleRateRatio this
  // becomes 1.81219.
  memset(arg1, 64, arg0 * sizeof(float));
}

// Test requesting multiples of ChunkSize() frames results in the proper number
// of callbacks.
TEST(SincResamplerTest, ChunkedResample) {
  MockSource mock_source;

  // Choose a high ratio of input to output samples which will result in quick
  // exhaustion of SincResampler's internal buffers.
  SincResampler resampler(kSampleRateRatio, SincResampler::kDefaultRequestSize,
                          &mock_source);

  static const int kChunks = 2;
  size_t max_chunk_size = resampler.ChunkSize() * kChunks;
  std::unique_ptr<float[]> resampled_destination(new float[max_chunk_size]);

  // Verify requesting ChunkSize() frames causes a single callback.
  EXPECT_CALL(mock_source, Run(_, _))
      .Times(1).WillOnce(ClearBuffer());
  resampler.Resample(resampler.ChunkSize(), resampled_destination.get());

  // Verify requesting kChunks * ChunkSize() frames causes kChunks callbacks.
  testing::Mock::VerifyAndClear(&mock_source);
  EXPECT_CALL(mock_source, Run(_, _))
      .Times(kChunks).WillRepeatedly(ClearBuffer());
  resampler.Resample(max_chunk_size, resampled_destination.get());
}

// Test flush resets the internal state properly.
TEST(SincResamplerTest, Flush) {
  MockSource mock_source;
  SincResampler resampler(kSampleRateRatio, SincResampler::kDefaultRequestSize,
                          &mock_source);
  std::unique_ptr<float[]> resampled_destination(
      new float[resampler.ChunkSize()]);

  // Fill the resampler with junk data.
  EXPECT_CALL(mock_source, Run(_, _))
      .Times(1).WillOnce(FillBuffer());
  resampler.Resample(resampler.ChunkSize() / 2, resampled_destination.get());
  ASSERT_NE(resampled_destination[0], 0);

  // Flush and request more data, which should all be zeros now.
  resampler.Flush();
  testing::Mock::VerifyAndClear(&mock_source);
  EXPECT_CALL(mock_source, Run(_, _))
      .Times(1).WillOnce(ClearBuffer());
  resampler.Resample(resampler.ChunkSize() / 2, resampled_destination.get());
  for (size_t i = 0; i < resampler.ChunkSize() / 2; ++i)
    ASSERT_FLOAT_EQ(resampled_destination[i], 0);
}

// Test flush resets the internal state properly.
TEST(SincResamplerTest, DISABLED_SetRatioBench) {
  MockSource mock_source;
  SincResampler resampler(kSampleRateRatio, SincResampler::kDefaultRequestSize,
                          &mock_source);

  int64_t start = rtc::TimeNanos();
  for (int i = 1; i < 10000; ++i)
    resampler.SetRatio(1.0 / i);
  double total_time_c_us =
      (rtc::TimeNanos() - start) / rtc::kNumNanosecsPerMicrosec;
  printf("SetRatio() took %.2fms.\n", total_time_c_us / 1000);
}


// Define platform independent function name for Convolve* tests.
#if defined(WEBRTC_ARCH_X86_FAMILY)
#define CONVOLVE_FUNC Convolve_SSE
#elif defined(WEBRTC_ARCH_ARM_V7)
#define CONVOLVE_FUNC Convolve_NEON
#endif

// Ensure various optimized Convolve() methods return the same value.  Only run
// this test if other optimized methods exist, otherwise the default Convolve()
// will be tested by the parameterized SincResampler tests below.
#if defined(CONVOLVE_FUNC)
TEST(SincResamplerTest, Convolve) {
#if defined(WEBRTC_ARCH_X86_FAMILY)
  ASSERT_TRUE(WebRtc_GetCPUInfo(kSSE2));
#elif defined(WEBRTC_ARCH_ARM_V7)
  ASSERT_TRUE(WebRtc_GetCPUFeaturesARM() & kCPUFeatureNEON);
#endif

  // Initialize a dummy resampler.
  MockSource mock_source;
  SincResampler resampler(kSampleRateRatio, SincResampler::kDefaultRequestSize,
                          &mock_source);

  // The optimized Convolve methods are slightly more precise than Convolve_C(),
  // so comparison must be done using an epsilon.
  static const double kEpsilon = 0.00000005;

  // Use a kernel from SincResampler as input and kernel data, this has the
  // benefit of already being properly sized and aligned for Convolve_SSE().
  double result = resampler.Convolve_C(
      resampler.kernel_storage_.get(), resampler.kernel_storage_.get(),
      resampler.kernel_storage_.get(), kKernelInterpolationFactor);
  double result2 = resampler.CONVOLVE_FUNC(
      resampler.kernel_storage_.get(), resampler.kernel_storage_.get(),
      resampler.kernel_storage_.get(), kKernelInterpolationFactor);
  EXPECT_NEAR(result2, result, kEpsilon);

  // Test Convolve() w/ unaligned input pointer.
  result = resampler.Convolve_C(
      resampler.kernel_storage_.get() + 1, resampler.kernel_storage_.get(),
      resampler.kernel_storage_.get(), kKernelInterpolationFactor);
  result2 = resampler.CONVOLVE_FUNC(
      resampler.kernel_storage_.get() + 1, resampler.kernel_storage_.get(),
      resampler.kernel_storage_.get(), kKernelInterpolationFactor);
  EXPECT_NEAR(result2, result, kEpsilon);
}
#endif

// Benchmark for the various Convolve() methods.  Make sure to build with
// branding=Chrome so that RTC_DCHECKs are compiled out when benchmarking.
// Original benchmarks were run with --convolve-iterations=50000000.
TEST(SincResamplerTest, ConvolveBenchmark) {
  // Initialize a dummy resampler.
  MockSource mock_source;
  SincResampler resampler(kSampleRateRatio, SincResampler::kDefaultRequestSize,
                          &mock_source);

  // Retrieve benchmark iterations from command line.
  // TODO(ajm): Reintroduce this as a command line option.
  const int kConvolveIterations = 1000000;

  printf("Benchmarking %d iterations:\n", kConvolveIterations);

  // Benchmark Convolve_C().
  int64_t start = rtc::TimeNanos();
  for (int i = 0; i < kConvolveIterations; ++i) {
    resampler.Convolve_C(
        resampler.kernel_storage_.get(), resampler.kernel_storage_.get(),
        resampler.kernel_storage_.get(), kKernelInterpolationFactor);
  }
  double total_time_c_us =
      (rtc::TimeNanos() - start) / rtc::kNumNanosecsPerMicrosec;
  printf("Convolve_C took %.2fms.\n", total_time_c_us / 1000);

#if defined(CONVOLVE_FUNC)
#if defined(WEBRTC_ARCH_X86_FAMILY)
  ASSERT_TRUE(WebRtc_GetCPUInfo(kSSE2));
#elif defined(WEBRTC_ARCH_ARM_V7)
  ASSERT_TRUE(WebRtc_GetCPUFeaturesARM() & kCPUFeatureNEON);
#endif

  // Benchmark with unaligned input pointer.
  start = rtc::TimeNanos();
  for (int j = 0; j < kConvolveIterations; ++j) {
    resampler.CONVOLVE_FUNC(
        resampler.kernel_storage_.get() + 1, resampler.kernel_storage_.get(),
        resampler.kernel_storage_.get(), kKernelInterpolationFactor);
  }
  double total_time_optimized_unaligned_us =
      (rtc::TimeNanos() - start) / rtc::kNumNanosecsPerMicrosec;
  printf(STRINGIZE(CONVOLVE_FUNC) "(unaligned) took %.2fms; which is %.2fx "
         "faster than Convolve_C.\n", total_time_optimized_unaligned_us / 1000,
         total_time_c_us / total_time_optimized_unaligned_us);

  // Benchmark with aligned input pointer.
  start = rtc::TimeNanos();
  for (int j = 0; j < kConvolveIterations; ++j) {
    resampler.CONVOLVE_FUNC(
        resampler.kernel_storage_.get(), resampler.kernel_storage_.get(),
        resampler.kernel_storage_.get(), kKernelInterpolationFactor);
  }
  double total_time_optimized_aligned_us =
      (rtc::TimeNanos() - start) / rtc::kNumNanosecsPerMicrosec;
  printf(STRINGIZE(CONVOLVE_FUNC) " (aligned) took %.2fms; which is %.2fx "
         "faster than Convolve_C and %.2fx faster than "
         STRINGIZE(CONVOLVE_FUNC) " (unaligned).\n",
         total_time_optimized_aligned_us / 1000,
         total_time_c_us / total_time_optimized_aligned_us,
         total_time_optimized_unaligned_us / total_time_optimized_aligned_us);
#endif
}

#undef CONVOLVE_FUNC

typedef std::tr1::tuple<int, int, double, double> SincResamplerTestData;
class SincResamplerTest
    : public testing::TestWithParam<SincResamplerTestData> {
 public:
  SincResamplerTest()
      : input_rate_(std::tr1::get<0>(GetParam())),
        output_rate_(std::tr1::get<1>(GetParam())),
        rms_error_(std::tr1::get<2>(GetParam())),
        low_freq_error_(std::tr1::get<3>(GetParam())) {
  }

  virtual ~SincResamplerTest() {}

 protected:
  int input_rate_;
  int output_rate_;
  double rms_error_;
  double low_freq_error_;
};

// Tests resampling using a given input and output sample rate.
TEST_P(SincResamplerTest, Resample) {
  // Make comparisons using one second of data.
  static const double kTestDurationSecs = 1;
  const size_t input_samples =
      static_cast<size_t>(kTestDurationSecs * input_rate_);
  const size_t output_samples =
      static_cast<size_t>(kTestDurationSecs * output_rate_);

  // Nyquist frequency for the input sampling rate.
  const double input_nyquist_freq = 0.5 * input_rate_;

  // Source for data to be resampled.
  SinusoidalLinearChirpSource resampler_source(
      input_rate_, input_samples, input_nyquist_freq, 0);

  const double io_ratio = input_rate_ / static_cast<double>(output_rate_);
  SincResampler resampler(io_ratio, SincResampler::kDefaultRequestSize,
                          &resampler_source);

  // Force an update to the sample rate ratio to ensure dyanmic sample rate
  // changes are working correctly.
  std::unique_ptr<float[]> kernel(new float[SincResampler::kKernelStorageSize]);
  memcpy(kernel.get(), resampler.get_kernel_for_testing(),
         SincResampler::kKernelStorageSize);
  resampler.SetRatio(M_PI);
  ASSERT_NE(0, memcmp(kernel.get(), resampler.get_kernel_for_testing(),
                      SincResampler::kKernelStorageSize));
  resampler.SetRatio(io_ratio);
  ASSERT_EQ(0, memcmp(kernel.get(), resampler.get_kernel_for_testing(),
                      SincResampler::kKernelStorageSize));

  // TODO(dalecurtis): If we switch to AVX/SSE optimization, we'll need to
  // allocate these on 32-byte boundaries and ensure they're sized % 32 bytes.
  std::unique_ptr<float[]> resampled_destination(new float[output_samples]);
  std::unique_ptr<float[]> pure_destination(new float[output_samples]);

  // Generate resampled signal.
  resampler.Resample(output_samples, resampled_destination.get());

  // Generate pure signal.
  SinusoidalLinearChirpSource pure_source(
      output_rate_, output_samples, input_nyquist_freq, 0);
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

  // Convert each error to dbFS.
  #define DBFS(x) 20 * log10(x)
  rms_error = DBFS(rms_error);
  low_freq_max_error = DBFS(low_freq_max_error);
  high_freq_max_error = DBFS(high_freq_max_error);

  EXPECT_LE(rms_error, rms_error_);
  EXPECT_LE(low_freq_max_error, low_freq_error_);

  // All conversions currently have a high frequency error around -6 dbFS.
  static const double kHighFrequencyMaxError = -6.02;
  EXPECT_LE(high_freq_max_error, kHighFrequencyMaxError);
}

// Almost all conversions have an RMS error of around -14 dbFS.
static const double kResamplingRMSError = -14.58;

// Thresholds chosen arbitrarily based on what each resampling reported during
// testing.  All thresholds are in dbFS, http://en.wikipedia.org/wiki/DBFS.
INSTANTIATE_TEST_CASE_P(
    SincResamplerTest, SincResamplerTest, testing::Values(
        // To 44.1kHz
        std::tr1::make_tuple(8000, 44100, kResamplingRMSError, -62.73),
        std::tr1::make_tuple(11025, 44100, kResamplingRMSError, -72.19),
        std::tr1::make_tuple(16000, 44100, kResamplingRMSError, -62.54),
        std::tr1::make_tuple(22050, 44100, kResamplingRMSError, -73.53),
        std::tr1::make_tuple(32000, 44100, kResamplingRMSError, -63.32),
        std::tr1::make_tuple(44100, 44100, kResamplingRMSError, -73.53),
        std::tr1::make_tuple(48000, 44100, -15.01, -64.04),
        std::tr1::make_tuple(96000, 44100, -18.49, -25.51),
        std::tr1::make_tuple(192000, 44100, -20.50, -13.31),

        // To 48kHz
        std::tr1::make_tuple(8000, 48000, kResamplingRMSError, -63.43),
        std::tr1::make_tuple(11025, 48000, kResamplingRMSError, -62.61),
        std::tr1::make_tuple(16000, 48000, kResamplingRMSError, -63.96),
        std::tr1::make_tuple(22050, 48000, kResamplingRMSError, -62.42),
        std::tr1::make_tuple(32000, 48000, kResamplingRMSError, -64.04),
        std::tr1::make_tuple(44100, 48000, kResamplingRMSError, -62.63),
        std::tr1::make_tuple(48000, 48000, kResamplingRMSError, -73.52),
        std::tr1::make_tuple(96000, 48000, -18.40, -28.44),
        std::tr1::make_tuple(192000, 48000, -20.43, -14.11),

        // To 96kHz
        std::tr1::make_tuple(8000, 96000, kResamplingRMSError, -63.19),
        std::tr1::make_tuple(11025, 96000, kResamplingRMSError, -62.61),
        std::tr1::make_tuple(16000, 96000, kResamplingRMSError, -63.39),
        std::tr1::make_tuple(22050, 96000, kResamplingRMSError, -62.42),
        std::tr1::make_tuple(32000, 96000, kResamplingRMSError, -63.95),
        std::tr1::make_tuple(44100, 96000, kResamplingRMSError, -62.63),
        std::tr1::make_tuple(48000, 96000, kResamplingRMSError, -73.52),
        std::tr1::make_tuple(96000, 96000, kResamplingRMSError, -73.52),
        std::tr1::make_tuple(192000, 96000, kResamplingRMSError, -28.41),

        // To 192kHz
        std::tr1::make_tuple(8000, 192000, kResamplingRMSError, -63.10),
        std::tr1::make_tuple(11025, 192000, kResamplingRMSError, -62.61),
        std::tr1::make_tuple(16000, 192000, kResamplingRMSError, -63.14),
        std::tr1::make_tuple(22050, 192000, kResamplingRMSError, -62.42),
        std::tr1::make_tuple(32000, 192000, kResamplingRMSError, -63.38),
        std::tr1::make_tuple(44100, 192000, kResamplingRMSError, -62.63),
        std::tr1::make_tuple(48000, 192000, kResamplingRMSError, -73.44),
        std::tr1::make_tuple(96000, 192000, kResamplingRMSError, -73.52),
        std::tr1::make_tuple(192000, 192000, kResamplingRMSError, -73.52)));

}  // namespace webrtc
