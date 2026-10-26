package io.github.shoalapps.swordfish

/**
 * Needs YouTube to answer. CI skips these, because YouTube blocks data-centre addresses;
 * run them on a real network with `./gradlew connectedDebugAndroidTest`.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class NetworkTest
