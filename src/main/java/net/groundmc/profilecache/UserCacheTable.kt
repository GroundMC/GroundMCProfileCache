package net.groundmc.profilecache

import com.destroystokyo.paper.profile.PlayerProfile
import com.destroystokyo.paper.profile.ProfileProperty
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import kotlinx.coroutines.launch
import net.groundmc.extensions.exposed.profilePropertySet
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class UserCacheTable(private val main: Main) : Table("ProfileCache") {

    val name = varchar("username", 255).primaryKey()

    val id = uuid("id").uniqueIndex()

    val properties = profilePropertySet("profile", 4096)

    private val expire = datetime("expire").index()

    private val userCache = CacheBuilder.newBuilder()
            .refreshAfterWrite(5, TimeUnit.MINUTES)
            .expireAfterAccess(20, TimeUnit.MINUTES)
            .maximumSize(2500)
            .build(CacheLoader.asyncReloading(UserCacheLoader(), Executors.newCachedThreadPool()))

    private inner class UserCacheLoader : CacheLoader<String, UserCacheEntry>() {
        @Throws(NullPointerException::class)
        override fun load(key: String): UserCacheEntry {
            return transaction {
                return@transaction select { (name eq key) and (expire greater DateTime.now()) }
                        .map { UserCacheEntry(it[name], it[id], it[properties], it[expire]) }
                        .firstOrNull()
            } ?: throw NullPointerException()

        }

        override fun loadAll(keys: Iterable<String>): Map<String, UserCacheEntry> {
            return transaction {
                return@transaction select { (name inList keys) and (expire greater DateTime.now()) }
                        .map { UserCacheEntry(it[name], it[id], it[properties], it[expire]) }
                        .associateBy { it.name }
            }
        }
    }

    fun forName(username: String) = try {
        userCache[username]
    } catch (e: Exception) {
        null
    }

    fun forId(uuid: UUID) =
            userCache.asMap().values.firstOrNull { it.id == uuid }
                    ?: transaction {
                        val cacheEntry = select { (id eq uuid) and (expire greater DateTime.now()) }
                                .map { UserCacheEntry(it[name], it[id], it[properties], it[expire]) }
                                .firstOrNull()
                        if (cacheEntry != null) {
                            userCache.put(cacheEntry.name, cacheEntry)
                        }
                        cacheEntry
                    }

    private fun anyForId(uuid: UUID) = transaction {
        return@transaction select { id eq uuid }.count() > 0
    }

    fun cacheProfile(playerProfile: PlayerProfile) =
            main.scope.launch {
                val uuid = playerProfile.id
                val username = playerProfile.name
                if (uuid == null || username == null) return@launch
                if (!playerProfile.hasTextures()) {
                    return@launch
                }
                transaction {
                    if (!anyForId(uuid)) {
                        insert {
                            it[id] = uuid
                            it[name] = username
                            it[properties] = playerProfile.properties
                            it[expire] = DateTime.now().plusHours(2)
                        }
                    } else {
                        update({ id eq uuid }) {
                            it[name] = username
                            it[properties] = playerProfile.properties
                            it[expire] = DateTime.now().plusHours(2)
                        }
                    }
                    commit()
                    userCache.refresh(username)
                }
            }

    data class UserCacheEntry(
            val name: String,
            val id: UUID,
            val properties: Set<ProfileProperty>,
            val expire: DateTime
    )

}
