#ifndef __STRP_STREAM_H__
#define __STRP_STREAM_H__

#include <openssl/aes.h>
#include <openssl/hmac.h>

#define SRTP_AES_KEY_SIZE 16
#define SRTP_SALT_SIZE    14
#define SRTP_MAC_KEY_SIZE 20

#define SRTP_MAC_SIZE  20

#include "RtpPacket.h"

class SrtpStreamParameters {

public:
  uint8_t cipherKey[SRTP_AES_KEY_SIZE];
  uint8_t macKey[SRTP_MAC_KEY_SIZE];
  uint8_t salt[SRTP_SALT_SIZE];

  SrtpStreamParameters(uint8_t *cipherKeyPtr, uint8_t* macKeyPtr, uint8_t *saltPtr)
  {
    memcpy(cipherKey, cipherKeyPtr, SRTP_AES_KEY_SIZE);
    memcpy(macKey, macKeyPtr, SRTP_MAC_KEY_SIZE);
    memcpy(salt, saltPtr, SRTP_SALT_SIZE);
  }

};

class SrtpStream {

private:
  SrtpStreamParameters *parameters;
  AES_KEY key;

  void setIv(int64_t logicalSequence, uint32_t ssrc, uint8_t *salt, uint8_t *iv);

public:

  SrtpStream(SrtpStreamParameters *parameters);
  ~SrtpStream();

  int init();
  int decrypt(RtpPacket &packet, int64_t logicalSequence);
  int encrypt(RtpPacket &packet, int64_t logicalSequence);

};

#endif