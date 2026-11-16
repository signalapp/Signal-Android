/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.billing

import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.PurchasesUpdatedListener
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.signal.core.util.billing.BillingError
import org.signal.core.util.logging.Log
import java.util.concurrent.Executors

/**
 * Owns a [BillingClient] and its connection lifecycle, so callers can just run work once the client is ready.
 */
internal class BillingClientConnection(
  context: Context,
  listener: PurchasesUpdatedListener
) {

  companion object {
    private val TAG = Log.tag(BillingClientConnection::class)
  }

  val client: BillingClient = BillingClient.newBuilder(context)
    .setListener(listener)
    .enablePendingPurchases(
      PendingPurchasesParams.newBuilder()
        .enableOneTimeProducts()
        .build()
    )
    .build()

  private val connectionState = MutableStateFlow<State>(State.Init)
  private val connectionStateDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

  /**
   * Runs [block] once the billing client is connected.
   *
   * @throws BillingError if the connection could not be established.
   */
  suspend fun <T> withConnection(caller: String, block: suspend () -> T): T {
    Log.d(TAG, "Awaiting connection from $caller... (current state: ${connectionState.value})", true)
    startConnectionIfNecessary()

    val state = connectionState
      .filter { it == State.Connected || it is State.Failure }
      .first()

    Log.d(TAG, "Handling block from $caller.. (current state: ${connectionState.value})", true)
    return when (state) {
      State.Connected -> block()
      is State.Failure -> throw state.billingError
      else -> error("Unexpected state: $state")
    }
  }

  /**
   * Releases the Play connection and shuts down the state dispatcher's thread.
   *
   * The state is moved to [State.Failure] first, so a caller that is already awaiting a connection -- or that arrives
   * after this -- fails with a [BillingError] rather than suspending forever on a client that can never connect again.
   */
  fun close() {
    client.endConnection()
    connectionState.update { State.Failure(BillingError(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)) }
    connectionStateDispatcher.close()
  }

  private suspend fun startConnectionIfNecessary() {
    withContext(connectionStateDispatcher) {
      when (client.connectionState) {
        BillingClient.ConnectionState.DISCONNECTED -> {
          Log.d(TAG, "BillingClient is disconnected. Starting connection attempt.", true)
          connectionState.update { State.Connecting }
          client.startConnection(
            BillingListener(
              onStateUpdate = { new ->
                connectionState.update { old ->
                  Log.d(TAG, "Moving from state $old -> $new", true)
                  new
                }
              }
            )
          )
        }

        BillingClient.ConnectionState.CONNECTING -> {
          Log.d(TAG, "BillingClient is already connecting. Nothing to do.", true)
        }

        BillingClient.ConnectionState.CONNECTED -> {
          Log.d(TAG, "BillingClient is already connected. Nothing to do.", true)
        }

        BillingClient.ConnectionState.CLOSED -> {
          Log.w(TAG, "BillingClient was permanently closed. Cannot proceed.", true)
        }
      }
    }
  }

  private class BillingListener(
    private val onStateUpdate: (State) -> Unit
  ) : BillingClientStateListener {
    override fun onBillingServiceDisconnected() {
      Log.d(TAG, "BillingListener#onBillingServiceDisconnected", true)
      onStateUpdate(State.Disconnected)
    }

    override fun onBillingSetupFinished(billingResult: BillingResult) {
      Log.d(TAG, "BillingListener#onBillingSetupFinished: ${billingResult.responseCode}", true)
      if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
        Log.d(TAG, "BillingListener#onBillingSetupFinished: ready", true)
        onStateUpdate(State.Connected)
      } else {
        Log.d(TAG, "BillingListener#onBillingSetupFinished: failure", true)
        onStateUpdate(State.Failure(BillingError(billingResponseCode = billingResult.responseCode)))
      }
    }
  }

  private sealed interface State {
    data object Init : State
    data object Connecting : State
    data object Connected : State
    data object Disconnected : State
    data class Failure(val billingError: BillingError) : State
  }
}
