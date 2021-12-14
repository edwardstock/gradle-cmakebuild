package com.edwardstock.cmakebuild

/**
 * @param cppFlags C++ compiler extra flags
 * @param cFlags C compiler extra flags
 * @param arguments List of CMake arguments
 * @param definitions Map of CMake definitions.
 * It passes the key-value for -D${YOUR_KEY}=${YOUR_VALUE}.
 * Can be useful sometimes
 *
 */
data class OsSpecificOpts(
    var cppFlags: MutableList<String> = ArrayList(0),
    var cFlags: MutableList<String> = ArrayList(0),
    var arguments: MutableList<String> = ArrayList(0),
    var definitions: MutableMap<String, String> = HashMap()
) {
    internal val cFlagsMerged: String
        get() = cFlags.joinToString(" ").trim()

    internal val cppFlagsMerged: String
        get() = cppFlags.joinToString(" ").trim()

    /**
     * Immutable function
     */
    operator fun plus(other: OsSpecificOpts): OsSpecificOpts {
        return OsSpecificOpts(
            cppFlags = (cppFlags + other.cppFlags).toMutableList(),
            cFlags = (cFlags + other.cFlags).toMutableList(),
            arguments = (arguments + other.arguments).toMutableList(),
            definitions = HashMap((definitions + other.definitions)),
        )
    }
}
