package net.groundmc.profilecache

import com.zaxxer.hikari.HikariDataSource
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

class Main : JavaPlugin() {

    val datasource = HikariDataSource().apply {
            jdbcUrl = config.getString("database.url").replace("\$dataFolder", dataFolder.absolutePath)
            username = config.getString("database.username", "root")
            password = config.getString("database.password", "")
            transactionIsolation = "TRANSACTION_READ_COMMITTED"
            maximumPoolSize = 2
    }

    override fun onEnable() {
        saveDefaultConfig()
        Database.connect(datasource)
        transaction {
            SchemaUtils.createMissingTablesAndColumns(UserCacheTable)
        }
        Bukkit.getPluginManager().registerEvents(ProfileListener, this)
    }

    override fun onDisable() {
        datasource.close()
    }
}
