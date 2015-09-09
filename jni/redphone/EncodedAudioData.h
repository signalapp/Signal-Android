#ifndef __ENCODED_AUDIO_DATA_H__
#define __ENCODED_AUDIO_DATA_H__

#include <sys/types.h>
#include <string.h>
#include <stdlib.h>

class EncodedAudioData {

private:
  char *data;
  int dataLen;
  int64_t sequence;

public:
  EncodedAudioData(char* encoded, int encodedLen, int64_t sequence) :
    data(NULL), dataLen(encodedLen), sequence(sequence)
  {
    data = (char*)malloc(encodedLen);
    memcpy(data, encoded, encodedLen);
  }

  ~EncodedAudioData() {
    free(data);
  }

  int64_t getSequence() {
    return sequence;
  }

  char* getData() {
    return data;
  }

  int getDataLen() {
    return dataLen;
  }
};

#endif