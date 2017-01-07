/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_processing/transient/wpd_tree.h"

#include <memory>
#include <sstream>
#include <string>

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/modules/audio_processing/transient/daubechies_8_wavelet_coeffs.h"
#include "webrtc/modules/audio_processing/transient/file_utils.h"
#include "webrtc/system_wrappers/include/file_wrapper.h"
#include "webrtc/test/testsupport/fileutils.h"

namespace webrtc {

TEST(WPDTreeTest, Construction) {
  const size_t kTestBufferSize = 100;
  const int kLevels = 5;
  const int kExpectedNumberOfNodes = (1 << (kLevels + 1)) - 1;

  float test_buffer[kTestBufferSize];
  memset(test_buffer, 0.f, kTestBufferSize * sizeof(*test_buffer));
  float test_coefficients[] = {1.f, 2.f, 3.f, 4.f, 5.f};
  const size_t kTestCoefficientsLength = sizeof(test_coefficients) /
      sizeof(test_coefficients[0]);
  WPDTree tree(kTestBufferSize,
               test_coefficients,
               test_coefficients,
               kTestCoefficientsLength,
               kLevels);
  ASSERT_EQ(kExpectedNumberOfNodes, tree.num_nodes());
  // Checks for NodeAt(level, index).
  int nodes_at_level = 0;
  for (int level = 0; level <= kLevels; ++level) {
    nodes_at_level = 1 << level;
    for (int i = 0; i < nodes_at_level; ++i) {
      ASSERT_TRUE(NULL != tree.NodeAt(level, i));
    }
    // Out of bounds.
    EXPECT_EQ(NULL, tree.NodeAt(level, -1));
    EXPECT_EQ(NULL, tree.NodeAt(level, -12));
    EXPECT_EQ(NULL, tree.NodeAt(level, nodes_at_level));
    EXPECT_EQ(NULL, tree.NodeAt(level, nodes_at_level + 5));
  }
  // Out of bounds.
  EXPECT_EQ(NULL, tree.NodeAt(-1, 0));
  EXPECT_EQ(NULL, tree.NodeAt(-12, 0));
  EXPECT_EQ(NULL, tree.NodeAt(kLevels + 1, 0));
  EXPECT_EQ(NULL, tree.NodeAt(kLevels + 5, 0));
  // Checks for Update().
  EXPECT_EQ(0, tree.Update(test_buffer, kTestBufferSize));
  EXPECT_EQ(-1, tree.Update(NULL, kTestBufferSize));
  EXPECT_EQ(-1, tree.Update(test_buffer, kTestBufferSize - 1));
}

// This test is for the correctness of the tree.
// Checks the results from the Matlab equivalent, it is done comparing the
// results that are stored in the output files from Matlab.
// It also writes the results in its own set of files in the out directory.
// Matlab and output files contain all the results in double precision (Little
// endian) appended.
#if defined(WEBRTC_IOS)
TEST(WPDTreeTest, DISABLED_CorrectnessBasedOnMatlabFiles) {
#else
TEST(WPDTreeTest, CorrectnessBasedOnMatlabFiles) {
#endif
  // 10 ms at 16000 Hz.
  const size_t kTestBufferSize = 160;
  const int kLevels = 3;
  const int kLeaves = 1 << kLevels;
  const size_t kLeavesSamples = kTestBufferSize >> kLevels;
  // Create tree with Discrete Meyer Wavelet Coefficients.
  WPDTree tree(kTestBufferSize,
               kDaubechies8HighPassCoefficients,
               kDaubechies8LowPassCoefficients,
               kDaubechies8CoefficientsLength,
               kLevels);
  // Allocate and open all matlab and out files.
  std::unique_ptr<FileWrapper> matlab_files_data[kLeaves];
  std::unique_ptr<FileWrapper> out_files_data[kLeaves];

  for (int i = 0; i < kLeaves; ++i) {
    // Matlab files.
    matlab_files_data[i].reset(FileWrapper::Create());

    std::ostringstream matlab_stream;
    matlab_stream << "audio_processing/transient/wpd" << i;
    std::string matlab_string = test::ResourcePath(matlab_stream.str(), "dat");
    matlab_files_data[i]->OpenFile(matlab_string.c_str(), true);  // Read only.

    bool file_opened = matlab_files_data[i]->is_open();
    ASSERT_TRUE(file_opened) << "File could not be opened.\n" << matlab_string;

    // Out files.
    out_files_data[i].reset(FileWrapper::Create());

    std::ostringstream out_stream;
    out_stream << test::OutputPath() << "wpd_" << i << ".out";
    std::string out_string = out_stream.str();

    out_files_data[i]->OpenFile(out_string.c_str(), false);  // Write mode.

    file_opened = out_files_data[i]->is_open();
    ASSERT_TRUE(file_opened) << "File could not be opened.\n" << out_string;
  }

  // Prepare the test file.
  std::string test_file_name = test::ResourcePath(
      "audio_processing/transient/ajm-macbook-1-spke16m", "pcm");

  std::unique_ptr<FileWrapper> test_file(FileWrapper::Create());

  test_file->OpenFile(test_file_name.c_str(), true);  // Read only.

  bool file_opened = test_file->is_open();
  ASSERT_TRUE(file_opened) << "File could not be opened.\n" << test_file_name;

  float test_buffer[kTestBufferSize];

  // Only the first frames of the audio file are tested. The matlab files also
  // only contains information about the first frames.
  const size_t kMaxFramesToTest = 100;
  const float kTolerance = 0.03f;

  size_t frames_read = 0;

  // Read first buffer from the PCM test file.
  size_t file_samples_read = ReadInt16FromFileToFloatBuffer(test_file.get(),
                                                            kTestBufferSize,
                                                            test_buffer);
  while (file_samples_read > 0 && frames_read < kMaxFramesToTest) {
    ++frames_read;

    if (file_samples_read < kTestBufferSize) {
      // Pad the rest of the buffer with zeros.
      for (size_t i = file_samples_read; i < kTestBufferSize; ++i) {
        test_buffer[i] = 0.0;
      }
    }
    tree.Update(test_buffer, kTestBufferSize);
    double matlab_buffer[kTestBufferSize];

    // Compare results with data from the matlab test files.
    for (int i = 0; i < kLeaves; ++i) {
      // Compare data values
      size_t matlab_samples_read =
          ReadDoubleBufferFromFile(matlab_files_data[i].get(),
                                   kLeavesSamples,
                                   matlab_buffer);

      ASSERT_EQ(kLeavesSamples, matlab_samples_read)
          << "Matlab test files are malformed.\n"
          << "File: 3_" << i;
      // Get output data from the corresponding node
      const float* node_data = tree.NodeAt(kLevels, i)->data();
      // Compare with matlab files.
      for (size_t j = 0; j < kLeavesSamples; ++j) {
        EXPECT_NEAR(matlab_buffer[j], node_data[j], kTolerance)
            << "\nLeaf: " << i << "\nSample: " << j
            << "\nFrame: " << frames_read - 1;
      }

      // Write results to out files.
      WriteFloatBufferToFile(out_files_data[i].get(),
                             kLeavesSamples,
                             node_data);
    }

    // Read next buffer from the PCM test file.
    file_samples_read = ReadInt16FromFileToFloatBuffer(test_file.get(),
                                                       kTestBufferSize,
                                                       test_buffer);
  }

  // Close all matlab and out files.
  for (int i = 0; i < kLeaves; ++i) {
    matlab_files_data[i]->CloseFile();
    out_files_data[i]->CloseFile();
  }

  test_file->CloseFile();
}

}  // namespace webrtc
