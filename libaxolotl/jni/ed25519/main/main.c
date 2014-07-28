#include <stdio.h>
#include <string.h>
#include "crypto_hash_sha512.h"
#include "curve_sigs.h"

#define MSG_LEN 200

int main(int argc, char* argv[])
{
  unsigned char privkey[32];
  unsigned char pubkey[32];
  unsigned char signature[64];
  unsigned char msg[MSG_LEN];
  unsigned char random[64];

  /* Initialize pubkey, privkey, msg */
  memset(msg, 0, MSG_LEN);
  memset(privkey, 0, 32);
  memset(pubkey, 0, 32);
  privkey[0] &= 248;
  privkey[31] &= 63;
  privkey[31] |= 64;

  privkey[8] = 189; /* just so there's some bits set */


  /* SHA512 test */
  unsigned char sha512_input[112] = "abcdefghbcdefghicdefghijdefghijkefghijklfghijklmghijklmnhijklmnoijklmnopjklmnopqklmnopqrlmnopqrsmnopqrstnopqrstu";
  unsigned char sha512_correct_output[64] =
{
0x8E, 0x95, 0x9B, 0x75, 0xDA, 0xE3, 0x13, 0xDA,
0x8C, 0xF4, 0xF7, 0x28, 0x14, 0xFC, 0x14, 0x3F,
0x8F, 0x77, 0x79, 0xC6, 0xEB, 0x9F, 0x7F, 0xA1,
0x72, 0x99, 0xAE, 0xAD, 0xB6, 0x88, 0x90, 0x18,
0x50, 0x1D, 0x28, 0x9E, 0x49, 0x00, 0xF7, 0xE4,
0x33, 0x1B, 0x99, 0xDE, 0xC4, 0xB5, 0x43, 0x3A,
0xC7, 0xD3, 0x29, 0xEE, 0xB6, 0xDD, 0x26, 0x54,
0x5E, 0x96, 0xE5, 0x5B, 0x87, 0x4B, 0xE9, 0x09
};
  unsigned char sha512_actual_output[64];

  crypto_hash_sha512_ref(sha512_actual_output, sha512_input, sizeof(sha512_input));
  if (memcmp(sha512_actual_output, sha512_correct_output, 64) != 0)
    printf("SHA512 bad #1\n");
  else
    printf("SHA512 good #1\n");

  sha512_input[111] ^= 1;

  crypto_hash_sha512_ref(sha512_actual_output, sha512_input, sizeof(sha512_input));
  if (memcmp(sha512_actual_output, sha512_correct_output, 64) != 0)
    printf("SHA512 good #2\n");
  else
    printf("SHA512 bad #2\n");
  
  /* Signature test */
  curve25519_keygen(pubkey, privkey);

  curve25519_sign(signature, privkey, msg, MSG_LEN, random);

  if (curve25519_verify(signature, pubkey, msg, MSG_LEN) == 0)
    printf("Signature good #1\n");
  else
    printf("Signature bad #1\n");

  signature[0] ^= 1;

  if (curve25519_verify(signature, pubkey, msg, MSG_LEN) == 0)
    printf("Signature bad #2\n");
  else
    printf("Signature good #2\n");


  printf("Random testing...\n");
  for (int count = 0; count < 10000; count++) {
    unsigned char b[64];
    crypto_hash_sha512_ref(b, privkey, 32);
    memmove(privkey, b, 32);
    crypto_hash_sha512_ref(b, privkey, 32);
    memmove(random, b, 64);

    privkey[0] &= 248;
    privkey[31] &= 63;
    privkey[31] |= 64;

    curve25519_keygen(pubkey, privkey);

    curve25519_sign(signature, privkey, msg, MSG_LEN, random);

    if (curve25519_verify(signature, pubkey, msg, MSG_LEN) != 0) {
      printf("failure #1 %d\n", count);
      return -1;
    }

    if (b[63] & 1)
      signature[count % 64] ^= 1;
    else
      msg[count % MSG_LEN] ^= 1;
    if (curve25519_verify(signature, pubkey, msg, MSG_LEN) == 0) {
      printf("failure #2 %d\n", count);
      return -1;
    }
  }
  printf("OK\n");
  return 1;
}
