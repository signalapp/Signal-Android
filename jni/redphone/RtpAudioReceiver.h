#ifndef __RTP_AUDIO_RECEIVER_H__
#define __RTP_AUDIO_RECEIVER_H__

#include "RtpPacket.h"

#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <arpa/inet.h>
#include <sys/socket.h>

#include "SequenceCounter.h"
#include "SrtpStream.h"

class RtpAudioReceiver {

private:
  int socketFd;

  SequenceCounter sequenceCounter;
  SrtpStream      srtpStream;

public:
  RtpAudioReceiver(int socketFd, SrtpStreamParameters *parameters);

  int init();
  RtpPacket* receive(char* encodedData, int encodedDataLen);

};


#endif