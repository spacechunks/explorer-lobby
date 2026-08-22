package space.chunks.lobby.modules.spawn

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.FileConfiguration

data class LocationConfig(
    val world: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float,
) {
    companion object {
        fun parse(config: ConfigurationSection): LocationConfig {
            return LocationConfig(
                config.getString("world") ?: throw IllegalArgumentException("world is missing"),
                config.getDouble("x", 0.0),
                config.getDouble("y", 0.0),
                config.getDouble("z", 0.0),
                config.getDouble("yaw", 0.0).toFloat(),
                config.getDouble("pitch", 0.0).toFloat(),
            )
        }
    }

}

data class Config(
    val spawnLocation: LocationConfig,
    val roboSpawnLocation: LocationConfig,
    val postgresDSN: String
) {
    companion object {
        fun parse(config: FileConfiguration): Config {
            val spawnLoc =
                config.getConfigurationSection("spawn.location")
                    ?: throw IllegalArgumentException("spawn.location is missing")

            val roboLoc =
                config.getConfigurationSection("spawn.roboLocation")
                    ?: throw IllegalArgumentException("spawn.roboLocation is missing")

            val postgresDSN =
                config.getString("spawn.postgresDSN") ?: throw IllegalArgumentException("postgresDSN is missing")

            return Config(
                LocationConfig.parse(spawnLoc),
                LocationConfig.parse(roboLoc),
                postgresDSN,
            )
        }
    }
}


