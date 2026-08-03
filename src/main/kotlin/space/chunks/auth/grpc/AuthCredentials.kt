package space.chunks.auth.grpc

import io.grpc.CallCredentials
import io.grpc.Metadata
import space.chunks.auth.oauth.ReusableTokenSource
import java.util.concurrent.Executor

class AuthCredentials(private val tokenSrc: ReusableTokenSource) : CallCredentials() {
    override fun applyRequestMetadata(
        requestInfo: RequestInfo?,
        appExecutor: Executor?,
        applier: MetadataApplier?
    ) {
        val tok = this.tokenSrc.token()
        
        println(tok.accessToken.toString())

        val m = Metadata()
        val k = Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER)
        m.put(k, tok.accessToken.toString())
        applier?.apply(m)
    }
}