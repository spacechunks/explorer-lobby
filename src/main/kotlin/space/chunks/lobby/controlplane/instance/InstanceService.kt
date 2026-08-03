package space.chunks.lobby.controlplane.instance

import chunks.space.api.explorer.instance.v1alpha1.Api
import chunks.space.api.explorer.instance.v1alpha1.InstanceServiceGrpcKt
import chunks.space.api.explorer.instance.v1alpha1.Types
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.*
import kotlinx.coroutines.future.future
import java.util.concurrent.CompletableFuture
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.time.Duration

class InstanceService(
    private val logger: Logger,
    private val client: InstanceServiceGrpcKt.InstanceServiceCoroutineStub,
    private val instancePollInterval: Duration,
) {
    private val actorJobs = mutableMapOf<String, Job>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun runFlavorVersion(orderedBy: String, versionId: String): CompletableFuture<Types.Instance> {
        return this.scope.future {
            val req = Api.RunFlavorVersionRequest.newBuilder()
                .setFlavorVersionId(versionId)
                .setOrderedBy(orderedBy)
                .build()

            println("======")
            println("WEEEE RUUUUNNNN")

            try {
                return@future client.runFlavorVersion(req).instance
            } catch (ex: Exception) {
                throw ex
            }
//            println("======")
//            println(f.id)
        }
    }

    fun cancelScope() {
        this.scope.cancel()
    }

    fun getInstance(instanceId: String): CompletableFuture<Types.Instance> {
        return this.scope.future {
            val req = Api.GetInstanceRequest.newBuilder().setId(instanceId).build()
            return@future client.getInstance(req).instance
        }
    }

    fun waitForInstance(actorId: String, instanceId: String, onPoll: (instance: Types.Instance) -> Unit) {
        val job = this.scope.launch {
            pollInstance(actorId, instanceId, onPoll)
        }

        this.actorJobs[actorId] = job
    }

    fun cancelJob(actorId: String) {
        this.actorJobs[actorId]?.cancel()
        this.actorJobs.remove(actorId)
    }

    private suspend fun pollInstance(actorId: String, instanceId: String, onPoll: (instance: Types.Instance) -> Unit) {
        this.logger.info("waiting for instance to be ready. actorId=$actorId instanceId=$instanceId")
        while (true) {
            try {
                val req = Api.GetInstanceRequest.newBuilder().setId(instanceId).build()
                val instance = client.getInstance(req).instance

                onPoll(instance)

                if (instance.state == Types.InstanceState.RUNNING) {
                    this.actorJobs.remove(actorId)
                    return
                }
            } catch (e: StatusException) {
                logger.log(Level.WARNING, "error polling instance. instanceId=$instanceId", e)
                if (e.status.code == Status.Code.NOT_FOUND) {
                    return
                }
            }
            delay(instancePollInterval)
        }
    }
}