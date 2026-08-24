package space.chunks.lobby.modules.matchmaking.listener

import com.noxcrew.interfaces.InterfacesConstants
import com.noxcrew.interfaces.InterfacesListeners
import kotlinx.coroutines.launch
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import space.chunks.lobby.controlplane.instance.InstanceService
import space.chunks.lobby.extensions.latestCompleted
import space.chunks.lobby.extensions.setBool
import space.chunks.lobby.extensions.toAudience
import space.chunks.lobby.modules.matchmaking.Config
import space.chunks.lobby.modules.matchmaking.MMService
import space.chunks.lobby.modules.matchmaking.event.*
import space.chunks.lobby.modules.matchmaking.getPlayersByActorId
import space.chunks.lobby.modules.matchmaking.waitForInstance
import space.chunks.lobby.modules.party.PartyService
import space.chunks.lobby.player.PlayerMetadataKeys
import space.chunks.lobby.ui.Texts
import space.chunks.lobby.ui.bossbar.BossBars
import space.chunks.lobby.ui.bossbar.LoadingBossBar
import java.util.*
import java.util.logging.Logger

class TicketListener(
    private val logger: Logger,
    private val config: Config,
    private val mmService: MMService,
    private val instanceService: InstanceService,
    private val partyService: PartyService,
    private val bossbars: BossBars,
    private val texts: Texts,
) : Listener {
    @EventHandler
    fun onTicketAssignment(event: TicketAssignmentEvent) {
        waitForInstance(
            logger,
            config,
            instanceService,
            partyService,
            mmService,
            bossbars,
            texts,
            event.actorId,
            event.instanceId,
            event.ticket.match.playerCount,
        )
    }

    @EventHandler
    fun onTicketActivated(event: TicketActivatedEvent) {
        val data = event.data
        val players = getPlayersByActorId(this.partyService, data.actorId)

        val party = partyService.getPartyById(data.actorId)
        val player = party?.owner?.asPlayer() ?: Bukkit.getPlayer(UUID.fromString(data.actorId))

        player?.let {
            it.setBool(PlayerMetadataKeys.MM_SEARCH_ONGOING, true)

            InterfacesConstants.SCOPE.launch {
                InterfacesListeners.INSTANCE.getOpenPlayerInterface(it.uniqueId)?.reopen()
            }
        }

        val version = data.chunk
            .flavorsList
            .find { it.id == data.flavor.id }
            ?.versionsList
            ?.latestCompleted()

        bossbars.sendLoadingBar(
            players,
            players.size,
            LoadingBossBar.LoadingState.WAITING_FOR_PLAYERS,
            data.chunk.name,
            data.flavor.name,
            version?.maxPlayers ?: -1,
        )
    }

    @EventHandler
    fun onMatchFound(event: MatchUpdateEvent) {
        val data = event.data
        val players = getPlayersByActorId(this.partyService, data.actorId)
        bossbars.sendLoadingBar(
            players,
            data.ticket.match.playerCount,
            LoadingBossBar.LoadingState.MATCH_FOUND,
            data.chunk.name,
            data.flavor.name,
            data.ticket.match.maxPlayers,
        )
    }

    @EventHandler
    fun onMatchCancelled(event: MatchCancelledEvent) {
        val data = event.data
        val players = getPlayersByActorId(this.partyService, data.actorId)

        this.texts.send(
            players.toAudience(),
            "matchmaking.match.cancelled",
        )

        // this is a bit nasty, because we operate on potentially outdated data,
        // since the chunk is still the one initially fetched by the chunk viewer.
        // in the meantime there could be a new flavor version with a different
        // max player count. but to be honest, it will _probably_ be fine in 99.9%
        // of the cases, so we'll care about it if people complain.
        val version = data.chunk
            .flavorsList
            .find { it.id == data.flavor.id }
            ?.versionsList
            ?.latestCompleted()

        this.mmService.createAndPollTicket(data.actorId, data.chunk, data.flavor, players.size)

        bossbars.sendLoadingBar(
            players,
            players.size,
            LoadingBossBar.LoadingState.WAITING_FOR_PLAYERS,
            data.chunk.name,
            data.flavor.name,
            version?.maxPlayers ?: -1,
        )
    }

    @EventHandler
    fun onTicketCancelled(event: TicketCancelledEvent) {
        val players = getPlayersByActorId(this.partyService, event.actorId)
        this.bossbars.clearLoadingBar(players)
        when (event.cause) {
            TicketCancelCause.NO_PLAYABLE_FLAVOR_VERSION -> this.texts.send(
                players.toAudience(),
                "matchmaking.ticket.cancelled-no-playable-flavor-version",
            )

            TicketCancelCause.NOT_FOUND -> this.texts.send(
                players.toAudience(),
                "matchmaking.ticket.cancelled-not-found",
                mapOf("ticket_id" to event.ticketId!!)
            )

            TicketCancelCause.SERVICE_UNAVAILABLE -> this.texts.send(
                players.toAudience(),
                "matchmaking.ticket.cancelled-service-unavailable",
            )

            TicketCancelCause.REMOVED -> this.texts.send(
                players.toAudience(),
                "matchmaking.ticket.cancelled-ticket-removed"
            )
        }
    }
}