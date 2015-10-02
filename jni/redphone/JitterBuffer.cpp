#include "JitterBuffer.h"

#include <android/log.h>

#define TAG "JitterBuffer"

JitterBuffer::JitterBuffer() :
  pendingAudio()
{
  pthread_mutex_init(&lock, NULL);
}

void JitterBuffer::addAudio(int64_t sequence, char* encodedData, int encodedDataLen) {
  EncodedAudioData *encodedAudioData = new EncodedAudioData(encodedData, encodedDataLen, sequence);

  pthread_mutex_lock(&lock);
  pendingAudio.push(encodedAudioData);
  __android_log_print(ANDROID_LOG_WARN, TAG, "Queue Size: %d", pendingAudio.size());
  pthread_mutex_unlock(&lock);
}

EncodedAudioData* JitterBuffer::getAudio() {
  EncodedAudioData *next = NULL;

  pthread_mutex_lock(&lock);

  if (!pendingAudio.empty()) {
    next = pendingAudio.top();
    pendingAudio.pop();
  }

  pthread_mutex_unlock(&lock);
  return next;
}