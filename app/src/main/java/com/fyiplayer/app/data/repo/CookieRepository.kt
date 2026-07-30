package com.fyiplayer.app.data.repo

import com.fyiplayer.app.data.db.CookieDao
import com.fyiplayer.app.data.db.CookieEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class Cookie(val domain: String, val name: String, val value: String, val path: String, val expiresAt: Long?)

private fun CookieEntity.toCookie() = Cookie(domain, name, value, path, expiresAt)
private fun Cookie.toEntity() = CookieEntity(domain, name, value, path, expiresAt)

// domain-scoped reads only -- callers never get a cross-domain jar to accidentally leak.
class CookieRepository(private val dao: CookieDao) {
    suspend fun forDomain(domain: String): List<Cookie> = dao.forDomain(domain).map { it.toCookie() }

    fun observeAll(): Flow<List<Cookie>> = dao.observeAll().map { list -> list.map { it.toCookie() } }

    suspend fun save(cookie: Cookie) = dao.upsert(cookie.toEntity())

    suspend fun clearDomain(domain: String) = dao.clearDomain(domain)

    suspend fun clearAll() = dao.clearAll()
}
