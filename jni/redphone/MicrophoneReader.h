#ifndef __MICROPHONE_READER_H__
#define __MICROPHONE_READER_H__

#include "AudioCodec.h"
#include "RtpAudioSender.h"
#include "Clock.h"

#include <jni.h>

#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>
#include <SLES/OpenSLES_AndroidConfiguration.h>

#define SAMPLE_RATE 8000
#define FRAME_RATE  50
#define FRAME_SIZE  SAMPLE_RATE / FRAME_RATE

class MicrophoneReader {
  private:
    int androidSdkVersion;
    volatile int muteEnabled;

    AudioCodec &audioCodec;
    RtpAudioSender &rtpAudioSender;
    Clock &clock;

    short inputBuffer[FRAME_SIZE * 2];
    char encodedAudio[1024];

    SLObjectItf recorderObject;
    SLRecordItf recorderRecord;

    SLAndroidConfigurationItf androidConfig;
    SLAndroidSimpleBufferQueueItf recorderBufferQueue;

  public:
    MicrophoneReader(int androidSdkVersion, AudioCodec &audioCodec, RtpAudioSender &rtpAudioSender, Clock &clock);
    ~MicrophoneReader();

    int start(SLEngineItf *engineEngine);
    void stop();

    void setMute(int muteEnabled);

    void recorderCallback(SLAndroidSimpleBufferQueueItf bufferQueue);
    static void recorderCallback(SLAndroidSimpleBufferQueueItf bufferQueue, void* context);

};

#endif