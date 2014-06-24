#ifndef __SHA512_H__
#define __SHA512_H__

#include "sha512.h"
#include "sph_sha2.h"

int crypto_hash_sha512_ref(unsigned char *output ,const unsigned char *input,
                           unsigned long long len);

#endif
