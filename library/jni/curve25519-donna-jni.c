/**
 * Copyright (C) 2013 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

#include <string.h>
#include <stdint.h>

#include <jni.h>
#include "curve25519-donna.h"

JNIEXPORT jbyteArray JNICALL Java_org_whispersystems_textsecure_crypto_ecc_Curve25519_generatePrivateKey
  (JNIEnv *env, jclass clazz, jbyteArray random, jboolean ephemeral)
{
  uint8_t* privateKey = (uint8_t*)(*env)->GetByteArrayElements(env, random, 0);

  privateKey[0] &= 248;

  if (ephemeral) {
    privateKey[0] |= 1;
  }

  privateKey[31] &= 127;
  privateKey[31] |= 64;

  (*env)->ReleaseByteArrayElements(env, random, privateKey, 0);

  return random;
}

JNIEXPORT jbyteArray JNICALL Java_org_whispersystems_textsecure_crypto_ecc_Curve25519_generatePublicKey
  (JNIEnv *env, jclass clazz, jbyteArray privateKey)
{
    static const uint8_t  basepoint[32] = {9};

    jbyteArray publicKey       = (*env)->NewByteArray(env, 32);
    uint8_t*   publicKeyBytes  = (uint8_t*)(*env)->GetByteArrayElements(env, publicKey, 0);
    uint8_t*   privateKeyBytes = (uint8_t*)(*env)->GetByteArrayElements(env, privateKey, 0);

    curve25519_donna(publicKeyBytes, privateKeyBytes, basepoint);

    (*env)->ReleaseByteArrayElements(env, publicKey, publicKeyBytes, 0);
    (*env)->ReleaseByteArrayElements(env, privateKey, privateKeyBytes, 0);

    return publicKey;
}

JNIEXPORT jbyteArray JNICALL Java_org_whispersystems_textsecure_crypto_ecc_Curve25519_calculateAgreement
  (JNIEnv *env, jclass clazz, jbyteArray privateKey, jbyteArray publicKey)
{
    jbyteArray sharedKey       = (*env)->NewByteArray(env, 32);
    uint8_t*   sharedKeyBytes  = (uint8_t*)(*env)->GetByteArrayElements(env, sharedKey, 0);
    uint8_t*   privateKeyBytes = (uint8_t*)(*env)->GetByteArrayElements(env, privateKey, 0);
    uint8_t*   publicKeyBytes  = (uint8_t*)(*env)->GetByteArrayElements(env, publicKey, 0);

    curve25519_donna(sharedKeyBytes, privateKeyBytes, publicKeyBytes);

    (*env)->ReleaseByteArrayElements(env, sharedKey, sharedKeyBytes, 0);
    (*env)->ReleaseByteArrayElements(env, publicKey, publicKeyBytes, 0);
    (*env)->ReleaseByteArrayElements(env, privateKey, privateKeyBytes, 0);

    return sharedKey;
}
