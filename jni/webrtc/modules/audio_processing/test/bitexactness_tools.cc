/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_processing/test/bitexactness_tools.h"

#include <math.h>
#include <algorithm>
#include <string>
#include <vector>

#include "webrtc/base/array_view.h"
#include "webrtc/test/testsupport/fileutils.h"

namespace webrtc {
namespace test {

std::string GetApmRenderTestVectorFileName(int sample_rate_hz) {
  switch (sample_rate_hz) {
    case 8000:
      return ResourcePath("far8_stereo", "pcm");
    case 16000:
      return ResourcePath("far16_stereo", "pcm");
    case 32000:
      return ResourcePath("far32_stereo", "pcm");
    case 48000:
      return ResourcePath("far48_stereo", "pcm");
    default:
      RTC_DCHECK(false);
  }
  return "";
}

std::string GetApmCaptureTestVectorFileName(int sample_rate_hz) {
  switch (sample_rate_hz) {
    case 8000:
      return ResourcePath("near8_stereo", "pcm");
    case 16000:
      return ResourcePath("near16_stereo", "pcm");
    case 32000:
      return ResourcePath("near32_stereo", "pcm");
    case 48000:
      return ResourcePath("near48_stereo", "pcm");
    default:
      RTC_DCHECK(false);
  }
  return "";
}

void ReadFloatSamplesFromStereoFile(size_t samples_per_channel,
                                    size_t num_channels,
                                    InputAudioFile* stereo_pcm_file,
                                    rtc::ArrayView<float> data) {
  RTC_DCHECK_EQ(data.size(), samples_per_channel * num_channels);
  std::vector<int16_t> read_samples(samples_per_channel * 2);
  stereo_pcm_file->Read(samples_per_channel * 2, read_samples.data());

  // Convert samples to float and discard any channels not needed.
  for (size_t sample = 0; sample < samples_per_channel; ++sample) {
    for (size_t channel = 0; channel < num_channels; ++channel) {
      data[sample * num_channels + channel] =
          read_samples[sample * 2 + channel] / 32768.0f;
    }
  }
}

::testing::AssertionResult VerifyDeinterleavedArray(
    size_t samples_per_channel,
    size_t num_channels,
    rtc::ArrayView<const float> reference,
    rtc::ArrayView<const float> output,
    float element_error_bound) {
  // Form vectors to compare the reference to. Only the first values of the
  // outputs are compared in order not having to specify all preceeding frames
  // as testvectors.
  const size_t reference_frame_length =
      rtc::CheckedDivExact(reference.size(), num_channels);

  std::vector<float> output_to_verify;
  for (size_t channel_no = 0; channel_no < num_channels; ++channel_no) {
    output_to_verify.insert(output_to_verify.end(),
                            output.begin() + channel_no * samples_per_channel,
                            output.begin() + channel_no * samples_per_channel +
                                reference_frame_length);
  }

  return VerifyArray(reference, output_to_verify, element_error_bound);
}

::testing::AssertionResult VerifyArray(rtc::ArrayView<const float> reference,
                                       rtc::ArrayView<const float> output,
                                       float element_error_bound) {
  // The vectors are deemed to be bitexact only if
  // a) output have a size at least as long as the reference.
  // b) the samples in the reference are bitexact with the corresponding samples
  //    in the output.

  bool equal = true;
  if (output.size() < reference.size()) {
    equal = false;
  } else {
    // Compare the first samples in the vectors.
    for (size_t k = 0; k < reference.size(); ++k) {
      if (fabs(output[k] - reference[k]) > element_error_bound) {
        equal = false;
        break;
      }
    }
  }

  if (equal) {
    return ::testing::AssertionSuccess();
  }

  // Lambda function that produces a formatted string with the data in the
  // vector.
  auto print_vector_in_c_format = [](rtc::ArrayView<const float> v,
                                     size_t num_values_to_print) {
    std::string s = "{ ";
    for (size_t k = 0; k < std::min(num_values_to_print, v.size()); ++k) {
      s += std::to_string(v[k]) + "f";
      s += (k < (num_values_to_print - 1)) ? ", " : "";
    }
    return s + " }";
  };

  // If the vectors are deemed not to be similar, return a report of the
  // difference.
  return ::testing::AssertionFailure()
         << std::endl
         << "    Actual values : "
         << print_vector_in_c_format(output,
                                     std::min(output.size(), reference.size()))
         << std::endl
         << "    Expected values: "
         << print_vector_in_c_format(reference, reference.size()) << std::endl;
}

}  // namespace test
}  // namespace webrtc
