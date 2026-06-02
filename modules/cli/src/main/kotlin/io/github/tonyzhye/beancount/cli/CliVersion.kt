package io.github.tonyzhye.beancount.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.parameters.options.eagerOption

const val BEANCOUNT_VERSION = "3.2.3"

/**
 * Adds a --version option that prints "Beancount <version>" and exits,
 * matching the behavior of Python beancount CLI.
 */
fun CliktCommand.beancountVersionOption() {
    eagerOption("--version") {
        throw PrintMessage("Beancount $BEANCOUNT_VERSION")
    }
}
