package com.fyiplayer.app.core

/**
 * The only place a platform is named. Registration, settings and URL resolution read [all];
 * Home and search read [browseSourcesFor]; the vertical pager reads [shortsSourcesFor].
 *
 * Takes enabled ids as a plain [Set] rather than reading preferences itself — `core` depends on
 * nothing, and that keeps every predicate here pure and JVM-testable.
 */
object SourceRegistry {
    /** Populated by each platform module as it lands. */
    val all: List<VideoSource> = emptyList()

    /**
     * Sources that never appear as a Home tab or a search target, even when enabled — a
     * short-form-only platform belongs here while staying in [all] so its saved URLs still resolve.
     */
    private val SHORTS_ONLY_IDS: Set<String> = emptySet()

    /** Pure so it is testable without any Android or preference plumbing. */
    internal fun browsable(source: VideoSource): Boolean = source.id !in SHORTS_ONLY_IDS

    /** Enabled, minus the shorts-only ones: what Home and search browse. */
    fun browseSourcesFor(enabledIds: Set<String>): List<VideoSource> =
        all.filter { it.id in enabledIds && browsable(it) }

    /** Enabled sources that contribute clips to the vertical feed. Shorts-only sources belong here. */
    fun shortsSourcesFor(enabledIds: Set<String>): List<VideoSource> =
        all.filter { it.id in enabledIds && it.providesShorts }

    fun bySourceId(id: String): VideoSource? = all.find { it.id == id }

    /** Which source owns a pasted or shared URL, or null when nothing here can handle it. */
    fun forUrl(url: String): VideoSource? = all.find { it.matches(url) }
}
