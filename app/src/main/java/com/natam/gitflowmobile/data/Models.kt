package com.natam.gitflowmobile.data

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

fun JSONObject.str(name: String): String = if (isNull(name)) "" else optString(name, "")

fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

data class Repo(
    val id: Long,
    val name: String,
    val fullName: String,
    val owner: String,
    val description: String,
    val isPrivate: Boolean,
    val stars: Int,
    val forks: Int,
    val language: String,
    val defaultBranch: String,
    val htmlUrl: String,
    val updatedAt: String,
    val isFork: Boolean,
    val archived: Boolean,
    val canPush: Boolean,
    val canAdmin: Boolean
) {
    companion object {
        fun from(o: JSONObject): Repo {
            val perms = o.optJSONObject("permissions")
            return Repo(
                id = o.optLong("id"),
                name = o.str("name"),
                fullName = o.str("full_name"),
                owner = o.optJSONObject("owner")?.str("login").orEmpty(),
                description = o.str("description"),
                isPrivate = o.optBoolean("private"),
                stars = o.optInt("stargazers_count"),
                forks = o.optInt("forks_count"),
                language = o.str("language"),
                defaultBranch = o.str("default_branch").ifEmpty { "main" },
                htmlUrl = o.str("html_url"),
                updatedAt = o.str("pushed_at").ifEmpty { o.str("updated_at") },
                isFork = o.optBoolean("fork"),
                archived = o.optBoolean("archived"),
                canPush = perms?.optBoolean("push") ?: false,
                canAdmin = perms?.optBoolean("admin") ?: false
            )
        }
    }
}

data class GhUser(
    val login: String,
    val name: String,
    val htmlUrl: String,
    val type: String
)

data class CodeHit(
    val fileName: String,
    val path: String,
    val repoFullName: String,
    val htmlUrl: String
)

data class Asset(
    val id: Long,
    val name: String,
    val size: Long,
    val apiUrl: String
)

data class Release(
    val tag: String,
    val name: String,
    val prerelease: Boolean,
    val assets: List<Asset>
)

data class RunInfo(
    val id: Long,
    val status: String,
    val conclusion: String?,
    val htmlUrl: String,
    val number: Int
)

data class ArtifactInfo(
    val id: Long,
    val name: String,
    val size: Long,
    val expired: Boolean
)

data class SearchPage<T>(
    val total: Int,
    val items: List<T>
)

data class SavedFile(
    val name: String,
    val uri: Uri
)
