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
import kotlin.time.Duration.Companion.seconds

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

    // Optional: timeout represented as serializable seconds for Gradle input fingerprinting
    @get:Input
    abstract val processTimeoutSeconds: Property<Long>

    @get:Input
    abstract val useScijavaLoaderTemplate: Property<Boolean>

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

        val artifactDirs = mutableListOf<Pair<String, String>>()

        // Per-ABI configure
        abisVal.forEach { abi ->
            val artifactsSubDir = if (useScijavaLoaderTemplate.get()) {
                normalizeABIForScijavaLoader(abi)
            } else {
                abi
            }
            val artifactsDir = listOf(outRoot, artifactsSubDir).toOsPath()
            artifactDirs += artifactsDir to abi

            defs.putIfAbsent("CMAKE_BUILD_TYPE", buildType)
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

            if (debug.get()) {
                ProcessRunner(cmake, configureArgs.toMutableList())
                    .execStreaming(processTimeoutSeconds.get().seconds, logger) // streams live
            } else {
                ProcessRunner(cmake, configureArgs.toMutableList())
                    .runWithTimeout(processTimeoutSeconds.get().seconds)
                    .throwIfFailed("Configure failed for ABI=$abi")
            }
        }

        val buildArgs = mutableListOf(
            "--build", buildDir,
            "--config", buildType
        )

        logger.lifecycle("Build:\n${listOf(cmake).plus(buildArgs).joinToString(" ")}")

        if (debug.get()) {
            ProcessRunner(cmake, buildArgs)
                .execStreaming(processTimeoutSeconds.get().seconds, logger)
        } else {
            ProcessRunner(cmake, buildArgs)
                .runWithTimeout(processTimeoutSeconds.get().seconds)
                .throwIfFailed("Build failed")
        }

        artifactDirs.forEach {
            logArtifacts(File(it.first), it.second)
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
