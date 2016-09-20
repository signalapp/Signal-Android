/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_CODECS_ISAC_AUDIO_ENCODER_ISAC_T_H_
#define WEBRTC_MODULES_AUDIO_CODING_CODECS_ISAC_AUDIO_ENCODER_ISAC_T_H_

#include <vector>

#include "webrtc/base/constructormagic.h"
#include "webrtc/base/scoped_ref_ptr.h"
#include "webrtc/modules/audio_coding/codecs/audio_encoder.h"
#include "webrtc/modules/audio_coding/codecs/isac/locked_bandwidth_info.h"

namespace webrtc {

struct CodecInst;

template <typename T>
class AudioEncoderIsacT final : public AudioEncoder {
 public:
  // Allowed combinations of sample rate, frame size, and bit rate are
  //  - 16000 Hz, 30 ms, 10000-32000 bps
  //  - 16000 Hz, 60 ms, 10000-32000 bps
  //  - 32000 Hz, 30 ms, 10000-56000 bps (if T has super-wideband support)
  struct Config {
    bool IsOk() const;

    rtc::scoped_refptr<LockedIsacBandwidthInfo> bwinfo;

    int payload_type = 103;
    int sample_rate_hz = 16000;
    int frame_size_ms = 30;
    int bit_rate = kDefaultBitRate;  // Limit on the short-term average bit
                                     // rate, in bits/s.
    int max_payload_size_bytes = -1;
    int max_bit_rate = -1;

    // If true, the encoder will dynamically adjust frame size and bit rate;
    // the configured values are then merely the starting point.
    bool adaptive_mode = false;

    // In adaptive mode, prevent adaptive changes to the frame size. (Not used
    // in nonadaptive mode.)
    bool enforce_frame_size = false;
  };

  explicit AudioEncoderIsacT(const Config& config);
  explicit AudioEncoderIsacT(
      const CodecInst& codec_inst,
      const rtc::scoped_refptr<LockedIsacBandwidthInfo>& bwinfo);
  ~AudioEncoderIsacT() override;

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
  // This value is taken from STREAM_SIZE_MAX_60 for iSAC float (60 ms) and
  // STREAM_MAXW16_60MS for iSAC fix (60 ms).
  static const size_t kSufficientEncodeBufferSizeBytes = 400;

  static const int kDefaultBitRate = 32000;

  // Recreate the iSAC encoder instance with the given settings, and save them.
  void RecreateEncoderInstance(const Config& config);

  Config config_;
  typename T::instance_type* isac_state_ = nullptr;
  rtc::scoped_refptr<LockedIsacBandwidthInfo> bwinfo_;

  // Have we accepted input but not yet emitted it in a packet?
  bool packet_in_progress_ = false;

  // Timestamp of the first input of the currently in-progress packet.
  uint32_t packet_timestamp_;

  // Timestamp of the previously encoded packet.
  uint32_t last_encoded_timestamp_;

  RTC_DISALLOW_COPY_AND_ASSIGN(AudioEncoderIsacT);
};

}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_CODING_CODECS_ISAC_AUDIO_ENCODER_ISAC_T_H_
