package com.edwardstock.cmakebuild

import org.gradle.api.Plugin
import org.gradle.api.Project

class CMakeBuild : Plugin<Project> {
    companion object {
        const val EXT_NAME = "cmakeBuild"
        const val TASK_NAME = "buildCMake"
    }

    override fun apply(target: Project) {
        val ext = target.extensions.create(EXT_NAME, CMakeBuildConfig::class.java)
        ext.applyConventions()

        target.tasks.register(TASK_NAME, CMakeBuildTask::class.java) { t ->
            t.doFirst {
                ext.validate()
            }

            t.group = "build"
            t.sourceDir.set(ext.path)
            t.buildDir.set(ext.stagingPath) // already has default convention
            t.outputsDir.set(target.getCMakeBuildPath())

            t.abis.set(ext.abis)
            t.buildType.set(ext.buildType)
            t.cmakeBin.set(ext.cmakeBin)
            t.debug.set(ext.debug)
            t.processTimeoutSeconds.set(ext.processTimeout.map { it.inWholeSeconds })
            t.useScijavaLoaderTemplate.set(ext.useScijavaLoaderTemplate)

            t.effectiveOpts.set(ext.allOpts)
        }
    }

}
