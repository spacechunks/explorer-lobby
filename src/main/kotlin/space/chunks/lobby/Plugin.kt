package space.chunks.lobby

import chunks.space.api.explorer.chunk.v1alpha1.ChunkServiceGrpcKt
import chunks.space.api.explorer.instance.v1alpha1.InstanceServiceGrpcKt
import chunks.space.api.matchmaking.v1alpha1.MatchmakingServiceGrpcKt
import com.noxcrew.interfaces.InterfacesListeners
import io.grpc.ManagedChannelBuilder
import io.grpc.netty.NettyChannelBuilder
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent
import net.kyori.adventure.resource.ResourcePackInfo
import net.kyori.adventure.resource.ResourcePackRequest
import net.kyori.adventure.resource.ResourcePackStatus
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.generator.ChunkGenerator
import org.bukkit.plugin.java.JavaPlugin
import org.ktorm.database.Database
import org.ktorm.dsl.*
import org.ktorm.schema.Table
import org.ktorm.schema.timestamp
import org.ktorm.schema.uuid
import space.chunks.auth.grpc.AuthCredentials
import space.chunks.auth.oauth.ClientCredentialsTokenSource
import space.chunks.auth.oauth.ReusableTokenSource
import space.chunks.lobby.controlplane.instance.InstanceService
import space.chunks.lobby.modules.chunkviewer.ChunkViewerModule
import space.chunks.lobby.modules.chunkviewer.world.VoidWorldGenerator
import space.chunks.lobby.modules.matchmaking.MMModule
import space.chunks.lobby.modules.matchmaking.MMService
import space.chunks.lobby.modules.party.PartyModule
import space.chunks.lobby.modules.party.PartyService
import space.chunks.lobby.modules.spawn.SpawnModule
import space.chunks.lobby.pack.PackService
import space.chunks.lobby.pack.ResourcePackConfig
import space.chunks.lobby.ui.ActionBar
import space.chunks.lobby.ui.Texts
import space.chunks.lobby.ui.bossbar.BossBars
import space.chunks.visual.ui.UiService
import java.net.URI
import java.time.Instant
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import space.chunks.lobby.controlplane.Config as ControlPlaneConfig
import space.chunks.lobby.modules.matchmaking.Config as MMConfig
import space.chunks.lobby.modules.spawn.Config as SpawnConfig

class Plugin : JavaPlugin(), Listener {
    private val cpConfig = ControlPlaneConfig.parse(this.config)
    private val mmConfig = MMConfig.parse(this.config)
    private val spawnConfig = SpawnConfig.parse(this.config)

    private val cpChannel = ManagedChannelBuilder
        .forAddress(this.cpConfig.addr, this.cpConfig.port)
        .keepAliveTime(30, TimeUnit.SECONDS)      // ping server every 30s
        .keepAliveTimeout(10, TimeUnit.SECONDS)   // wait 10s for pong
        .useTransportSecurity()
        .build()

    private val mmChannel = NettyChannelBuilder
        .forAddress(this.mmConfig.addr, this.mmConfig.port)
        .usePlaintext()
        .keepAliveTime(30, TimeUnit.SECONDS)
        .keepAliveTimeout(10, TimeUnit.SECONDS)
        .keepAliveWithoutCalls(true)
        .build()

    val tokSrc = ReusableTokenSource(ClientCredentialsTokenSource(this.cpConfig.authConfig))

    private val mmClient = MatchmakingServiceGrpcKt.MatchmakingServiceCoroutineStub(mmChannel)
    private val instanceClient = InstanceServiceGrpcKt.InstanceServiceCoroutineStub(cpChannel)
        .withCallCredentials(AuthCredentials(tokSrc))

    private val chunkClient = ChunkServiceGrpcKt.ChunkServiceCoroutineStub(cpChannel)
        .withCallCredentials(AuthCredentials(tokSrc))

    private val packConfig = ResourcePackConfig.parse(this.config)
    private val packService = PackService(this.logger, this, packConfig)
    private val partyService = PartyService()
    private val mmService = MMService(
        this.logger,
        this.mmClient,
        this.mmConfig.ticketPollInterval.seconds,
        this,
    )
    private val instanceService = InstanceService(
        this.logger,
        this.instanceClient,
        this.cpConfig.instancePollIntervalSeconds.seconds,
    )
    private val uiService = UiService(ActionBar::send)
    private val texts = Texts(this)
    private val bossbars = BossBars(this.uiService)

