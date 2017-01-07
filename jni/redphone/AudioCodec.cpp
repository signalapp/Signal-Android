#include "AudioCodec.h"

#include <speex/speex.h>
//#include <speex/speex_preprocess.h>
#include <stdlib.h>
#include <stdio.h>
#include <stdarg.h>

#include <android/log.h>

#define TAG "AudioCodec"

#define ECHO_TAIL_MILLIS 75

AudioCodec::AudioCodec() : enc(NULL), dec(NULL), aecm(NULL), ns(NULL), initialized(0)
{ }

int AudioCodec::init() {

  if ((enc = speex_encoder_init(speex_lib_get_mode(SPEEX_MODEID_NB))) == NULL) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "Encoder failed to initialize!");
    return -1;
  }

  if ((dec = speex_decoder_init(speex_lib_get_mode(SPEEX_MODEID_NB))) == NULL) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "Decoder failed to initialize!");
    return -1;
  }

  if ((aecm = WebRtcAecm_Create()) == NULL) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "AECM failed to create!");
    return -1;
  }

  if (WebRtcAecm_Init(aecm, SPEEX_SAMPLE_RATE) != 0) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "AECM failed to initialize!");
    return -1;
  }

//  if (WebRtcNsx_Create(&ns) != 0) {
//    __android_log_print(ANDROID_LOG_WARN, TAG, "NS failed to create!");
//    return -1;
//  }
//
//  if (WebRtcNsx_Init(ns, SPEEX_SAMPLE_RATE) != 0) {
//    __android_log_print(ANDROID_LOG_WARN, TAG, "NS failed to initialize!");
//    return -1;
//  }
//
//  if (WebRtcNsx_set_policy(ns, 0) != 0) { // "Mild"
//    __android_log_print(ANDROID_LOG_WARN, TAG, "NS policy failed!");
//    return -1;
//  }

  spx_int32_t config = 1;
  speex_decoder_ctl(dec, SPEEX_SET_ENH, &config);
  config = 0;
  speex_encoder_ctl(enc, SPEEX_SET_VBR, &config);
  config = 4;
  speex_encoder_ctl(enc, SPEEX_SET_QUALITY, &config);
  config = 1;
  speex_encoder_ctl(enc, SPEEX_SET_COMPLEXITY, &config);

  speex_encoder_ctl(enc, SPEEX_GET_FRAME_SIZE, &enc_frame_size );
  speex_decoder_ctl(dec, SPEEX_GET_FRAME_SIZE, &dec_frame_size );

  __android_log_print(ANDROID_LOG_WARN, TAG, "Encoding frame size: %d", enc_frame_size);
  __android_log_print(ANDROID_LOG_WARN, TAG, "Decoding frame size: %d", dec_frame_size);

  speex_bits_init(&enc_bits);
  speex_bits_init(&dec_bits);

  initialized = 1;

  return 0;
}

AudioCodec::~AudioCodec() {
  if (initialized) {
    speex_bits_destroy( &enc_bits );
    speex_bits_destroy( &dec_bits );
  }

  if (aecm != NULL) WebRtcAecm_Free(aecm);

  if (enc != NULL) speex_encoder_destroy( enc );
  if (dec != NULL) speex_decoder_destroy( dec );
}

int AudioCodec::encode(short *rawData, char* encodedData, int maxEncodedDataLen) {
//  short nonoiseData[SPEEX_FRAME_SIZE];
  short cleanData[SPEEX_FRAME_SIZE];

//  WebRtcNsx_Process(ns, rawData, NULL, nonoiseData, NULL);
//  WebRtcNsx_Process(ns, rawData+80, NULL, nonoiseData+80, NULL);

  WebRtcAecm_Process(aecm, rawData, NULL, cleanData, SPEEX_FRAME_SIZE, ECHO_TAIL_MILLIS);

  speex_bits_reset(&enc_bits);
  speex_encode_int(enc, (spx_int16_t *)cleanData, &enc_bits);

  return speex_bits_write(&enc_bits, encodedData, maxEncodedDataLen);
}

int AudioCodec::decode(char* encodedData, int encodedDataLen, short *decoded) {
  int decodedOffset = 0;

  speex_bits_read_from(&dec_bits, encodedData, encodedDataLen);

  while (speex_decode_int(dec, &dec_bits, decoded + decodedOffset) == 0) {
    WebRtcAecm_BufferFarend(aecm, decoded + decodedOffset, dec_frame_size);
    decodedOffset += dec_frame_size;
  }

  return decodedOffset;
}

int AudioCodec::conceal(int frames, short *rawData) {
  int i=0;
  for (i=0;i<frames;i++) {
    speex_decode_int(dec, NULL, rawData + (i * dec_frame_size));
  }

  return frames * dec_frame_size;
}

void AudioCodec::reset() {}

int AudioCodec::getErrorCode() {
  return -1;
}

int AudioCodec::getSampleRateInHz() {
  return 8000;
}

size_t AudioCodec::getChannels() {
  return 1;
}
