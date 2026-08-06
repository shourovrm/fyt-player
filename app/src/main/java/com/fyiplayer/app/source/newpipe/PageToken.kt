package com.fyiplayer.app.source.newpipe

import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.schabi.newpipe.extractor.Page

private val pageTokenJson = Json { ignoreUnknownKeys = true }

/**
 * NewPipe's [Page] carries a different continuation shape per service (url, id, ids, cookies, an
 * opaque POST body) -- this is a lossless String<->[Page] codec so [com.fyiplayer.app.core.VideoSource]'s
 * opaque `page` token can carry any of them, body base64'd.
 *
 * Deliberately NOT carrying reconstruction context (query string, channel URL, tab): every
 * [com.fyiplayer.app.core.VideoSource] paging method already receives that from its own
 * parameters on every call, so duplicating it into the token would be dead weight the codec would
 * have to keep in sync for no reason.
 */
@Serializable
internal data class PageToken(
    val url: String? = null,
    val id: String? = null,
    val ids: List<String>? = null,
    val cookies: Map<String, String>? = null,
    val bodyBase64: String? = null,
)

internal fun Page.toToken(): String = pageTokenJson.encodeToString(
    PageToken.serializer(),
    PageToken(
        url = url,
        id = id,
        ids = ids,
        cookies = cookies,
        bodyBase64 = body?.let { Base64.getEncoder().encodeToString(it) },
    ),
)

internal fun String.toPage(): Page {
    val t = pageTokenJson.decodeFromString(PageToken.serializer(), this)
    return Page(t.url, t.id, t.ids, t.cookies, t.bodyBase64?.let { Base64.getDecoder().decode(it) })
}
