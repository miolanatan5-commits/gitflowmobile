package com.natam.gitflowmobile.data

import android.content.Context
import android.util.Base64
import com.natam.gitflowmobile.R
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class ApiException(val code: Int, message: String) : IOException(message)

/** GitHub REST API client. All functions are blocking: call them from Dispatchers.IO. */
class GitHubApi(private val context: Context, private val tokenProvider: () -> String) {

    private val base = "https://api.github.com"
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun builder(url: String, accept: String = "application/vnd.github+json"): Request.Builder =
        Request.Builder()
            .url(url)
            .header("Accept", accept)
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("Authorization", "Bearer ${tokenProvider()}")
            .header("User-Agent", "GitFlowMobile-Android")

    private fun errorText(code: Int, body: String): String {
        val detail = try {
            val o = JSONObject(body)
            val sb = StringBuilder(o.optString("message"))
            val errors = o.optJSONArray("errors")
            if (errors != null) {
                for (i in 0 until errors.length()) {
                    val e = errors.opt(i)
                    val t = if (e is JSONObject) e.optString("message") else e.toString()
                    if (t.isNotBlank()) sb.append(" — ").append(t)
                }
            }
            sb.toString()
        } catch (e: Exception) {
            ""
        }
        val prefix = when (code) {
            401 -> context.getString(R.string.api_err_401)
            403 -> context.getString(R.string.api_err_403)
            404 -> context.getString(R.string.api_err_404)
            409 -> context.getString(R.string.api_err_409)
            422 -> context.getString(R.string.api_err_422)
            else -> context.getString(R.string.api_err_other, code)
        }
        return if (detail.isBlank()) prefix else "$prefix $detail"
    }

    private fun execute(makeRequest: () -> Request): String {
        var attempt = 0
        while (true) {
            client.newCall(makeRequest()).execute().use { r ->
                val body = r.body?.string().orEmpty()
                if (r.isSuccessful) return body
                val retryAfter = r.header("Retry-After")?.toLongOrNull()
                val limited = r.code == 429 ||
                    (r.code == 403 && (retryAfter != null || body.contains("rate limit", ignoreCase = true)))
                if (limited && attempt < 3) {
                    attempt++
                    val seconds = (retryAfter ?: (20L * attempt)).coerceAtMost(90L)
                    Thread.sleep(seconds * 1000)
                    return@use
                }
                throw ApiException(r.code, errorText(r.code, body))
            }
        }
    }

    private fun get(path: String): String = execute { builder("$base$path").get().build() }

    private fun send(method: String, path: String, json: JSONObject?): String = execute {
        val body: RequestBody? = when {
            json != null -> json.toString().toRequestBody(jsonType)
            method == "DELETE" -> null
            else -> "".toRequestBody(jsonType)
        }
        builder("$base$path").method(method, body).build()
    }

    // ---------- Account ----------

    fun currentUser(): GhUser {
        val o = JSONObject(get("/user"))
        return GhUser(o.str("login"), o.str("name"), o.str("html_url"), o.str("type"))
    }

    // ---------- Repositories ----------

    fun myRepos(page: Int): List<Repo> =
        JSONArray(get("/user/repos?per_page=50&page=$page&sort=updated&affiliation=owner,collaborator"))
            .objects().map { Repo.from(it) }

    fun userRepos(login: String, page: Int): List<Repo> =
        JSONArray(get("/users/${enc(login)}/repos?per_page=50&page=$page&sort=updated"))
            .objects().map { Repo.from(it) }

    fun repo(fullName: String): Repo = Repo.from(JSONObject(get("/repos/$fullName")))

    fun createRepo(name: String, description: String, isPrivate: Boolean, autoInit: Boolean): Repo {
        val o = JSONObject()
            .put("name", name)
            .put("private", isPrivate)
            .put("auto_init", autoInit)
        if (description.isNotBlank()) o.put("description", description)
        return Repo.from(JSONObject(send("POST", "/user/repos", o)))
    }

    fun deleteRepo(fullName: String) {
        send("DELETE", "/repos/$fullName", null)
    }

    // ---------- Search ----------

    fun searchRepos(q: String, page: Int, sort: String): SearchPage<Repo> {
        val sortPart = if (sort.isBlank()) "" else "&sort=$sort&order=desc"
        val o = JSONObject(get("/search/repositories?q=${enc(q)}&per_page=30&page=$page$sortPart"))
        val items = o.optJSONArray("items")?.objects().orEmpty().map { Repo.from(it) }
        return SearchPage(o.optInt("total_count"), items)
    }

    fun searchUsers(q: String, page: Int): SearchPage<GhUser> {
        val o = JSONObject(get("/search/users?q=${enc(q)}&per_page=30&page=$page"))
        val items = o.optJSONArray("items")?.objects().orEmpty()
            .map { GhUser(it.str("login"), "", it.str("html_url"), it.str("type")) }
        return SearchPage(o.optInt("total_count"), items)
    }

    fun searchCode(q: String, page: Int): SearchPage<CodeHit> {
        val o = JSONObject(get("/search/code?q=${enc(q)}&per_page=30&page=$page"))
        val items = o.optJSONArray("items")?.objects().orEmpty().map {
            CodeHit(
                fileName = it.str("name"),
                path = it.str("path"),
                repoFullName = it.optJSONObject("repository")?.str("full_name").orEmpty(),
                htmlUrl = it.str("html_url")
            )
        }
        return SearchPage(o.optInt("total_count"), items)
    }

