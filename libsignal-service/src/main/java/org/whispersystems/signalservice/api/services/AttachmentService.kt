package org.whispersystems.signalservice.api.services

import io.reactivex.rxjava3.core.Single
import org.whispersystems.signalservice.api.SignalWebSocket
import org.whispersystems.signalservice.internal.ServiceResponse
import org.whispersystems.signalservice.internal.ServiceResponseProcessor
import org.whispersystems.signalservice.internal.push.AttachmentUploadForm
import org.whispersystems.signalservice.internal.websocket.DefaultResponseMapper
import org.whispersystems.signalservice.internal.websocket.WebSocketRequestMessage
import org.whispersystems.signalservice.internal.websocket.WebsocketResponse
import java.security.SecureRandom

/**
 * Provide WebSocket based interface to attachment upload endpoints.
 *
 * Note: To be expanded to have REST fallback and other attachment related operations.
 */
class AttachmentService(private val signalWebSocket: SignalWebSocket) {
  fun getAttachmentV4UploadAttributes(): Single<ServiceResponse<AttachmentUploadForm>> {
    val requestMessage = WebSocketRequestMessage(
      id = SecureRandom().nextLong(),
      verb = "GET",
      path = "/v4/attachments/form/upload"
    )

    return signalWebSocket.request(requestMessage)
      .map { response: WebsocketResponse? -> DefaultResponseMapper.getDefault(AttachmentUploadForm::class.java).map(response) }
      .onErrorReturn { throwable: Throwable? -> ServiceResponse.forUnknownError(throwable) }
  }

  class AttachmentAttributesResponseProcessor<T>(response: ServiceResponse<T>) : ServiceResponseProcessor<T>(response)
}
