
#include "AudioCodec.h"
#include "MicrophoneReader.h"
#include "SequenceCounter.h"
#include "JitterBuffer.h"
#include "RtpAudioReceiver.h"
#include "RtpAudioSender.h"
#include "AudioPlayer.h"
#include "NetworkUtil.h"

#include "CallAudioManager.h"

#include <string.h>
#include <stdint.h>
#include <unistd.h>

#include <jni.h>
#include <android/log.h>

#define TAG "CallAudioManager"

CallAudioManager::CallAudioManager(int androidSdkVersion, int socketFd,
                                   struct sockaddr *sockAddr, int sockAddrLen,
                                   SrtpStreamParameters *senderParameters, SrtpStreamParameters *receiverParameters)
  : running(0), finished(1), engineObject(NULL), engineEngine(NULL), audioCodec(),
    audioSender(socketFd, sockAddr, sockAddrLen, senderParameters),
    audioReceiver(socketFd, receiverParameters),
    webRtcJitterBuffer(audioCodec), clock(),
    microphoneReader(androidSdkVersion, audioCodec, audioSender, clock),
    audioPlayer(webRtcJitterBuffer, audioCodec),
    sockAddr(sockAddr)
{
}

int CallAudioManager::init() {
  if (pthread_mutex_init(&mutex, NULL) != 0) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "Failed to create mutex!");
    return -1;
  }

  if (pthread_cond_init(&condition, NULL) != 0) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "Failed to create condition!");
    return -1;
  }

  return 0;
}

CallAudioManager::~CallAudioManager() {
  __android_log_print(ANDROID_LOG_WARN, TAG, "Shutting down...");

  microphoneReader.stop();

  __android_log_print(ANDROID_LOG_WARN, TAG, "Stopping audio player...");
  audioPlayer.stop();

  __android_log_print(ANDROID_LOG_WARN, TAG, "Stopping jitter buffer...");
  webRtcJitterBuffer.stop();

  __android_log_print(ANDROID_LOG_WARN, TAG, "Freeing resources...");

  if (sockAddr != NULL) {
    free(sockAddr);
  }

  if (engineObject != NULL) {
    (*engineObject)->Destroy(engineObject);
  }

  __android_log_print(ANDROID_LOG_WARN, TAG, "Shutdown complete....");
}

int CallAudioManager::start() {
  running  = 1;
  finished = 0;

  if (slCreateEngine(&engineObject, 0, NULL, 0, NULL, NULL) != SL_RESULT_SUCCESS) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "Failed to create engineObject!");
    return -1;
  }

  if ((*engineObject)->Realize(engineObject, SL_BOOLEAN_FALSE) != SL_RESULT_SUCCESS) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "Failed to realize engineObject!");
    return -1;
  }

  if ((*engineObject)->GetInterface(engineObject, SL_IID_ENGINE, &engineEngine) != SL_RESULT_SUCCESS) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "Failed to get engine interface!");
    return -1;
  }

  if (audioCodec.init() != 0) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "Failed to initialize codec!");
    return -1;
  }

  if (audioSender.init() != 0) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "Failed to initialize RTP sender!");
    return -1;
  }

  if (audioReceiver.init() != 0) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "Failed to initialize RTP receiver!");
    return -1;
  }

  if (webRtcJitterBuffer.init() != 0) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "Failed to initialize jitter buffer!");
    return -1;
  }

  __android_log_print(ANDROID_LOG_WARN, TAG, "Starting MicrophoneReader...");

  if (microphoneReader.start(&engineEngine) == -1) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "ERROR -- MicrophoneReader::start() returned -1!");
    return -1;
  }

  __android_log_print(ANDROID_LOG_WARN, TAG, "Starting AudioPlayer...");

  if (audioPlayer.start(&engineEngine) == -1) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "AudioPlayer::start() returned -1!");
    return -1;
  }

  char buffer[4096];

  while(running) {
    RtpPacket *packet = audioReceiver.receive(buffer, sizeof(buffer));

    if (packet != NULL) {

      if (packet->getTimestamp() == 0) {
        packet->setTimestamp(clock.getImprovisedTimestamp(packet->getPayloadLen()));
      }

      webRtcJitterBuffer.addAudio(packet, clock.getTickCount());
      delete packet;
    }
  }

  if (pthread_mutex_lock(&mutex) != 0) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "Failed to acquire mutex!");
    return 0;
  }

  finished = 1;

  pthread_cond_signal(&condition);
  pthread_mutex_unlock(&mutex);

  return 0;
}

void CallAudioManager::stop() {
  running = 0;

  microphoneReader.stop();
  audioPlayer.stop();
  webRtcJitterBuffer.stop();

  pthread_mutex_lock(&mutex);
  while (finished == 0) {
    pthread_cond_wait(&condition, &mutex);
  }
  pthread_mutex_unlock(&mutex);

  usleep(40000); // Duration of microphone frame.
}

void CallAudioManager::setMute(int muteEnabled) {
  microphoneReader.setMute(muteEnabled);
}

static void constructSockAddr(JNIEnv *env, jstring serverIpString, jint serverPort,
                              struct sockaddr** result, int *resultLen)
{
  const char* serverIp    = env->GetStringUTFChars(serverIpString, 0);
  int         addressType = NetworkUtil::getAddressType(serverIp);

