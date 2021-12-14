package com.edwardstock.cmakebuild

import java.io.BufferedReader
import java.io.InputStreamReader
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

    private fun runCommand(
        timeoutAmount: Long = 60,
        timeoutUnit: TimeUnit = TimeUnit.SECONDS
    ): ExecResult {
        val p = ProcessBuilder(args.prepare().also {
//            println("EXEC: \"${it.joinToString(" ")}\"")
        })

            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .redirectErrorStream(false)
            .start()

        val sb = StringBuilder()
        val sbError = StringBuilder()
        var line: String?
        BufferedReader(InputStreamReader(p.inputStream)).use { br ->
            while (br.readLine().also { line = it } != null) {
                line?.let {
                    sb.append(it).append('\n')
                }
            }
        }
        line = null
        BufferedReader(InputStreamReader(p.errorStream)).use { br ->
            while (br.readLine().also { line = it } != null) {
                line?.let {
                    sbError.append(it).append('\n')
                }
            }
        }

        p.waitFor(timeoutAmount, timeoutUnit)
        return ExecResult(
            sb.toString(),
            if (sbError.isEmpty()) null else sbError.toString(),
            p.exitValue()
        )
    }

    private fun execCommand(
        timeoutAmount: Long = 60,
        timeoutUnit: TimeUnit = TimeUnit.SECONDS
    ) {
        val processBuilder = ProcessBuilder(args.prepare().also {
//            println("EXEC: \"${it.joinToString(" ")}\"")
        })
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
