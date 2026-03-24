/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.signal.core.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * An Effect to provide a result even between different screens
 *
 * The trailing lambda provides the result from a flow of results.
 *
 * @param resultEventBus the ResultEventBus to retrieve the result from. The default value
 * is read from the `LocalResultEventBus` composition local.
 * @param resultKey the key that should be associated with this effect
 * @param onResult the callback to invoke when a result is received
 */
@Composable
inline fun <reified T> ResultEffect(
  resultEventBus: ResultEventBus = LocalResultEventBus.current,
  resultKey: String = T::class.toString(),
  crossinline onResult: suspend (T) -> Unit
) {
  LaunchedEffect(resultKey, resultEventBus.channelMap[resultKey]) {
    resultEventBus.getResultFlow<T>(resultKey)?.collect { result ->
      onResult.invoke(result as T)
    }
  }
}
