package com.edwardstock.cmakebuild

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class CMakeBuildTask : DefaultTask() {
    // Inputs/outputs (wired from the extension in plugin.apply)
    @get:InputDirectory
    abstract val sourceDir: DirectoryProperty

    @get:OutputDirectory
    abstract val buildDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputsDir: DirectoryProperty

    @get:Input
    abstract val abis: ListProperty<String>

    @get:Input
    abstract val buildType: Property<String>

    @get:Input
    abstract val cmakeBin: Property<String>

    @get:Input
    abstract val debug: Property<Boolean>

    // Optional: pass merged/effective opts precomputed in apply(), or rebuild them here
    @get:Internal
    abstract val effectiveOpts: Property<OsSpecificOpts>

    // Optional: timeout (seconds)
    @get:Input
    abstract val timeoutSeconds: Property<Long>

    @get:Input
    abstract val useScijavaLoaderTemplate: Property<Boolean>

    init {
        // Sensible defaults
        debug.convention(false)
        timeoutSeconds.convention(1800L) // 30 minutes
    }

    @TaskAction
    fun run() {
        val cmake = cmakeBin.get()
        val src = sourceDir.asFile.get().canonicalPath
        val buildDir = buildDir.asFile.get().canonicalPath
        val outRoot = outputsDir.asFile.get().canonicalPath
        val buildType = buildType.get()
        val abisVal = abis.get().map(::normalizeABI)
        val opts = effectiveOpts.get()

        // Ensure args contain -S/-B and build type definition (single-config)
        val args = (opts.arguments + listOf("-S$src", "-B$buildDir")).toMutableList()
        val defs = opts.definitions.toMutableMap()

        // Per-ABI configure
        abisVal.forEach { abi ->
            val artifactsSubDir = if (useScijavaLoaderTemplate.get()) {
                normalizeABIForScijavaLoader(abi)
            } else {
                abi
            }
            val artifactsDir = listOf(outRoot, artifactsSubDir).toOsPath()

            defs.putIfAbsent("CMAKE_ARCHIVE_OUTPUT_DIRECTORY", artifactsDir)
            defs.putIfAbsent("CMAKE_LIBRARY_OUTPUT_DIRECTORY", artifactsDir)
            defs.putIfAbsent("CMAKE_RUNTIME_OUTPUT_DIRECTORY", artifactsDir)

            // Merge flags if present
            if (opts.cFlags.isNotEmpty()) {
                defs["CMAKE_C_FLAGS"] = (defs["CMAKE_C_FLAGS"]?.plus(" ") ?: "") + opts.cFlagsMerged
            }
            if (opts.cppFlags.isNotEmpty()) {
                defs["CMAKE_CXX_FLAGS"] = (defs["CMAKE_CXX_FLAGS"]?.plus(" ") ?: "") + opts.cppFlagsMerged
            }

            // Build full configure command
            val configureArgs = args + defs.map { (k, v) -> "-D$k=$v" }
            logger.lifecycle("Configure ABI=$abi:\n${listOf(cmake).plus(configureArgs).joinToString(" ")}")

            ProcessRunner(cmake, configureArgs.toMutableList())
                .runWithTimeout(timeoutSeconds.get())
                .throwIfFailed("Configure failed for ABI=$abi")

            if (debug.get()) {
                ProcessRunner(cmake, configureArgs.toMutableList())
                    .execStreaming(timeoutSeconds.get(), logger) // streams live
            } else {
                ProcessRunner(cmake, configureArgs.toMutableList())
                    .runWithTimeout(timeoutSeconds.get())
                    .throwIfFailed("Configure failed for ABI=$abi")
            }

            logArtifacts(File(artifactsDir), abi)
        }


        val buildArgs = mutableListOf(
            "--build", buildDir,
            "--config", buildType
        )

        logger.lifecycle("Build:\n${listOf(cmake).plus(buildArgs).joinToString(" ")}")

        if (debug.get()) {
            ProcessRunner(cmake, buildArgs)
                .execStreaming(timeoutSeconds.get(), logger)
        } else {
            ProcessRunner(cmake, buildArgs)
                .runWithTimeout(timeoutSeconds.get())
                .throwIfFailed("Build failed")
        }
    }

    private fun logArtifacts(artifactDir: File, abi: String) {
        val files = artifactDir.walkTopDown()
            .maxDepth(2)
            .filter { it.isFile && (it.extension in listOf("a", "so", "dylib", "lib", "dll")) }
            .toList()

        if (files.isEmpty()) {
            logger.warn("⚠️  No artifacts found in: ${artifactDir.absolutePath}")
        } else {
            logger.lifecycle("✅ Artifacts for [$abi] → ${artifactDir.absolutePath}")
            files.forEach { f -> logger.lifecycle("   • ${f.name} (${f.length()} bytes)") }
        }
    }
}
