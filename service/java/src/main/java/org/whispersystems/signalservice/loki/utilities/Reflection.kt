package org.whispersystems.signalservice.loki.utilities

import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

@Suppress("UNCHECKED_CAST")
fun <T : Any, U> T.getProperty(name: String): U {
    val p = this::class.memberProperties.first { it.name == name } as KProperty1<T, U>
    return p.get(this)
}
