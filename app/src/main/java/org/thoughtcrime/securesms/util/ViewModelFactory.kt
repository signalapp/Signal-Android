package org.thoughtcrime.securesms.util

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Simplifies [ViewModel] creation by providing default implementations of [ViewModelProvider.Factory]
 * and a factory producer that call through to a lambda to create the view model instance.
 *
 * Example use:
 *
 * private val viewModel: MyViewModel by viewModels(factoryProducer = ViewModelFactory.factoryProducer { MyViewModel(inputParams) })
 */
class ViewModelFactory<MODEL : ViewModel>(private val create: () -> MODEL) : ViewModelProvider.Factory {
  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    return create() as T
  }

  companion object {
    fun <MODEL : ViewModel> factoryProducer(create: () -> MODEL): () -> ViewModelProvider.Factory {
      return { ViewModelFactory(create) }
    }
  }
}
