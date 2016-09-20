/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_CODECS_G722_AUDIO_ENCODER_G722_H_
#define WEBRTC_MODULES_AUDIO_CODING_CODECS_G722_AUDIO_ENCODER_G722_H_

#include <memory>

#include "webrtc/base/buffer.h"
#include "webrtc/base/constructormagic.h"
#include "webrtc/modules/audio_coding/codecs/audio_encoder.h"
#include "webrtc/modules/audio_coding/codecs/g722/g722_interface.h"

namespace webrtc {

struct CodecInst;

class AudioEncoderG722 final : public AudioEncoder {
 public:
  struct Config {
    bool IsOk() const;

    int payload_type = 9;
    int frame_size_ms = 20;
    size_t num_channels = 1;
  };

  explicit AudioEncoderG722(const Config& config);
  explicit AudioEncoderG722(const CodecInst& codec_inst);
  ~AudioEncoderG722() override;

  int SampleRateHz() const override;
  size_t NumChannels() const override;
  int RtpTimestampRateHz() const override;
  size_t Num10MsFramesInNextPacket() const override;
  size_t Max10MsFramesInAPacket() const override;
  int GetTargetBitrate() const override;
  void Reset() override;

 protected:
  EncodedInfo EncodeImpl(uint32_t rtp_timestamp,
                         rtc::ArrayView<const int16_t> audio,
                         rtc::Buffer* encoded) override;

 private:
  // The encoder state for one channel.
  struct EncoderState {
    G722EncInst* encoder;
    std::unique_ptr<int16_t[]> speech_buffer;   // Queued up for encoding.
    rtc::Buffer encoded_buffer;                 // Already encoded.
    EncoderState();
    ~EncoderState();
  };

  size_t SamplesPerChannel() const;

  const size_t num_channels_;
  const int payload_type_;
  const size_t num_10ms_frames_per_packet_;
  size_t num_10ms_frames_buffered_;
  uint32_t first_timestamp_in_buffer_;
  const std::unique_ptr<EncoderState[]> encoders_;
  rtc::Buffer interleave_buffer_;
  RTC_DISALLOW_COPY_AND_ASSIGN(AudioEncoderG722);
};

}  // namespace webrtc
#endif  // WEBRTC_MODULES_AUDIO_CODING_CODECS_G722_AUDIO_ENCODER_G722_H_
