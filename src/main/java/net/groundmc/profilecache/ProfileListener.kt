package net.groundmc.profilecache

import com.destroystokyo.paper.event.profile.FillProfileEvent
import com.destroystokyo.paper.event.profile.LookupProfileEvent
import com.destroystokyo.paper.event.profile.PreFillProfileEvent
import com.destroystokyo.paper.event.profile.PreLookupProfileEvent
import kotlinx.coroutines.experimental.async
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

@Suppress("unused")
object ProfileListener : Listener {

    @EventHandler(priority = EventPriority.LOW)
    fun eagerCache(event: PlayerJoinEvent) {
        async {
            Bukkit.createProfile(event.player.uniqueId).complete(true)
        }.start()
    }

    @EventHandler
    fun lookupCachedProfile(event: PreLookupProfileEvent) {
        val property = UserCacheTable.forName(event.name) ?: return
        event.uuid = property[UserCacheTable.id]
        event.profileProperties = property[UserCacheTable.properties]
    }

    @EventHandler
    fun fillCachedProfile(event: PreFillProfileEvent) {
        val property = if (event.playerProfile.name != null) {
            UserCacheTable.forName(event.playerProfile.name!!) ?: return
        } else {
            UserCacheTable.forId(event.playerProfile.id!!) ?: return
        }
        event.playerProfile.setProperties(property[UserCacheTable.properties])
    }

    @EventHandler
    fun storeCachedProfile(event: LookupProfileEvent) {
        UserCacheTable.cacheProfile(event.playerProfile)
    }

    @EventHandler
    fun storeCachedProfile(event: FillProfileEvent) {
        UserCacheTable.cacheProfile(event.playerProfile)
    }
}
