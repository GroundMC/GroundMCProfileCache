package net.groundmc.profilecache

import com.destroystokyo.paper.profile.PlayerProfile
import com.destroystokyo.paper.profile.ProfileProperty
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.gson.JsonParser
import com.google.gson.stream.JsonWriter
import kotlinx.coroutines.experimental.async
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import java.io.StringWriter
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object UserCacheTable : Table("ProfileCache") {

    val name = varchar("username", 255).primaryKey()

    val id = uuid("id").index()

    val profile = varchar("profile", 4096)

    private val expire = datetime("expire").index()

    private val userCache = CacheBuilder.newBuilder()
            .refreshAfterWrite(5, TimeUnit.MINUTES)
            .expireAfterAccess(20, TimeUnit.MINUTES)
            .maximumSize(2500)
            .build(CacheLoader.asyncReloading(UserCacheLoader, Executors.newCachedThreadPool()))

    private object UserCacheLoader : CacheLoader<String, ResultRow>() {
        @Throws(NullPointerException::class)
        override fun load(key: String): ResultRow {
            val row = transaction {
                return@transaction select { (name eq key) and (expire greater DateTime.now()) }.firstOrNull()
            }
            if (row != null) {
                return row
            }
            throw NullPointerException()
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
        return@transaction select { id eq uuid }.firstOrNull()
    }

    @Suppress("UNCHECKED_CAST")
    fun getProperties(json: String) = JsonParser().parse(json).asJsonArray
            .map {
                with(it.asJsonObject) {
                    ProfileProperty(get("name").asString, get("value").asString, if (has("signature")) get("signature").asString else null)
                }
            }.toSet()

    fun cacheProfile(playerProfile: PlayerProfile) {
        async {
            val uuid = playerProfile.id
            val username = playerProfile.name
            if (uuid == null || username == null) return@async
            if (!playerProfile.hasTextures()) {
                return@async
            }
            transaction {
                if (anyForId(uuid) == null) {
                    insert {
                        it[id] = uuid
                        it[name] = username
                        it[profile] = propertiesToJson(playerProfile.properties)
                        it[expire] = DateTime.now().plusHours(2)
                    }
                } else {
                    update({ id eq uuid }) {
                        it[name] = username
                        it[profile] = propertiesToJson(playerProfile.properties)
                        it[expire] = DateTime.now().plusHours(2)
                    }
                }
                Users.update({ Users.id eq uuid }) {
                    it[Users.lastName] = username
                }
                commit()
            }
        }
    }

    private fun propertiesToJson(properties: Set<ProfileProperty>): String {
        val writer = StringWriter()
        val out = JsonWriter(writer)
        out.beginArray()
        properties.forEach {
            out.beginObject().name("name").value(it.name)
                    .name("value").value(it.value)
            if (it.isSigned) {
                out.name("signature").value(it.signature)
            }
            out.endObject()
        }
        out.endArray()
        return writer.toString()
    }

}