  if (addressType == 1) {
    struct sockaddr_in *sockAddr = (struct sockaddr_in*)malloc(sizeof(struct sockaddr_in));
    memset(sockAddr, 0, sizeof(struct sockaddr_in));

    sockAddr->sin_family = AF_INET;
    sockAddr->sin_port   = htons(serverPort);

    if (inet_aton(serverIp, &(sockAddr->sin_addr)) == 0) {
      __android_log_print(ANDROID_LOG_WARN, TAG, "Invalid address: %s", serverIp);
      free(sockAddr);
      sockAddr = NULL;
    }

    *result    = (struct sockaddr*)sockAddr;
    *resultLen = sizeof(struct sockaddr_in);
  } else if (addressType == 0) {
    struct sockaddr_in6 *sockAddr = (struct sockaddr_in6*)malloc(sizeof(struct sockaddr_in6));
    memset(sockAddr, 0, sizeof(struct sockaddr_in6));

    sockAddr->sin6_family = AF_INET6;
    sockAddr->sin6_port   = htons(serverPort);

    if (inet_pton(AF_INET6, serverIp, &(sockAddr->sin6_addr)) != 1) {
      __android_log_print(ANDROID_LOG_WARN, TAG, "Invalid IPv6 address: %s", serverIp);
      free(sockAddr);
      sockAddr = NULL;
    }

    *result    = (struct sockaddr*)sockAddr;
    *resultLen = sizeof(struct sockaddr_in6);
  } else {
    __android_log_print(ANDROID_LOG_WARN, TAG, "Unknown address type: %d", addressType);
    *result    = NULL;
    *resultLen = 0;
  }

  env->ReleaseStringUTFChars(serverIpString, serverIp);
}

static SrtpStreamParameters* constructSrtpStreamParameters(JNIEnv *env, jbyteArray cipherKey, jbyteArray macKey, jbyteArray salt) {
  uint8_t* cipherKeyBytes = (uint8_t*)env->GetByteArrayElements(cipherKey, 0);
  uint8_t* macKeyBytes    = (uint8_t*)env->GetByteArrayElements(macKey, 0);
  uint8_t* saltBytes      = (uint8_t*)env->GetByteArrayElements(salt, 0);

  SrtpStreamParameters *parameters = new SrtpStreamParameters(cipherKeyBytes, macKeyBytes, saltBytes);

  env->ReleaseByteArrayElements(cipherKey, (jbyte*)cipherKeyBytes, 0);
  env->ReleaseByteArrayElements(macKey, (jbyte*)macKeyBytes, 0);
  env->ReleaseByteArrayElements(salt, (jbyte*)saltBytes, 0);

  return parameters;
}

jlong JNICALL Java_org_thoughtcrime_redphone_audio_CallAudioManager_create
  (JNIEnv *env, jobject obj, jint androidSdkVersion,
   jint socketFd, jstring serverIpString, jint serverPort,
   jbyteArray senderCipherKey, jbyteArray senderMacKey, jbyteArray senderSalt,
   jbyteArray receiverCipherKey, jbyteArray receiverMacKey, jbyteArray receiverSalt)
{
  struct sockaddr *sockAddr;
  int sockAddrLen;

  constructSockAddr(env, serverIpString, serverPort, &sockAddr, &sockAddrLen);

  if (sockAddr == NULL) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "Failed to construct sockAddr!");
    env->ThrowNew(env->FindClass("org/thoughtcrime/redphone/audio/NativeAudioException"),
                                 "Failed to initialize native audio");
    return -1;
  }

  SrtpStreamParameters *senderParameters   = constructSrtpStreamParameters(env, senderCipherKey, senderMacKey, senderSalt);
  SrtpStreamParameters *receiverParameters = constructSrtpStreamParameters(env, receiverCipherKey, receiverMacKey, receiverSalt);

  CallAudioManager *manager = new CallAudioManager(androidSdkVersion, socketFd, sockAddr, sockAddrLen,
                                                   senderParameters, receiverParameters);

  if (manager->init() != 0) {
    delete manager;
    env->ThrowNew(env->FindClass("org/thoughtcrime/redphone/audio/NativeAudioException"),
                                 "Failed to initialize native audio");
    return -1;
  }

  return (jlong)manager;
}

void JNICALL Java_org_thoughtcrime_redphone_audio_CallAudioManager_start
(JNIEnv *env, jobject obj, jlong handle)
{
  CallAudioManager *manager = reinterpret_cast<CallAudioManager *>(handle);
  int              result   = manager->start();

  if (result == -1) {
    env->ThrowNew(env->FindClass("org/thoughtcrime/redphone/audio/NativeAudioException"),
                                 "Failed to start native audio");
  }
}

void JNICALL Java_org_thoughtcrime_redphone_audio_CallAudioManager_setMute
(JNIEnv *env, jobject obj, jlong handle, jboolean muteEnabled)
{
  CallAudioManager *manager = reinterpret_cast<CallAudioManager *>(handle);
  manager->setMute(muteEnabled);
}

void JNICALL Java_org_thoughtcrime_redphone_audio_CallAudioManager_stop
(JNIEnv *env, jobject obj, jlong handle)
{
  CallAudioManager *manager = reinterpret_cast<CallAudioManager*>(handle);
  manager->stop();
}

void JNICALL Java_org_thoughtcrime_redphone_audio_CallAudioManager_dispose
(JNIEnv *env, jobject obj, jlong handle)
{
  CallAudioManager *manager = reinterpret_cast<CallAudioManager*>(handle);
  delete manager;
}


