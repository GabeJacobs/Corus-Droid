package fm.corus.android.service

import java.net.URI
import java.net.URLDecoder
import java.util.Base64

/** Kept independent of Android so URL ambiguity and case handling are testable on the JVM. */
object ReferralLinks {
    private fun canonical(s: String?) =
        s?.takeIf { it.matches(Regex("[a-fA-F0-9]{24}")) }?.lowercase()

    fun brandedURL(code: String): String? =
        canonical(code)?.let { value ->
            "https://invite.corus.fm/" +
                Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(value.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
        }

    fun attribution(route: String?, token: String?, destination: String? = null): String? {
        val code = canonical(token) ?: return null
        return code.takeIf {
            route == "invite" && (destination == null || parse(destination) == code)
        }
    }

    fun parse(raw: String): String? {
        val text = raw.trim()
        canonical(text)?.let {
            return it
        }
        if (text.matches(Regex("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{8}", RegexOption.IGNORE_CASE)))
            return text.uppercase()
        return try {
            val uri = URI(text)
            val host = uri.host?.lowercase()
            val path = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
            if (uri.userInfo != null || uri.port != -1) return null
            val query =
                uri.rawQuery
                    .orEmpty()
                    .split('&')
                    .filter { it.isNotEmpty() }
                    .map { part ->
                        val kv = part.split('=', limit = 2)
                        URLDecoder.decode(kv[0], "UTF-8") to
                            URLDecoder.decode(kv.getOrElse(1) { "" }, "UTF-8")
                    }
            fun one(name: String) = query.count { it.first == name } <= 1
            fun value(name: String) = query.firstOrNull { it.first == name }?.second
            if (uri.scheme == "corus" && host == "invite" && path.size == 1) return parse(path[0])
            if (uri.scheme != "https") return null
            when {
                host == "invite.corus.fm" &&
                    path.size == 1 &&
                    path[0].matches(Regex("[A-Za-z0-9_-]{16}")) -> {
                    val bytes = Base64.getUrlDecoder().decode(path[0])
                    if (bytes.size == 12)
                        bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
                    else null
                }
                host == "corus.onelink.me" && path.firstOrNull() == "fgUi" ->
                    if (listOf("deep_link_value", "deep_link_sub1", "af_dp").all(::one))
                        attribution(
                            value("deep_link_value"),
                            value("deep_link_sub1"),
                            value("af_dp"),
                        )
                    else null
                host == "corus.sng.link" && uri.path == "/Cygn9/dusl" -> {
                    if (!listOf("_dl", "_ddl", "_p").all(::one)) return null
                    val values =
                        query
                            .filter { it.first in listOf("_dl", "_ddl", "_p") }
                            .map {
                                if (it.first == "_p") canonical(it.second) else parse(it.second)
                            }
                    values.firstOrNull()?.takeIf { c -> values.all { it == c } }
                }
                host in listOf("corus.fm", "www.corus.fm", "app.corus.fm") &&
                    path.size == 2 &&
                    path[0] == "invite" -> parse(path[1])
                host in listOf("corus.fm", "www.corus.fm", "app.corus.fm") && one("ref") ->
                    value("ref")?.let(::parse)
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }
}
