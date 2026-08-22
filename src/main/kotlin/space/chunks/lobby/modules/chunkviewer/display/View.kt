package space.chunks.lobby.modules.chunkviewer.display

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.*
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Transformation
import org.joml.AxisAngle4f
import org.joml.Matrix4f
import org.joml.Vector3f
import kotlin.math.cos
import kotlin.math.sin

abstract class View(
    protected val plugin: Plugin,
    protected val center: Location,
    protected val session: DisplaySession
) {
    protected val elements = mutableListOf<Entity>()
    protected val mm = MiniMessage.miniMessage()
    protected val animationTasks = mutableListOf<BukkitRunnable>()

    abstract fun render()
    abstract fun close()
    abstract fun handleInput(player: Player, input: Input)

    protected fun closeElements() {
        this.animationTasks.forEach { it.cancel() }
        this.animationTasks.clear()
        this.elements.forEach { it.remove() }
        this.elements.clear()
    }

    protected fun spawnTextElement(txt: Component, loc: Location, scale: Float): TextDisplay {
        return loc.world.spawn(loc, TextDisplay::class.java) { d ->
            this.elements.add(d)
            d.text(txt)
            d.setTransformationMatrix(
                Matrix4f().scale(scale).rotate(AxisAngle4f(Math.toRadians(-180.0).toFloat(), 0f, 1f, 0f))
            )
            d.alignment = TextDisplay.TextAlignment.LEFT
            d.billboard = Display.Billboard.FIXED
            d.backgroundColor = Color.fromARGB(0, 0, 0, 0) // transparent background
            d.brightness = Display.Brightness(15, 15)
            SelectorShaderMarkers.mark(d)
        }
    }

    protected fun spawnItemDisplay(
        location: Location,
        scale: Vector3f,
        key: NamespacedKey?,
        animate: Boolean,
    ): ItemDisplay {
        return location.world.spawn(location, ItemDisplay::class.java) { d ->
            val stack = ItemStack(Material.PAPER)
            stack.editMeta { m ->
                m.itemModel = key
            }

            d.setItemStack(stack)
            d.billboard = Display.Billboard.CENTER
            d.setTransformationMatrix(Matrix4f().scale(scale))
            SelectorShaderMarkers.mark(d)

            this.elements.add(d)

            if (!animate) return@spawn

            // AI
            val base: Transformation = d.getTransformation()
            val baseTranslation = Vector3f(base.getTranslation())

            val rand = java.util.Random()
            val step = rand.nextFloat(0.02f, 0.04f)

            val task = object : BukkitRunnable() {
                var time: Double = Math.random() * Math.PI * 2

                override fun run() {
                    time += step

                    val x = (sin(time) * 0.15).toFloat()
                    val y = (sin(time * 1.5) * 0.10).toFloat()
                    val z = (cos(time * 1.2) * 0.15).toFloat()

                    val translation = Vector3f(
                        baseTranslation.x + x,
                        baseTranslation.y + y,
                        baseTranslation.z + z
                    )

                    d.transformation = Transformation(
                        translation,
                        base.getLeftRotation(),
                        base.getScale(),
                        base.getRightRotation()
                    )
                    d.interpolationDelay = 0
                }
            }
            this.animationTasks.add(task)
            task.runTaskTimer(this.plugin, 0L, 1)
        }
    }
}
