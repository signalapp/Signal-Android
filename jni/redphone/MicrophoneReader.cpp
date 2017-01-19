#include "MicrophoneReader.h"
#include "SampleRateUtil.h"

#include <jni.h>

#include <android/log.h>

#define TAG "MicrophoneReader"

#ifndef SL_ANDROID_RECORDING_PRESET_VOICE_COMMUNICATION
#define SL_ANDROID_RECORDING_PRESET_VOICE_COMMUNICATION ((SLuint32) 0x00000004)
#endif

MicrophoneReader::MicrophoneReader(int androidSdkVersion, AudioCodec &audioCodec, RtpAudioSender &rtpAudioSender, Clock &clock) :
  androidSdkVersion(androidSdkVersion), muteEnabled(0),
  audioCodec(audioCodec), rtpAudioSender(rtpAudioSender), clock(clock),
  recorderObject(NULL), recorderRecord(NULL), recorderBufferQueue(NULL)
{
}

MicrophoneReader::~MicrophoneReader() {
}

void MicrophoneReader::recorderCallback(SLAndroidSimpleBufferQueueItf bufferQueue, void *context) {
  MicrophoneReader* microphoneReader = static_cast<MicrophoneReader*>(context);
  microphoneReader->recorderCallback(bufferQueue);
}

void MicrophoneReader::recorderCallback(SLAndroidSimpleBufferQueueItf bufferQueue)
{
  if (muteEnabled) {
    memset(inputBuffer, 0, FRAME_SIZE * 2 * sizeof(short));
  }

  int encodedAudioLen = audioCodec.encode(inputBuffer, encodedAudio, sizeof(encodedAudio));
  encodedAudioLen += audioCodec.encode(inputBuffer + FRAME_SIZE, encodedAudio + encodedAudioLen, sizeof(encodedAudio) - encodedAudioLen);

  rtpAudioSender.send(clock.tick(2), encodedAudio, encodedAudioLen);

  (*bufferQueue)->Enqueue(bufferQueue, inputBuffer, FRAME_SIZE * 2 * sizeof(short));
}

void MicrophoneReader::setMute(int muteEnabled) {
  this->muteEnabled = muteEnabled;
}

int MicrophoneReader::start(SLEngineItf *engineEnginePtr) {
  SLEngineItf engineEngine = *engineEnginePtr;

  SLDataLocator_AndroidSimpleBufferQueue loc_bq     = {SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, 1};
  SLDataFormat_PCM                       format_pcm = {SL_DATAFORMAT_PCM, 1, SL_SAMPLINGRATE_8,
                                                       SL_PCMSAMPLEFORMAT_FIXED_16, SL_PCMSAMPLEFORMAT_FIXED_16,
                                                       SL_SPEAKER_FRONT_CENTER, SL_BYTEORDER_LITTLEENDIAN};

  SLDataLocator_IODevice loc_dev  = {SL_DATALOCATOR_IODEVICE, SL_IODEVICE_AUDIOINPUT, SL_DEFAULTDEVICEID_AUDIOINPUT, NULL};

  SLDataSource audioSrc = {&loc_dev, NULL};
  SLDataSink   audioSnk = {&loc_bq, &format_pcm};

  const SLInterfaceID id[2]  = {SL_IID_ANDROIDSIMPLEBUFFERQUEUE, SL_IID_ANDROIDCONFIGURATION};
  const SLboolean     req[2] = {SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE};

  if ((*engineEngine)->CreateAudioRecorder(engineEngine, &recorderObject, &audioSrc,
                                           &audioSnk, 2, id, req) != SL_RESULT_SUCCESS)
  {
    return -1;
  }

  if ((*recorderObject)->GetInterface(recorderObject, SL_IID_ANDROIDCONFIGURATION, &androidConfig) == SL_RESULT_SUCCESS) {
    SLint32 recordingPreset = SL_ANDROID_RECORDING_PRESET_GENERIC;

    if (androidSdkVersion >= 14) {
       __android_log_print(ANDROID_LOG_WARN, TAG, "Using voice communication Microphone preset...");
      recordingPreset = SL_ANDROID_RECORDING_PRESET_VOICE_COMMUNICATION;
    }

    (*androidConfig)->SetConfiguration(androidConfig, SL_ANDROID_KEY_RECORDING_PRESET,
                                       &recordingPreset, sizeof(SLint32));
  }

  if ((*recorderObject)->Realize(recorderObject, SL_BOOLEAN_FALSE) != SL_RESULT_SUCCESS) {
    return -1;
  }

  if ((*recorderObject)->GetInterface(recorderObject, SL_IID_RECORD, &recorderRecord) != SL_RESULT_SUCCESS) {
    return -1;
  }

  if ((*recorderObject)->GetInterface(recorderObject, SL_IID_ANDROIDSIMPLEBUFFERQUEUE, &recorderBufferQueue) != SL_RESULT_SUCCESS) {
    return -1;
  }

  if ((*recorderBufferQueue)->RegisterCallback(recorderBufferQueue, &MicrophoneReader::recorderCallback, this) != SL_RESULT_SUCCESS) {
    return -1;
  }

  if ((*recorderBufferQueue)->Enqueue(recorderBufferQueue, inputBuffer, FRAME_SIZE * 2 * sizeof(short)) != SL_RESULT_SUCCESS) {
    return -1;
  }

  if ((*recorderRecord)->SetRecordState(recorderRecord, SL_RECORDSTATE_RECORDING) != SL_RESULT_SUCCESS) {
    return -1;
  }

  return 0;
}

void MicrophoneReader::stop() {
  if (recorderRecord != NULL) {
    (*recorderRecord)->SetRecordState(recorderRecord, SL_RECORDSTATE_STOPPED);
  }

  if (recorderBufferQueue != NULL) {
    (*recorderBufferQueue)->Clear(recorderBufferQueue);
  }

  if (recorderObject != NULL) {
    (*recorderObject)->Destroy(recorderObject);

    recorderRecord      = NULL;
    recorderObject      = NULL;
    recorderBufferQueue = NULL;
  }
}