#include "RtpPacket.h"

#include <stdlib.h>
#include <string.h>
#include <arpa/inet.h>

#include "SrtpStream.h"
//#include <srtp.h>

RtpPacket::RtpPacket(char* packetBuf, int packetLen) {
  packet     = (char*)malloc(packetLen);
  payloadLen = packetLen - sizeof(RtpHeader);
  memcpy(packet, packetBuf, packetLen);
}

RtpPacket::RtpPacket(char* payload, int payloadBufLen, int sequenceNumber, int timestamp) {
  packet     = (char*)malloc(sizeof(RtpHeader) + payloadBufLen + SRTP_MAC_SIZE);
  payloadLen = payloadBufLen;

  memset(packet, 0, sizeof(RtpHeader) + payloadLen + SRTP_MAC_SIZE);

  RtpHeader *header = (RtpHeader*)packet;
  header->flags          = htons(32768);
  header->sequenceNumber = htons(sequenceNumber);
  header->ssrc           = 0;
  header->timestamp      = htonl(timestamp);

  memcpy(packet + sizeof(RtpHeader), payload, payloadLen);
}

RtpPacket::~RtpPacket() {
  free(packet);
}

uint16_t RtpPacket::getSequenceNumber() {
  RtpHeader *header = (RtpHeader*)packet;
  return ntohs(header->sequenceNumber);
}

int RtpPacket::getPayloadType() {
  RtpHeader *header = (RtpHeader*)packet;
  return header->flags & 0x7F;
}

uint32_t RtpPacket::getTimestamp() {
  RtpHeader *header = (RtpHeader*)packet;
  return ntohl(header->timestamp);
}

void RtpPacket::setTimestamp(uint32_t timestamp) {
  RtpHeader *header = (RtpHeader*)packet;
  header->timestamp = htonl(timestamp);
}

uint32_t RtpPacket::getSsrc() {
  RtpHeader *header = (RtpHeader*)packet;
  return ntohl(header->ssrc);
}

char* RtpPacket::getPayload() {
  return packet + sizeof(RtpHeader);
}

uint32_t RtpPacket::getPayloadLen() {
  return payloadLen;
}

void RtpPacket::setPayloadLen(uint32_t payloadLen) {
  this->payloadLen = payloadLen;
}

char* RtpPacket::getSerializedPacket() {
  return packet;
}

int RtpPacket::getSerializedPacketLen() {
  return sizeof(RtpHeader) + payloadLen;
}