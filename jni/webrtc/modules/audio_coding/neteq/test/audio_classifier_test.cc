/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/neteq/audio_classifier.h"

#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <iostream>
#include <memory>
#include <string>

int main(int argc, char* argv[]) {
  if (argc != 5) {
    std::cout << "Usage: " << argv[0] <<
        " channels output_type <input file name> <output file name> "
        << std::endl << std::endl;
    std::cout << "Where channels can be 1 (mono) or 2 (interleaved stereo),";
    std::cout << " outputs can be 1 (classification (boolean)) or 2";
    std::cout << " (classification and music probability (float)),"
        << std::endl;
    std::cout << "and the sampling frequency is assumed to be 48 kHz."
        << std::endl;
    return -1;
  }

  const int kFrameSizeSamples = 960;
  int channels = atoi(argv[1]);
  if (channels < 1 || channels > 2) {
    std::cout << "Disallowed number of channels  " << channels << std::endl;
    return -1;
  }

  int outputs = atoi(argv[2]);
  if (outputs < 1 || outputs > 2) {
    std::cout << "Disallowed number of outputs  " << outputs << std::endl;
    return -1;
  }

  const int data_size = channels * kFrameSizeSamples;
  std::unique_ptr<int16_t[]> in(new int16_t[data_size]);

  std::string input_filename = argv[3];
  std::string output_filename = argv[4];

  std::cout << "Input file: " << input_filename << std::endl;
  std::cout << "Output file: " << output_filename << std::endl;

  FILE* in_file = fopen(input_filename.c_str(), "rb");
  if (!in_file) {
    std::cout << "Cannot open input file " << input_filename << std::endl;
    return -1;
  }

  FILE* out_file = fopen(output_filename.c_str(), "wb");
  if (!out_file) {
    std::cout << "Cannot open output file " << output_filename << std::endl;
    return -1;
  }

  webrtc::AudioClassifier classifier;
  int frame_counter = 0;
  int music_counter = 0;
  while (fread(in.get(), sizeof(*in.get()),
               data_size, in_file) == (size_t) data_size) {
    bool is_music = classifier.Analysis(in.get(), data_size, channels);
    if (!fwrite(&is_music, sizeof(is_music), 1, out_file)) {
       std::cout << "Error writing." << std::endl;
       return -1;
    }
    if (is_music) {
      music_counter++;
    }
    std::cout << "frame " << frame_counter << " decision " << is_music;
    if (outputs == 2) {
      float music_prob = classifier.music_probability();
      if (!fwrite(&music_prob, sizeof(music_prob), 1, out_file)) {
        std::cout << "Error writing." << std::endl;
        return -1;
      }
      std::cout << " music prob " << music_prob;
    }
    std::cout << std::endl;
    frame_counter++;
  }
  std::cout << frame_counter << " frames processed." << std::endl;
  if (frame_counter > 0) {
    float music_percentage = music_counter / static_cast<float>(frame_counter);
    std::cout <<  music_percentage <<  " percent music." << std::endl;
  }

  fclose(in_file);
  fclose(out_file);
  return 0;
}
