/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_NETEQ_INTERFACE_AUDIO_DECODER_H_
#define WEBRTC_MODULES_AUDIO_CODING_NETEQ_INTERFACE_AUDIO_DECODER_H_

#include <stdlib.h>  // NULL

#include "webrtc/base/constructormagic.h"
#include "webrtc/typedefs.h"

namespace webrtc {

enum NetEqDecoder {
  kDecoderPCMu,
  kDecoderPCMa,
  kDecoderPCMu_2ch,
  kDecoderPCMa_2ch,
  kDecoderILBC,
  kDecoderISAC,
  kDecoderISACswb,
  kDecoderISACfb,
  kDecoderPCM16B,
  kDecoderPCM16Bwb,
  kDecoderPCM16Bswb32kHz,
  kDecoderPCM16Bswb48kHz,
  kDecoderPCM16B_2ch,
  kDecoderPCM16Bwb_2ch,
  kDecoderPCM16Bswb32kHz_2ch,
  kDecoderPCM16Bswb48kHz_2ch,
  kDecoderPCM16B_5ch,
  kDecoderG722,
  kDecoderG722_2ch,
  kDecoderRED,
  kDecoderAVT,
  kDecoderCNGnb,
  kDecoderCNGwb,
  kDecoderCNGswb32kHz,
  kDecoderCNGswb48kHz,
  kDecoderArbitrary,
  kDecoderOpus,
  kDecoderOpus_2ch,
  kDecoderCELT_32,
  kDecoderCELT_32_2ch,
};

// This is the interface class for decoders in NetEQ. Each codec type will have
// and implementation of this class.
class AudioDecoder {
 public:
  enum SpeechType {
    kSpeech = 1,
    kComfortNoise = 2
  };

  // Used by PacketDuration below. Save the value -1 for errors.
  enum { kNotImplemented = -2 };

  explicit AudioDecoder(enum NetEqDecoder type)
    : codec_type_(type),
      channels_(1),
      state_(NULL) {
  }

  virtual ~AudioDecoder() {}

  // Decodes |encode_len| bytes from |encoded| and writes the result in
  // |decoded|. The number of samples from all channels produced is in
  // the return value. If the decoder produced comfort noise, |speech_type|
  // is set to kComfortNoise, otherwise it is kSpeech.
  virtual int Decode(const uint8_t* encoded, size_t encoded_len,
                     int16_t* decoded, size_t decoded_size,
                     SpeechType* speech_type) = 0;

  // Same as Decode(), but interfaces to the decoders redundant decode function.
  // The default implementation simply calls the regular Decode() method.
  virtual int DecodeRedundant(const uint8_t* encoded, size_t encoded_len,
                              int16_t* decoded, size_t decoded_size,
                               SpeechType* speech_type);

  // Indicates if the decoder implements the DecodePlc method.
  virtual bool HasDecodePlc() const;

  // Calls the packet-loss concealment of the decoder to update the state after
  // one or several lost packets.
  virtual int DecodePlc(int num_frames, int16_t* decoded);

  // Initializes the decoder.
  virtual int Init() = 0;

  // Notifies the decoder of an incoming packet to NetEQ.
  virtual int IncomingPacket(const uint8_t* payload,
                             size_t payload_len,
                             uint16_t rtp_sequence_number,
                             uint32_t rtp_timestamp,
                             uint32_t arrival_timestamp);

  // Returns the last error code from the decoder.
  virtual int ErrorCode();

  // Returns the duration in samples of the payload in |encoded| which is
  // |encoded_len| bytes long. Returns kNotImplemented if no duration estimate
  // is available, or -1 in case of an error.
  virtual int PacketDuration(const uint8_t* encoded, size_t encoded_len);

  // Returns the duration in samples of the redandant payload in |encoded| which
  // is |encoded_len| bytes long. Returns kNotImplemented if no duration
  // estimate is available, or -1 in case of an error.
  virtual int PacketDurationRedundant(const uint8_t* encoded,
                                      size_t encoded_len) const;

  // Detects whether a packet has forward error correction. The packet is
  // comprised of the samples in |encoded| which is |encoded_len| bytes long.
  // Returns true if the packet has FEC and false otherwise.
  virtual bool PacketHasFec(const uint8_t* encoded, size_t encoded_len) const;

  virtual NetEqDecoder codec_type() const;

  // Returns the underlying decoder state.
  void* state() { return state_; }

  // Returns true if |codec_type| is supported.
  static bool CodecSupported(NetEqDecoder codec_type);

  // Returns the sample rate for |codec_type|.
  static int CodecSampleRateHz(NetEqDecoder codec_type);

  // Creates an AudioDecoder object of type |codec_type|. Returns NULL for
  // for unsupported codecs, and when creating an AudioDecoder is not
  // applicable (e.g., for RED and DTMF/AVT types).
  static AudioDecoder* CreateAudioDecoder(NetEqDecoder codec_type);

  size_t channels() const { return channels_; }

 protected:
  static SpeechType ConvertSpeechType(int16_t type);

  enum NetEqDecoder codec_type_;
  size_t channels_;
  void* state_;

 private:
  DISALLOW_COPY_AND_ASSIGN(AudioDecoder);
};

}  // namespace webrtc
#endif  // WEBRTC_MODULES_AUDIO_CODING_NETEQ_INTERFACE_AUDIO_DECODER_H_
