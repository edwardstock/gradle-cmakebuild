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
        val buildRoot = buildDir.asFile.get()
        val outRoot = outputsDir.asFile.get().canonicalPath
        val buildType = buildType.get()
        val abisVal = abis.get().map(::normalizeABI)
        val opts = effectiveOpts.get()

        val baseArgs = opts.arguments.toMutableList()
        val baseDefs = opts.definitions.toMutableMap().apply {
            putIfAbsent("CMAKE_BUILD_TYPE", buildType)
        }

        val artifactDirs = mutableListOf<Pair<File, String>>()

        // Per-ABI configure + build
        abisVal.forEach { abi ->
            val abiBuildDir = File(buildRoot, abi).apply { mkdirs() }.canonicalFile
            val abiDefs = baseDefs.toMutableMap()
            val artifactsSubDir = if (useScijavaLoaderTemplate.get()) {
                normalizeABIForScijavaLoader(abi)
            } else {
                abi
            }
            val artifactsDir = listOf(outRoot, artifactsSubDir).toOsPath()
            artifactDirs += File(artifactsDir) to abi

            abiDefs["CMAKE_ARCHIVE_OUTPUT_DIRECTORY"] = artifactsDir
            abiDefs["CMAKE_LIBRARY_OUTPUT_DIRECTORY"] = artifactsDir
            abiDefs["CMAKE_RUNTIME_OUTPUT_DIRECTORY"] = artifactsDir

            // Merge flags if present
            if (opts.cFlags.isNotEmpty()) {
                abiDefs["CMAKE_C_FLAGS"] = (abiDefs["CMAKE_C_FLAGS"]?.plus(" ") ?: "") + opts.cFlagsMerged
            }
            if (opts.cppFlags.isNotEmpty()) {
                abiDefs["CMAKE_CXX_FLAGS"] = (abiDefs["CMAKE_CXX_FLAGS"]?.plus(" ") ?: "") + opts.cppFlagsMerged
            }

            val configureArgs = (
                    baseArgs
                            + listOf("-S$src", "-B${abiBuildDir.canonicalPath}")
                            + abiDefs.map { (k, v) -> "-D$k=$v" }
                    )
                .toMutableList()
            logger.lifecycle("Configure ABI=$abi:\n${formatProgramRun(cmake, configureArgs)}")

            if (debug.get()) {
                ProcessRunner(cmake, configureArgs)
                    .execStreaming(processTimeoutSeconds.get().seconds, logger) // streams live
            } else {
                ProcessRunner(cmake, configureArgs)
                    .runWithTimeout(processTimeoutSeconds.get().seconds)
                    .throwIfFailed("Configure failed for ABI=$abi")
            }

            val buildArgs = mutableListOf(
                "--build", abiBuildDir.canonicalPath,
                "--config", buildType
            )
            logger.lifecycle("Build ABI=$abi:\n${formatProgramRun(cmake, buildArgs)}")

            if (debug.get()) {
                ProcessRunner(cmake, buildArgs)
                    .execStreaming(processTimeoutSeconds.get().seconds, logger)
            } else {
                ProcessRunner(cmake, buildArgs)
                    .runWithTimeout(processTimeoutSeconds.get().seconds)
                    .throwIfFailed("Build failed for ABI=$abi")
            }
        }

        artifactDirs.forEach { (dir, abi) ->
            logArtifacts(dir, abi)
        }
    }

    private fun formatProgramRun(program: String?, buildArgs: List<String>) =
        listOf(program).plus(buildArgs).joinToString(" ")

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
