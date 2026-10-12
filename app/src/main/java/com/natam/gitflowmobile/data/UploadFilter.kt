package com.natam.gitflowmobile.data

/**
 * Decides which entries of an uploaded .zip become part of the commit.
 *
 * The skip rules follow the secret and build-output patterns the project
 * repositories declare in their .gitignore ("Signing secrets - nunca versionar"):
 * release.keystore, *.jks, *.keystore, release.properties, release.properties.txt,
 * local.properties, and generated build output.
 */
internal object UploadFilter {

    /** Directory segments that hold generated output or tool state. */
    private val excludedSegments = setOf("..", ".git", ".gradle", ".idea", "__MACOSX", "build")

    /** Exact file names that hold signing material or local machine config. */
    private val secretNames = setOf("local.properties", "keystore.properties")

    /** Name prefixes of signing files that carry a variant suffix (release.properties.txt). */
    private val secretPrefixes = setOf("release.properties")

    /** Extensions that identify signing keystores. */
    private val secretExtensions = setOf("jks", "keystore")

    /** True when the entry is signing material or local machine config. */
    fun isSecret(path: String): Boolean {
        val name = path.substringAfterLast('/')
        if (name in secretNames) return true
        if (secretPrefixes.any { name.startsWith(it) }) return true
        return name.substringAfterLast('.', "").lowercase() in secretExtensions
    }

    /** True when the entry is not tool state or generated build output. */
    fun keep(path: String): Boolean {
        if (path.isBlank()) return false
        if (path.split('/').any { it in excludedSegments }) return false
        if (path.endsWith(".DS_Store")) return false
        return true
    }
}
