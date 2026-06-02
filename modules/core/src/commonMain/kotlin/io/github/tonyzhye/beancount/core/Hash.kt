package io.github.tonyzhye.beancount.core

/**
 * Platform-independent MD5 hash function.
 * JVM implementation uses java.security.MessageDigest.
 */
expect fun md5Hash(input: String): String
