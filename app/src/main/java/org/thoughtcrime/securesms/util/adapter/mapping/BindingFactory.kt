package org.thoughtcrime.securesms.util.adapter.mapping

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding

/**
 * Allows ViewHolders to be generated with a ViewBinding. Intended usage is as follows:
 *
 * BindingFactory(::MyBindingViewHolder, MyBinding::inflate)
 */
class BindingFactory<T : MappingModel<T>, B : ViewBinding>(
  private val creator: (B) -> BindingViewHolder<T, B>,
  private val inflater: (LayoutInflater, ViewGroup, Boolean) -> B
) : Factory<T> {
  override fun createViewHolder(parent: ViewGroup): MappingViewHolder<T> {
    val binding = inflater(LayoutInflater.from(parent.context), parent, false)
    return creator(binding)
  }
}
