package net.groundmc.profilecache

import com.zaxxer.hikari.HikariDataSource
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
        Database.connect(HikariDataSource().apply {
            jdbcUrl = config.getString("database.url").replace("\$dataFolder", dataFolder.absolutePath)
            username = config.getString("database.username", "root")
            password = config.getString("database.password", "")
        })
        TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_READ_COMMITTED
        transaction {
            SchemaUtils.createMissingTablesAndColumns(UserCacheTable)
        }
        Bukkit.getPluginManager().registerEvents(ProfileListener, this)
    }

}
