/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/test/PCMFile.h"

#include <ctype.h>
#include <stdio.h>
#include <string.h>

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/modules/include/module_common_types.h"

namespace webrtc {

#define MAX_FILE_NAME_LENGTH_BYTE 500

PCMFile::PCMFile()
    : pcm_file_(NULL),
      samples_10ms_(160),
      frequency_(16000),
      end_of_file_(false),
      auto_rewind_(false),
      rewinded_(false),
      read_stereo_(false),
      save_stereo_(false) {
  timestamp_ = (((uint32_t) rand() & 0x0000FFFF) << 16) |
      ((uint32_t) rand() & 0x0000FFFF);
}

PCMFile::PCMFile(uint32_t timestamp)
    : pcm_file_(NULL),
      samples_10ms_(160),
      frequency_(16000),
      end_of_file_(false),
      auto_rewind_(false),
      rewinded_(false),
      read_stereo_(false),
      save_stereo_(false) {
  timestamp_ = timestamp;
}

int16_t PCMFile::ChooseFile(std::string* file_name, int16_t max_len,
                            uint16_t* frequency_hz) {
  char tmp_name[MAX_FILE_NAME_LENGTH_BYTE];

  EXPECT_TRUE(fgets(tmp_name, MAX_FILE_NAME_LENGTH_BYTE, stdin) != NULL);
  tmp_name[MAX_FILE_NAME_LENGTH_BYTE - 1] = '\0';
  int16_t n = 0;

  // Removing trailing spaces.
  while ((isspace(tmp_name[n]) || iscntrl(tmp_name[n])) && (tmp_name[n] != 0)
      && (n < MAX_FILE_NAME_LENGTH_BYTE)) {
    n++;
  }
  if (n > 0) {
    memmove(tmp_name, &tmp_name[n], MAX_FILE_NAME_LENGTH_BYTE - n);
  }

  // Removing trailing spaces.
  n = (int16_t)(strlen(tmp_name) - 1);
  if (n >= 0) {
    while ((isspace(tmp_name[n]) || iscntrl(tmp_name[n])) && (n >= 0)) {
      n--;
    }
  }
  if (n >= 0) {
    tmp_name[n + 1] = '\0';
  }

  int16_t len = (int16_t) strlen(tmp_name);
  if (len > max_len) {
    return -1;
  }
  if (len > 0) {
    std::string tmp_string(tmp_name, len + 1);
    *file_name = tmp_string;
  }
  printf("Enter the sampling frequency (in Hz) of the above file [%u]: ",
         *frequency_hz);
  EXPECT_TRUE(fgets(tmp_name, 10, stdin) != NULL);
  uint16_t tmp_frequency = (uint16_t) atoi(tmp_name);
  if (tmp_frequency > 0) {
    *frequency_hz = tmp_frequency;
  }
  return 0;
}

void PCMFile::Open(const std::string& file_name, uint16_t frequency,
                   const char* mode, bool auto_rewind) {
  if ((pcm_file_ = fopen(file_name.c_str(), mode)) == NULL) {
    printf("Cannot open file %s.\n", file_name.c_str());
    ADD_FAILURE() << "Unable to read file";
  }
  frequency_ = frequency;
  samples_10ms_ = (uint16_t)(frequency_ / 100);
  auto_rewind_ = auto_rewind;
  end_of_file_ = false;
  rewinded_ = false;
}

int32_t PCMFile::SamplingFrequency() const {
  return frequency_;
}

uint16_t PCMFile::PayloadLength10Ms() const {
  return samples_10ms_;
}

int32_t PCMFile::Read10MsData(AudioFrame& audio_frame) {
  uint16_t channels = 1;
  if (read_stereo_) {
    channels = 2;
  }

  int32_t payload_size = (int32_t) fread(audio_frame.data_, sizeof(uint16_t),
                                         samples_10ms_ * channels, pcm_file_);
  if (payload_size < samples_10ms_ * channels) {
    for (int k = payload_size; k < samples_10ms_ * channels; k++) {
      audio_frame.data_[k] = 0;
    }
    if (auto_rewind_) {
      rewind(pcm_file_);
      rewinded_ = true;
    } else {
      end_of_file_ = true;
    }
  }
  audio_frame.samples_per_channel_ = samples_10ms_;
  audio_frame.sample_rate_hz_ = frequency_;
  audio_frame.num_channels_ = channels;
  audio_frame.timestamp_ = timestamp_;
  timestamp_ += samples_10ms_;
  ++blocks_read_;
  if (num_10ms_blocks_to_read_ && blocks_read_ >= *num_10ms_blocks_to_read_)
    end_of_file_ = true;
  return samples_10ms_;
}

void PCMFile::Write10MsData(AudioFrame& audio_frame) {
  if (audio_frame.num_channels_ == 1) {
    if (!save_stereo_) {
      if (fwrite(audio_frame.data_, sizeof(uint16_t),
                 audio_frame.samples_per_channel_, pcm_file_) !=
          static_cast<size_t>(audio_frame.samples_per_channel_)) {
        return;
      }
    } else {
      int16_t* stereo_audio = new int16_t[2 * audio_frame.samples_per_channel_];
      for (size_t k = 0; k < audio_frame.samples_per_channel_; k++) {
        stereo_audio[k << 1] = audio_frame.data_[k];
        stereo_audio[(k << 1) + 1] = audio_frame.data_[k];
      }
      if (fwrite(stereo_audio, sizeof(int16_t),
                 2 * audio_frame.samples_per_channel_, pcm_file_) !=
          static_cast<size_t>(2 * audio_frame.samples_per_channel_)) {
        return;
      }
      delete[] stereo_audio;
    }
  } else {
    if (fwrite(audio_frame.data_, sizeof(int16_t),
               audio_frame.num_channels_ * audio_frame.samples_per_channel_,
               pcm_file_) !=
        static_cast<size_t>(audio_frame.num_channels_ *
                            audio_frame.samples_per_channel_)) {
      return;
    }
  }
}

void PCMFile::Write10MsData(int16_t* playout_buffer, size_t length_smpls) {
  if (fwrite(playout_buffer, sizeof(uint16_t), length_smpls, pcm_file_) !=
      length_smpls) {
    return;
  }
}

void PCMFile::Close() {
  fclose(pcm_file_);
  pcm_file_ = NULL;
  blocks_read_ = 0;
}

void PCMFile::FastForward(int num_10ms_blocks) {
  const int channels = read_stereo_ ? 2 : 1;
  long num_bytes_to_move =
      num_10ms_blocks * sizeof(int16_t) * samples_10ms_ * channels;
  int error = fseek(pcm_file_, num_bytes_to_move, SEEK_CUR);
  RTC_DCHECK_EQ(error, 0);
}

void PCMFile::Rewind() {
  rewind(pcm_file_);
  end_of_file_ = false;
  blocks_read_ = 0;
}

bool PCMFile::Rewinded() {
  return rewinded_;
}

void PCMFile::SaveStereo(bool is_stereo) {
  save_stereo_ = is_stereo;
}

void PCMFile::ReadStereo(bool is_stereo) {
  read_stereo_ = is_stereo;
}

void PCMFile::SetNum10MsBlocksToRead(int value) {
  num_10ms_blocks_to_read_ = rtc::Optional<int>(value);
}

}  // namespace webrtc
