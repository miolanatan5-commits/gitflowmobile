package com.natam.gitflowmobile.data

import android.content.Context
import android.net.Uri
import com.natam.gitflowmobile.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream

/** Uploads the contents of a .zip to a repository as a SINGLE commit (Git Data API). */
class Uploader(private val context: Context, private val api: GitHubApi) {

    private val secretFiles = setOf("release.keystore", "release.properties", "local.properties")

    private fun isSecret(path: String): Boolean = secretFiles.contains(path.substringAfterLast('/'))

    private fun keep(path: String): Boolean {
        if (path.isBlank()) return false
        val segments = path.split('/')
        if (segments.any { it == ".." || it == ".git" || it == ".gradle" || it == ".idea" || it == "__MACOSX" }) return false
        if (path.endsWith(".DS_Store")) return false
        return true
    }

    private fun decodeUtf8OrNull(bytes: ByteArray): String? {
        if (bytes.any { it == 0.toByte() }) return null
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: CharacterCodingException) {
            null
        }
    }

    suspend fun upload(
        repo: Repo,
        zipUri: Uri,
        branchInput: String,
        commitMessage: String,
        stripRoot: Boolean,
        progress: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver

        // Step 1: list entry names to detect the root folder and count files
        progress(context.getString(R.string.up_reading))
        val names = ArrayList<String>()
        (resolver.openInputStream(zipUri) ?: throw IOException(context.getString(R.string.up_err_open)))
            .use { input ->
                ZipInputStream(input).use { zip ->
                    var e = zip.nextEntry
                    while (e != null) {
                        if (!e.isDirectory) names.add(e.name.replace('\\', '/').trimStart('/'))
                        e = zip.nextEntry
                    }
                }
            }
        val useful = names.filter { keep(it) && !isSecret(it) }
        val skippedSecrets = names.count { keep(it) && isSecret(it) }
        if (useful.isEmpty()) throw IOException(context.getString(R.string.up_err_empty))

        var root = ""
        if (stripRoot) {
            val first = useful.first().substringBefore('/', "")
            if (first.isNotEmpty() && useful.all { it.startsWith("$first/") }) root = "$first/"
        }
        val total = useful.size

        // Step 2: prepare the branch
        progress(context.getString(R.string.up_preparing))
        var branch = branchInput.trim().ifEmpty { repo.defaultBranch }
        var headSha = api.branchHead(repo.fullName, branch)
        var createBranch = false
        if (headSha == null) {
            val defaultHead = api.branchHead(repo.fullName, repo.defaultBranch)
            if (defaultHead != null) {
                headSha = defaultHead
                createBranch = true
            } else {
                // Empty repository: create an initial commit on the default branch
                branch = repo.defaultBranch
                progress(context.getString(R.string.up_empty_repo))
                api.putFile(repo.fullName, ".gitkeep", "\n".toByteArray(), "Initial commit", branch, null)
                headSha = api.branchHead(repo.fullName, branch)
                    ?: throw IOException(context.getString(R.string.up_err_init))
            }
        }
        val parentSha: String = headSha ?: throw IOException(context.getString(R.string.up_err_head))
        var treeSha = api.commitTree(repo.fullName, parentSha)

        // Step 3: read the .zip again and upload in chunks
        var chunk = JSONArray()
        var chunkBytes = 0L
        var done = 0
        var skippedLarge = 0

        fun flush() {
            if (chunk.length() == 0) return
            treeSha = api.createTree(repo.fullName, treeSha, chunk)
            chunk = JSONArray()
            chunkBytes = 0
        }

        (resolver.openInputStream(zipUri) ?: throw IOException(context.getString(R.string.up_err_open)))
            .use { input ->
                ZipInputStream(input).use { zip ->
                    var e = zip.nextEntry
                    while (e != null) {
                        if (!e.isDirectory) {
                            val p = e.name.replace('\\', '/').trimStart('/')
                            if (keep(p) && !isSecret(p)) {
                                val rel = if (root.isNotEmpty()) p.removePrefix(root) else p
                                val bytes = zip.readBytes()
                                if (bytes.size > 50L * 1024 * 1024) {
                                    skippedLarge++
                                } else {
                                    val mode = if (rel == "gradlew" || rel.endsWith(".sh")) "100755" else "100644"
                                    val entry = JSONObject()
                                        .put("path", rel)
                                        .put("mode", mode)
                                        .put("type", "blob")
                                    val text = if (bytes.isEmpty()) null else decodeUtf8OrNull(bytes)
                                    if (text != null) {
                                        entry.put("content", text)
                                    } else {
                                        entry.put("sha", api.createBlob(repo.fullName, bytes))
                                    }
                                    chunk.put(entry)
                                    chunkBytes += bytes.size
                                    done++
                                    if (chunk.length() >= 80 || chunkBytes > 3_000_000L) {
                                        flush()
                                        progress(context.getString(R.string.up_progress, done, total))
                                    }
                                }
                            }
                        }
                        e = zip.nextEntry
                    }
                }
            }
        flush()
        if (done == 0) throw IOException(context.getString(R.string.up_none))

        progress(context.getString(R.string.up_commit))
        val message = commitMessage.trim().ifEmpty { context.getString(R.string.default_commit_message) }
        val commit = api.createCommit(repo.fullName, message, treeSha, parentSha)
        if (createBranch) api.createRef(repo.fullName, branch, commit) else api.updateRef(repo.fullName, branch, commit)

        buildString {
            append(context.getString(R.string.up_done, done, branch))
            if (skippedSecrets > 0) append(context.getString(R.string.up_skipped_secrets, skippedSecrets))
            if (skippedLarge > 0) append(context.getString(R.string.up_skipped_large, skippedLarge))
        }
    }
}
