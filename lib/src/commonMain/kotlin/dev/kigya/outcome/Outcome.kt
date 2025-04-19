package dev.kigya.outcome

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * A lightweight container of a computation result that can be either a [Success] with a [Value]
 * or a [Failure] with an [Error].
 */
public sealed interface Outcome<out Error, out Value> {

    /** Represents a successful outcome holding a [Value]. */
    public data class Success<Value>(val value: Value) : Outcome<Nothing, Value>

    /** Represents a failed outcome holding an [Error]. */
    public data class Failure<Error>(val error: Error) : Outcome<Error, Nothing>

    public companion object {
        /** Wraps a successful [value] in an [Outcome]. */
        public fun <Value> success(value: Value): Outcome<Nothing, Value> = Success(value)

        /** Wraps an [error] in an [Outcome]. */
        public fun <Error> failure(error: Error): Outcome<Error, Nothing> = Failure(error)
    }
}

// ─── Retrieval helpers ──────────────────────────────────────────────────────

/** Returns the [Value] if this is [Success], or `null` otherwise. */
public fun <Error, Value> Outcome<Error, Value>.getOrNull(): Value? =
    (this as? Outcome.Success)?.value

/** Returns the [Error] if this is [Failure], or `null` otherwise. */
public fun <Error, Value> Outcome<Error, Value>.errorOrNull(): Error? =
    (this as? Outcome.Failure)?.error

/**
 * Returns the [Value] if [Outcome.Success], or computes an alternative via [onFailure].
 *
 * ```kotlin
 * // Define your error model
 * sealed interface AppError {
 *   object NotFound: AppError
 *   object Timeout: AppError
 * }
 *
 * // Success example
 * val ok: Outcome<AppError, String> = Outcome.success("Hello")
 * val greeting = ok.getOrElse { error ->
 *   // This branch will never run for this instance
 *   when (error) {
 *     AppError.NotFound -> "NotFound"
 *     AppError.Timeout  -> "Timeout"
 *   }
 * }
 * println(greeting)  // Hello
 *
 * // Failure example
 * val missing: Outcome<AppError, String> = Outcome.failure(AppError.NotFound)
 * val fallback = missing.getOrElse { error ->
 *   when (error) {
 *     AppError.NotFound -> println("Resource not found")
 *     AppError.Timeout  -> println("Request timed out")
 *   }
 *   "<default>"
 * }
 * println(fallback)  // <default>
 * ```
 */
public inline fun <Error, Value> Outcome<Error, Value>.getOrElse(
    onFailure: (Error) -> Value,
): Value = when (this) {
    is Outcome.Success -> value
    is Outcome.Failure -> onFailure(error)
}

/**
 * Returns the [Value] if [Outcome.Success], or [defaultValue] if [Outcome.Failure].
 *
 * ```kotlin
 * val x: Outcome<String, Int> = Outcome.failure("Oops")
 * println(x.getOrDefault(0)) // 0
 *
 * val y: Outcome<String, Int> = Outcome.success(5)
 * println(y.getOrDefault(0)) // 5
 * ```
 */
public fun <Error, Value> Outcome<Error, Value>.getOrDefault(
    defaultValue: Value,
): Value = if (this is Outcome.Success) value else defaultValue

// ─── Observers ──────────────────────────────────────────────────────────────

/**
 * Invoke one of the handlers based on state, then return the original [Outcome].
 *
 * ```kotlin
 * sealed interface AppError { object BadRequest: AppError }
 *
 * // On Success
 * val r1 = Outcome.success<AppError, Int>(42)
 * r1.fold(
 *   onFailure = { println("Error: $it") },
 *   onSuccess = { println("Value: $it") }
 * ) // prints: Value: 42
 *
 * // On Failure
 * val r2 = Outcome.failure<AppError, Int>(AppError.BadRequest)
 * r2.fold(
 *   onFailure = { println("Error: $it") },
 *   onSuccess = { println("Value: $it") }
 * ) // prints: Error: BadRequest
 * ```
 */
