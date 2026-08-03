package space.chunks.auth.oauth

import com.nimbusds.oauth2.sdk.ClientCredentialsGrant
import com.nimbusds.oauth2.sdk.Scope
import com.nimbusds.oauth2.sdk.TokenRequest
import com.nimbusds.oauth2.sdk.TokenResponse
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic
import com.nimbusds.oauth2.sdk.auth.Secret
import com.nimbusds.oauth2.sdk.id.ClientID
import com.nimbusds.oauth2.sdk.token.AccessToken
import java.net.URI
import java.time.Duration
import java.time.Instant

data class ClientCredentialsConfig(
    val clientId: String,
    val clientSecret: String,
    val tokenUrl: String,
    val scopes: List<String> = emptyList(),
)

data class Token(val accessToken: AccessToken, val expiry: Instant?) {
    fun validWithBuffer(buffer: Duration): Boolean {
        val exp = expiry ?: return true
        return Instant.now().isBefore(exp.minus(buffer))
    }
}

class ClientCredentialsTokenSource(private val cfg: ClientCredentialsConfig) {

    fun fetchToken(): Token {
        val clientAuth = ClientSecretBasic(ClientID(cfg.clientId), Secret(cfg.clientSecret))
        val grant = ClientCredentialsGrant()
        val scope = if (cfg.scopes.isNotEmpty()) Scope(*cfg.scopes.toTypedArray()) else null

        val request = TokenRequest(URI(cfg.tokenUrl), clientAuth, grant, scope)

        val httpResponse = request.toHTTPRequest().apply {
            connectTimeout = 5000
            readTimeout = 5000
        }.send()

        val response = TokenResponse.parse(httpResponse)

        if (!response.indicatesSuccess()) {
            val errorResponse = response.toErrorResponse()
            error("token request failed: ${errorResponse.errorObject}")
        }

        val successResponse = response.toSuccessResponse()
        val tokens = successResponse.tokens
        val accessToken = tokens.accessToken

        // lifetime is in seconds, 0 means "not specified"
        val expiry = accessToken.lifetime.takeIf { it > 0 }
            ?.let { Instant.now().plusSeconds(it) }

        return Token(accessToken, expiry)
    }
}