package org.thoughtcrime.securesms.util.adapter.mapping

import androidx.viewbinding.ViewBinding

/**
 * A ViewHolder which is populated with a ViewBinding, used in conjunction with BindingFactory
 */
abstract class BindingViewHolder<T, B : ViewBinding>(protected val binding: B) : MappingViewHolder<T>(binding.root)