    // modules
    private val chunkViewerMod = ChunkViewerModule(this, this.packConfig, this.chunkClient, this.texts)
    private val spawnMod = SpawnModule(
        this.chunkViewerMod.sessionService,
        this,
        this.texts,
        this.uiService,
        this.partyService,
        this.mmService,
    )
    private val partyMod = PartyModule(this, this.partyService, this.texts, this.bossbars)
    private val mmMod = MMModule(
        this,
        this.partyService,
        this.mmService,
        this.instanceService,
        this.texts,
        this.bossbars,
        this.mmConfig,
    )

    private lateinit var db: Database

    private val modules = listOf(
        chunkViewerMod,
        spawnMod,
        partyMod,
        mmMod,
    )

    override fun onEnable() {
        Class.forName("org.postgresql.Driver")
        this.db = Database.connect(
            url = this.spawnConfig.postgresDSN,
        )
        installInterfaces()
        Bukkit.getPluginManager().registerEvents(this.uiService, this)
        this.uiService.start(this)
        this.packService.startPeriodicPull()
        Bukkit.getPluginManager().registerEvents(this, this)

        modules.forEach {
            it.onEnable()
        }
    }

    override fun onDisable() {
        this.modules.forEach {
            it.onDisable()
        }
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!label.equals("mm", ignoreCase = true)) {
            return false
        }

        this.chunkViewerMod.sessionService.startSession(sender as Player, true)
        return true
    }

    private fun installInterfaces() {
        runCatching {
            InterfacesListeners.install(this)
        }.onFailure { ex ->
            if (ex !is IllegalArgumentException) {
                throw ex
            }
            logger.info("Noxcrew interfaces are already installed; reusing the existing listener.")
        }
    }

    object PlayerProfiles : Table<Nothing>("player_profiles") {
        val id = uuid("id").primaryKey()
        val minecraftId = uuid("minecraft_id")
        val firstJoined = timestamp("first_joined")
        val createdAt = timestamp("created_at")
        val updatedAt = timestamp("updated_at")
    }

    fun getFirstJoined(database: Database, minecraftId: UUID): Instant? {
        return database
            .from(PlayerProfiles)
            .select(PlayerProfiles.firstJoined)
            .where { PlayerProfiles.minecraftId eq minecraftId }
            .map { row -> row[PlayerProfiles.firstJoined] }
            .firstOrNull()
    }

    @EventHandler
    fun onAsyncConfigure(event: AsyncPlayerConnectionConfigureEvent) {
        val conn = event.connection
        val firstJoined = getFirstJoined(this.db, conn.profile.id!!)

        if (firstJoined == null) {
            val gdprDiag = PrivacyPolicyDialog()
            val gdprFut = gdprDiag.show(conn.audience)

            val accepted = try {
                gdprFut.get(3, TimeUnit.MINUTES)
            } catch (_: Exception) {
                false
            }

            if (!accepted) {
                conn.disconnect(
                    Component.text("You have to accept the privacy policy to play!")
                        .color(NamedTextColor.RED)
                )
                return
            }

            this.db.insert(PlayerProfiles) {
                set(it.minecraftId, conn.profile.id)
                set(it.firstJoined, Instant.now())
            }
        }

        val packFut = CompletableFuture<ResourcePackStatus>()
        val hash = this.packService.packHash.get()
        val info = ResourcePackInfo.resourcePackInfo(
            UUID.fromString("92de217b-8b2b-403b-86a5-fe26fa3a9b5f"),
            URI.create(this.packService.packDownloadUrl),
            hash
        )

        val request = ResourcePackRequest.resourcePackRequest()
            .packs(info)
            .required(true)
            .callback { _, status, _ ->
                packFut.complete(status)
            }
            .build()

        conn.audience.sendResourcePacks(request)

        val status = try {
            packFut.get(30, TimeUnit.SECONDS)
        } catch (_: Throwable) {
            null
        }

        when (status) {
            ResourcePackStatus.ACCEPTED,
            ResourcePackStatus.SUCCESSFULLY_LOADED,
            ResourcePackStatus.DOWNLOADED -> {
                this.packService.setCurrentPack(conn.profile.id!!, hash)
                return
            }

            ResourcePackStatus.DECLINED -> {
                conn.disconnect(this.texts.component("common.resource-pack.declined"))
            }

            ResourcePackStatus.FAILED_DOWNLOAD,
            ResourcePackStatus.FAILED_RELOAD,
            ResourcePackStatus.INVALID_URL,
            ResourcePackStatus.DISCARDED,
            null -> {
                conn.disconnect(this.texts.component("common.resource-pack.failed"))
            }
        }
    }

    override fun getDefaultWorldGenerator(worldName: String, id: String?): ChunkGenerator {
        return VoidWorldGenerator()
    }
}
