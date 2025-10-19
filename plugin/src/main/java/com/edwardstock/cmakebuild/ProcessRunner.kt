package com.edwardstock.cmakebuild

import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Short wrapper for process execution results.
 */
data class ExecResult(
    val stdout: String,
    val stderr: String? = null,
    val exitCode: Int = 0
) {
    fun print() {
        if (stdout.isNotBlank()) println(stdout)
        stderr?.takeIf { it.isNotBlank() }?.let { System.err.println(it) }
    }

    fun isOk(): Boolean = exitCode == 0

    override fun toString(): String = "ExecResult(exitCode=$exitCode, stdout='${stdout.trim()}', stderr='${stderr?.trim()}')"

    fun throwIfFailed(prefix: String) {
        if (!isOk()) {
            print()
            throw CMakeException("$prefix: exitCode=$exitCode\n${this}")
        }
    }
}

internal class ProcessRunner(
    private val program: String,
    private val args: MutableList<String> = mutableListOf()
) {

    constructor(program: String, arg: String) : this(program, mutableListOf(arg))
    constructor(program: String, vararg arg: String) : this(program, mutableListOf(*arg))

    /**
     * Execute the command and stream output to stdout/stderr. Uses default timeout (60s).
     */
    operator fun invoke() {
        execCommand()
    }

    /**
     * Compatibility helper used across the codebase.
     */
    fun runWithTimeout(timeout: Duration): ExecResult = run(timeout)

    /**
     * Run the command and capture stdout/stderr. Default timeout is 60 seconds.
     */
    fun run(timeout: Duration = 60.seconds): ExecResult = runCommand(timeout)

    fun addArg(arg: String): ProcessRunner {
        args += arg
        return this
    }

    @Suppress("unused")
    fun addArgs(vararg arg: String): ProcessRunner {
        args += arg
        return this
    }

    fun addArgs(arg: List<String>): ProcessRunner {
        args += arg
        return this
    }

    operator fun plus(arg: String): ProcessRunner = addArg(arg)
    operator fun plus(arg: List<String>): ProcessRunner = addArgs(arg)

    private fun String.escapeIfRequired(): String = if (this.contains(" ")) "\"$this\"" else this

    /**
     * Execute and stream output to the provided Gradle logger. Throws on timeout or non-zero exit.
     */
    fun execStreaming(timeout: Duration, logger: org.gradle.api.logging.Logger) {
        val pb = ProcessBuilder(listOf(program) + args)
            .redirectErrorStream(true)
        val process = pb.start()

        val readerThread = Thread {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { logger.lifecycle("[CMake] $it") }
            }
        }
        readerThread.start()

        try {
            if (!process.waitFor(timeout.inWholeSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                readerThread.join()
                throw CMakeException("Process timed out: ${getCommand()}")
            }
        } catch (ie: InterruptedException) {
            process.destroyForcibly()
            Thread.currentThread().interrupt()
            throw CMakeException("Process interrupted: ${getCommand()}")
        }

        readerThread.join()

        if (process.exitValue() != 0) {
            throw CMakeException("Process failed (${process.exitValue()}): ${getCommand()}")
        }
    }

    private fun List<String>.prepare(): List<String> {
//        return if (OsCheck.operatingSystemType == OsCheck.OSType.Windows) {
//            (mutableListOf("cmd.exe", "/c") + listOf(program.escapeIfRequired()) + this)
//        } else {
        return (listOf(program) + this)
//        }
    }

    private fun getCommand(): String = args.prepare().joinToString(" ")

    private fun command(): List<String> = listOf(program) + args

    private fun runCommand(
        timeout: Duration = 60.seconds
    ): ExecResult {
        val pb = ProcessBuilder(command())
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)

        val process = pb.start()
        try {
            if (!process.waitFor(timeout.inWholeSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return ExecResult("", "Timed out: ${command().joinToString(" ")}", -1)
            }

            val out = process.inputStream.bufferedReader().use { it.readText() }
            val errText = process.errorStream.bufferedReader().use { it.readText() }.ifBlank { null }
            return ExecResult(out, errText, process.exitValue())
        } catch (ie: InterruptedException) {
            process.destroyForcibly()
            Thread.currentThread().interrupt()
            return ExecResult("", "Interrupted: ${command().joinToString(" ")}", -1)
        }
    }

    private fun execCommand(
        timeout: Duration = 60.seconds
    ) {
        val processBuilder = ProcessBuilder(args.prepare())
        val process = processBuilder.start()

        val stdoutThread = Thread {
            process.inputStream.bufferedReader().useLines { lines -> lines.forEach { println(it) } }
        }
        val stderrThread = Thread {
            process.errorStream.bufferedReader().useLines { lines -> lines.forEach { System.err.println(it) } }
        }

        stdoutThread.start()
        stderrThread.start()

        try {
            if (!process.waitFor(timeout.inWholeSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                stdoutThread.join()
                stderrThread.join()
                throw CMakeException("Process timed out: ${getCommand()}")
            }
        } catch (ie: InterruptedException) {
            process.destroyForcibly()
            Thread.currentThread().interrupt()
            throw CMakeException("Process interrupted: ${getCommand()}")
        }

        stdoutThread.join()
        stderrThread.join()

        if (process.exitValue() != 0) {
            throw CMakeException("Unable to execute process: exit code ${process.exitValue()}. See log for details.")
        }
    }
}
