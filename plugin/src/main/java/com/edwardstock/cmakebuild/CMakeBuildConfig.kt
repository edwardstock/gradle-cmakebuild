package com.edwardstock.cmakebuild

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Nested
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

abstract class CMakeBuildConfig @Inject constructor(

) {
    // --- Gradle services (available via service injection) ---
    @get:Inject
    protected abstract val objects: ObjectFactory

    @get:Inject
    protected abstract val providers: ProviderFactory

    @get:Inject
    protected abstract val layout: ProjectLayout

    // --- Host OS helpers (computed, not inputs) ---
    val currentOs: OsCheck.OSType = OsCheck.operatingSystemType
    val isWindows = currentOs == OsCheck.OSType.Windows
    val isLinux = currentOs == OsCheck.OSType.Linux
    val isMacOS = currentOs == OsCheck.OSType.MacOS

    // --- Inputs (Provider-based) ---
    abstract val cmakeBin: Property<String>
    abstract val path: DirectoryProperty
    abstract val stagingPath: DirectoryProperty

    abstract val abis: ListProperty<String>
    abstract val buildType: Property<String>
    abstract val debug: Property<Boolean>
    abstract val enable: Property<Boolean>
    abstract val processTimeout: Property<Duration>

    /**
     * If you use https://github.com/scijava/native-lib-loader
     * you can set this to true so cmake will create a valid directory hierarchy
     * that is suitable for loading via SciJava's NativeLoader
     *
     * Example:
     * ```kotlin
     * cmakeBuild {
     *     path = rootProject.file("native")
     *     useScijavaLoaderTemplate = true
     * }
     *
     * /**
     *  * Include the CMake build output (native libs) into the JVM resources,
     *  * so they get packaged into the JAR and are available at runtime.
     *  */
     * run {
     *     val cmakeOutDir = providers.provider {
     *         getCMakeBuildPathHostSpecific(cmakeBuild).parentFile.parentFile
     *     }
     *
     *     // Add to jvmMain resources
     *     kotlin.sourceSets.named("jvmMain") {
     *         resources.srcDir(cmakeOutDir)
     *     }
     *
     *
     *     tasks.named<ProcessResources>("jvmProcessResources").configure {
     *         dependsOn("buildCMake")
     *         inputs.dir(cmakeOutDir)
     *     }
     *
     *     tasks.matching { it.name in setOf("jvmJar", "jar") }.configureEach {
     *         dependsOn("jvmProcessResources")
     *     }
     * }
     * ```
     */
    abstract val useScijavaLoaderTemplate: Property<Boolean>

    // --- Nested OS-specific options (Provider-friendly) ---
    @get:Nested
    abstract val defaultOpts: OsSpecificOptsConfig

    @get:Nested
    abstract val windowsOpts: OsSpecificOptsConfig

    @get:Nested
    abstract val macosOpts: OsSpecificOptsConfig

    @get:Nested
    abstract val linuxOpts: OsSpecificOptsConfig

    /**
     * Set all conventions. Call this from the plugin's `apply {}` AFTER creating the extension.
     * Avoids accessing non-final properties in the constructor/`init` block.
     */
    internal fun applyConventions() {
        buildType.convention("Debug")
        debug.convention(false)
        enable.convention(true)
        processTimeout.convention(1800.seconds) // 30 minutes
        useScijavaLoaderTemplate.convention(false)

        // default build dir: build/cmake
        stagingPath.convention(layout.buildDirectory.dir("cmake"))

        // default ABIs: host arch
        abis.convention(listOf(System.getProperty("os.arch")))

        // lazily discover cmake if not set by the user
        cmakeBin.convention("cmake")

        // initialize nested option holders
        defaultOpts.initDefaults(objects)
        windowsOpts.initDefaults(objects)
        macosOpts.initDefaults(objects)
        linuxOpts.initDefaults(objects)
    }

    // ---- DSL helpers ----
    fun allOS(acceptor: OsSpecificOptsConfig.() -> Unit) = defaultOpts.acceptor()
    fun windows(acceptor: OsSpecificOptsConfig.() -> Unit) = windowsOpts.acceptor()
    fun macos(acceptor: OsSpecificOptsConfig.() -> Unit) = macosOpts.acceptor()
    fun linux(acceptor: OsSpecificOptsConfig.() -> Unit) = linuxOpts.acceptor()

    // ---- Provider-based merges (no eager reads) ----
    val commonOpts get() = defaultOpts.asModel()
    val currentOsOpts
        get() = when (currentOs) {
            OsCheck.OSType.Windows -> windowsOpts.asModel()
            OsCheck.OSType.MacOS -> macosOpts.asModel()
            OsCheck.OSType.Linux -> linuxOpts.asModel()
            else -> providers.provider { OsSpecificOpts() }
        }
    val allOpts get() = commonOpts.zip(currentOsOpts) { a, b -> a + b }

    internal fun validate() {
        if (!enable.get()) return
        if (!path.isPresent) throw CMakeException("cmakeBuild.path must be set")
        // cmakeBin resolves lazily; nothing else to do
    }
}

abstract class OsSpecificOptsConfig {
    abstract val cppFlags: ListProperty<String>
    abstract val cFlags: ListProperty<String>
    abstract val arguments: ListProperty<String>
    abstract val definitions: MapProperty<String, String>

    internal fun initDefaults(objects: ObjectFactory) {
        cppFlags.convention(emptyList())
        cFlags.convention(emptyList())
        arguments.convention(emptyList())
        definitions.convention(emptyMap())
    }

    fun asModel() = definitions.map { defs ->
        OsSpecificOpts(
            cppFlags = cppFlags.get().toMutableList(),
            cFlags = cFlags.get().toMutableList(),
            arguments = arguments.get().toMutableList(),
            definitions = defs.toMutableMap()
        )
    }
}
