/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.util.adapter.mapping.compose

import android.content.Context
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.toPersistentHashMap
import kotlinx.coroutines.flow.distinctUntilChanged
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.paging.PagingController
import org.signal.paging.ProxyPagingController
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.SettingHeader
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder

data class MappingEntry<T>(
  val key: ((T) -> Any)? = null,
  val content: @Composable LazyItemScope.(T) -> Unit
)

typealias MappingEntryProvider<T> = PersistentMap<Class<out T>, MappingEntry<out T>>

@Composable
fun <T : Any> MappingLazyColumn(
  controller: MappingLazyListController<T>,
  modifier: Modifier = Modifier,
  lazyListState: LazyListState = rememberLazyListState(),
  userScrollEnabled: Boolean = true
) {
  val items = controller.items

  LazyColumn(
    state = lazyListState,
    userScrollEnabled = userScrollEnabled,
    modifier = modifier
  ) {
    insertProvidedItems(items, controller.entryProvider, controller.placeholder)
  }

  PagerEffect(controller, lazyListState)
}

@Composable
fun <T : Any> MappingLazyRow(
  controller: MappingLazyListController<T>,
  modifier: Modifier = Modifier,
  lazyListState: LazyListState = rememberLazyListState()
) {
  val items = controller.items

  LazyRow(
    state = lazyListState,
    modifier = modifier
  ) {
    insertProvidedItems(items, controller.entryProvider, controller.placeholder)
  }

  PagerEffect(controller, lazyListState)
}

private fun <T : Any> LazyListScope.insertProvidedItems(
  items: List<T?>,
  provider: MappingEntryProvider<T>,
  placeholder: @Composable () -> Unit
) {
  itemsIndexed(
    items = items,
    contentType = { _, model -> model?.javaClass },
    key = { index, model ->
      if (model == null) {
        index
      } else {
        @Suppress("UNCHECKED_CAST")
        val entry = provider[model.javaClass] as MappingEntry<T>
        entry.key?.invoke(model) ?: model.javaClass
      }
    }
  ) { _, model ->
    if (model == null) {
      placeholder()
    } else {
      @Suppress("UNCHECKED_CAST")
      val entry = provider[model.javaClass] as MappingEntry<T>
      with(entry) {
        content(model)
      }
    }
  }
}

@Composable
private fun <T : Any> PagerEffect(controller: MappingLazyListController<T>, lazyListState: LazyListState) {
  LaunchedEffect(controller, lazyListState) {
    snapshotFlow { lazyListState.layoutInfo.visibleItemsInfo.map { it.index } }
      .distinctUntilChanged()
      .collect { indices ->
        indices.forEach { index ->
          controller.pagingController.onDataNeededAroundIndex(index)
        }
      }
  }
}

@Composable
fun <T : Any> rememberMappingEntryProvider(
  builderFn: MappingEntryProviderBuilder<T>.() -> Unit
): MappingEntryProvider<T> {
  return remember {
    MappingEntryProviderBuilder<T>().apply {
      builderFn()
    }.build()
  }
}

@DayNightPreviews
@Composable
private fun MappingLazyColumnPreview() {
  Previews.Preview {
    val provider = rememberMappingEntryProvider<Any> {
      entry<String> {
        Text(text = "String $it")
      }

      entry<Int> {
        Text(text = "Int $it")
      }

      viewHolder<SettingHeader.Item> {
        LayoutFactory(
          { view -> SettingHeader.ViewHolder(view) },
          R.layout.base_settings_header_item
        ).createViewHolder(FrameLayout(it))
      }
    }

    val controller = remember(provider) {
      MappingLazyListController(provider).apply {
        items = listOf("A", "B", "C", 1, 2, 3, SettingHeader.Item("SettingHeader.Item"))
      }
    }

    MappingLazyColumn(
      controller = controller,
      modifier = Modifier.fillMaxSize()
    )
  }
}

class MappingEntryProviderBuilder<T : Any> {
  val map: MutableMap<Class<out T>, MappingEntry<out T>> = hashMapOf()

  inline fun <reified R : T> entry(noinline key: ((R) -> Any)? = null, noinline content: @Composable (R) -> Unit) {
    map[R::class.java] = MappingEntry(key = key) { model -> content(model) }
  }

  inline fun <reified R : T> provider(entryProvider: MappingEntryProvider<R>) {
    map.putAll(entryProvider)
  }

  inline fun <reified R : T> viewHolder(noinline key: ((R) -> Any)? = null, crossinline createViewHolder: (Context) -> MappingViewHolder<R>) {
    entry<R>(key = key, content = { model ->
      var viewHolder: MappingViewHolder<R>? by remember { mutableStateOf(null) }

      AndroidView(
        factory = {
          val holder = createViewHolder(it)
          viewHolder = holder
          holder.itemView
        },
        update = {
          viewHolder?.bind(model)
        }
      )
    })
  }

  fun build(): MappingEntryProvider<T> {
    return map.toPersistentHashMap()
  }
}

@Stable
class MappingLazyListController<T : Any>(
  val entryProvider: MappingEntryProvider<T>,
  val placeholder: @Composable () -> Unit = { Spacer(Modifier.height(100.dp)) }
) {

  private val proxyController = ProxyPagingController<Any>()

  var pagingController: PagingController<*>
    get() = proxyController
    set(value) {
      @Suppress("UNCHECKED_CAST")
      proxyController.set(value as PagingController<Any>)
    }

  var items: List<T?> by mutableStateOf(emptyList())
}
