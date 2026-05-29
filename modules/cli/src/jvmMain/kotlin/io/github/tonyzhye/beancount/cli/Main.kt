package io.github.tonyzhye.beancount.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands

/**
 * Main entry point for CLI.
 * Supports multiple commands: bean-check, bean-doctor, etc.
 */
class MainCommand : CliktCommand(
    name = "beancount",
    help = "Beancount CLI tools"
) {
    init {
        subcommands(
            BeanCheckCommand(),
            BeanDoctorCommand(),
            BeanExampleCommand(),
            BeanFormatCommand(),
            BeanQueryCommand()
        )
    }

    override fun run() {}
}

fun main(args: Array<String>) {
    MainCommand().main(args)
}
