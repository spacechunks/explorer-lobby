package space.chunks.lobby.controlplane

import org.bukkit.configuration.file.FileConfiguration
import space.chunks.auth.oauth.ClientCredentialsConfig
import space.chunks.lobby.extensions.parseAddress

data class Config(
    val addr: String,
    val port: Int,
    val authConfig: ClientCredentialsConfig,
    val instancePollIntervalSeconds: Int,
) {
    companion object {
        fun parse(config: FileConfiguration): Config {
            val explorerEndpoint =
                config.getString("controlPlane.endpoint")
                    ?: throw RuntimeException("controlPlane.endpoint is missing")

            val parts = explorerEndpoint.parseAddress()

            val authTokenUrl = config.getString("controlPlane.auth.tokenUrl")
                ?: throw RuntimeException("controlPlane.auth.tokenUrl is missing")

            val authClientId = config.getString("controlPlane.auth.clientId")
                ?: throw RuntimeException("controlPlane.auth.clientId is missing")

            val authClientSecret = config.getString("controlPlane.auth.clientSecret")
                ?: throw RuntimeException("controlPlane.auth.clientSecret is missing")

            val authScopes = config.getStringList("controlPlane.auth.scopes")

            val instancePollIntervalSeconds = config.getInt("controlPlane.instancePollIntervalSeconds", 1)

            return Config(
                parts.first,
                parts.second,
                ClientCredentialsConfig(
                    authClientId,
                    authClientSecret,
                    authTokenUrl,
                    authScopes,
                ),
                instancePollIntervalSeconds
            )
        }
    }
}