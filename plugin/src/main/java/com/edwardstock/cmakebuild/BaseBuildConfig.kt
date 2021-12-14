package com.edwardstock.cmakebuild

abstract class BaseBuildConfig {
    protected var osSpecificOpts: MutableMap<OsCheck.OSType, OsSpecificOpts> = mutableMapOf(
        OsCheck.OSType.Default to OsSpecificOpts(),
        OsCheck.OSType.Windows to OsSpecificOpts(),
        OsCheck.OSType.MacOS to OsSpecificOpts(),
        OsCheck.OSType.Linux to OsSpecificOpts(),
    )

    internal val commonOpts: OsSpecificOpts
        get() = getOptsOrDefault(OsCheck.OSType.Default)

    internal val allOpts: OsSpecificOpts
        get() = getOptsOrDefault(OsCheck.OSType.Default) + getOptsOrDefault(OsCheck.operatingSystemType)

    private fun getOptsOrDefault(os: OsCheck.OSType): OsSpecificOpts = osSpecificOpts[os] ?: OsSpecificOpts()
}
