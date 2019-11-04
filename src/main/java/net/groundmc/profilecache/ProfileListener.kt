package net.groundmc.profilecache

import com.destroystokyo.paper.event.profile.FillProfileEvent
import com.destroystokyo.paper.event.profile.LookupProfileEvent
import com.destroystokyo.paper.event.profile.PreFillProfileEvent
import com.destroystokyo.paper.event.profile.PreLookupProfileEvent
import kotlinx.coroutines.launch
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

@Suppress("unused")
class ProfileListener(private val main: Main,
                      private val userCacheTable: UserCacheTable
) : Listener {

    @EventHandler(priority = EventPriority.LOW)
    fun eagerCache(event: PlayerJoinEvent) {
        main.scope.launch {
            Bukkit.createProfile(event.player.uniqueId).complete(true)
        }
    }

    @EventHandler
    fun lookupCachedProfile(event: PreLookupProfileEvent) {
        val entry = userCacheTable.forName(event.name) ?: return
        event.uuid = entry.id
        event.profileProperties = entry.properties
    }

    @EventHandler
    fun fillCachedProfile(event: PreFillProfileEvent) {
        val entry = if (event.playerProfile.name != null) {
            userCacheTable.forName(event.playerProfile.name!!) ?: return
        } else {
            userCacheTable.forId(event.playerProfile.id!!) ?: return
        }
        event.playerProfile.setProperties(entry.properties)
    }

    @EventHandler
    fun storeCachedProfile(event: LookupProfileEvent) {
        userCacheTable.cacheProfile(event.playerProfile)
    }

    @EventHandler
    fun storeCachedProfile(event: FillProfileEvent) {
        userCacheTable.cacheProfile(event.playerProfile)
    }
}
