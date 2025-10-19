package com.edwardstock.cmakebuild

import org.gradle.api.Project
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PrintStream
import java.security.MessageDigest
import java.util.Locale

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

internal fun String.normalizePath(): String {
    return if (isNotWindows()) {
        this
    } else {
        this.replace("\\", "/")
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

internal fun normalizeABI(abi: String): String = when (abi) {
    "x86" -> abi
    "x86_64" -> abi
    "amd64",
    "x86-64" -> "x86_64"
    "arm64",
    "aarch64" -> "aarch64"
    else -> throw CMakeException("Unsupported ABI $abi")
}

internal fun getCurrentOsName(): String {
    val osName = OsCheck.operatingSystemType
    val hostOs = when (osName) {
        OsCheck.OSType.Windows -> "windows"
        OsCheck.OSType.MacOS -> "osx"
        else -> "linux"
    }

    return hostOs
}

internal fun normalizeABIForScijavaLoader(abi: String): String {
    val os = getCurrentOsName()
    val a = abi.lowercase()

    val osAndBitDepth = when (os) {
        "windows" -> when (a) {
            "x86", "i386", "i686" -> "windows_32"
            "x86_64", "amd64", "x86-64" -> "windows_64"
            "aarch64", "arm64" -> "windows_arm64"
            else -> throw CMakeException("Unsupported Windows ABI: $abi")
        }

        "osx" -> when (a) {
            "x86_64", "amd64", "x86-64" -> "osx_64"
            "aarch64", "arm64" -> "osx_arm64"
            else -> throw CMakeException("Unsupported macOS ABI: $abi")
        }

        "linux" -> when (a) {
            "x86", "i386", "i686" -> "linux_32"
            "x86_64", "amd64", "x86-64" -> "linux_64"
            "aarch64", "arm64" -> "linux_arm64"   // ← your case
            else -> throw CMakeException("Unsupported Linux ABI: $abi")
        }

        else -> throw CMakeException("Unexpected OS: $os")
    }

    return "natives/$osAndBitDepth"
}

internal fun String.sha256(): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(this.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}

fun Project.getCMakeBuildPath(): File {
    return File(layout.buildDirectory.asFile.get(), ".cxx")
}

fun Project.getCMakeBuildPathHostSpecific(config: CMakeBuildConfig): File {
    val baseBuildPath = File(layout.buildDirectory.asFile.get(), ".cxx")
    val arch = when (System.getProperty("os.arch")) {
        "amd64" -> "x86_64"
        else -> System.getProperty("os.arch")
    }
    val localABI = normalizeABI(arch)

    if (config.useScijavaLoaderTemplate.get()) {
        val scijavaABI = normalizeABIForScijavaLoader(localABI)
        val buildPath = File(baseBuildPath, scijavaABI)
        return buildPath
    } else {

        val buildPath = File(baseBuildPath, localABI)
        return if (config.isWindows) {
            File(buildPath, config.buildType.get())
        } else {
            buildPath
        }
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
    val operatingSystemType: OSType
        get() {
            val os = System.getProperty("os.name", "generic").lowercase(Locale.ENGLISH)
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
