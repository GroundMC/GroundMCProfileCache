package net.groundmc.profilecache

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection

class Main : JavaPlugin() {

    override fun onEnable() {
        saveDefaultConfig()
        Database.connect(config.getString("database.url").replace("\$dataFolder", dataFolder.absolutePath), config.getString("database.driver"), config.getString("database.username", ""), config.getString("database.password", ""))
        TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
        transaction {
            SchemaUtils.createMissingTablesAndColumns(UserCacheTable)
        }
        Bukkit.getPluginManager().registerEvents(ProfileListener, this)
    }

}
