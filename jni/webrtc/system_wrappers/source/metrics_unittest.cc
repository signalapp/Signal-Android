/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "testing/gtest/include/gtest/gtest.h"

#include "webrtc/system_wrappers/include/metrics.h"
#include "webrtc/system_wrappers/include/metrics_default.h"

namespace webrtc {
namespace {
const int kSample = 22;

void AddSparseSample(const std::string& name, int sample) {
  RTC_HISTOGRAM_COUNTS_SPARSE_100(name, sample);
}
void AddSampleWithVaryingName(int index, const std::string& name, int sample) {
  RTC_HISTOGRAMS_COUNTS_100(index, name, sample);
}
#if RTC_DCHECK_IS_ON && GTEST_HAS_DEATH_TEST && !defined(WEBRTC_ANDROID)
void AddSample(const std::string& name, int sample) {
  RTC_HISTOGRAM_COUNTS_100(name, sample);
}
#endif  // RTC_DCHECK_IS_ON && GTEST_HAS_DEATH_TEST && !defined(WEBRTC_ANDROID)
}  // namespace

class MetricsTest : public ::testing::Test {
 public:
  MetricsTest() {}

 protected:
  virtual void SetUp() {
    metrics::Reset();
  }
};

TEST_F(MetricsTest, InitiallyNoSamples) {
  EXPECT_EQ(0, metrics::NumSamples("NonExisting"));
  EXPECT_EQ(0, metrics::NumEvents("NonExisting", kSample));
}

TEST_F(MetricsTest, RtcHistogramPercent_AddSample) {
  const std::string kName = "Percentage";
  RTC_HISTOGRAM_PERCENTAGE(kName, kSample);
  EXPECT_EQ(1, metrics::NumSamples(kName));
  EXPECT_EQ(1, metrics::NumEvents(kName, kSample));
}

TEST_F(MetricsTest, RtcHistogramEnumeration_AddSample) {
  const std::string kName = "Enumeration";
  RTC_HISTOGRAM_ENUMERATION(kName, kSample, kSample + 1);
  EXPECT_EQ(1, metrics::NumSamples(kName));
  EXPECT_EQ(1, metrics::NumEvents(kName, kSample));
}

TEST_F(MetricsTest, RtcHistogramCountsSparse_AddSample) {
  const std::string kName = "CountsSparse100";
  RTC_HISTOGRAM_COUNTS_SPARSE_100(kName, kSample);
  EXPECT_EQ(1, metrics::NumSamples(kName));
  EXPECT_EQ(1, metrics::NumEvents(kName, kSample));
}

TEST_F(MetricsTest, RtcHistogramCounts_AddSample) {
  const std::string kName = "Counts100";
  RTC_HISTOGRAM_COUNTS_100(kName, kSample);
  EXPECT_EQ(1, metrics::NumSamples(kName));
  EXPECT_EQ(1, metrics::NumEvents(kName, kSample));
}

TEST_F(MetricsTest, RtcHistogramCounts_AddMultipleSamples) {
  const std::string kName = "Counts200";
  const int kNumSamples = 10;
  for (int i = 1; i <= kNumSamples; ++i) {
    RTC_HISTOGRAM_COUNTS_200(kName, i);
    EXPECT_EQ(1, metrics::NumEvents(kName, i));
    EXPECT_EQ(i, metrics::NumSamples(kName));
  }
}

TEST_F(MetricsTest, RtcHistogramsCounts_AddSample) {
  AddSampleWithVaryingName(0, "Name1", kSample);
  AddSampleWithVaryingName(1, "Name2", kSample + 1);
  AddSampleWithVaryingName(2, "Name3", kSample + 2);
  EXPECT_EQ(1, metrics::NumSamples("Name1"));
  EXPECT_EQ(1, metrics::NumSamples("Name2"));
  EXPECT_EQ(1, metrics::NumSamples("Name3"));
  EXPECT_EQ(1, metrics::NumEvents("Name1", kSample + 0));
  EXPECT_EQ(1, metrics::NumEvents("Name2", kSample + 1));
  EXPECT_EQ(1, metrics::NumEvents("Name3", kSample + 2));
}

#if RTC_DCHECK_IS_ON && GTEST_HAS_DEATH_TEST && !defined(WEBRTC_ANDROID)
TEST_F(MetricsTest, RtcHistogramsCounts_InvalidIndex) {
  EXPECT_DEATH(RTC_HISTOGRAMS_COUNTS_1000(-1, "Name", kSample), "");
  EXPECT_DEATH(RTC_HISTOGRAMS_COUNTS_1000(3, "Name", kSample), "");
  EXPECT_DEATH(RTC_HISTOGRAMS_COUNTS_1000(3u, "Name", kSample), "");
}
#endif

TEST_F(MetricsTest, RtcHistogramSparse_NonConstantNameWorks) {
  AddSparseSample("Sparse1", kSample);
  AddSparseSample("Sparse2", kSample);
  EXPECT_EQ(1, metrics::NumSamples("Sparse1"));
  EXPECT_EQ(1, metrics::NumSamples("Sparse2"));
}

#if RTC_DCHECK_IS_ON && GTEST_HAS_DEATH_TEST && !defined(WEBRTC_ANDROID)
TEST_F(MetricsTest, RtcHistogram_FailsForNonConstantName) {
  AddSample("ConstantName1", kSample);
  EXPECT_DEATH(AddSample("NotConstantName1", kSample), "");
}
#endif

}  // namespace webrtc