    // ---------- Releases / downloads ----------

    fun releases(fullName: String): List<Release> =
        JSONArray(get("/repos/$fullName/releases?per_page=8")).objects().map { r ->
            val assets = r.optJSONArray("assets")?.objects().orEmpty().map { a ->
                Asset(a.optLong("id"), a.str("name"), a.optLong("size"), a.str("url"))
            }
            Release(r.str("tag_name"), r.str("name"), r.optBoolean("prerelease"), assets)
        }

    /** Downloads any API URL (asset, artifact, logs) into the OutputStream, following redirects. */
    fun downloadTo(url: String, out: OutputStream) {
        client.newCall(builder(url, "application/octet-stream").get().build()).execute().use { r ->
            if (!r.isSuccessful) {
                throw ApiException(r.code, errorText(r.code, r.body?.string().orEmpty()))
            }
            val body = r.body ?: throw IOException(context.getString(R.string.api_err_empty))
            body.byteStream().use { it.copyTo(out) }
        }
    }

    // ---------- File contents ----------

    fun fileSha(fullName: String, path: String, ref: String): String? {
        return try {
            JSONObject(get("/repos/$fullName/contents/$path?ref=${enc(ref)}")).optString("sha").ifBlank { null }
        } catch (e: ApiException) {
            if (e.code == 404) null else throw e
        }
    }

    fun putFile(fullName: String, path: String, content: ByteArray, message: String, branch: String, sha: String?) {
        val o = JSONObject()
            .put("message", message)
            .put("content", Base64.encodeToString(content, Base64.NO_WRAP))
            .put("branch", branch)
        if (sha != null) o.put("sha", sha)
        send("PUT", "/repos/$fullName/contents/$path", o)
    }

    // ---------- GitHub Actions ----------

    fun dispatchWorkflow(fullName: String, workflowFile: String, ref: String) {
        send("POST", "/repos/$fullName/actions/workflows/$workflowFile/dispatches", JSONObject().put("ref", ref))
    }

    fun workflowRuns(fullName: String, workflowFile: String): List<RunInfo> {
        val o = JSONObject(get("/repos/$fullName/actions/workflows/$workflowFile/runs?per_page=20"))
        return o.optJSONArray("workflow_runs")?.objects().orEmpty().map {
            RunInfo(
                id = it.optLong("id"),
                status = it.str("status"),
                conclusion = if (it.isNull("conclusion")) null else it.str("conclusion"),
                htmlUrl = it.str("html_url"),
                number = it.optInt("run_number")
            )
        }
    }

    fun runArtifacts(fullName: String, runId: Long): List<ArtifactInfo> {
        val o = JSONObject(get("/repos/$fullName/actions/runs/$runId/artifacts"))
        return o.optJSONArray("artifacts")?.objects().orEmpty().map {
            ArtifactInfo(it.optLong("id"), it.str("name"), it.optLong("size_in_bytes"), it.optBoolean("expired"))
        }
    }

    fun downloadArtifact(fullName: String, artifactId: Long, out: OutputStream) {
        downloadTo("$base/repos/$fullName/actions/artifacts/$artifactId/zip", out)
    }

    fun downloadRunLogs(fullName: String, runId: Long, out: OutputStream) {
        downloadTo("$base/repos/$fullName/actions/runs/$runId/logs", out)
    }

    // ---------- Git Data (uploading code as a single commit) ----------

    fun branchHead(fullName: String, branch: String): String? {
        return try {
            JSONObject(get("/repos/$fullName/git/ref/heads/$branch")).getJSONObject("object").getString("sha")
        } catch (e: ApiException) {
            if (e.code == 404 || e.code == 409) null else throw e
        }
    }

    fun commitTree(fullName: String, commitSha: String): String =
        JSONObject(get("/repos/$fullName/git/commits/$commitSha")).getJSONObject("tree").getString("sha")

    fun createBlob(fullName: String, bytes: ByteArray): String {
        val o = JSONObject()
        if (bytes.isEmpty()) {
            o.put("content", "").put("encoding", "utf-8")
        } else {
            o.put("content", Base64.encodeToString(bytes, Base64.NO_WRAP)).put("encoding", "base64")
        }
        return JSONObject(send("POST", "/repos/$fullName/git/blobs", o)).getString("sha")
    }

    fun createTree(fullName: String, baseTree: String, entries: JSONArray): String {
        val o = JSONObject().put("base_tree", baseTree).put("tree", entries)
        return JSONObject(send("POST", "/repos/$fullName/git/trees", o)).getString("sha")
    }

    fun createCommit(fullName: String, message: String, treeSha: String, parentSha: String): String {
        val o = JSONObject()
            .put("message", message)
            .put("tree", treeSha)
            .put("parents", JSONArray().put(parentSha))
        return JSONObject(send("POST", "/repos/$fullName/git/commits", o)).getString("sha")
    }

    fun updateRef(fullName: String, branch: String, sha: String) {
        send("PATCH", "/repos/$fullName/git/refs/heads/$branch", JSONObject().put("sha", sha).put("force", false))
    }

    fun createRef(fullName: String, branch: String, sha: String) {
        send("POST", "/repos/$fullName/git/refs", JSONObject().put("ref", "refs/heads/$branch").put("sha", sha))
    }
}
