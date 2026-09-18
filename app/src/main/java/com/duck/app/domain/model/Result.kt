package com.duck.app.domain.model

/**
 * Типизированный Result из контракта §4.3: `Result<T, DuckError>`.
 */
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Failure(val error: DuckError) : Result<Nothing>()

    inline fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(data))
        is Failure -> this
    }

    inline fun <R> flatMap(transform: (T) -> Result<R>): Result<R> = when (this) {
        is Success -> transform(data)
        is Failure -> this
    }

    inline fun onFailure(action: (DuckError) -> Unit): Result<T> {
        if (this is Failure) action(error)
        return this
    }
}