/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <vector>

#include "gflags/gflags.h"
#include "webrtc/base/checks.h"
#include "webrtc/base/format_macros.h"
#include "webrtc/common_audio/channel_buffer.h"
#include "webrtc/common_audio/wav_file.h"
#include "webrtc/modules/audio_processing/beamformer/nonlinear_beamformer.h"
#include "webrtc/modules/audio_processing/test/test_utils.h"

DEFINE_string(i, "", "The name of the input file to read from.");
DEFINE_string(o, "out.wav", "Name of the output file to write to.");
DEFINE_string(mic_positions, "",
    "Space delimited cartesian coordinates of microphones in meters. "
    "The coordinates of each point are contiguous. "
    "For a two element array: \"x1 y1 z1 x2 y2 z2\"");

namespace webrtc {
namespace {

const int kChunksPerSecond = 100;
const int kChunkSizeMs = 1000 / kChunksPerSecond;

const char kUsage[] =
    "Command-line tool to run beamforming on WAV files. The signal is passed\n"
    "in as a single band, unlike the audio processing interface which splits\n"
    "signals into multiple bands.";

}  // namespace

int main(int argc, char* argv[]) {
  google::SetUsageMessage(kUsage);
  google::ParseCommandLineFlags(&argc, &argv, true);

  WavReader in_file(FLAGS_i);
  WavWriter out_file(FLAGS_o, in_file.sample_rate(), 1);

  const size_t num_mics = in_file.num_channels();
  const std::vector<Point> array_geometry =
      ParseArrayGeometry(FLAGS_mic_positions, num_mics);
  RTC_CHECK_EQ(array_geometry.size(), num_mics);

  NonlinearBeamformer bf(array_geometry);
  bf.Initialize(kChunkSizeMs, in_file.sample_rate());

  printf("Input file: %s\nChannels: %" PRIuS ", Sample rate: %d Hz\n\n",
         FLAGS_i.c_str(), in_file.num_channels(), in_file.sample_rate());
  printf("Output file: %s\nChannels: %" PRIuS ", Sample rate: %d Hz\n\n",
         FLAGS_o.c_str(), out_file.num_channels(), out_file.sample_rate());

  ChannelBuffer<float> in_buf(
      rtc::CheckedDivExact(in_file.sample_rate(), kChunksPerSecond),
      in_file.num_channels());
  ChannelBuffer<float> out_buf(
      rtc::CheckedDivExact(out_file.sample_rate(), kChunksPerSecond),
      out_file.num_channels());

  std::vector<float> interleaved(in_buf.size());
  while (in_file.ReadSamples(interleaved.size(),
                             &interleaved[0]) == interleaved.size()) {
    FloatS16ToFloat(&interleaved[0], interleaved.size(), &interleaved[0]);
    Deinterleave(&interleaved[0], in_buf.num_frames(),
                 in_buf.num_channels(), in_buf.channels());

    bf.ProcessChunk(in_buf, &out_buf);

    Interleave(out_buf.channels(), out_buf.num_frames(),
               out_buf.num_channels(), &interleaved[0]);
    FloatToFloatS16(&interleaved[0], interleaved.size(), &interleaved[0]);
    out_file.WriteSamples(&interleaved[0], interleaved.size());
  }

  return 0;
}

}  // namespace webrtc

int main(int argc, char* argv[]) {
  return webrtc::main(argc, argv);
}
