package ro.smakeru.cgmglance.domain.time

/**
 * Source of the current wall clock time, in epoch milliseconds.
 *
 * Every piece of logic that reasons about time takes one of these instead of calling
 * `System.currentTimeMillis()`. That is not ceremony: freshness is the safety core of this app,
 * and logic that reads the clock directly can only be tested by waiting in real time, which
 * means in practice it is not tested at all.
 *
 * Deliberately not `kotlin.time.Clock`: this needs no dependency, no experimental opt-in, and
 * exactly one method.
 */
fun interface Clock {
    fun nowMillis(): Long
}
