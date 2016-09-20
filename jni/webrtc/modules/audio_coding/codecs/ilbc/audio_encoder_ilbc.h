/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_CODECS_ILBC_AUDIO_ENCODER_ILBC_H_
#define WEBRTC_MODULES_AUDIO_CODING_CODECS_ILBC_AUDIO_ENCODER_ILBC_H_

#include "webrtc/base/constructormagic.h"
#include "webrtc/modules/audio_coding/codecs/audio_encoder.h"
#include "webrtc/modules/audio_coding/codecs/ilbc/ilbc.h"

namespace webrtc {

struct CodecInst;

class AudioEncoderIlbc final : public AudioEncoder {
 public:
  struct Config {
    bool IsOk() const;

    int payload_type = 102;
    int frame_size_ms = 30;  // Valid values are 20, 30, 40, and 60 ms.
    // Note that frame size 40 ms produces encodings with two 20 ms frames in
    // them, and frame size 60 ms consists of two 30 ms frames.
  };

  explicit AudioEncoderIlbc(const Config& config);
  explicit AudioEncoderIlbc(const CodecInst& codec_inst);
  ~AudioEncoderIlbc() override;

  int SampleRateHz() const override;
  size_t NumChannels() const override;
  size_t Num10MsFramesInNextPacket() const override;
  size_t Max10MsFramesInAPacket() const override;
  int GetTargetBitrate() const override;
  EncodedInfo EncodeImpl(uint32_t rtp_timestamp,
                         rtc::ArrayView<const int16_t> audio,
                         rtc::Buffer* encoded) override;
  void Reset() override;

 private:
  size_t RequiredOutputSizeBytes() const;

  static const size_t kMaxSamplesPerPacket = 480;
  const Config config_;
  const size_t num_10ms_frames_per_packet_;
  size_t num_10ms_frames_buffered_;
  uint32_t first_timestamp_in_buffer_;
  int16_t input_buffer_[kMaxSamplesPerPacket];
  IlbcEncoderInstance* encoder_;
  RTC_DISALLOW_COPY_AND_ASSIGN(AudioEncoderIlbc);
};

}  // namespace webrtc
#endif  // WEBRTC_MODULES_AUDIO_CODING_CODECS_ILBC_AUDIO_ENCODER_ILBC_H_
