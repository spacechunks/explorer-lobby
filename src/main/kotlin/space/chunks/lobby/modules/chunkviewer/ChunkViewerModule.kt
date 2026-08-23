package space.chunks.lobby.modules.chunkviewer

import chunks.space.api.explorer.chunk.v1alpha1.ChunkServiceGrpcKt
import chunks.space.api.explorer.chunk.v1alpha1.listChunksRequest
import kotlinx.coroutines.*
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.GameRules
import org.bukkit.NamespacedKey
import org.bukkit.WorldCreator
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.util.Vector
import space.chunks.lobby.modules.LobbyModule
import space.chunks.lobby.modules.chunkviewer.display.ChunkDisplay
import space.chunks.lobby.modules.chunkviewer.display.DisplaySessionService
import space.chunks.lobby.modules.chunkviewer.listener.CancelListener
import space.chunks.lobby.modules.chunkviewer.listener.ControlsListener
import space.chunks.lobby.modules.chunkviewer.listener.PlayerListener
import space.chunks.lobby.modules.chunkviewer.world.VoidWorldGenerator
import space.chunks.lobby.pack.ResourcePackConfig
import space.chunks.lobby.ui.Texts
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

class ChunkViewerModule(
    plugin: Plugin,
    private val packConfig: ResourcePackConfig,
    private val chunkClient: ChunkServiceGrpcKt.ChunkServiceCoroutineStub,
    private val texts: Texts,
    private val fetchChunks: Boolean = true,
) : LobbyModule(plugin, "chunk-viewer") {
    val chunks = CopyOnWriteArrayList<ChunkDisplay>()
    val worldName = "chunk_viewer"
    val sessionService = DisplaySessionService(
        this.plugin,
        this.chunks,
        Vector(0.0, 100.0, 0.0),
        this.worldName,
        this.texts,
    )

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // LIGHT BLUE #7ce8fe
    // A BIT DARKER BLUE #53d0fd

    override fun onEnable() {
        this.clearPersistedViewerEntities()

        Bukkit.createWorld(
            WorldCreator(this.worldName)
                .generator(VoidWorldGenerator())
                .generateStructures(false)
        )?.let { w ->
            w.setGameRule(GameRules.ADVANCE_TIME, false)
            w.setGameRule(GameRules.ADVANCE_WEATHER, false)
            w.setGameRule(GameRules.SPAWN_MOBS, false)
            w.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0)
            w.setGameRule(GameRules.BLOCK_DROPS, false)
            w.setGameRule(GameRules.LOCATOR_BAR, false)
            w.time = 14000
            w.clearWeatherDuration = -1
            this.clearViewerWorld(w)
        }

        // TODO: implement pagination -> create ChunkService in controlplane/chunk package
        Bukkit.getScheduler().runTaskTimerAsynchronously(this.plugin, { _ ->
            if (!this.fetchChunks) return@runTaskTimerAsynchronously

            runBlocking {
                val resp = chunkClient.listChunks(listChunksRequest {})
                logger.info("got ${resp.chunksList.size} chunks from control plane")

                val d = resp.chunksList.map { c ->
                    val textureFile = File(
                        dataFolder,
                        "pack-files/${packConfig.thumbnailsLocation}/${c.id}.png"
                    )

                    val key =
                        if (textureFile.exists())
                            NamespacedKey.fromString("${packConfig.thumbnailKeyPrefix}/${c.id}")!!
                        else
                            NamespacedKey.fromString(packConfig.thumbnailMissingKey)!!

                    ChunkDisplay(Component.text(c.name), c, key)
                }.toList()

                chunks.clear()
                chunks.addAll(d)
            }
        }, 0, 20 * 5)

        Bukkit.getPluginManager().registerEvents(ControlsListener(this.sessionService), this.plugin)
        Bukkit.getPluginManager().registerEvents(PlayerListener(this.sessionService), this.plugin)
        Bukkit.getPluginManager().registerEvents(CancelListener(), this.plugin)
    }

    override fun onDisable() {
        scope.cancel()
    }

    private fun clearPersistedViewerEntities() {
        val entitiesFolder = File(Bukkit.getWorldContainer(), "$worldName/entities")
        if (!entitiesFolder.exists()) {
            return
        }

        if (entitiesFolder.deleteRecursively()) {
            this.logger.info("cleared persisted entity region data from $worldName")
        } else {
            this.logger.warning("failed to clear persisted entity region data from $worldName")
        }
    }

    private fun clearViewerWorld(world: org.bukkit.World) {
        val removed = world.entities
            .filterNot { it is Player }
            .onEach { it.remove() }
            .count()

        this.logger.info("cleared $removed stale entities from $worldName")
    }
}
