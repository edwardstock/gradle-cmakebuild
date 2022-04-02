package com.edwardstock.cmakebuild

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project

class CMakeBuild : Plugin<Project> {
    companion object {
        private var CACHE_CMAKE_BIN: String? = null
    }

    private fun String.normalizePath(): String {
        return if (isNotWindows()) {
            this
        } else {
            this.replace("\\", "/")
        }
    }

    override fun apply(target: Project) {
        val config = target.extensions.create("cmakeBuild", CMakeBuildConfig::class.java)

        target.tasks.register("buildCMake", DefaultTask::class.java) {
            action(it, target, config)
        }
    }

    private fun CMakeBuildConfig.setup() {
        // finding cmake in windows may take some time, so cache value for subsequent usage
        CACHE_CMAKE_BIN?.let {
            println("Getting cmake bin from cache")
            cmakeBin = it
        }

        if (cmakeBin == null) {
            cmakeBin = findCMakeBin().also { CACHE_CMAKE_BIN = it }
        }

        if (abis.isEmpty()) {
            abis += mutableListOf(System.getProperty("os.arch"))
        }

        if (path == null) {
            throw CMakeException("cmakeBuild.path must be set")
        }
    }

    private fun action(task: DefaultTask, target: Project, config: CMakeBuildConfig) {
        task.group = "build"

        if (!config.enable) {
            return
        }

        config.setup()

        val buildDir = config.stagingPath?.canonicalPath ?: listOf(target.buildDir.canonicalPath, "cmake").toOsPath()
        val srcDir =
            config.path?.canonicalPath ?: throw IllegalStateException("CMake project path is null")
        val outputsDir = listOf(target.buildDir.canonicalPath, ".cxx").toOsPath()

        val allOpts: OsSpecificOpts = config.allOpts

        allOpts.arguments += listOf(
            "-B${buildDir.normalizePath()}",
            "-S${srcDir.normalizePath()}",
        )
        allOpts.definitions["CMAKE_BUILD_TYPE"] = config.buildType

        task.inputs.dir(srcDir)
        task.outputs.dir(outputsDir)

        task.doFirst {
            for (abi in config.abis) {
                val validAbi = when (abi) {
                    "x86" -> abi
                    "x86_64" -> abi
                    "amd64",
                    "x86-64" -> "x86_64"
                    "aarch64" -> "aarch64"
                    else -> throw CMakeException("Unsupported ABI $abi")
                }

                configure(validAbi, allOpts, config, outputsDir)
            }
        }

        task.doLast() {
            for (abi in config.abis) {
                build(config, buildDir)
            }
        }
    }

    private fun MutableMap<String, String>.setIfEmpty(key: String, value: String) {
        if (!containsKey(key)) {
            this[key] = value
        }
    }

    private fun MutableMap<String, String>.append(key: String, value: String) {
        if (containsKey(key)) {
            this[key] += " $value"
        } else {
            this[key] = value
        }
    }

    private fun OsSpecificOpts.getArgs(): List<String> {
        return this.arguments + this.definitions.map {
            "-D${it.key}=${it.value}"
        }
    }

    private fun configure(
        abi: String,
        opts: OsSpecificOpts,
        config: CMakeBuildConfig,
        outputsDir: String
    ) {
        /**
         * TODO: add support for cross-compilation, at least in UNIX platforms with gcc/clang
         */
        val crossFlag = when (abi) {
            "x86" -> "-m32"
            "x86_64" -> "-m64"
            else -> throw CMakeException("Unsupported ABI $abi")
        }

        val artifactsDir = listOf(outputsDir, abi).toOsPath().normalizePath()

        // do not override user-defined output dir
        opts.definitions.setIfEmpty("CMAKE_ARCHIVE_OUTPUT_DIRECTORY", artifactsDir.normalizePath())
        opts.definitions.setIfEmpty("CMAKE_LIBRARY_OUTPUT_DIRECTORY", artifactsDir.normalizePath())
        opts.definitions.setIfEmpty("CMAKE_RUNTIME_OUTPUT_DIRECTORY", artifactsDir.normalizePath())

        if (isNotWindows()) {
            opts.cFlags += crossFlag
            opts.cppFlags += crossFlag
        }

        if (opts.cFlags.isNotEmpty()) {
            opts.definitions.append("CMAKE_C_FLAGS", opts.cFlagsMerged)
        }

        if (opts.cppFlags.isNotEmpty()) {
            opts.definitions.append("CMAKE_CXX_FLAGS", opts.cppFlagsMerged)
        }

        val process = ProcessRunner(config.cmakeBin!!)
        process.addArgs(opts.getArgs())

        if (config.debug) {
            println("Configure args: ${process.getCommand()}")
        }
        process()
    }

    private fun build(config: CMakeBuildConfig, buildDir: String) {
        val process = ProcessRunner(config.cmakeBin!!)
        process.addArgs("--build", buildDir)
        if (isNotWindows()) {
            process.addArgs("--target", "all")
        }

        if (config.debug) {
            println("Build args: ${process.getCommand()}")
        }
        process()
    }



}
