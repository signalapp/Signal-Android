#ifndef __AUDIO_CODEC_H__
#define __AUDIO_CODEC_H__

#include <sys/types.h>
#include <speex/speex.h>
#include <speex/speex_echo.h>

#include <modules/audio_processing/aecm/include/echo_control_mobile.h>
#include <modules/audio_processing/ns/include/noise_suppression_x.h>

#define SPEEX_BIT_RATE    8000
#define SPEEX_SAMPLE_RATE 8000
#define SPEEX_FRAME_RATE  50
#define SPEEX_FRAME_SIZE  (SPEEX_SAMPLE_RATE / SPEEX_FRAME_RATE)

#define SPEEX_ENCODED_FRAME_SIZE 20

class AudioCodec {

private:
  void *enc; //speex encoder
  void *dec; //speex decoder

  void *aecm;
  NsxHandle *ns;

  SpeexBits enc_bits, dec_bits;
  SpeexEchoState *echo_state;

  int enc_frame_size, dec_frame_size;
  int initialized;

public:
  AudioCodec();
  ~AudioCodec();

  int init();
  int encode(short *rawData, char* encodedData, int encodedDataLen);
  int decode(char* encodedData, int encodedDataLen, short* rawData, size_t decodeMaxSize);
  int conceal(int frames, short *rawData);

};

#endif