public inline fun <Error, Value> Outcome<Error, Value>.fold(
    onFailure: (Error) -> Unit = {},
    onSuccess: (Value) -> Unit = {},
): Outcome<Error, Value> = when (this) {
    is Outcome.Success -> {
        onSuccess(value); this
    }

    is Outcome.Failure -> {
        onFailure(error); this
    }
}

/**
 * Perform [action] if this is [Outcome.Success].
 *
 * ```kotlin
 * val r = Outcome.success<Nothing, String>("OK")
 * r.onSuccess { println("Got: $it") } // Got: OK
 * ```
 */
public inline fun <Error, Value> Outcome<Error, Value>.onSuccess(
    action: (Value) -> Unit,
): Outcome<Error, Value> = apply {
    if (this is Outcome.Success) action(value)
}

/**
 * Perform [action] if this is [Outcome.Failure].
 *
 * ```kotlin
 * val r = Outcome.failure<String, Int>("Fail")
 * r.onFailure { println("Error: $it") } // Error: Fail
 * ```
 */
public inline fun <Error, Value> Outcome<Error, Value>.onFailure(
    action: (Error) -> Unit,
): Outcome<Error, Value> = apply {
    if (this is Outcome.Failure) action(error)
}

// ─── Transformation ─────────────────────────────────────────────────────────

/**
 * Transform the successful [Value] into [NewValue], preserving any [Error].
 *
 * ```kotlin
 * val r = Outcome.success<Nothing, Int>(2)
 * val doubled = r.mapSuccess { it * 2 }
 * println(doubled) // Success(value=4)
 * ```
 */
public inline fun <Error, Value, NewValue> Outcome<Error, Value>.mapSuccess(
    transform: (Value) -> NewValue,
): Outcome<Error, NewValue> = when (this) {
    is Outcome.Success -> Outcome.success(transform(value))
    is Outcome.Failure -> this
}

/**
 * Transform the [Error] into [NewError], preserving any [Value].
 *
 * ```kotlin
 * val r: Outcome<Int, String> = Outcome.failure(404)
 * val mapped = r.mapError { code -> "Error code: $code" }
 * println(mapped) // Failure(error=Error code: 404)
 * ```
 */
public inline fun <Error, Value, NewError> Outcome<Error, Value>.mapError(
    transform: (Error) -> NewError,
): Outcome<NewError, Value> = when (this) {
    is Outcome.Success -> this
    is Outcome.Failure -> Outcome.failure(transform(error))
}

// ─── Exception handling ─────────────────────────────────────────────────────

/**
 * Execute [block] and wrap its result or exception in an [Outcome].
 *
 * Example:
 * ```kotlin
 * // Simple safe parse
 * val success = outcomeCatching { "123".toInt() }
 * success.onSuccess { println("Parsed: $it") }
 *        .onFailure { println("Error: ${it::class.simpleName}") }
 * // Output: Parsed: 123
 *
 * // Failure case
 * val failure = outcomeCatching { "NaN".toInt() }
 * failure.onSuccess { println("Parsed: $it") }
 *        .onFailure { println("Error: ${it::class.simpleName}") }
 * // Output: Error: NumberFormatException
 * ```
 */
public inline fun <Value> outcomeCatching(
    block: () -> Value,
): Outcome<Throwable, Value> = try {
    Outcome.success(block())
} catch (t: Throwable) {
    Outcome.failure(t)
}

/**
 * Execute [block], mapping any exception via [mapError] into a custom error type.
 *
 * Example:
 * ```kotlin
 * sealed interface AppError {
 *   object IoError : AppError
 *   data class Other(val cause: Throwable) : AppError
 * }
 *
 * val result = outcomeCatching(
 *   mapError = { t ->
 *     if (t is IOException) AppError.IoError else AppError.Other(t)
 *   }
 * ) {
 *   throw IOException("Disk full")
 * }
 *
 * result.onFailure { println("Mapped error: $it") }
 * // Output: Mapped error: IoError
 * ```
 */
public inline fun <Error, Value> outcomeCatching(
    mapError: (Throwable) -> Error,
    block: () -> Value,
): Outcome<Error, Value> = try {
    Outcome.success(block())
} catch (c: CancellationException) {
    throw c
} catch (t: Throwable) {
    Outcome.failure(mapError(t))
}

