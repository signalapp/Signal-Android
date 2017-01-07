/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
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
const char kName[] = "Name";

int NumSamples(
    const std::string& name,
    const std::map<std::string, std::unique_ptr<metrics::SampleInfo>>&
        histograms) {
  const auto it = histograms.find(name);
  if (it == histograms.end())
    return 0;

  int num_samples = 0;
  for (const auto& sample : it->second->samples)
    num_samples += sample.second;

  return num_samples;
}

int NumEvents(const std::string& name,
              int sample,
              const std::map<std::string, std::unique_ptr<metrics::SampleInfo>>&
                  histograms) {
  const auto it = histograms.find(name);
  if (it == histograms.end())
    return 0;

  const auto it_sample = it->second->samples.find(sample);
  if (it_sample == it->second->samples.end())
    return 0;

  return it_sample->second;
}
}  // namespace

class MetricsDefaultTest : public ::testing::Test {
 public:
  MetricsDefaultTest() {}

 protected:
  virtual void SetUp() {
    metrics::Reset();
  }
};

TEST_F(MetricsDefaultTest, Reset) {
  RTC_HISTOGRAM_PERCENTAGE(kName, kSample);
  EXPECT_EQ(1, metrics::NumSamples(kName));
  metrics::Reset();
  EXPECT_EQ(0, metrics::NumSamples(kName));
}

TEST_F(MetricsDefaultTest, NumSamples) {
  RTC_HISTOGRAM_PERCENTAGE(kName, 5);
  RTC_HISTOGRAM_PERCENTAGE(kName, 5);
  RTC_HISTOGRAM_PERCENTAGE(kName, 10);
  EXPECT_EQ(3, metrics::NumSamples(kName));
  EXPECT_EQ(0, metrics::NumSamples("NonExisting"));
}

TEST_F(MetricsDefaultTest, NumEvents) {
  RTC_HISTOGRAM_PERCENTAGE(kName, 5);
  RTC_HISTOGRAM_PERCENTAGE(kName, 5);
  RTC_HISTOGRAM_PERCENTAGE(kName, 10);
  EXPECT_EQ(2, metrics::NumEvents(kName, 5));
  EXPECT_EQ(1, metrics::NumEvents(kName, 10));
  EXPECT_EQ(0, metrics::NumEvents(kName, 11));
  EXPECT_EQ(0, metrics::NumEvents("NonExisting", 5));
}

TEST_F(MetricsDefaultTest, MinSample) {
  RTC_HISTOGRAM_PERCENTAGE(kName, kSample);
  RTC_HISTOGRAM_PERCENTAGE(kName, kSample + 1);
  EXPECT_EQ(kSample, metrics::MinSample(kName));
  EXPECT_EQ(-1, metrics::MinSample("NonExisting"));
}

TEST_F(MetricsDefaultTest, Overflow) {
  const std::string kName = "Overflow";
  // Samples should end up in overflow bucket.
  RTC_HISTOGRAM_PERCENTAGE(kName, 101);
  EXPECT_EQ(1, metrics::NumSamples(kName));
  EXPECT_EQ(1, metrics::NumEvents(kName, 101));
  RTC_HISTOGRAM_PERCENTAGE(kName, 102);
  EXPECT_EQ(2, metrics::NumSamples(kName));
  EXPECT_EQ(2, metrics::NumEvents(kName, 101));
}

TEST_F(MetricsDefaultTest, Underflow) {
  const std::string kName = "Underflow";
  // Samples should end up in underflow bucket.
  RTC_HISTOGRAM_COUNTS_10000(kName, 0);
  EXPECT_EQ(1, metrics::NumSamples(kName));
  EXPECT_EQ(1, metrics::NumEvents(kName, 0));
  RTC_HISTOGRAM_COUNTS_10000(kName, -1);
  EXPECT_EQ(2, metrics::NumSamples(kName));
  EXPECT_EQ(2, metrics::NumEvents(kName, 0));
}

TEST_F(MetricsDefaultTest, GetAndReset) {
  std::map<std::string, std::unique_ptr<metrics::SampleInfo>> histograms;
  metrics::GetAndReset(&histograms);
  EXPECT_EQ(0u, histograms.size());
  RTC_HISTOGRAM_PERCENTAGE("Histogram1", 4);
  RTC_HISTOGRAM_PERCENTAGE("Histogram1", 5);
  RTC_HISTOGRAM_PERCENTAGE("Histogram1", 5);
  RTC_HISTOGRAM_PERCENTAGE("Histogram2", 10);
  EXPECT_EQ(3, metrics::NumSamples("Histogram1"));
  EXPECT_EQ(1, metrics::NumSamples("Histogram2"));

  metrics::GetAndReset(&histograms);
  EXPECT_EQ(2u, histograms.size());
  EXPECT_EQ(0, metrics::NumSamples("Histogram1"));
  EXPECT_EQ(0, metrics::NumSamples("Histogram2"));

  EXPECT_EQ(3, NumSamples("Histogram1", histograms));
  EXPECT_EQ(1, NumSamples("Histogram2", histograms));
  EXPECT_EQ(1, NumEvents("Histogram1", 4, histograms));
  EXPECT_EQ(2, NumEvents("Histogram1", 5, histograms));
  EXPECT_EQ(1, NumEvents("Histogram2", 10, histograms));
}

TEST_F(MetricsDefaultTest, TestMinMaxBucket) {
  const std::string kName = "MinMaxCounts100";
  RTC_HISTOGRAM_COUNTS_100(kName, 4);

  std::map<std::string, std::unique_ptr<metrics::SampleInfo>> histograms;
  metrics::GetAndReset(&histograms);
  EXPECT_EQ(1u, histograms.size());
  EXPECT_EQ(kName, histograms.begin()->second->name);
  EXPECT_EQ(1, histograms.begin()->second->min);
  EXPECT_EQ(100, histograms.begin()->second->max);
  EXPECT_EQ(50u, histograms.begin()->second->bucket_count);
  EXPECT_EQ(1u, histograms.begin()->second->samples.size());
}

}  // namespace webrtc
