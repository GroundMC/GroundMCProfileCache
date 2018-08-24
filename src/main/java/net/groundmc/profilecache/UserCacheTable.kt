package net.groundmc.profilecache

import com.destroystokyo.paper.profile.PlayerProfile
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import kotlinx.coroutines.experimental.async
import net.groundmc.extensions.exposed.profilePropertySet
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object UserCacheTable : Table("ProfileCache") {

    val name = varchar("username", 255).primaryKey()

    val id = uuid("id").uniqueIndex()

    val properties = profilePropertySet("profile", 4096)

    private val expire = datetime("expire").index()

    private val userCache = CacheBuilder.newBuilder()
            .refreshAfterWrite(5, TimeUnit.MINUTES)
            .expireAfterAccess(20, TimeUnit.MINUTES)
            .maximumSize(2500)
            .build(CacheLoader.asyncReloading(UserCacheLoader, Executors.newCachedThreadPool()))

    private object UserCacheLoader : CacheLoader<String, ResultRow>() {
        @Throws(NullPointerException::class)
        override fun load(key: String): ResultRow {
            return transaction {
                return@transaction select { (name eq key) and (expire greater DateTime.now()) }.firstOrNull()
            } ?: throw NullPointerException()
        }

        override fun loadAll(keys: Iterable<String>): Map<String, ResultRow> {
            return transaction {
                return@transaction select { (name inList keys) and (expire greater DateTime.now()) }.associateBy { it[name] }
            }
        }
    }

    fun forName(username: String) = try {
        userCache[username]
    } catch (e: Exception) {
        null
    }

    fun forId(uuid: UUID) =
            userCache.asMap().values.firstOrNull { it[id] == uuid }
                    ?: transaction {
                        val row = select { (id eq uuid) and (expire greater DateTime.now()) }.firstOrNull()
                        if (row != null) {
                            userCache.put(row[name], row)
                        }
                        row
                    }

    private fun anyForId(uuid: UUID) = transaction {
        return@transaction select { id eq uuid }.count() > 0
    }

    fun cacheProfile(playerProfile: PlayerProfile) =
            async {
                val uuid = playerProfile.id
                val username = playerProfile.name
                if (uuid == null || username == null) return@async
                if (!playerProfile.hasTextures()) {
                    return@async
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

}
