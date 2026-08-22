package space.chunks.lobby.modules.spawn

import com.google.gson.JsonParser
import com.noxcrew.interfaces.InterfacesConstants
import io.papermc.paper.event.player.AsyncChatEvent
import kotlinx.coroutines.launch
import kr.toxicity.model.api.BetterModel
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter
import kr.toxicity.model.api.tracker.DummyTracker
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import org.bukkit.*
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.player.*
import org.bukkit.plugin.Plugin
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitTask
import space.chunks.lobby.modules.chunkviewer.display.DisplaySessionService
import space.chunks.lobby.modules.chunkviewer.event.PlayerIntentLeaveDisplaySessionEvent
import space.chunks.lobby.modules.chunkviewer.event.PlayerSelectFlavorEvent
import space.chunks.lobby.modules.matchmaking.MMService
import space.chunks.lobby.modules.party.PartyService
import space.chunks.lobby.pack.Models
import space.chunks.lobby.ui.ActionBar
import space.chunks.lobby.ui.ScreenTransition
import space.chunks.lobby.ui.Texts
import space.chunks.visual.ui.UiService
import java.time.Duration
import java.util.function.Consumer
import java.util.logging.Logger

class PlayerListener(
    private val logger: Logger,
    private val plugin: Plugin,
    private val config: Config,
    private val sessionService: DisplaySessionService,
    private val texts: Texts,
    private val uiService: UiService,
    private val mmService: MMService,
    private val partyService: PartyService,
) : Listener {
    private val transition = ScreenTransition(this.plugin, this.texts)
    private val hotbar = Hotbar(
        this.logger,
        this.sessionService,
        this.texts,
        this.uiService,
        this.transition,
        this.partyService,
        this.mmService,
    )

    private val spawnLocation = this.location(this.config.spawnLocation)
    private val robosPerPlayer = mutableMapOf<Player, DummyTracker>()
    private val tasksPerPlayer = mutableMapOf<Player, BukkitTask>()

//    by Atlas.api.requiredLocation(
//        world = { Bukkit.getWorld(this.config.world) },
//        key = "spawn",
//        fallback = { this.configuredSpawnLocation() },
//    )

    @EventHandler
    private fun onPlayerJoin(event: PlayerJoinEvent) {
        event.joinMessage(Component.empty())

        val player = event.player

        // clear all keys, because they should only be valid for one session.
        // a session in this case lasts until the player disconnects.
        player.persistentDataContainer.keys.forEach {
            player.persistentDataContainer.remove(it)
        }

        // disable appleskin mod showing stuff in the actionbar
        player.saturation = 0f

        this.uiService.show(player)
        ActionBar.clear(player)
        player.inventory.clear()
        player.gameMode = GameMode.ADVENTURE

        player.teleport(this.spawnLocation)

        player.sendPlayerListHeaderAndFooter(
            this.texts.component("spawn.tablist.header"),
            this.texts.component("spawn.tablist.footer")
        )

        val title = Title.title(
            this.texts.component("spawn.title.logo"),
            Component.empty(),
            Title.Times.times(
                Duration.ofMillis(0),
                Duration.ofMillis(2000),
                Duration.ofMillis(1000)
            )
        )
        player.showTitle(title)

        val playerName = this.playerName(player)
        player.playerListName(playerName)

        InterfacesConstants.SCOPE.launch {
            hotbar.open(player)
        }

        player
            .retrieveCookie(NamespacedKey.fromString("spacechunks:explorer/gateway/pushback")!!)
            .thenAccept {
                if (it == null) {
                    return@thenAccept
                }

                val obj = JsonParser.parseString(it.decodeToString()).asJsonObject
                val reason = obj["reason"].asString

                if (reason == "CONNECTION_ERROR") {
                    this.texts.send(player, "spawn.pushback.connection-error")
                    return@thenAccept
                }

                if (reason == "TRANSFER_DATA_INVALID") {
                    this.texts.send(player, "spawn.pushback.transfer-data-invalid")
                    return@thenAccept
                }

                if (reason == "TRANSFER_DATA_RECEIVE_TIMEOUT") {
                    this.texts.send(player, "spawn.pushback.transfer-data-timeout")
                    return@thenAccept
                }
            }

        val roboLoc = this.location(this.config.roboSpawnLocation)
        roboLoc.yaw = 180f

        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            BetterModel.model(Models.ROBO_BLUE)
                .map({ r -> r.create(BukkitAdapter.adapt(roboLoc)) })
                .ifPresent { dummy ->
                    this.robosPerPlayer[player] = dummy
                    dummy.spawn(BukkitAdapter.adapt(player))

                    val task = LookAtPlayerTask(dummy, player, roboLoc)
                        .runTaskTimer(plugin, 0L, 2L)

                    this.tasksPerPlayer[player] = task

                    Bukkit.getScheduler().runTaskTimer(plugin, Consumer {
                        if (player.resourcePackStatus != PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
                            return@Consumer
                        }
                        
                        // wait a bit until loading screen is gone
                        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                            dummy.animate("hand_wave")
                        }, 10)

                        it.cancel()
                    }, 0, 20)
                }
        }, 20)
    }

    @EventHandler
    private fun onAsyncChat(event: AsyncChatEvent) {
        event.renderer { source, _, message, _ ->
            this.texts.component(
                "${this.playerTextPath(source)}.chat-format",
                mapOf(
                    "name" to source.name,
                    "playerName" to event.player.name,
                    "message" to message,
                )
            )
        }
    }

    @EventHandler(ignoreCancelled = true)
    private fun onPlayerInteract(event: PlayerInteractEvent) {
        // nexo uses note blocks to display custom blocks.
        // interacting with them will change the block.
        if (event.clickedBlock?.type == Material.NOTE_BLOCK) {
            event.isCancelled = true
        }
    }

    @EventHandler
    private fun onWorldChange(event: PlayerChangedWorldEvent) {
        // we have to respawn robo again, because it's gone when we switch worlds
        // delay, so the player's chunks are actually loaded client-side first.
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            this.robosPerPlayer[event.player]?.despawn()
            this.robosPerPlayer[event.player]?.spawn(BukkitAdapter.adapt(event.player))
            this.robosPerPlayer[event.player]?.animate("hand_wave")
        }, 5L);
    }

    @EventHandler
    private fun onEntityDamage(event: EntityDamageEvent) {
        event.isCancelled = true
    }

    @EventHandler
    private fun onDropItem(event: PlayerDropItemEvent) {
        event.isCancelled = true
    }

    @EventHandler
    private fun onFoodLevelChangeEvent(event: FoodLevelChangeEvent) {
        event.isCancelled = true
    }

    @EventHandler
    private fun onQuit(event: PlayerQuitEvent) {
        event.quitMessage(Component.empty())

        val player = event.player

        this.tasksPerPlayer[player]?.cancel()
        this.tasksPerPlayer.remove(player)

        this.robosPerPlayer[player]?.close()
        this.robosPerPlayer.remove(player)
    }

    @EventHandler
    private fun onPlayerFlavorSelect(event: PlayerSelectFlavorEvent) {
        this.closeSessionAndBackToSpawn(event.player)
    }

    @EventHandler
    private fun onPlayerIntentLeaveDisplaySession(event: PlayerIntentLeaveDisplaySessionEvent) {
        this.closeSessionAndBackToSpawn(event.player)
    }

    private fun closeSessionAndBackToSpawn(player: Player) {
        player.addPotionEffect(
            PotionEffectType.DARKNESS
                .createEffect(25, Int.MAX_VALUE)
                .withParticles(false)
        )
        player.spectatorTarget = null
        player.gameMode = GameMode.ADVENTURE
        player.teleport(this.spawnLocation)

        this.sessionService.closeSession(player)
        this.uiService.show(player)
        ActionBar.clear(player)
        this.hotbar.open(player)
    }

    private fun playerName(player: Player): Component =
        this.texts.component(
            "${this.playerTextPath(player)}.player-name",
            mapOf("name" to player.name)
        )

    private fun location(cfg: LocationConfig): Location {
        val world = Bukkit.getWorld(cfg.world)
            ?: WorldCreator.ofKey(NamespacedKey.fromString(cfg.world)!!).createWorld();

        val loc = Location(
            world,
            cfg.x,
            cfg.y,
            cfg.z,
        )
        loc.yaw = cfg.yaw
        loc.pitch = cfg.yaw
        return loc
    }

    private fun playerTextPath(player: Player): String {
        val sections = this.texts.sectionKeys("spawn.player")

        sections.forEach { section ->
            if (section == "default") {
                return@forEach
            }

            val permission = this.texts.stringOrNull("spawn.player.$section.permission") ?: return@forEach
            if (permission.isNotBlank() && player.hasPermission(permission)) {
                return "spawn.player.$section"
            }
        }

        return "spawn.player.default"
    }
}
