package com.fyiplayer.app.source.newpipe

import java.util.Calendar

/**
 * Search terms behind Home's "Local" chip, per content country. YouTube has no country-local
 * chart (its per-country charts rank whatever the country watches, which for BD is Bollywood +
 * global hits), but a search phrased in the local language surfaces local artists/studios --
 * verified: "new song" with gl=BD returns Hindi, "নতুন গান" returns Bangladeshi artists.
 *
 * Each country gets three phrases: song / movie / drama. The year is appended at call time from
 * the device clock, never baked in, so the chip never goes stale in January.
 */
internal object LocalQueries {
    private val EN = listOf("new songs", "new movie trailer", "new tv series")

    private val BY_COUNTRY = mapOf(
        "BD" to listOf("নতুন গান", "নতুন সিনেমা", "নতুন নাটক"),
        "IN" to listOf("नया गाना", "नई फिल्म ट्रेलर", "नया सीरियल"),
        "PK" to listOf("نیا گانا", "نئی فلم", "نیا ڈرامہ"),
        "DE" to listOf("neue Lieder", "neuer Film Trailer", "neue Serie"),
        "FR" to listOf("nouvelle chanson", "nouveau film bande-annonce", "nouvelle série"),
        "JP" to listOf("新曲", "新作映画 予告", "新ドラマ"),
        "KR" to listOf("신곡", "새 영화 예고편", "새 드라마"),
        "BR" to listOf("música nova", "filme novo trailer", "nova série"),
        "RU" to listOf("новая песня", "новый фильм трейлер", "новый сериал"),
        "TR" to listOf("yeni şarkı", "yeni film fragman", "yeni dizi"),
        "ID" to listOf("lagu baru", "film baru trailer", "sinetron baru"),
        "MX" to listOf("canción nueva", "película nueva tráiler", "serie nueva"),
        "ES" to listOf("canción nueva", "película nueva tráiler", "serie nueva"),
        "IT" to listOf("nuova canzone", "nuovo film trailer", "nuova serie"),
        "NL" to listOf("nieuw liedje", "nieuwe film trailer", "nieuwe serie"),
        "PH" to listOf("bagong kanta", "bagong pelikula trailer", "bagong teleserye"),
        "VN" to listOf("bài hát mới", "phim mới trailer", "phim truyền hình mới"),
        "EG" to listOf("أغنية جديدة", "فيلم جديد تريلر", "مسلسل جديد"),
        "SA" to listOf("أغنية جديدة", "فيلم جديد تريلر", "مسلسل جديد"),
        "TH" to listOf("เพลงใหม่", "หนังใหม่ ตัวอย่าง", "ละครใหม่"),
        // US, GB, CA, AU, NG: English
    )

    fun forCountry(countryCode: String, year: Int = Calendar.getInstance().get(Calendar.YEAR)): List<String> =
        (BY_COUNTRY[countryCode.uppercase()] ?: EN).map { "$it $year" }
}
