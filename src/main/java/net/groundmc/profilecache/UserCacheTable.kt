package net.groundmc.profilecache

import com.destroystokyo.paper.profile.PlayerProfile
import com.destroystokyo.paper.profile.ProfileProperty
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.TypeAdapter
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import kotlinx.coroutines.experimental.async
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object UserCacheTable : Table("ProfileCache") {

    private val gson = GsonBuilder()
            .registerTypeAdapter(TypeToken.getParameterized(Set::class.java, ProfileProperty::class.java).type, ProfilePropertyTypeAdapter())
            .create()

    val name = varchar("username", 255).primaryKey()

    val id = uuid("id").uniqueIndex()

    val properties = properties("profile", 4096)

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
                addLogger(StdOutSqlLogger)
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
                    addLogger(StdOutSqlLogger)
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

    private fun properties(name: String, length: Int, collate: String? = null) = registerColumn<Set<ProfileProperty>>(name, PropertySetColumnType(length, collate))

    class PropertySetColumnType(length: Int, collate: String?) : VarCharColumnType(length, collate) {
        override fun nonNullValueToString(value: Any): String {
            return when (value) {
                is Set<*> -> gson.toJson(value)
                else -> super.nonNullValueToString(value)
            }
        }

        override fun valueFromDB(value: Any): Any {
            if (value is String) {
                return JsonParser().parse(value).asJsonArray
                        .map {
                            with(it.asJsonObject) {
                                ProfileProperty(get("name").asString, get("value").asString, if (has("signature")) get("signature").asString else null)
                            }
                        }.toSet()
            }
            return super.valueFromDB(value)
        }
    }

    private class ProfilePropertyTypeAdapter : TypeAdapter<Set<ProfileProperty>>() {
        override fun write(out: JsonWriter, value: Set<ProfileProperty>) {
            out.beginArray()
            value.forEach {
                out.beginObject().name("name").value(it.name)
                        .name("value").value(it.value)
                if (it.isSigned) {
                    out.name("signature").value(it.signature)
                }
                out.endObject()
            }
            out.endArray()
        }

        override fun read(reader: JsonReader): Set<ProfileProperty> {
            val set = mutableSetOf<ProfileProperty>()

            reader.beginArray()
            while (reader.hasNext()) {
                reader.beginObject()
                val builder = ProfilePropertyBuilder()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "name" -> builder.name = reader.nextString()
                        "value" -> builder.value = reader.nextString()
                        "signature" -> builder.signature = reader.nextString()
                        else -> reader.skipValue()
                    }
                }
                set += builder.build() ?: continue
                reader.endObject()
            }
            reader.endArray()
            return set
        }
    }

    private class ProfilePropertyBuilder(var name: String? = null, var value: String? = null, var signature: String? = null) {
        fun build() = if (name != null || value != null) {
            ProfileProperty(name!!, value!!, signature)
        } else {
            null
        }
    }

}
