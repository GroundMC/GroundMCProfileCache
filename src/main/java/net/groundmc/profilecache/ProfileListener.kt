package net.groundmc.profilecache

import com.destroystokyo.paper.event.profile.FillProfileEvent
import com.destroystokyo.paper.event.profile.LookupProfileEvent
import com.destroystokyo.paper.event.profile.PreFillProfileEvent
import com.destroystokyo.paper.event.profile.PreLookupProfileEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

@Suppress("unused")
object ProfileListener : Listener {

    @EventHandler
    fun lookupCachedProfile(event: PreLookupProfileEvent) {
        val property = UserCacheTable.forName(event.name) ?: return
        event.uuid = property[UserCacheTable.id]
        event.profileProperties = UserCacheTable.getProperties(property[UserCacheTable.profile])
    }

    @EventHandler
    fun fillCachedProfile(event: PreFillProfileEvent) {
        val property = if (event.playerProfile.name != null) {
            UserCacheTable.forName(event.playerProfile.name!!) ?: return
        } else {
            UserCacheTable.forId(event.playerProfile.id!!) ?: return
        }
        event.playerProfile.setProperties(UserCacheTable.getProperties(property[UserCacheTable.profile]))
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
