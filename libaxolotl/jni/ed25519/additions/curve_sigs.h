
#ifndef __CURVE_SIGS_H__
#define __CURVE_SIGS_H__

void curve25519_keygen(unsigned char* curve25519_pubkey_out,
                       unsigned char* curve25519_privkey_in);

void curve25519_sign(unsigned char* signature_out,
                     unsigned char* curve25519_privkey,
                     unsigned char* msg, unsigned long msg_len);

/* returns 0 on success */
int curve25519_verify(unsigned char* signature,
                      unsigned char* curve25519_pubkey,                      
                      unsigned char* msg, unsigned long msg_len);

/* helper function - modified version of crypto_sign() to use 
   explicit private key */
int crypto_sign_modified(
  unsigned char *sm,unsigned long long *smlen,
  const unsigned char *m,unsigned long long mlen,
  const unsigned char *sk
  );

#endif
