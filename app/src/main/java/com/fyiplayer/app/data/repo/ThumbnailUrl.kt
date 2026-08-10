package com.fyiplayer.app.data.repo

// Listing thumbnails carry a signature query (`?sqp=...&rs=...`) that expires; only the database
// column is stripped to the bare path here, never the in-memory VideoRef -- the unsigned path
// keeps rendering indefinitely and no signature/token is written to disk.
fun canonicalThumbnailUrl(url: String?): String? = url?.substringBefore('?')
