package org.thoughtcrime.securesms.repository

import kotlinx.coroutines.CancellationException

sealed class ResultOf<out T> {

    data class Success<out R>(val value: R) : ResultOf<R>()

    data class Failure(val throwable: Throwable) : ResultOf<Nothing>()

    inline fun onFailure(block: (throwable: Throwable) -> Unit) = this.also {
        if (this is Failure) {
            block(throwable)
        }
    }

    inline fun onSuccess(block: (value: T) -> Unit) = this.also {
        if (this is Success) {
            block(value)
        }
    }

    inline fun <R> flatMap(mapper: (T) -> R): ResultOf<R> = when (this) {
        is Success -> wrap { mapper(value) }
        is Failure -> Failure(throwable)
    }

    fun getOrThrow(): T = when (this) {
        is Success -> value
        is Failure -> throw throwable
    }

    companion object {
        inline fun <T> wrap(block: () -> T): ResultOf<T> =
            try {
                Success(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Failure(e)
            }
    }
}
