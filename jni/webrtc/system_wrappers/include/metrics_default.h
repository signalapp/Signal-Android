/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_SYSTEM_WRAPPERS_INCLUDE_METRICS_DEFAULT_H_
#define WEBRTC_SYSTEM_WRAPPERS_INCLUDE_METRICS_DEFAULT_H_

#include <map>
#include <memory>
#include <string>

namespace webrtc {
namespace metrics {

struct SampleInfo {
  SampleInfo(const std::string& name, int min, int max, size_t bucket_count);
  ~SampleInfo();

  const std::string name;
  const int min;
  const int max;
  const size_t bucket_count;
  std::map<int, int> samples;  // <value, # of events>
};

// Enables collection of samples.
// This method should be called before any other call into webrtc.
void Enable();

// Gets histograms and clears all samples.
void GetAndReset(
    std::map<std::string, std::unique_ptr<SampleInfo>>* histograms);

// Functions below are mainly for testing.

// Clears all samples.
void Reset();

// Returns the number of times the |sample| has been added to the histogram.
int NumEvents(const std::string& name, int sample);

// Returns the total number of added samples to the histogram.
int NumSamples(const std::string& name);

// Returns the minimum sample value (or -1 if the histogram has no samples).
int MinSample(const std::string& name);

}  // namespace metrics
}  // namespace webrtc

#endif  // WEBRTC_SYSTEM_WRAPPERS_INCLUDE_METRICS_DEFAULT_H_
