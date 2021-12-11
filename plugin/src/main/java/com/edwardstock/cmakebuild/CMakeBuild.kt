package com.edwardstock.cmakebuild

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PrintStream
import java.util.concurrent.TimeUnit

abstract class CMakeBuildConfig {
    var cmakeBin: String? = null

    /**
     * CMake project directory
     * For example: ${project.projectDir}/path/to/mylibrary
     */
    @InputDirectory
    var path: File? = null

    /**
     * List of ABIs to build. Now only supported system-only toolchain. Cross-compilation does not work yet
     */
    var abis: MutableList<String> = mutableListOf()
    var arguments: MutableList<String> = mutableListOf()
    var cppFlags: MutableList<String> = mutableListOf()
    var cFlags: MutableList<String> = mutableListOf()
    var buildType: String? = "Debug"
    var debug: Boolean = true
}

class CMakeBuild : Plugin<Project> {

    private fun List<String>.runCommand(
        workingDir: File = File("."),
        timeoutAmount: Long = 60,
        timeoutUnit: TimeUnit = TimeUnit.SECONDS
    ): String? = runCatching {
        ProcessBuilder(this)
            .directory(workingDir)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start().also { it.waitFor(timeoutAmount, timeoutUnit) }
            .inputStream.bufferedReader().readText()
    }.onFailure { throw it }.getOrNull()

    private fun List<String>.execCommand(
        workingDir: File = File("."),
        timeoutAmount: Long = 60,
        timeoutUnit: TimeUnit = TimeUnit.SECONDS
    ) {
        val processBuilder = ProcessBuilder(this)
            .directory(workingDir)
            .redirectErrorStream(true)
        val process = processBuilder.start()
        val thread = Thread(processReader(process.inputStream))
        thread.start()
        process.waitFor(timeoutAmount, timeoutUnit)
        thread.join()
        if (process.exitValue() != 0) {
            throw CMakeException("Unable to execute process: exit code ${process.exitValue()}. See log for details.")
        }
    }


    private fun findCMakeBin(): String {
        val path = listOf("which", "cmake").runCommand()
        if (path.isNullOrBlank()) {
            throw CMakeException("Unable to find cmake binary")
        }
        return path.trim()
    }

    private fun processReader(stream: InputStream, output: PrintStream = System.out) = Runnable {
        val br = BufferedReader(InputStreamReader(stream))
        var line: String?
        try {
            while (br.readLine().also { line = it } != null) {
                line?.let {
                    output.println(String.format("[CMake] %s", it))
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun apply(target: Project) {
        val ext = target.extensions.create("cmakeBuild", CMakeBuildConfig::class.java)

        target.tasks.register("buildCMake", DefaultTask::class.java) { task ->
            task.group = "build"

            if (ext.cmakeBin == null) {
                ext.cmakeBin = findCMakeBin()
            }

            if (ext.abis.isEmpty()) {
                ext.abis += mutableListOf("x86_64")
            }

            if (ext.path == null) {
                throw CMakeException("cmakeBuild.path must be set")
            }

            if (ext.buildType == null) {
                ext.buildType = "Debug"
            }

            ext.arguments += listOf(
                "-B${target.buildDir}/cmake",
                "-S${ext.path!!.absolutePath}",
                "-DCMAKE_BUILD_TYPE=${ext.buildType}",
            )

            task.inputs.dir(ext.path!!)
            task.outputs.dir("${target.buildDir}/.cxx/")

            task.doFirst {
                for (abi in ext.abis) {
                    val validAbi = when (abi) {
                        "x86" -> abi
                        "amd64",
                        "x86_64",
                        "x86-64" -> "x86_64"
                        else -> throw CMakeException("Unsupported ABI $abi")
                    }

                    configure(validAbi, target, ext)
                    build(target, ext)
                }
            }
        }
    }

    private fun build(project: Project, config: CMakeBuildConfig) {
        val configureDir = "${project.buildDir}/cmake"

        val buildArgs = listOf(
            config.cmakeBin!!,
            "--build", configureDir,
            "--target", "all"
        )
        if(config.debug) {
            println("Build args: $buildArgs")
        }


        buildArgs.execCommand()
    }

    private fun configure(abi: String, project: Project, config: CMakeBuildConfig) {
        /**
         * TODO: add support for cross-compilation, at least in UNIX platforms with gcc/clang
         */
        val crossFlag = when (abi) {
            "x86" -> "-m32"
            "x86_64" -> "-m64"
            else -> throw CMakeException("Unsupported ABI $abi")
        }

        val buildDir = "${project.buildDir}/.cxx/${abi}"

        val configureArgs: MutableList<String> = mutableListOf(
            config.cmakeBin!!,
            "-DCMAKE_ARCHIVE_OUTPUT_DIRECTORY=$buildDir",
            "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=$buildDir",
            "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY=$buildDir",
        )
        configureArgs += config.arguments

        configureArgs += "-DCMAKE_C_FLAGS=${config.cFlags.joinToString { " " }} $crossFlag"
        configureArgs += "-DCMAKE_CXX_FLAGS=${config.cppFlags.joinToString { " " }} $crossFlag"


        if(config.debug) {
            println("Configure args: $configureArgs")
        }

        configureArgs.execCommand()
    }

}