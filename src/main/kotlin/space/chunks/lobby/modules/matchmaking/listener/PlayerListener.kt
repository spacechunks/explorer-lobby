package space.chunks.lobby.modules.matchmaking.listener

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.bukkit.NamespacedKey
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin
import space.chunks.lobby.controlplane.instance.InstanceService
import space.chunks.lobby.extensions.removeMetadata
import space.chunks.lobby.extensions.setBool
import space.chunks.lobby.modules.chunkviewer.event.PlayerSelectFlavorEvent
import space.chunks.lobby.modules.matchmaking.Config
import space.chunks.lobby.modules.matchmaking.MMService
import space.chunks.lobby.modules.matchmaking.waitForInstance
import space.chunks.lobby.modules.party.PartyService
import space.chunks.lobby.player.PlayerMetadataKeys
import space.chunks.lobby.ui.Texts
import space.chunks.lobby.ui.bossbar.BossBars
import java.util.logging.Logger

class PlayerListener(
    private val logger: Logger,
    private val plugin: Plugin,
    private val mmService: MMService,
    private val partyService: PartyService,
    private val texts: Texts,
    private val bossbars: BossBars,
    private val config: Config,
    private val instanceService: InstanceService,
) : Listener {
    @EventHandler
    private fun onPlayerFlavorSelect(event: PlayerSelectFlavorEvent) {
        val player = event.player
        val flavor = event.flavor
        val chunk = event.chunk
        val ver = flavor.versionsList.first()

        var actorId = event.player.uniqueId.toString()

        val party = this.partyService.getParty(player.uniqueId)
        if (party != null) {
            if (party.owner.id != player.uniqueId) {
                player.sendMessage(this.texts.component("chunkviewer.instance.owner-required"))
                return
            }

            val arr = JsonArray()
            party.members
                .map { it.id.toString() }
                .toList()
                .forEach { arr.add(it) }

            val obj = JsonObject()
            obj.add("members", arr)

            party.owner.asPlayer()!!.storeCookie(
                NamespacedKey.fromString("spacechunks:party/members")!!,
                obj.toString().toByteArray()
            )

            actorId = party.id
        }

        val players =
            listOf(player, *party?.members?.mapNotNull { it.asPlayer() }?.toTypedArray() ?: arrayOf())

        this.logger.info("flavor selected. playerId=${player.uniqueId} flavorId=${flavor.id} flavorVersionId=${ver.id}")

        if (event.mmMode) {
            this.logger.info("creating ticket. flavorId=${flavor.id} players=${players.joinToString(",") { it.name }}")
            this.mmService.createAndPollTicket(actorId, chunk, flavor, players.size)
            return
        }


        val orderedBy = party?.owner?.id?.toString() ?: player.uniqueId.toString()
        player.setBool(PlayerMetadataKeys.MM_PRIVATE_ONGOING, true)
        this.instanceService.runFlavorVersion(orderedBy, ver.id)
            .exceptionally { ex ->
                player.removeMetadata(PlayerMetadataKeys.MM_PRIVATE_ONGOING)
                players.forEach {
                    it.sendMessage(
                        texts.component(
                            "chunkviewer.instance.creation-failed", // TODO: make matchmaking text
                            mapOf("state" to ex.message.toString())
                        )
                    )
                }
                return@exceptionally null
            }.thenAccept { instance ->
                waitForInstance(
                this.logger,
                config,
                this.instanceService,
                this.partyService,
                this.mmService,
                this.bossbars,
                this.texts,
                actorId,
                instance.id,
                players.size,
                onComplete = {
                    player.removeMetadata(PlayerMetadataKeys.MM_PRIVATE_ONGOING)
                },
            )
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        this.mmService.removeTicketByActor(player.uniqueId.toString())
        this.instanceService.cancelJob(player.uniqueId.toString())
    }
}