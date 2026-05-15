package com.example.miuipower.heartrate

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.getcapacitor.JSObject
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class HeartRateGithubUploader(context: Context) {
    private val appContext = context.applicationContext
    private val preferences: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveSettings(
        owner: String,
        repo: String,
        branch: String,
        pathPrefix: String,
        token: String?,
    ): JSObject {
        val editor = preferences.edit()
            .putString(KEY_OWNER, owner.trim())
            .putString(KEY_REPO, repo.trim())
            .putString(KEY_BRANCH, branch.trim().ifEmpty { DEFAULT_BRANCH })
            .putString(KEY_PATH_PREFIX, normalizePathPrefix(pathPrefix))
        if (!token.isNullOrBlank()) {
            saveEncryptedToken(token.trim())
        }
        editor.apply()
        return getSettings().apply {
            put("ok", true)
            put("method", "github_settings_saved")
        }
    }

    fun getSettings(): JSObject {
        val settings = readSettings()
        return JSObject().apply {
            put("ok", true)
            put("method", "github_settings")
            put("owner", settings.owner)
            put("repo", settings.repo)
            put("branch", settings.branch)
            put("pathPrefix", settings.pathPrefix)
            put("tokenSaved", readToken() != null)
        }
    }

    fun testSettings(): JSObject {
        val settings = readSettings()
        val token = readToken()
        val invalid = validateSettings(settings, token)
        if (invalid != null) {
            return invalid
        }

        val url = "${GITHUB_API}/repos/${encodeSegment(settings.owner)}/${encodeSegment(settings.repo)}/branches/${encodeSegment(settings.branch)}"
        val response = request("GET", url, token = token)
        return if (response.code in 200..299) {
            JSObject().apply {
                put("ok", true)
                put("method", "github_branch")
                put("owner", settings.owner)
                put("repo", settings.repo)
                put("branch", settings.branch)
            }
        } else {
            githubError("github_branch", response)
        }
    }

    fun uploadExport(export: HeartRateExportFile, requestedPath: String?): JSObject {
        val settings = readSettings()
        val token = readToken()
        val invalid = validateSettings(settings, token)
        if (invalid != null) {
            return invalid
        }

        val basePath = requestedUploadPath(settings, export, requestedPath)
        val contentBase64 = Base64.encodeToString(export.file.readBytes(), Base64.NO_WRAP)
        var lastDuplicateResponse: HttpResponse? = null
        for (attempt in 0..9) {
            val candidatePath = if (attempt == 0) basePath else withDuplicateSuffix(basePath, attempt)
            val body = JSONObject().apply {
                put("message", "Add heart-rate export ${export.file.name}")
                put("content", contentBase64)
                put("branch", settings.branch)
            }
            val url = "${GITHUB_API}/repos/${encodeSegment(settings.owner)}/${encodeSegment(settings.repo)}/contents/${encodePath(candidatePath)}"
            val response = request("PUT", url, token = token, body = body.toString())
            if (response.code in 200..299) {
                val json = runCatching { JSONObject(response.text) }.getOrNull()
                val commitSha = json?.optJSONObject("commit")?.optString("sha", "")
                val htmlUrl = json?.optJSONObject("content")?.optString("html_url", "")
                return JSObject().apply {
                    put("ok", true)
                    put("method", "github_contents_api")
                    put("fileName", export.file.name)
                    put("path", candidatePath)
                    put("rowCount", export.result.optInt("rowCount", 0))
                    if (!commitSha.isNullOrBlank()) {
                        put("commitSha", commitSha)
                    }
                    if (!htmlUrl.isNullOrBlank()) {
                        put("htmlUrl", htmlUrl)
                    }
                }
            }
            if (isDuplicatePath(response)) {
                lastDuplicateResponse = response
                continue
            }
            return githubError("github_contents_api", response).apply {
                put("fileName", export.file.name)
                put("path", candidatePath)
                put("rowCount", export.result.optInt("rowCount", 0))
            }
        }

        return JSObject().apply {
            put("ok", false)
            put("method", "duplicate_path")
            put("fileName", export.file.name)
            put("path", basePath)
            put("rowCount", export.result.optInt("rowCount", 0))
            put("statusCode", lastDuplicateResponse?.code ?: 422)
            put("error", "GitHub path already exists after duplicate suffix retries.")
        }
    }

    private fun readSettings(): GithubSettings {
        return GithubSettings(
            owner = preferences.getString(KEY_OWNER, "")?.trim().orEmpty(),
            repo = preferences.getString(KEY_REPO, "")?.trim().orEmpty(),
            branch = preferences.getString(KEY_BRANCH, DEFAULT_BRANCH)?.trim().orEmpty().ifEmpty { DEFAULT_BRANCH },
            pathPrefix = normalizePathPrefix(preferences.getString(KEY_PATH_PREFIX, DEFAULT_PATH_PREFIX).orEmpty()),
        )
    }

    private fun validateSettings(settings: GithubSettings, token: String?): JSObject? {
        if (settings.owner.isBlank() || settings.repo.isBlank() || settings.branch.isBlank()) {
            return JSObject().apply {
                put("ok", false)
                put("method", "invalid_settings")
                put("error", "GitHub owner, repo, and branch are required.")
            }
        }
        if (token.isNullOrBlank()) {
            return JSObject().apply {
                put("ok", false)
                put("method", "missing_token")
                put("error", "A GitHub fine-grained PAT with Contents write access is required.")
            }
        }
        return null
    }

    private fun requestedUploadPath(
        settings: GithubSettings,
        export: HeartRateExportFile,
        requestedPath: String?,
    ): String {
        val trimmedPath = requestedPath?.trim().orEmpty()
        if (trimmedPath.isNotBlank()) {
            return if (trimmedPath.endsWith("/") || trimmedPath.endsWith("\\")) {
                normalizeRepoPath("$trimmedPath/${export.file.name}")
            } else {
                normalizeRepoPath(trimmedPath)
            }
        }

        val createdAtMs = export.result.optLong("createdAtMs", System.currentTimeMillis())
        val yearMonth = SimpleDateFormat("yyyy/MM", Locale.US).format(Date(createdAtMs))
        return normalizeRepoPath("${settings.pathPrefix}/$yearMonth/${export.file.name}")
    }

    private fun withDuplicateSuffix(path: String, suffix: Int): String {
        val slashIndex = path.lastIndexOf('/')
        val directory = if (slashIndex >= 0) path.substring(0, slashIndex + 1) else ""
        val name = if (slashIndex >= 0) path.substring(slashIndex + 1) else path
        val dotIndex = name.lastIndexOf('.')
        val nextName = if (dotIndex > 0) {
            "${name.substring(0, dotIndex)}_$suffix${name.substring(dotIndex)}"
        } else {
            "${name}_$suffix"
        }
        return "$directory$nextName"
    }

    private fun isDuplicatePath(response: HttpResponse): Boolean {
        if (response.code != 422) {
            return false
        }
        val text = response.text.lowercase(Locale.US)
        return text.contains("already exists") || text.contains("\"sha\"") || text.contains("sha")
    }

    private fun githubError(method: String, response: HttpResponse): JSObject {
        val message = runCatching {
            JSONObject(response.text).optString("message", response.text)
        }.getOrDefault(response.text)
        return JSObject().apply {
            put("ok", false)
            put("method", method)
            put("statusCode", response.code)
            put("error", "${response.code} ${message.take(400)}")
        }
    }

    private fun request(
        method: String,
        url: String,
        token: String?,
        body: String? = null,
    ): HttpResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", GITHUB_API_VERSION)
            connection.setRequestProperty("User-Agent", "MiuiPower")
            if (!token.isNullOrBlank()) {
                connection.setRequestProperty("Authorization", "Bearer $token")
            }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { stream ->
                    stream.write(body.toByteArray(Charsets.UTF_8))
                }
            }
            val code = connection.responseCode
            val stream = runCatching {
                if (code in 200..299) connection.inputStream else connection.errorStream ?: connection.inputStream
            }.getOrNull()
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return HttpResponse(code, text)
        } finally {
            connection.disconnect()
        }
    }

    private fun saveEncryptedToken(token: String) {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        preferences.edit()
            .putString(KEY_TOKEN, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(KEY_TOKEN_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    private fun readToken(): String? {
        val encryptedText = preferences.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() } ?: return null
        val ivText = preferences.getString(KEY_TOKEN_IV, null)?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val encrypted = Base64.decode(encryptedText, Base64.NO_WRAP)
            val iv = Base64.decode(ivText, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) {
            return existing
        }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun normalizePathPrefix(value: String): String {
        return normalizeRepoPath(value.ifBlank { DEFAULT_PATH_PREFIX })
    }

    private fun normalizeRepoPath(value: String): String {
        val parts = value
            .replace('\\', '/')
            .split('/')
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "." }
        require(parts.none { it == ".." }) { "Repository paths cannot contain .. segments." }
        return parts.joinToString("/")
    }

    private fun encodePath(path: String): String {
        return path.split('/').joinToString("/") { encodeSegment(it) }
    }

    private fun encodeSegment(value: String): String {
        return URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    }

    private data class GithubSettings(
        val owner: String,
        val repo: String,
        val branch: String,
        val pathPrefix: String,
    )

    private data class HttpResponse(
        val code: Int,
        val text: String,
    )

    companion object {
        private const val PREFS_NAME = "heart_rate_github.v1"
        private const val KEY_OWNER = "owner"
        private const val KEY_REPO = "repo"
        private const val KEY_BRANCH = "branch"
        private const val KEY_PATH_PREFIX = "path_prefix"
        private const val KEY_TOKEN = "token"
        private const val KEY_TOKEN_IV = "token_iv"
        private const val DEFAULT_BRANCH = "main"
        private const val DEFAULT_PATH_PREFIX = "data/heart_rate"
        private const val GITHUB_API = "https://api.github.com"
        private const val GITHUB_API_VERSION = "2026-03-10"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "miui_power_github_pat_v1"
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
