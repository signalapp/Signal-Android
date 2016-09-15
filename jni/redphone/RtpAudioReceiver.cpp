#include "RtpAudioReceiver.h"

#include <android/log.h>

#define TAG "RtpAudioReceiver"

RtpAudioReceiver::RtpAudioReceiver(int socketFd, SrtpStreamParameters *parameters) :
  socketFd(socketFd), sequenceCounter(), srtpStream(parameters)
{
}

int RtpAudioReceiver::init() {
  if (srtpStream.init() != 0) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "SRTP stream failed to initialize!");
    return -1;
  }

  return 0;
}

RtpPacket* RtpAudioReceiver::receive(char* encodedData, int encodedDataLen) {
  int received = recv(socketFd, encodedData, encodedDataLen, 0);

  if (received == -1) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "recv() failed!");
    return NULL;
  }

  if (received < RtpPacket::getMinimumSize()) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "recveived malformed packet!");
    return NULL;
  }

  RtpPacket *packet = new RtpPacket(encodedData, received);

  if (srtpStream.decrypt(*packet, sequenceCounter.convertNext(packet->getSequenceNumber())) != 0) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "SRTP decrypt failed!");
    delete packet;
    return NULL;
  }

  return packet;
}