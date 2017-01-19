#ifndef __RTP_PACKET_H__
#define __RTP_PACKET_H__

#include <sys/types.h>

typedef struct _RtpHeader {
  uint16_t flags;
  uint16_t sequenceNumber;
  uint32_t timestamp;
  uint32_t ssrc;
} RtpHeader;

class RtpPacket {

private:
  char *packet;
  int     payloadLen;

public:
  RtpPacket(char *packet, int packetLen);
  RtpPacket(char *payload, int payloadLen, int sequenceNumber, int timestamp);
  ~RtpPacket();

  static int getMinimumSize() {
    return sizeof(RtpHeader);
  }

  uint16_t getSequenceNumber();
  int getPayloadType();
  uint32_t getTimestamp();
  void setTimestamp(uint32_t timestamp);
  uint32_t getSsrc();

  char* getPayload();
  uint32_t getPayloadLen();
  void setPayloadLen(uint32_t len);

  char* getSerializedPacket();
  int getSerializedPacketLen();
};

#endif