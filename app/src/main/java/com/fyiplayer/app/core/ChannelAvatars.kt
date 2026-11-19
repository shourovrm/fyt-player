package com.fyiplayer.app.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Profile pictures of channels seen this session, by channel page URL. Memory only: an image URL
 * is never written to the database (project rule), so after an app start a channel has no picture
 * here until one of its pages has been fetched -- the feed refresh does that for every channel in
 * the feed. A list reads the whole map once and draws from it, with no request per row.
 */
object ChannelAvatars {
    private val _byChannelUrl = MutableStateFlow<Map<String, String>>(emptyMap())
    val byChannelUrl: StateFlow<Map<String, String>> = _byChannelUrl.asStateFlow()

    fun record(channelUrl: String, avatarUrl: String?) {
        if (avatarUrl == null || _byChannelUrl.value[channelUrl] == avatarUrl) return
        _byChannelUrl.update { it + (channelUrl to avatarUrl) }
    }
}
