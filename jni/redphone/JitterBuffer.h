#ifndef __JITTER_BUFFER_H__
#define __JITTER_BUFFER_H__

#include <iostream>
#include <queue>
#include <iomanip>

#include <pthread.h>

#include "EncodedAudioData.h"

class CompareSequence {
  public:
    bool operator()(EncodedAudioData *lh, EncodedAudioData *rh)
    {
      return lh->getSequence() > rh->getSequence();
    }
};

class JitterBuffer {

private:
  pthread_mutex_t lock;

  std::priority_queue<EncodedAudioData*, std::vector<EncodedAudioData*>, CompareSequence> pendingAudio;

public:
  JitterBuffer();
  void addAudio(int64_t sequence, char* encodedAudio, int encodedAudioLen);
  EncodedAudioData* getAudio();

};

#endif