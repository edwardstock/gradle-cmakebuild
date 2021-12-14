package com.edwardstock.cmakebuild

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PrintStream
import java.util.*

internal fun String.escape(): String {
    return String.format("\"%s\"", this)
}

internal fun isNotWindows(): Boolean = OsCheck.operatingSystemType != OsCheck.OSType.Windows

internal fun findNinjaBuild(): String {
    val result = ProcessRunner(
        "where.exe", mutableListOf(
            "/F",
            "/R", "c:\\Program Files".escape(),
            "ninja.exe"
        )
    ).run()

    if (result.isOk()) {
        println("RESULT of find cmake: ${result.stdout}")

        if (result.stdout.contains("\"")) {
            return result.stdout
                .split("\n")
                .first()
                .replace("\"", "")
                .also {
                    println("Ninja binary: $it")
                }
        }
    }
    throw CMakeException("Unable to find Ninja binary: [${result.exitCode}] ${result.stderr}")
}

internal fun findCMakeBin(): String {

    if (isNotWindows()) {
        val result = ProcessRunner("which", "cmake").run()
        if (!result.isOk() || result.stdout.isBlank()) {
            throw CMakeException("Unable to find cmake binary: [${result.exitCode}] ${result.stderr}")
        }
        return result.stdout.trim()
    } else {
        val result = ProcessRunner(
            "where.exe",
            "/F",
            "/R", "c:\\Program Files".escape(),
            "cmake.exe"
        ).run()

        println("RESULT of find cmake: ${result.stdout}")

        if (result.stdout.contains("\"")) {
            return result.stdout
                .split("\n")
                .first()
                .replace("\"", "")
                .also {
                    println("CMake binary: $it")
                }
        }

        throw CMakeException("Unable to find cmake binary: [${result.exitCode}] ${result.stderr}")
    }
}

internal fun List<String>.toOsPath(): String {
    return joinToString("/")
}

internal fun processReader(stream: InputStream, output: PrintStream = System.out) = Runnable {
    val br = BufferedReader(InputStreamReader(stream))
    var line: String?
    try {
        while (br.readLine().also { line = it } != null) {
            line?.let {
                output.println(
                    String.format("[CMake] %s", it)
                )
            }
        }
    } catch (e: IOException) {
        e.printStackTrace()
    } finally {
        br.close()
    }
}

object OsCheck {
    // cached result of OS detection
    private var detectedOS: OSType? = null

    /**
     * detect the operating system from the os.name System property and cache
     * the result
     *
     * @returns - the operating system detected
     */
    @Suppress("DEPRECATION")
    val operatingSystemType: OSType
        get() {
            val os = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH)
            return detectedOS ?: (if (os.indexOf("mac") >= 0 || os.indexOf("darwin") >= 0) {
                OSType.MacOS
            } else if (os.indexOf("win") >= 0) {
                OSType.Windows
            } else if (os.indexOf("nux") >= 0) {
                OSType.Linux
            } else {
                OSType.Default
            }).also { detectedOS = it }
        }

    /**
     * types of Operating Systems
     */
    enum class OSType {
        Windows, MacOS, Linux, Default
    }
}
