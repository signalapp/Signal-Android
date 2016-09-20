#ifndef __WEB_RTC_CODEC_H__
#define __WEB_RTC_CODEC_H__

#include "AudioCodec.h"
#include <sys/types.h>

#include <modules/audio_coding/codecs/audio_decoder.h>

class WebRtcCodec : public webrtc::AudioDecoder {

private:
  AudioCodec &codec;

public:
  WebRtcCodec(AudioCodec &codec) :
    AudioDecoder(), codec(codec)
  {}

  int DecodeInternal(const uint8_t* encoded, size_t encoded_len,
                     int sample_rate_hz, int16_t* decoded,
                     SpeechType* speech_type)
  {
    *speech_type = kSpeech;
    return codec.decode((char*)encoded, encoded_len, decoded);
  }



  // Indicates if the decoder implements the DecodePlc method.
  bool HasDecodePlc() const {
    return 1;
  }

  // Calls the packet-loss concealment of the decoder to update the state after
  // one or several lost packets. The caller has to make sure that the
  // memory allocated in |decoded| should accommodate |num_frames| frames.
  size_t DecodePlc(size_t num_frames, int16_t* decoded) {
    return codec.conceal(num_frames, decoded);
  }

  // Resets the decoder state (empty buffers etc.).
  void Reset() {
    codec.reset();
  }

  // Returns the last error code from the decoder.
  int ErrorCode() {
    return codec.getErrorCode();
  }

 // Returns the duration in samples-per-channel of the payload in |encoded|
  // which is |encoded_len| bytes long. Returns kNotImplemented if no duration
  // estimate is available, or -1 in case of an error.
  int PacketDuration(const uint8_t* encoded, size_t encoded_len) const {
    return (encoded_len / SPEEX_ENCODED_FRAME_SIZE) * SPEEX_FRAME_SIZE;
  }

  // Returns the duration in samples-per-channel of the redandant payload in
  // |encoded| which is |encoded_len| bytes long. Returns kNotImplemented if no
  // duration estimate is available, or -1 in case of an error.
  int PacketDurationRedundant(const uint8_t* encoded, size_t encoded_len) const {
    return this->PacketDuration(encoded, encoded_len);
  }

  // Detects whether a packet has forward error correction. The packet is
  // comprised of the samples in |encoded| which is |encoded_len| bytes long.
  // Returns true if the packet has FEC and false otherwise.
  bool PacketHasFec(const uint8_t* encoded, size_t encoded_len) const {
    return 0;
  }

  // Returns the actual sample rate of the decoder's output. This value may not
  // change during the lifetime of the decoder.
  int SampleRateHz() const {
    return codec.getSampleRateInHz();
  }

  // The number of channels in the decoder's output. This value may not change
  // during the lifetime of the decoder.
  size_t Channels() const {
    return codec.getChannels();
  }

};



#endif
