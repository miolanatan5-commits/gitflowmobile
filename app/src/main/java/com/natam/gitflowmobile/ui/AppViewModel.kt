package com.natam.gitflowmobile.ui

import android.app.Application
import androidx.annotation.StringRes
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.natam.gitflowmobile.R
import com.natam.gitflowmobile.data.ApiException
import com.natam.gitflowmobile.data.Asset
import com.natam.gitflowmobile.data.BuildService
import com.natam.gitflowmobile.data.CodeHit
import com.natam.gitflowmobile.data.FileUtils
import com.natam.gitflowmobile.data.GhUser
import com.natam.gitflowmobile.data.GitHubApi
import com.natam.gitflowmobile.data.Release
import com.natam.gitflowmobile.data.Repo
import com.natam.gitflowmobile.data.SavedFile
import com.natam.gitflowmobile.data.TokenStore
import com.natam.gitflowmobile.data.Uploader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.SocketTimeoutException
import java.net.UnknownHostException

sealed interface Screen {
    data class Detail(val fullName: String) : Screen
    data class User(val login: String) : Screen
}

enum class Tab(@StringRes val labelRes: Int) {
    MY_REPOS(R.string.tab_my_repos),
    SEARCH(R.string.tab_search),
    ACCOUNT(R.string.tab_account)
}

enum class SearchType(@StringRes val labelRes: Int) {
    REPOS(R.string.type_repos),
    USERS(R.string.type_users),
    CODE(R.string.type_code)
}

enum class SearchSort(@StringRes val labelRes: Int, val api: String) {
    MATCH(R.string.sort_match, ""),
    STARS(R.string.sort_stars, "stars"),
    UPDATED(R.string.sort_updated, "updated")
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    // ---------- Infrastructure ----------
    private var store: TokenStore? = null
    private val api = GitHubApi(app) { token }
    private val uploader = Uploader(app, api)
    private val builder = BuildService(app, api)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()
    private fun str(@StringRes id: Int, vararg args: Any): String {
        val ctx = getApplication<Application>()
        return if (args.isEmpty()) ctx.getString(id) else ctx.getString(id, *args)
    }

    private fun say(text: String) {
        _messages.tryEmit(text)
    }

    private fun friendly(e: Throwable): String = when (e) {
        is ApiException -> e.message ?: str(R.string.err_api_generic, e.code)
        is UnknownHostException -> str(R.string.err_no_internet)
        is SocketTimeoutException -> str(R.string.err_timeout)
        else -> e.message ?: str(R.string.err_unexpected)
    }

    // ---------- Session ----------
    var storeError by mutableStateOf<String?>(null)
        private set
    var initializing by mutableStateOf(true)
        private set
    var token by mutableStateOf("")
        private set
    var login by mutableStateOf("")
        private set
    var loginBusy by mutableStateOf(false)
        private set
    var loginError by mutableStateOf<String?>(null)
        private set

    val stack = mutableStateListOf<Screen>()
    var tab by mutableStateOf(Tab.MY_REPOS)

    fun initStore() {
        storeError = null
        initializing = true
        try {
            val s = TokenStore(getApplication<Application>())
            store = s
            token = s.getToken()
        } catch (e: Exception) {
            storeError = str(R.string.err_store, e.message ?: str(R.string.err_unknown))
            initializing = false
            return
        }
        if (token.isNotEmpty()) {
            viewModelScope.launch {
                try {
                    val u = withContext(Dispatchers.IO) { api.currentUser() }
                    login = u.login
                    loadMyRepos(true)
                } catch (e: ApiException) {
                    if (e.code == 401) {
                        token = ""
                        store?.clear()
                        loginError = e.message
                    } else {
                        say(friendly(e))
                    }
                } catch (e: Exception) {
                    say(friendly(e))
                }
                initializing = false
            }
        } else {
            initializing = false
        }
    }

