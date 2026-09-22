package com.natam.gitflowmobile.data

import android.content.Context
import com.natam.gitflowmobile.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.zip.ZipFile

/** Builds the project on GitHub Actions and downloads the APK / the log. */
class BuildService(private val context: Context, private val api: GitHubApi) {

    companion object {
        const val WORKFLOW_FILE = "gitflowmobile-build.yml"
        const val WORKFLOW_PATH = ".github/workflows/gitflowmobile-build.yml"
    }

    data class BuildOutcome(
        val runId: Long,
        val success: Boolean,
        val conclusion: String,
        val htmlUrl: String
    )

    private val workflowYaml = """
name: Build APK (GitFlow Mobile)

on:
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Check out the code
        uses: actions/checkout@v4

      - name: Set up Java
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'

      - name: Set up Gradle (project without wrapper)
        if: hashFiles('gradlew') == ''
        uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: current

      - name: Set up Gradle (project with wrapper)
        if: hashFiles('gradlew') != ''
        uses: gradle/actions/setup-gradle@v4

      - name: Build with the wrapper
        if: hashFiles('gradlew') != ''
        run: |
          chmod +x gradlew
          ./gradlew assembleDebug --stacktrace

      - name: Build with the installed Gradle
        if: hashFiles('gradlew') == ''
        run: gradle assembleDebug --stacktrace

      - name: Store the APK
        uses: actions/upload-artifact@v4
        with:
          name: apk
          path: "**/build/outputs/apk/**/*.apk"
          if-no-files-found: error
""".trimStart()

    private fun ensureWorkflow(repo: Repo, branch: String, force: Boolean) {
        val sha = api.fileSha(repo.fullName, WORKFLOW_PATH, branch)
        if (sha != null && !force) return
        api.putFile(
            repo.fullName,
            WORKFLOW_PATH,
            workflowYaml.toByteArray(Charsets.UTF_8),
            "Add build workflow (GitFlow Mobile)",
            branch,
            sha
        )
    }

    suspend fun runBuild(
        repo: Repo,
        branch: String,
        recreateWorkflow: Boolean,
        status: (String) -> Unit
    ): BuildOutcome = withContext(Dispatchers.IO) {
        status(context.getString(R.string.bs_checking))
        ensureWorkflow(repo, repo.defaultBranch, recreateWorkflow)
        if (branch != repo.defaultBranch) ensureWorkflow(repo, branch, recreateWorkflow)

        val before = try {
            api.workflowRuns(repo.fullName, WORKFLOW_FILE).maxOfOrNull { it.id } ?: 0L
        } catch (e: ApiException) {
            0L
        }

        status(context.getString(R.string.bs_starting))
        var attempts = 0
        while (true) {
            try {
                api.dispatchWorkflow(repo.fullName, WORKFLOW_FILE, branch)
                break
            } catch (e: ApiException) {
                attempts++
                if (attempts >= 8 || (e.code != 404 && e.code != 422)) throw e
                delay(5000)
            }
        }

        status(context.getString(R.string.bs_waiting_run))
        var found: RunInfo? = null
        for (i in 0 until 30) {
            delay(3000)
            found = try {
                api.workflowRuns(repo.fullName, WORKFLOW_FILE).firstOrNull { it.id > before }
            } catch (e: IOException) {
                null
            }
            if (found != null) break
        }
        val started = found
            ?: throw IOException(context.getString(R.string.bs_err_no_run))

        var current = started
        var lastText = ""
        var polls = 0
        while (current.status != "completed") {
            val text = when (current.status) {
                "queued", "waiting", "pending", "requested" -> context.getString(R.string.bs_queued)
                "in_progress" -> context.getString(R.string.bs_building)
                else -> context.getString(R.string.bs_waiting)
            }
            if (text != lastText) {
                status(text)
                lastText = text
            }
            delay(6000)
            polls++
            if (polls > 600) throw IOException(context.getString(R.string.bs_timeout))
            try {
                current = api.workflowRuns(repo.fullName, WORKFLOW_FILE).firstOrNull { it.id == started.id } ?: current
            } catch (e: IOException) {
                if (e is ApiException && e.code in 400..499 && e.code != 429) throw e
            }
        }
        BuildOutcome(current.id, current.conclusion == "success", current.conclusion.orEmpty(), current.htmlUrl)
    }

    private fun stamp(): String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    fun saveApks(repo: Repo, runId: Long): List<SavedFile> {
        val artifacts = api.runArtifacts(repo.fullName, runId).filter { !it.expired }
        if (artifacts.isEmpty()) throw IOException(context.getString(R.string.bs_err_no_apk))
        val saved = ArrayList<SavedFile>()
        val time = stamp()
        for (a in artifacts) {
            val tmp = File(context.cacheDir, "artifact-${a.id}.zip")
            try {
                tmp.outputStream().use { api.downloadArtifact(repo.fullName, a.id, it) }
                ZipFile(tmp).use { zf ->
                    val entries = Collections.list(zf.entries())
                        .filter { !it.isDirectory && it.name.endsWith(".apk", ignoreCase = true) }
                    val chosen = entries.filter { it.name.contains("universal", ignoreCase = true) }
                        .ifEmpty { entries }
                    for (en in chosen) {
                        val base = en.name.substringAfterLast('/')
                        val fileName = "${repo.name}-$time-$base"
                        val uri = FileUtils.saveToDownloads(
                            context, fileName, "application/vnd.android.package-archive"
                        ) { out -> zf.getInputStream(en).use { it.copyTo(out) } }
                        saved.add(SavedFile(fileName, uri))
                    }
                }
            } finally {
                tmp.delete()
            }
        }
        if (saved.isEmpty()) throw IOException(context.getString(R.string.bs_err_no_apk_file))
        return saved
    }

    fun saveLogs(repo: Repo, runId: Long): SavedFile {
        val tmp = File(context.cacheDir, "logs-$runId.zip")
        try {
            tmp.outputStream().use { api.downloadRunLogs(repo.fullName, runId, it) }
            val fileName = "${repo.name}-log-${stamp()}.txt"
            var uri: android.net.Uri? = null
            ZipFile(tmp).use { zf ->
                val all = Collections.list(zf.entries()).filter { !it.isDirectory }.sortedBy { it.name }
                val top = all.filter { !it.name.contains('/') }
                val chosen = top.ifEmpty { all }
                uri = FileUtils.saveToDownloads(context, fileName, "text/plain") { out ->
                    for (en in chosen) {
                        out.write("===== ${en.name} =====\n".toByteArray())
                        zf.getInputStream(en).use { it.copyTo(out) }
                        out.write("\n".toByteArray())
                    }
                }
            }
            return SavedFile(fileName, uri ?: throw IOException(context.getString(R.string.bs_err_log)))
        } finally {
            tmp.delete()
        }
    }
}
