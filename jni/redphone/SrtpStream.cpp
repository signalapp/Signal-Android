
#include "SrtpStream.h"
#include <android/log.h>
#include <unistd.h>

#define AES_BLOCK_SIZE    16

#define TAG "SrtpStream"

SrtpStream::SrtpStream(SrtpStreamParameters *parameters) :
  parameters(parameters)
{}

SrtpStream::~SrtpStream() {
  if (parameters != NULL) {
    delete parameters;
  }
}

int SrtpStream::init() {
  if (AES_set_encrypt_key(parameters->cipherKey, SRTP_AES_KEY_SIZE * 8, &key) != 0) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "Failed to set AES key!");
    return -1;
  }

  return 0;
}

void SrtpStream::setIv(int64_t logicalSequence, uint32_t ssrc, uint8_t *salt, uint8_t *iv) {
  memset(iv, 0, AES_BLOCK_SIZE);
  memcpy(iv, salt, SRTP_SALT_SIZE);

  iv[6]  ^= (uint8_t)(ssrc >> 8);
  iv[7]  ^= (uint8_t)(ssrc);
  iv[8]  ^= (uint8_t)(logicalSequence >> 40);
  iv[9]  ^= (uint8_t)(logicalSequence >> 32);
  iv[10] ^= (uint8_t)(logicalSequence >> 24);
  iv[11] ^= (uint8_t)(logicalSequence >> 16);
  iv[12] ^= (uint8_t)(logicalSequence >> 8);
  iv[13] ^= (uint8_t)(logicalSequence);
}


int SrtpStream::decrypt(RtpPacket &packet, int64_t logicalSequence) {
  uint8_t iv[AES_BLOCK_SIZE];
  uint8_t ecount[AES_BLOCK_SIZE];
  uint8_t ourMac[SRTP_MAC_SIZE];

  uint32_t num    = 0;
  uint32_t digest = 0;

  setIv(logicalSequence, packet.getSsrc(), parameters->salt, iv);
  memset(ecount, 0, sizeof(ecount));

  if (packet.getPayloadLen() < (SRTP_MAC_SIZE + 1)) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "Packet shorter than MAC!");
    return -1;
  }

  HMAC(EVP_sha1(), parameters->macKey, SRTP_MAC_KEY_SIZE,
       (uint8_t*)packet.getSerializedPacket(), packet.getSerializedPacketLen() - SRTP_MAC_SIZE, ourMac, &digest);

  if (memcmp(ourMac, packet.getSerializedPacket() + packet.getSerializedPacketLen() - SRTP_MAC_SIZE,
      SRTP_MAC_SIZE) != 0)
  {
    __android_log_print(ANDROID_LOG_WARN, TAG, "MAC comparison failed!");
    return -1;
  }

  packet.setPayloadLen(packet.getPayloadLen() - SRTP_MAC_SIZE);

  AES_ctr128_encrypt((uint8_t*)packet.getPayload(), (uint8_t*)packet.getPayload(),
                     packet.getPayloadLen(), &key, iv, ecount, &num);

  return 0;
}

int SrtpStream::encrypt(RtpPacket &packet, int64_t logicalSequence) {
  uint8_t iv[AES_BLOCK_SIZE];
  uint8_t ecount[AES_BLOCK_SIZE];

  uint32_t num    = 0;
  uint32_t digest = 0;

  setIv(logicalSequence, packet.getSsrc(), parameters->salt, iv);
  memset(ecount, 0, sizeof(ecount));

  AES_ctr128_encrypt((uint8_t*)packet.getPayload(), (uint8_t*)packet.getPayload(), packet.getPayloadLen(), &key, iv, ecount, &num);

  HMAC(EVP_sha1(), parameters->macKey, SRTP_MAC_KEY_SIZE,
       (uint8_t*)packet.getSerializedPacket(), packet.getSerializedPacketLen(),
       (uint8_t*)packet.getSerializedPacket() + packet.getSerializedPacketLen(), &digest);

  packet.setPayloadLen(packet.getPayloadLen() + SRTP_MAC_SIZE);

  return 0;
}