    fun signIn(input: String) {
        val t = input.trim()
        if (t.isEmpty()) {
            loginError = str(R.string.login_paste_token)
            return
        }
        if (loginBusy) return
        loginBusy = true
        loginError = null
        viewModelScope.launch {
            val previous = token
            token = t
            try {
                val u = withContext(Dispatchers.IO) { api.currentUser() }
                login = u.login
                withContext(Dispatchers.IO) { store?.saveToken(t) }
                loadMyRepos(true)
            } catch (e: Exception) {
                token = previous
                loginError = friendly(e)
            }
            loginBusy = false
        }
    }

    fun signOut() {
        store?.clear()
        token = ""
        login = ""
        myRepos.clear()
        stack.clear()
        tab = Tab.MY_REPOS
        say(str(R.string.msg_signed_out))
    }

    // ---------- Navigation ----------
    fun pop() {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
    }

    // ---------- My repositories ----------
    val myRepos = mutableStateListOf<Repo>()
    var myLoading by mutableStateOf(false)
        private set
    var myError by mutableStateOf<String?>(null)
        private set
    var myEnd by mutableStateOf(false)
        private set
    var myFilter by mutableStateOf("")
    private var myPage = 1

    fun loadMyRepos(reset: Boolean) {
        if (myLoading) return
        viewModelScope.launch {
            myLoading = true
            myError = null
            try {
                if (reset) {
                    myPage = 1
                    myEnd = false
                }
                val page = withContext(Dispatchers.IO) { api.myRepos(myPage) }
                if (reset) myRepos.clear()
                myRepos.addAll(page)
                if (page.size < 50) myEnd = true else myPage++
            } catch (e: Exception) {
                myError = friendly(e)
            }
            myLoading = false
        }
    }

    var createBusy by mutableStateOf(false)
        private set
    var createError by mutableStateOf<String?>(null)

    fun createRepo(name: String, description: String, isPrivate: Boolean, autoInit: Boolean, onSuccess: () -> Unit) {
        val n = name.trim()
        if (n.isEmpty()) {
            createError = str(R.string.create_name_required)
            return
        }
        if (createBusy) return
        createBusy = true
        createError = null
        viewModelScope.launch {
            try {
                val r = withContext(Dispatchers.IO) { api.createRepo(n, description.trim(), isPrivate, autoInit) }
                myRepos.add(0, r)
                say(str(R.string.repo_created, r.fullName))
                onSuccess()
            } catch (e: Exception) {
                createError = friendly(e)
            }
            createBusy = false
        }
    }

    var deleteBusy by mutableStateOf(false)
        private set

