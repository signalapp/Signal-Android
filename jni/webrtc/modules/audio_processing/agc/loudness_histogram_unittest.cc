/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// Use CreateHistUnittestFile.m to generate the input file.

#include "webrtc/modules/audio_processing/agc/loudness_histogram.h"

#include <stdio.h>
#include <algorithm>
#include <cmath>
#include <memory>

#include "gtest/gtest.h"
#include "webrtc/test/testsupport/fileutils.h"
#include "webrtc/modules/audio_processing/agc/utility.h"

namespace webrtc {

struct InputOutput {
  double rms;
  double activity_probability;
  double audio_content;
  double loudness;
};

const double kRelativeErrTol = 1e-10;

class LoudnessHistogramTest : public ::testing::Test {
 protected:
  void RunTest(bool enable_circular_buff, const char* filename);

 private:
  void TestClean();
  std::unique_ptr<LoudnessHistogram> hist_;
};

void LoudnessHistogramTest::TestClean() {
  EXPECT_EQ(hist_->CurrentRms(), 7.59621091765857e-02);
  EXPECT_EQ(hist_->AudioContent(), 0);
  EXPECT_EQ(hist_->num_updates(), 0);
}

void LoudnessHistogramTest::RunTest(bool enable_circular_buff,
                                    const char* filename) {
  FILE* in_file = fopen(filename, "rb");
  ASSERT_TRUE(in_file != NULL);
  if (enable_circular_buff) {
    int buffer_size;
    EXPECT_EQ(fread(&buffer_size, sizeof(buffer_size), 1, in_file), 1u);
    hist_.reset(LoudnessHistogram::Create(buffer_size));
  } else {
    hist_.reset(LoudnessHistogram::Create());
  }
  TestClean();

  InputOutput io;
  int num_updates = 0;
  int num_reset = 0;
  while (fread(&io, sizeof(InputOutput), 1, in_file) == 1) {
    if (io.rms < 0) {
      // We have to reset.
      hist_->Reset();
      TestClean();
      num_updates = 0;
      num_reset++;
      // Read the next chunk of input.
      if (fread(&io, sizeof(InputOutput), 1, in_file) != 1)
        break;
    }
    hist_->Update(io.rms, io.activity_probability);
    num_updates++;
    EXPECT_EQ(hist_->num_updates(), num_updates);
    double audio_content = hist_->AudioContent();

    double abs_err =
        std::min(audio_content, io.audio_content) * kRelativeErrTol;

    ASSERT_NEAR(audio_content, io.audio_content, abs_err);
    double current_loudness = Linear2Loudness(hist_->CurrentRms());
    abs_err =
        std::min(fabs(current_loudness), fabs(io.loudness)) * kRelativeErrTol;
    ASSERT_NEAR(current_loudness, io.loudness, abs_err);
  }
  fclose(in_file);
}

TEST_F(LoudnessHistogramTest, ActiveCircularBuffer) {
  RunTest(true, test::ResourcePath(
                    "audio_processing/agc/agc_with_circular_buffer", "dat")
                    .c_str());
}

TEST_F(LoudnessHistogramTest, InactiveCircularBuffer) {
  RunTest(false, test::ResourcePath(
                     "audio_processing/agc/agc_no_circular_buffer", "dat")
                     .c_str());
}

}  // namespace webrtc
