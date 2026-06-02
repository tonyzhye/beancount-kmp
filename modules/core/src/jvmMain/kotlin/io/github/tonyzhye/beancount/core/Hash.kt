package io.github.tonyzhye.beancount.core

import java.security.MessageDigest

/**
 * JVM implementation of MD5 hash using java.security.MessageDigest.
 */
actual fun md5Hash(input: String): String {
    val md = MessageDigest.getInstance("MD5")
    val digest = md.digest(input.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}
