package com.edwardstock.cmakebuild

import java.util.concurrent.TimeUnit

data class ExecResult(
    val stdout: String,
    val stderr: String? = null,
    val exitCode: Int = 0
) {
    fun print() {
        println(stdout)
        stderr?.let {
            System.err.println(stderr)
        }
    }

    fun isOk(): Boolean = exitCode == 0

    override fun toString(): String {
        return """ExecResult{
            |exitcode: $exitCode
            |stdout: $stdout
            |stderr: $stderr
            |}""".trimMargin()
    }


    fun throwIfFailed(prefix: String) {
        if (!isOk()) {
            print() // keep your printing behavior
            throw CMakeException("$prefix: exitCode=$exitCode")
        }
    }
}


internal class ProcessRunner(
    private val program: String,
    private val args: MutableList<String> = ArrayList()
) {

    constructor(program: String, arg: String) : this(program, mutableListOf(arg))
    constructor(program: String, vararg arg: String) : this(program, mutableListOf(*arg))

    operator fun invoke() {
        execCommand()
    }

    fun runWithTimeout(timeoutSeconds: Long): ExecResult {
        return run(timeoutSeconds)
    }

    fun run(timeoutSeconds: Long = 60): ExecResult {
        return runCommand(timeoutSeconds, TimeUnit.SECONDS)
    }

    fun run(): ExecResult {
        return runCommand()
    }

    fun addArg(arg: String): ProcessRunner {
        args += arg
        return this
    }

    fun addArgs(vararg arg: String): ProcessRunner {
        args += listOf(*arg)
        return this
    }

    fun addArgs(arg: List<String>): ProcessRunner {
        args += arg
        return this
    }

    operator fun plus(arg: String): ProcessRunner {
        return addArg(arg)
    }

    operator fun plus(arg: List<String>): ProcessRunner {
        return addArgs(arg)
    }

    private fun String.escapeIfRequired(): String {
        return if (this.contains(" ")) {
            String.format("\"%s\"", this)
        } else {
            this
        }

    }

    fun execStreaming(timeoutSeconds: Long, logger: org.gradle.api.logging.Logger) {
        val pb = ProcessBuilder(listOf(program) + args)
            .redirectErrorStream(true)
        val p = pb.start()
        val reader = p.inputStream.bufferedReader()
        val t = Thread {
            reader.useLines { lines ->
                lines.forEach { logger.lifecycle("[CMake] $it") } // or logger.info(...)
            }
        }
        t.start()
        if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            p.destroyForcibly()
            t.join()
            throw CMakeException("Process timed out: ${getCommand()}")
        }
        t.join()
        if (p.exitValue() != 0) {
            throw CMakeException("Process failed (${p.exitValue()}): ${getCommand()}")
        }
    }

    private fun List<String>.prepare(): List<String> {
        return (if (OsCheck.operatingSystemType == OsCheck.OSType.Windows) {
            (mutableListOf("cmd.exe", "/c") + listOf(program.escapeIfRequired()) + this)
        } else {
            (listOf(program) + this)
        })
    }

    fun getCommand(): String {
        return args.prepare().joinToString(" ")
    }

    private fun command(): List<String> = listOf(program) + args

    private fun runCommand(
        timeoutAmount: Long = 60,
        timeoutUnit: TimeUnit = TimeUnit.SECONDS
    ): ExecResult {
        val pb = ProcessBuilder(command())
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)

        val p = pb.start()
        if (!p.waitFor(timeoutAmount, timeoutUnit)) {
            p.destroyForcibly()
            return ExecResult("", "Timed out: ${command().joinToString(" ")}", -1)
        }
        val out = p.inputStream.bufferedReader().readText()
        val err = p.errorStream.bufferedReader().readText().ifBlank { null }
        return ExecResult(out, err, p.exitValue())
    }

    private fun execCommand(
        timeoutAmount: Long = 60,
        timeoutUnit: TimeUnit = TimeUnit.SECONDS
    ) {
        val processBuilder = ProcessBuilder(args.prepare())
        val process = processBuilder.start()
        val threadStdout = Thread(processReader(process.inputStream, System.out))
        val threadStderr = Thread(processReader(process.errorStream, System.err))
        threadStdout.start()
        threadStderr.start()
        process.waitFor(timeoutAmount, timeoutUnit)
        threadStdout.join()
        threadStderr.join()
        if (process.exitValue() != 0) {
            throw CMakeException("Unable to execute process: exit code ${process.exitValue()}. See log for details.")
        }
    }
}
