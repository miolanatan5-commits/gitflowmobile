package com.natam.gitflowmobile.ui

import androidx.compose.ui.res.stringResource
import com.natam.gitflowmobile.R
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.natam.gitflowmobile.data.CodeHit
import com.natam.gitflowmobile.data.GhUser
import com.natam.gitflowmobile.data.Repo
import java.util.Locale

@Composable
fun ScreenTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier
            .padding(vertical = 8.dp)
            .semantics { heading() }
    )
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .padding(top = 12.dp)
            .semantics { heading() }
    )
}

@Composable
fun LoadingRow(label: String = stringResource(R.string.loading)) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                liveRegion = LiveRegionMode.Polite
            },
        horizontalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun ErrorText(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
    )
}

@Composable
fun StatusText(text: String) {
    if (text.isNotBlank()) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
}

@Composable
fun ToggleRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = text, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

fun formatSize(bytes: Long): String {
    val mb = bytes / 1048576.0
    return if (mb >= 1) String.format(Locale.getDefault(), "%.1f MB", mb)
    else String.format(Locale.getDefault(), "%.0f KB", bytes / 1024.0)
}

@Composable
private fun visibilityText(r: Repo): String =
    stringResource(if (r.isPrivate) R.string.visibility_private else R.string.visibility_public)

@Composable
private fun repoSummary(r: Repo): String {
    val visibility = visibilityText(r)
    val fork = stringResource(R.string.tag_fork)
    val archived = stringResource(R.string.tag_archived)
    val language = if (r.language.isNotBlank()) stringResource(R.string.cd_language, r.language) else ""
    val stars = stringResource(R.string.cd_stars, r.stars)
    return buildString {
        append(r.fullName)
        append(", ").append(visibility)
        if (r.isFork) append(", ").append(fork)
        if (r.archived) append(", ").append(archived)
        if (r.description.isNotBlank()) append(". ").append(r.description)
        if (language.isNotEmpty()) append(". ").append(language)
        append(". ").append(stars)
    }
}

@Composable
fun RepoCard(repo: Repo, onClick: () -> Unit) {
    val visibility = visibilityText(repo)
    val summary = repoSummary(repo)
    val meta = buildString {
        append("★ ${repo.stars}")
        if (repo.language.isNotBlank()) append(" · ${repo.language}")
        append(" · ").append(visibility)
        if (repo.updatedAt.length >= 10) append(" · ${repo.updatedAt.take(10)}")
    }
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .semantics(mergeDescendants = true) { contentDescription = summary }
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = repo.fullName, style = MaterialTheme.typography.titleMedium)
            if (repo.description.isNotBlank()) {
                Text(text = repo.description, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Text(text = meta, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun UserCard(user: GhUser, onClick: () -> Unit) {
    val kind = stringResource(
        if (user.type.equals("Organization", ignoreCase = true)) R.string.user_kind_org else R.string.user_kind_user
    )
    val description = stringResource(R.string.cd_open_repos, user.login, kind)
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .semantics(mergeDescendants = true) { contentDescription = description }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = user.login, style = MaterialTheme.typography.titleMedium)
            Text(text = kind, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun CodeCard(hit: CodeHit, onClick: () -> Unit) {
    val description = stringResource(R.string.cd_code_hit, hit.path, hit.repoFullName)
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .semantics(mergeDescendants = true) { contentDescription = description }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = hit.path, style = MaterialTheme.typography.titleSmall)
            Text(text = hit.repoFullName, style = MaterialTheme.typography.bodySmall)
        }
    }
}

fun installApk(context: Context, uri: Uri) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, context.getString(R.string.toast_install_failed), Toast.LENGTH_LONG).show()
    }
}
