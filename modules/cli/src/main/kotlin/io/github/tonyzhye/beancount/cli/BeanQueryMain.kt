package io.github.tonyzhye.beancount.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands

/**
 * Main entry point for beanquery CLI.
 * Provides BQL (Beancount Query Language) interactive and batch query tools.
 */
class BeanQueryMainCommand : CliktCommand(
    name = "beanquery",
    help = "Beancount Query Language CLI"
) {
    init {
        subcommands(
            BeanQueryCommand()
        )
    }

    override fun run() {}
}

fun main(args: Array<String>) {
    BeanQueryMainCommand().main(args)
}