    fun deleteRepo(repo: Repo, onSuccess: () -> Unit) {
        if (deleteBusy) return
        deleteBusy = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { api.deleteRepo(repo.fullName) }
                myRepos.removeAll { it.fullName.equals(repo.fullName, ignoreCase = true) }
                say(str(R.string.repo_deleted, repo.fullName))
                onSuccess()
            } catch (e: Exception) {
                say(str(R.string.msg_delete_failed, friendly(e)))
            }
            deleteBusy = false
        }
    }

    // ---------- Search ----------
    var query by mutableStateOf("")
    var searchType by mutableStateOf(SearchType.REPOS)
    var searchSort by mutableStateOf(SearchSort.MATCH)
    var resultType by mutableStateOf(SearchType.REPOS)
        private set
    val repoResults = mutableStateListOf<Repo>()
    val userResults = mutableStateListOf<GhUser>()
    val codeResults = mutableStateListOf<CodeHit>()
    var searchTotal by mutableIntStateOf(-1)
        private set
    var searchLoading by mutableStateOf(false)
        private set
    var searchError by mutableStateOf<String?>(null)
        private set
    var searchEnd by mutableStateOf(true)
        private set
    private var searchPage = 1
    private var lastQuery = ""
    private var lastType = SearchType.REPOS
    private var lastSort = SearchSort.MATCH

    fun search(reset: Boolean) {
        val q = query.trim()
        if (reset && q.isEmpty()) {
            say(str(R.string.msg_search_term_required))
            return
        }
        if (searchLoading) return
        viewModelScope.launch {
            searchLoading = true
            searchError = null
            try {
                if (reset) {
                    searchPage = 1
                    searchEnd = false
                    lastQuery = q
                    lastType = searchType
                    lastSort = searchSort
                    resultType = searchType
                    repoResults.clear()
                    userResults.clear()
                    codeResults.clear()
                }
                var count = 0
                when (lastType) {
                    SearchType.REPOS -> {
                        val p = withContext(Dispatchers.IO) { api.searchRepos(lastQuery, searchPage, lastSort.api) }
                        repoResults.addAll(p.items)
                        searchTotal = p.total
                        count = p.items.size
                    }
                    SearchType.USERS -> {
                        val p = withContext(Dispatchers.IO) { api.searchUsers(lastQuery, searchPage) }
                        userResults.addAll(p.items)
                        searchTotal = p.total
                        count = p.items.size
                    }
                    SearchType.CODE -> {
                        val p = withContext(Dispatchers.IO) { api.searchCode(lastQuery, searchPage) }
                        codeResults.addAll(p.items)
                        searchTotal = p.total
                        count = p.items.size
                    }
                }
                // GitHub only returns the first 1000 results
                if (count < 30 || searchPage >= 33) searchEnd = true else searchPage++
            } catch (e: Exception) {
                searchError = friendly(e)
            }
            searchLoading = false
        }
    }

    // ---------- Repository detail ----------
    var detail by mutableStateOf<Repo?>(null)
        private set
    var detailLoading by mutableStateOf(false)
        private set
    var detailError by mutableStateOf<String?>(null)
        private set
    val releases = mutableStateListOf<Release>()

    fun openRepo(fullName: String, preview: Repo? = null) {
        stack.add(Screen.Detail(fullName))
        if (!busy) {
            buildBranch = ""
        }
        loadDetail(fullName, preview)
    }

    fun loadDetail(fullName: String, preview: Repo? = null) {
        viewModelScope.launch {
            detail = preview
            detailLoading = true
            detailError = null
            releases.clear()
            try {
                val r = withContext(Dispatchers.IO) { api.repo(fullName) }
                detail = r
                val rel = withContext(Dispatchers.IO) {
                    try {
                        api.releases(fullName)
                    } catch (e: ApiException) {
                        emptyList()
                    }
                }
                releases.addAll(rel)
            } catch (e: Exception) {
                detailError = friendly(e)
            }
            detailLoading = false
        }
    }

    // ---------- A user's repositories ----------
    val userRepos = mutableStateListOf<Repo>()
    var userReposLoading by mutableStateOf(false)
        private set
    var userReposError by mutableStateOf<String?>(null)
        private set
    var userReposEnd by mutableStateOf(false)
        private set
    private var userReposPage = 1
    private var userReposLogin = ""

    fun openUser(login: String) {
        stack.add(Screen.User(login))
        loadUserRepos(login, true)
    }

    fun loadUserRepos(login: String, reset: Boolean) {
        if (userReposLoading) return
        viewModelScope.launch {
            userReposLoading = true
            userReposError = null
            try {
                if (reset) {
                    userReposPage = 1
                    userReposEnd = false
                    userReposLogin = login
                    userRepos.clear()
                }
                val page = withContext(Dispatchers.IO) { api.userRepos(userReposLogin, userReposPage) }
                userRepos.addAll(page)
                if (page.size < 50) userReposEnd = true else userReposPage++
            } catch (e: Exception) {
                userReposError = friendly(e)
            }
            userReposLoading = false
        }
    }

    // ---------- Actions: download, upload, build ----------
    var actionRepo by mutableStateOf("")
        private set
    var downloading by mutableStateOf(false)
        private set
    var downloadStatus by mutableStateOf("")
        private set
    val savedFiles = mutableStateListOf<SavedFile>()

    var uploadRunning by mutableStateOf(false)
        private set
    var uploadStatus by mutableStateOf("")
        private set

    var buildRunning by mutableStateOf(false)
        private set
    var buildStatus by mutableStateOf("")
        private set
    var buildOutcome by mutableStateOf<BuildService.BuildOutcome?>(null)
        private set
    var buildBranch by mutableStateOf("")

    val busy: Boolean get() = uploadRunning || buildRunning || downloading

    private fun beginAction(repo: Repo): Boolean {
        if (busy) {
            say(str(R.string.msg_wait_task))
            return false
        }
        if (actionRepo != repo.fullName) {
            savedFiles.clear()
            buildOutcome = null
            uploadStatus = ""
            buildStatus = ""
            downloadStatus = ""
        }
        actionRepo = repo.fullName
        return true
    }

    fun downloadAsset(repo: Repo, asset: Asset) {
        if (!beginAction(repo)) return
        downloading = true
        downloadStatus = str(R.string.download_downloading, asset.name)
        viewModelScope.launch {
            try {
                val uri = withContext(Dispatchers.IO) {
                    FileUtils.saveToDownloads(
                        getApplication<Application>(), asset.name, "application/vnd.android.package-archive"
                    ) { out -> api.downloadTo(asset.apiUrl, out) }
                }
                savedFiles.add(SavedFile(asset.name, uri))
                downloadStatus = str(R.string.download_saved, asset.name)
            } catch (e: Exception) {
                downloadStatus = str(R.string.download_failed, friendly(e))
            }
            downloading = false
        }
    }

    fun startUpload(repo: Repo, zip: Uri, branch: String, commitMessage: String, stripRoot: Boolean) {
        if (!beginAction(repo)) return
        uploadRunning = true
        uploadStatus = str(R.string.upload_starting)
        viewModelScope.launch {
            try {
                val result = uploader.upload(repo, zip, branch, commitMessage, stripRoot) { uploadStatus = it }
                uploadStatus = result
                say(str(R.string.msg_code_uploaded, repo.fullName))
            } catch (e: Exception) {
                uploadStatus = str(R.string.upload_failed, friendly(e))
            }
            uploadRunning = false
        }
    }

    fun startBuild(repo: Repo, recreateWorkflow: Boolean) {
        if (!beginAction(repo)) return
        buildRunning = true
        buildOutcome = null
        buildStatus = str(R.string.build_starting)
        val branch = buildBranch.trim().ifEmpty { repo.defaultBranch }
        viewModelScope.launch {
            try {
                val outcome = builder.runBuild(repo, branch, recreateWorkflow) { buildStatus = it }
                buildOutcome = outcome
                if (outcome.success) {
                    buildStatus = str(R.string.build_downloading_apk)
                    val files = withContext(Dispatchers.IO) { builder.saveApks(repo, outcome.runId) }
                    savedFiles.addAll(files)
                    buildStatus = str(R.string.build_done, files.joinToString(", ") { it.name })
                    say(str(R.string.msg_apk_saved))
                } else {
                    buildStatus = str(
                        R.string.build_failed,
                        outcome.conclusion.ifEmpty { str(R.string.build_no_details) }
                    )
                }
            } catch (e: Exception) {
                buildStatus = str(R.string.build_error, friendly(e))
            }
            buildRunning = false
        }
    }

    var logBusy by mutableStateOf(false)
        private set

    fun downloadLog(repo: Repo, runId: Long) {
        if (logBusy) return
        logBusy = true
        viewModelScope.launch {
            try {
                val f = withContext(Dispatchers.IO) { builder.saveLogs(repo, runId) }
                say(str(R.string.msg_log_saved, f.name))
            } catch (e: Exception) {
                say(str(R.string.msg_log_failed, friendly(e)))
            }
            logBusy = false
        }
    }

    init {
        initStore()
    }
}
