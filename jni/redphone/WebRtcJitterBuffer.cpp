#include "WebRtcJitterBuffer.h"
#include <time.h>

#define TAG "WebRtcJitterBuffer"

static volatile int running = 0;

WebRtcJitterBuffer::WebRtcJitterBuffer(AudioCodec &codec) :
  neteq(NULL), webRtcCodec(codec)
{
  running = 1;
}

int WebRtcJitterBuffer::init() {
  webrtc::NetEq::Config config;
  config.sample_rate_hz = 8000;

  pthread_mutex_lock(&lock);
  neteq = webrtc::NetEq::Create(config);
  pthread_mutex_unlock(&lock);

  if (neteq == NULL) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "Failed to construct NetEq!");
    return -1;
  }

  if (neteq->RegisterExternalDecoder(&webRtcCodec, webrtc::kDecoderPCMu, 0) != 0) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "Failed to register external codec!");
    return -1;
  }

//  pthread_create(&stats, NULL, &WebRtcJitterBuffer::collectStats, this);

  return 0;
}

WebRtcJitterBuffer::~WebRtcJitterBuffer() {
  if (neteq != NULL) {
    delete neteq;
  }
}

void WebRtcJitterBuffer::addAudio(RtpPacket *packet, uint32_t tick) {
  webrtc::WebRtcRTPHeader header;
  header.header.payloadType    = packet->getPayloadType();
  header.header.sequenceNumber = packet->getSequenceNumber();
  header.header.timestamp      = packet->getTimestamp();
  header.header.ssrc           = packet->getSsrc();

  uint8_t *payload = (uint8_t*)malloc(packet->getPayloadLen());
  memcpy(payload, packet->getPayload(), packet->getPayloadLen());

  if (neteq->InsertPacket(header, payload, packet->getPayloadLen(), tick) != 0) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "neteq->InsertPacket() failed!");
  }
}

int WebRtcJitterBuffer::getAudio(short *rawData, int maxRawData) {
  int samplesPerChannel = 0;
  int numChannels       = 0;

  if (neteq->GetAudio(maxRawData, rawData, &samplesPerChannel, &numChannels, NULL) != 0) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "neteq->GetAudio() failed!");
  }

  return samplesPerChannel;
}

void WebRtcJitterBuffer::stop() {
//  pthread_mutex_lock(&lock);
  running = 0;
//  pthread_cond_signal(&condition);
//  pthread_mutex_unlock(&lock);

//  pthread_join(stats, NULL);
}

void WebRtcJitterBuffer::collectStats() {
  while (running) {
    webrtc::NetEqNetworkStatistics stats;

    pthread_mutex_lock(&lock);
    neteq->NetworkStatistics(&stats);
    pthread_mutex_unlock(&lock);

    __android_log_print(ANDROID_LOG_WARN, "WebRtcJitterBuffer",
                        "Jitter Stats:\n{\n" \
                        "  current_buffer_size_ms:   %d,\n" \
                        "  preferred_buffer_size_ms: %d\n" \
                        "  jitter_peaks_found:       %d\n" \
                        "  packet_loss_rate:         %d\n" \
                        "  packet_discard_rate:      %d\n" \
                        "  expand_rate:              %d\n" \
                        "  preemptive_rate:          %d\n" \
                        "  accelerate_rate:          %d\n" \
                        "  clockdrift_ppm:           %d\n" \
                        "  added_zero_samples:       %d\n" \
                        "}",
                        stats.current_buffer_size_ms,
                        stats.preferred_buffer_size_ms,
                        stats.jitter_peaks_found,
                        stats.packet_loss_rate,
                        stats.packet_discard_rate,
                        stats.expand_rate,
                        stats.preemptive_rate,
                        stats.accelerate_rate,
                        stats.clockdrift_ppm,
                        stats.added_zero_samples);

    struct timespec timeToWait;
    struct timeval  now;
    gettimeofday(&now, NULL);

    timeToWait.tv_sec  = now.tv_sec;
    timeToWait.tv_nsec = now.tv_usec * 1000;
    timeToWait.tv_sec += 30;

    pthread_mutex_lock(&lock);

    if (running) {
      pthread_cond_timedwait(&condition, &lock, &timeToWait);
    }

    pthread_mutex_unlock(&lock);
  }
}

void* WebRtcJitterBuffer::collectStats(void *context) {
  WebRtcJitterBuffer* jitterBuffer = static_cast<WebRtcJitterBuffer*>(context);
  jitterBuffer->collectStats();

  return 0;
}

