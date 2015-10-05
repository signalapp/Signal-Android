#ifndef __WEB_RTC_CODEC_H__
#define __WEB_RTC_CODEC_H__

#include "AudioCodec.h"
#include <sys/types.h>

#include <modules/audio_coding/neteq/interface/audio_decoder.h>

class WebRtcCodec : public webrtc::AudioDecoder {

private:
  AudioCodec &codec;

public:
  WebRtcCodec(AudioCodec &codec) :
    AudioDecoder(webrtc::kDecoderArbitrary), codec(codec)
  {}

  int Decode(const uint8_t* encoded, size_t encoded_len,
             int16_t* decoded, SpeechType* speech_type)
  {
    *speech_type = kSpeech;
    return codec.decode((char*)encoded, encoded_len, decoded);
  }

  bool HasDecodePlc() const {
    return 1;
  }

  int DecodePlc(int num_frames, int16_t* decoded) {
    return codec.conceal(num_frames, decoded);
  }

  int Init() { return 0; }

  int PacketDuration(const uint8_t* encoded, size_t encoded_len) const {
    return (encoded_len / SPEEX_ENCODED_FRAME_SIZE) * SPEEX_FRAME_SIZE;
  }

  int PacketDurationRedundant(const uint8_t* encoded, size_t encoded_len) const {
    return this->PacketDuration(encoded, encoded_len);
  }

  bool PacketHasFec(const uint8_t* encoded, size_t encoded_len) const {
    return 0;
  }
};



#endif