package com.mahjong.guobiao.update

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** GitHub Releases 上的最新版本信息。 */
data class LatestRelease(
    val versionName: String,   // 归一化后的版本号，如 "0.2.0"（去掉 tag 前缀 v）
    val releaseUrl: String     // Release 页面 URL，点击跳转浏览器
)

/** 基于 GitHub Releases 的版本检查。仓库固定为 origin 的 mahjongHelper。 */
object VersionChecker {

    private const val TAG = "VersionChecker"
    private const val OWNER = "linComupter"
    private const val REPO = "mahjongHelper"
    private const val RELEASES_API = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
    private const val RELEASES_PAGE = "https://github.com/$OWNER/$REPO/releases"

    /**
     * 拉取最新 Release（无网络 / 非 200 / 解析失败返回 null，不抛异常）。
     * GitHub API 要求 User-Agent 请求头，否则返回 403。
     */
    fun fetchLatestRelease(): LatestRelease? {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(RELEASES_API).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "GuobiaoMahjong-UpdateChecker/1.0")
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tag = json.optString("tag_name")
            if (tag.isBlank()) return null
            val url = json.optString("html_url").ifBlank { RELEASES_PAGE }
            LatestRelease(normalize(tag), url)
        } catch (e: Exception) {
            Log.w(TAG, "检查更新失败", e)
            null
        } finally {
            conn?.disconnect()
        }
    }

    /** "v0.2.0" / "V0.2.0" -> "0.2.0"。 */
    private fun normalize(version: String): String =
        version.trim().removePrefix("v").removePrefix("V")

    /** 语义化版本比较：a > b 返回正数，a < b 返回负数，相等返回 0。非数字段按 0 处理。 */
    fun compare(a: String, b: String): Int {
        val pa = a.split('.').map { it.toIntOrNull() ?: 0 }
        val pb = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = if (i < pa.size) pa[i] else 0
            val y = if (i < pb.size) pb[i] else 0
            if (x != y) return x - y
        }
        return 0
    }

    /** latest 是否比 current 新。 */
    fun isNewer(latest: String, current: String): Boolean = compare(latest, current) > 0
}
