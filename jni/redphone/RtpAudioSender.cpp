#include "RtpAudioSender.h"
#include "RtpPacket.h"

#include <android/log.h>
#include <errno.h>

#define TAG "RtpAudioSender"

RtpAudioSender::RtpAudioSender(int socketFd, struct sockaddr *sockAddr, int sockAddrLen,
                               SrtpStreamParameters *parameters) :
  socketFd(socketFd), sequenceNumber(0), sockAddr(sockAddr), sockAddrLen(sockAddrLen),
  srtpStream(parameters)
{
}

int RtpAudioSender::init() {
  if (srtpStream.init() != 0) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "SRTP stream failed!");
    return -1;
  }

  return 0;
}

int RtpAudioSender::send(int timestamp, char* encodedData, int encodedDataLen) {
  RtpPacket packet(encodedData, encodedDataLen, sequenceNumber, timestamp);

  if (srtpStream.encrypt(packet, sequenceNumber++) != 0) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "SRTP encrypt() failed!");
    return -1;
  }

  char* serializedPacket    = packet.getSerializedPacket();
  int   serializedPacketLen = packet.getSerializedPacketLen();

  if (sendto(socketFd, serializedPacket, serializedPacketLen, 0, sockAddr, sockAddrLen) == -1)
  {
    __android_log_print(ANDROID_LOG_WARN, TAG, "sendto() failed!");
    return -1;
  }

  return 0;
}