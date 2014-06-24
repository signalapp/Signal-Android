#ifndef crypto_sign_edwards25519sha512batch_H
#define crypto_sign_edwards25519sha512batch_H

#define crypto_sign_edwards25519sha512batch_ref10_SECRETKEYBYTES 64
#define crypto_sign_edwards25519sha512batch_ref10_PUBLICKEYBYTES 32
#define crypto_sign_edwards25519sha512batch_ref10_BYTES 64
#ifdef __cplusplus
#include <string>
extern std::string crypto_sign_edwards25519sha512batch_ref10(const std::string &,const std::string &);
extern std::string crypto_sign_edwards25519sha512batch_ref10_open(const std::string &,const std::string &);
extern std::string crypto_sign_edwards25519sha512batch_ref10_keypair(std::string *);
extern "C" {
#endif
extern int crypto_sign_edwards25519sha512batch_ref10(unsigned char *,unsigned long long *,const unsigned char *,unsigned long long,const unsigned char *);
extern int crypto_sign_edwards25519sha512batch_ref10_open(unsigned char *,unsigned long long *,const unsigned char *,unsigned long long,const unsigned char *);
extern int crypto_sign_edwards25519sha512batch_ref10_keypair(unsigned char *,unsigned char *);
#ifdef __cplusplus
}
#endif

#define crypto_sign_edwards25519sha512batch crypto_sign_edwards25519sha512batch_ref10
#define crypto_sign_edwards25519sha512batch_open crypto_sign_edwards25519sha512batch_ref10_open
#define crypto_sign_edwards25519sha512batch_keypair crypto_sign_edwards25519sha512batch_ref10_keypair
#define crypto_sign_edwards25519sha512batch_BYTES crypto_sign_edwards25519sha512batch_ref10_BYTES
#define crypto_sign_edwards25519sha512batch_PUBLICKEYBYTES crypto_sign_edwards25519sha512batch_ref10_PUBLICKEYBYTES
#define crypto_sign_edwards25519sha512batch_SECRETKEYBYTES crypto_sign_edwards25519sha512batch_ref10_SECRETKEYBYTES
#define crypto_sign_edwards25519sha512batch_IMPLEMENTATION "crypto_sign/edwards25519sha512batch/ref10"
#ifndef crypto_sign_edwards25519sha512batch_ref10_VERSION
#define crypto_sign_edwards25519sha512batch_ref10_VERSION "-"
#endif
#define crypto_sign_edwards25519sha512batch_VERSION crypto_sign_edwards25519sha512batch_ref10_VERSION

#endif
