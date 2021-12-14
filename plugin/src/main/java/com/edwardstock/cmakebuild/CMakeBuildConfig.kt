package com.edwardstock.cmakebuild

import org.gradle.api.tasks.InputDirectory
import java.io.File

abstract class CMakeBuildConfig : BaseBuildConfig() {
    /**
     * Represents current OS for developers needs.
     * You can access this variable in your project by using:
     * <code>
     * cmakeBuild.currentOs
     * </code>
     * OSType can detect only 3 basic OS types:
     * - Windows
     * - MacOS
     * - Linux
     * - Default - any os, used for internal needs
     */
    val currentOs: OsCheck.OSType = OsCheck.operatingSystemType

    val isWindows = currentOs == OsCheck.OSType.Windows
    val isLinux = currentOs == OsCheck.OSType.Linux
    val isMacOS = currentOs == OsCheck.OSType.MacOS

    /**
     * Path to CMake binary.
     * Plugin will try to find it by itself, if cmake not installed you'll see error
     */
    var cmakeBin: String? = null

    /**
     * CMake project directory
     * For example: ${project.projectDir}/path/to/myclibrary
     */
    @InputDirectory
    var path: File? = null

    /**
     * CMake build directory. By default: ${project.buildDir}/cmake
     */
    @InputDirectory
    var stagingPath: File? = null

    /**
     * List of ABIs to build. Now only supported system-only toolchain. Cross-compilation does not work yet
     */
    var abis: MutableList<String> = mutableListOf()

    /**
     * CMake build type: Debug, Release or whatever
     */
    var buildType: String = "Debug"

    /**
     * Prints extra information about build
     */
    var debug: Boolean = true

    /**
     * Enable cmake project build, it can be switched off if you have pre-built binaries
     */
    var enable: Boolean = true

    /**
     * Configuration for all OS
     */
    fun allOS(acceptor: OsSpecificOpts.() -> Unit) {
        osSpecificOpts[OsCheck.OSType.Default] = OsSpecificOpts().apply(acceptor)
    }

    /**
     * Configuration for windows
     */
    fun windows(acceptor: OsSpecificOpts.() -> Unit) {
        osSpecificOpts[OsCheck.OSType.Windows] = OsSpecificOpts().apply(acceptor)
    }

    /**
     * Configuration for MacOS
     */
    fun macos(acceptor: OsSpecificOpts.() -> Unit) {
        osSpecificOpts[OsCheck.OSType.MacOS] = OsSpecificOpts().apply(acceptor)
    }

    /**
     * Configuration for any Linux
     */
    fun linux(acceptor: OsSpecificOpts.() -> Unit) {
        osSpecificOpts[OsCheck.OSType.Linux] = OsSpecificOpts().apply(acceptor)
    }
}
