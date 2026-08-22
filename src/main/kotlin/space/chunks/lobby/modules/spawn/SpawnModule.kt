package space.chunks.lobby.modules.spawn

import com.mojang.brigadier.Command
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.*
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import space.chunks.lobby.modules.LobbyModule
import space.chunks.lobby.modules.chunkviewer.display.DisplaySessionService
import space.chunks.lobby.modules.chunkviewer.event.PlayerIntentLeaveDisplaySessionEvent
import space.chunks.lobby.modules.matchmaking.MMService
import space.chunks.lobby.modules.party.PartyService
import space.chunks.lobby.ui.Texts
import space.chunks.visual.ui.UiService


class SpawnModule(
    private val sessSvc: DisplaySessionService,
    plugin: Plugin,
    private val texts: Texts,
    private val uiService: UiService,
    private val partyService: PartyService,
    private val mmService: MMService,
) : LobbyModule(plugin, "spawn") {
    override fun onEnable() {
        val cfg = Config.parse(this.plugin.config)
        val w = WorldCreator.ofKey(NamespacedKey.fromString(cfg.spawnLocation.world)!!).createWorld()

        Bukkit.getPluginManager().registerEvents(
            PlayerListener(
                this.logger,
                this.plugin,
                cfg,
                this.sessSvc,
                this.texts,
                this.uiService,
                this.mmService,
                this.partyService,
            ),
            this.plugin,
        )

        this.plugin.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            event.registrar().register(
                Commands.literal("spawn")
                    .executes { ctx ->
                        if (ctx.source.sender !is Player) {
                            return@executes Command.SINGLE_SUCCESS
                        }

                        val player = ctx.source.sender as Player

                        if (this.sessSvc.getSession(player) != null) {
                            this.sessSvc.closeSession(player)
                            Bukkit.getPluginManager().callEvent(PlayerIntentLeaveDisplaySessionEvent(player))
                            return@executes Command.SINGLE_SUCCESS
                        }

                        player.teleport(
                            Location(
                                w,
                                cfg.spawnLocation.x,
                                cfg.spawnLocation.y,
                                cfg.spawnLocation.z,
                            )
                        )
                        Command.SINGLE_SUCCESS
                    }
                    .build()
            )
        }

        Bukkit.getServer().getWorld(cfg.spawnLocation.world)?.setGameRule(GameRules.LOCATOR_BAR, false)
    }

    override fun onDisable() {
        this.uiService.stop()
    }
}
