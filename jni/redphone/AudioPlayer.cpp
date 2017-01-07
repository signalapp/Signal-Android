
#include "AudioPlayer.h"
#include "EncodedAudioData.h"

#include <android/log.h>

#define TAG "AudioPlayer"

AudioPlayer::AudioPlayer(WebRtcJitterBuffer &webRtcJitterBuffer, AudioCodec &audioCodec) :
  webRtcJitterBuffer(webRtcJitterBuffer), audioCodec(audioCodec),
  bqPlayerObject(NULL), bqPlayerPlay(NULL), outputMixObject(NULL), bqPlayerBufferQueue(NULL)
{
}

AudioPlayer::~AudioPlayer() {
}

void AudioPlayer::playerCallback(SLAndroidSimpleBufferQueueItf bufferQueue, void *context) {
  AudioPlayer* audioPlayer = static_cast<AudioPlayer*>(context);
  audioPlayer->playerCallback(bufferQueue);
}

void AudioPlayer::playerCallback(SLAndroidSimpleBufferQueueItf bufferQueue) {
  if (webRtcJitterBuffer.getAudio(&audioFrame) == 0) {
    int length = audioFrame.samples_per_channel_ * audioFrame.num_channels_ * sizeof(short);
    (*bufferQueue)->Enqueue(bufferQueue, audioFrame.data_, length);
  }

//  int samples = webRtcJitterBuffer.getAudio(outputBuffer, FRAME_SIZE);
//  __android_log_print(ANDROID_LOG_WARN, TAG, "Jitter gave me: %d samples", samples);
//  (*bufferQueue)->Enqueue(bufferQueue, outputBuffer, samples * sizeof(short));
}

int AudioPlayer::start(SLEngineItf *engineEnginePtr) {
  SLEngineItf engineEngine = *engineEnginePtr;

  SLDataLocator_AndroidSimpleBufferQueue loc_bufq = {SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, 2};

  SLDataFormat_PCM format_pcm = {SL_DATAFORMAT_PCM, 1, SL_SAMPLINGRATE_8,
                                 SL_PCMSAMPLEFORMAT_FIXED_16, SL_PCMSAMPLEFORMAT_FIXED_16,
                                 SL_SPEAKER_FRONT_LEFT, SL_BYTEORDER_LITTLEENDIAN};

  SLDataSource audioSrc = {&loc_bufq, &format_pcm};

  const SLInterfaceID mixIds[] = {SL_IID_VOLUME};
  const SLboolean    mixReq[]  = {SL_BOOLEAN_FALSE};

  if ((*engineEngine)->CreateOutputMix(engineEngine, &outputMixObject, 1, mixIds, mixReq) != SL_RESULT_SUCCESS) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "CreateOutputMix failed!");
    return -1;
  }

  if ((*outputMixObject)->Realize(outputMixObject, SL_BOOLEAN_FALSE) != SL_RESULT_SUCCESS) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "Realize OutputMix failed!");
    return -1;
  }

  SLDataLocator_OutputMix loc_outmix = {SL_DATALOCATOR_OUTPUTMIX, outputMixObject};
  SLDataSink              audioSnk   = {&loc_outmix, NULL};

  const SLInterfaceID ids[2] = {SL_IID_ANDROIDCONFIGURATION, SL_IID_BUFFERQUEUE};
  const SLboolean     req[2] = {SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE};

  if ((*engineEngine)->CreateAudioPlayer(engineEngine, &bqPlayerObject, &audioSrc, &audioSnk, 2, ids, req) != SL_RESULT_SUCCESS) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "CreateAudioPlayer failed!");
    return -1;
  }

  SLAndroidConfigurationItf playerConfig;

  if ((*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_ANDROIDCONFIGURATION, &playerConfig) != SL_RESULT_SUCCESS) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "Get AndroidConfiguration interface failed!");
    return -1;
  }

  SLint32 streamType = SL_ANDROID_STREAM_VOICE;

  if ((*playerConfig)->SetConfiguration(playerConfig, SL_ANDROID_KEY_STREAM_TYPE, &streamType, sizeof(SLint32)) != SL_RESULT_SUCCESS) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "Setting SL_ANDROID_STREAM_VOICE failed!");
    return -1;
  }

  if ((*bqPlayerObject)->Realize(bqPlayerObject, SL_BOOLEAN_FALSE) != SL_RESULT_SUCCESS) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "Realize PlayerObject failed!");
    return -1;
  }

  if ((*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_PLAY, &bqPlayerPlay) != SL_RESULT_SUCCESS) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "GetInterface PlayerObject failed!");
    return -1;
  }

  if ((*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_BUFFERQUEUE, &bqPlayerBufferQueue) != SL_RESULT_SUCCESS) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "BufferQueue failed!");
    return -1;
  }

  if ((*bqPlayerBufferQueue)->RegisterCallback(bqPlayerBufferQueue, &AudioPlayer::playerCallback, this) != SL_RESULT_SUCCESS) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "RegisterCallback failed!");
    return -1;
  }

  memset(audioFrame.data_, 0, sizeof(audioFrame.data_));
//  memset(outputBuffer, 0, FRAME_SIZE * sizeof(short));

  if ((*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, audioFrame.data_, FRAME_SIZE * sizeof(short)) != SL_RESULT_SUCCESS) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "Player enqueue failed!");
    return -1;
  }

  if ((*bqPlayerPlay)->SetPlayState(bqPlayerPlay, SL_PLAYSTATE_PLAYING) != SL_RESULT_SUCCESS) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "Play state failed!");
    return -1;
  }

  return 0;
}

void AudioPlayer::stop() {
  if (bqPlayerPlay != NULL) {
    (*bqPlayerPlay)->SetPlayState(bqPlayerPlay, SL_PLAYSTATE_STOPPED);
  }

  if (bqPlayerBufferQueue != NULL) {
    (*bqPlayerBufferQueue)->Clear(bqPlayerBufferQueue);
  }

  if (bqPlayerObject != NULL) {
    (*bqPlayerObject)->Destroy(bqPlayerObject);
    bqPlayerPlay        = NULL;
    bqPlayerBufferQueue = NULL;
    bqPlayerObject      = NULL;
  }

  if (outputMixObject != NULL) {
    (*outputMixObject)->Destroy(outputMixObject);
    outputMixObject = NULL;
  }
}