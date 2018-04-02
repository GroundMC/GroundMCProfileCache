package net.groundmc.profilecache

import org.jetbrains.exposed.sql.Table
import org.joda.time.DateTime
import java.util.*

object Users : Table() {
    /**
     * The [UUID] of the player
     */
    val id = uuid("playerId").primaryKey()

    /**
     * The last name with which this player has been seen
     */
    val lastName = varchar("last_name", 16).index()

    /**
     * Whether the chat is silent for this player or not
     */
    val silentStatus = bool("silent_status").default(false)

    /**
     * One of the [VisibilityStates] visibility settings
     */
    val hiddenStatus = integer("hidden_status")

    /**
     * Whether this player is vanished or not
     */
    val vanishStatus = bool("vanish_status").default(false)

    /**
     * The last date at which the player has received daily coins (does not accumulate)
     */
    val lastDailyCoinsDate = date("lastDailyCoins").default(DateTime.parse("1970-01-01"))

}
