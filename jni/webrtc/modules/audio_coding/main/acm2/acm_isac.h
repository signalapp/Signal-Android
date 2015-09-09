/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_MAIN_ACM2_ACM_ISAC_H_
#define WEBRTC_MODULES_AUDIO_CODING_MAIN_ACM2_ACM_ISAC_H_

#include "webrtc/modules/audio_coding/main/acm2/acm_generic_codec.h"
#include "webrtc/modules/audio_coding/neteq/interface/audio_decoder.h"
#include "webrtc/system_wrappers/interface/scoped_ptr.h"
#include "webrtc/system_wrappers/interface/thread_annotations.h"

namespace webrtc {

class CriticalSectionWrapper;

namespace acm2 {

struct ACMISACInst;

enum IsacCodingMode {
  ADAPTIVE,
  CHANNEL_INDEPENDENT
};

class ACMISAC : public ACMGenericCodec, AudioDecoder {
 public:
  explicit ACMISAC(int16_t codec_id);
  ~ACMISAC();

  int16_t InternalInitDecoder(WebRtcACMCodecParams* codec_params)
      EXCLUSIVE_LOCKS_REQUIRED(codec_wrapper_lock_);

  // Methods below are inherited from ACMGenericCodec.
  ACMGenericCodec* CreateInstance(void) OVERRIDE;

  int16_t InternalEncode(uint8_t* bitstream,
                         int16_t* bitstream_len_byte) OVERRIDE
      EXCLUSIVE_LOCKS_REQUIRED(codec_wrapper_lock_);

  int16_t InternalInitEncoder(WebRtcACMCodecParams* codec_params) OVERRIDE
      EXCLUSIVE_LOCKS_REQUIRED(codec_wrapper_lock_);

  int16_t UpdateDecoderSampFreq(int16_t codec_id) OVERRIDE;

  int16_t UpdateEncoderSampFreq(uint16_t samp_freq_hz) OVERRIDE
      EXCLUSIVE_LOCKS_REQUIRED(codec_wrapper_lock_);

  int16_t EncoderSampFreq(uint16_t* samp_freq_hz) OVERRIDE;

  int32_t ConfigISACBandwidthEstimator(const uint8_t init_frame_size_msec,
                                       const uint16_t init_rate_bit_per_sec,
                                       const bool enforce_frame_size) OVERRIDE;

  int32_t SetISACMaxPayloadSize(const uint16_t max_payload_len_bytes) OVERRIDE;

  int32_t SetISACMaxRate(const uint32_t max_rate_bit_per_sec) OVERRIDE;

  int16_t REDPayloadISAC(const int32_t isac_rate,
                         const int16_t isac_bw_estimate,
                         uint8_t* payload,
                         int16_t* payload_len_bytes) OVERRIDE;

  // Methods below are inherited from AudioDecoder.
  virtual int Decode(const uint8_t* encoded,
                     size_t encoded_len,
                     int16_t* decoded,
                     SpeechType* speech_type) OVERRIDE;

  virtual bool HasDecodePlc() const OVERRIDE { return true; }

  virtual int DecodePlc(int num_frames, int16_t* decoded) OVERRIDE;

  virtual int Init() OVERRIDE { return 0; }

  virtual int IncomingPacket(const uint8_t* payload,
                             size_t payload_len,
                             uint16_t rtp_sequence_number,
                             uint32_t rtp_timestamp,
                             uint32_t arrival_timestamp) OVERRIDE;

  virtual int DecodeRedundant(const uint8_t* encoded,
                              size_t encoded_len,
                              int16_t* decoded,
                              SpeechType* speech_type) OVERRIDE;

  virtual int ErrorCode() OVERRIDE;

 protected:
  int16_t Transcode(uint8_t* bitstream,
                    int16_t* bitstream_len_byte,
                    int16_t q_bwe,
                    int32_t rate,
                    bool is_red);

  void UpdateFrameLen() EXCLUSIVE_LOCKS_REQUIRED(codec_wrapper_lock_);

  // Methods below are inherited from ACMGenericCodec.
  void DestructEncoderSafe() OVERRIDE
      EXCLUSIVE_LOCKS_REQUIRED(codec_wrapper_lock_);

  int16_t SetBitRateSafe(const int32_t bit_rate) OVERRIDE
      EXCLUSIVE_LOCKS_REQUIRED(codec_wrapper_lock_);

  int32_t GetEstimatedBandwidthSafe() OVERRIDE;

  int32_t SetEstimatedBandwidthSafe(int32_t estimated_bandwidth) OVERRIDE;

  int32_t GetRedPayloadSafe(uint8_t* red_payload,
                            int16_t* payload_bytes) OVERRIDE;

  int16_t InternalCreateEncoder() OVERRIDE;

  void InternalDestructEncoderInst(void* ptr_inst) OVERRIDE;

  void CurrentRate(int32_t* rate_bit_per_sec) OVERRIDE;

  virtual AudioDecoder* Decoder(int codec_id) OVERRIDE;

  // |codec_inst_crit_sect_| protects |codec_inst_ptr_|.
  const scoped_ptr<CriticalSectionWrapper> codec_inst_crit_sect_;
  ACMISACInst* codec_inst_ptr_ GUARDED_BY(codec_inst_crit_sect_);
  bool is_enc_initialized_;
  IsacCodingMode isac_coding_mode_;
  bool enforce_frame_size_;
  int32_t isac_current_bn_;
  uint16_t samples_in_10ms_audio_;
  bool decoder_initialized_;
};

}  // namespace acm2

}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_CODING_MAIN_ACM2_ACM_ISAC_H_