/**
 * Execute suspend [block] and wrap its result or exception in an [Outcome].
 *
 * Example:
 * ```kotlin
 * suspend fun mayTimeout(): String {
 *   throw TimeoutCancellationException("timed out")
 * }
 *
 * val r = runBlocking {
 *   outcomeSuspendCatching { mayTimeout() }
 * }
 *
 * r.onFailure { println("Timeout: ${it.message}") }
 * // Output: Timeout: timed out
 * ```
 */
public suspend inline fun <Value> outcomeSuspendCatching(
    block: suspend () -> Value,
): Outcome<Throwable, Value> = try {
    Outcome.success(block())
} catch (t: TimeoutCancellationException) {
    Outcome.failure(t)
} catch (c: CancellationException) {
    throw c
} catch (t: Throwable) {
    Outcome.failure(t)
}

/**
 * Execute suspend [block] and map exceptions via [mapError] into a custom error.
 *
 * Example:
 * ```kotlin
 * sealed interface AppError { object Timeout : AppError; data class Other(val t: Throwable) : AppError }
 *
 * runBlocking {
 *   val r = outcomeSuspendCatching(
 *     mapError = { t -> if (t is TimeoutCancellationException) AppError.Timeout else AppError.Other(t) }
 *   ) {
 *     error("boom")
 *   }
 *   r.onFailure { println("Mapped: $it") }
 *   // Output: Mapped: Other(java.lang.IllegalStateException: boom)
 * }
 * ```
 */
public suspend inline fun <Error, Value> outcomeSuspendCatching(
    crossinline mapError: (Throwable) -> Error,
    crossinline block: suspend () -> Value,
): Outcome<Error, Value> = try {
    Outcome.success(block())
} catch (t: TimeoutCancellationException) {
    Outcome.failure(mapError(t))
} catch (c: CancellationException) {
    throw c
} catch (t: Throwable) {
    Outcome.failure(mapError(t))
}

/**
 * Execute suspend [block] on [dispatcher] and wrap its result or exception in an [Outcome].
 *
 * Example:
 * ```kotlin
 * fun heavyComputation(): Int = 7 * 6
 *
 * runBlocking {
 *   val r = outcomeSuspendCatchingOn(Dispatchers.Default) { heavyComputation() }
 *   r.onSuccess { println("Result: $it") }
 *   // Output: Result: 42
 * }
 * ```
 */
public suspend inline fun <Value> outcomeSuspendCatchingOn(
    dispatcher: CoroutineDispatcher,
    crossinline block: suspend () -> Value,
): Outcome<Throwable, Value> = try {
    val result = withContext(dispatcher) { block() }
    Outcome.success(result)
} catch (t: TimeoutCancellationException) {
    Outcome.failure(t)
} catch (c: CancellationException) {
    throw c
} catch (t: Throwable) {
    Outcome.failure(t)
}

/**
 * Execute suspend [block] on [dispatcher] and map exceptions via [mapError] into a custom error.
 *
 * Example:
 * ```kotlin
 * sealed interface AppError { data class Other(val t: Throwable) : AppError }
 *
 * runBlocking {
 *   val r = outcomeSuspendCatchingOn(
 *     Dispatchers.IO,
 *     mapError = { t -> AppError.Other(t) }
 *   ) { error("fail") }
 *
 *   r.onFailure { println("Got: $it") }
 *   // Output: Got: Other(java.lang.IllegalStateException: fail)
 * }
 * ```
 */
public suspend inline fun <Error, Value> outcomeSuspendCatchingOn(
    dispatcher: CoroutineDispatcher,
    crossinline mapError: (Throwable) -> Error,
    crossinline block: suspend () -> Value,
): Outcome<Error, Value> = try {
    val result = withContext(dispatcher) { block() }
    Outcome.success(result)
} catch (t: TimeoutCancellationException) {
    Outcome.failure(mapError(t))
} catch (c: CancellationException) {
    throw c
} catch (t: Throwable) {
    Outcome.failure(mapError(t))
}
