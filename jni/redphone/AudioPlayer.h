#ifndef __AUDIO_PLAYER_H__
#define __AUDIO_PLAYER_H__

#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>
#include <SLES/OpenSLES_AndroidConfiguration.h>

#include <modules/include/module_common_types.h>

#include "WebRtcJitterBuffer.h"
#include "AudioCodec.h"
#include "JitterBuffer.h"

#define SAMPLE_RATE 8000
#define FRAME_RATE  50
#define FRAME_SIZE  SAMPLE_RATE / FRAME_RATE

class AudioPlayer {

private:
// JitterBuffer &jitterBuffer;
 WebRtcJitterBuffer &webRtcJitterBuffer;
 AudioCodec &audioCodec;

// int sampleRate;
// int bufferFrames;

 SLObjectItf bqPlayerObject;
 SLPlayItf   bqPlayerPlay;

 SLObjectItf outputMixObject;

 SLAndroidSimpleBufferQueueItf bqPlayerBufferQueue;
 webrtc::AudioFrame audioFrame;
// short outputBuffer[FRAME_SIZE];

public:
  AudioPlayer(WebRtcJitterBuffer &jitterBuffer, AudioCodec &audioCodec);
  ~AudioPlayer();

  int start(SLEngineItf *engineEngine);
  void stop();

  static void playerCallback(SLAndroidSimpleBufferQueueItf bufferQueue, void *context);
  void playerCallback(SLAndroidSimpleBufferQueueItf bufferQueue);
};

#endif