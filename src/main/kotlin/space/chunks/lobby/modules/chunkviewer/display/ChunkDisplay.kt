package space.chunks.lobby.modules.chunkviewer.display

import chunks.space.api.explorer.chunk.v1alpha1.Types
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Display
import org.bukkit.entity.EntityType
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.TextDisplay
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Transformation
import org.joml.AxisAngle4f
import org.joml.Matrix4f
import org.joml.Vector3f
import space.chunks.lobby.pack.Fonts

class ChunkDisplay(
    val title: Component,
    val chunk: Types.Chunk,
    val thumbnailKey: NamespacedKey,
) {
    var location: Location? = null
    var center: Location? = null

    private var backgroundDisplay: ItemDisplay? = null
    private var highlightDisplay: ItemDisplay? = null
    private var itemDisplay: ItemDisplay? = null
    private var tdName: TextDisplay? = null
    private var tdDesc: MutableList<TextDisplay> = mutableListOf()
    private var isFocused: Boolean = false
    private var breathingTask: BukkitTask? = null

    companion object {
        private val ICON_MATERIAL = Material.PAPER
        private const val ICON_SCALE = 3.5f
        private const val ICON_FOCUS_SCALE = 3.39f//3.2f //3.39f
        private const val ICON_OFFSET = 2.0
    }

    fun spawn() {
        this.remove()

        val iconLocation = this.location!!.clone().add(0.0, 0.0, ICON_OFFSET)
        val world = iconLocation.world

            this.itemDisplay = world.spawn(iconLocation, ItemDisplay::class.java) { display ->
                val iconItem = ItemStack(ICON_MATERIAL)
                iconItem.editMeta { ii ->
                    ii.itemModel = this.thumbnailKey
                }

                display.setItemStack(iconItem)

                display.transformation = Transformation(
                    Vector3f(0f, 0f, 0f),
                    AxisAngle4f(0f, 0f, 0f, 0f),
                    Vector3f(ICON_SCALE, ICON_SCALE, ICON_SCALE),
                    AxisAngle4f(0f, 0f, 0f, 0f)
                )

                display.billboard = Display.Billboard.CENTER
                SelectorShaderMarkers.mark(display)
            }
    }

    fun remove() {
        this.clearFocusDisplays()
        this.breathingTask?.cancel()
        this.breathingTask = null
        this.isFocused = false
        backgroundDisplay?.remove()
        highlightDisplay?.remove()
        itemDisplay?.remove()
        backgroundDisplay = null
        highlightDisplay = null
        itemDisplay = null
    }

    fun setFocus(focused: Boolean, animate: Boolean = false, plugin: org.bukkit.plugin.Plugin? = null): Boolean {
        if (isFocused == focused) return false

        // in german we call what comes next "kompletter einschiss"

        if (focused) {
            this.itemDisplay?.isGlowing = true
            this.breathingTask?.cancel()
            this.breathingTask = object : BukkitRunnable() {
                override fun run() {
                    val curr = this@ChunkDisplay.itemDisplay?.transformation?.scale?.x
                    val scale = if (curr == ICON_FOCUS_SCALE) ICON_SCALE else ICON_FOCUS_SCALE

                    this@ChunkDisplay.itemDisplay?.setTransformationMatrix(Matrix4f().scale(scale))
                    this@ChunkDisplay.itemDisplay?.interpolationDuration = 15
                    this@ChunkDisplay.itemDisplay?.interpolationDelay = 0
                }
            }.runTaskTimer(plugin!!, 0L, 15L)
        } else {
            this.breathingTask?.cancel()
            this.breathingTask = null
            this.itemDisplay?.isGlowing = false

            this.itemDisplay?.let { d ->
                d.setTransformationMatrix(Matrix4f().scale(ICON_SCALE))
                d.interpolationDuration = 0
                d.interpolationDelay = 0
            }
        }

        this.isFocused = focused

        if (focused) {
            val loc = center!!.clone().subtract(0.0, 4.7, 3.0)
            this.tdName = loc.world.spawnEntity(loc, EntityType.TEXT_DISPLAY) as TextDisplay
            this.tdName!!.text(
                this.title.color(TextColor.fromHexString("#52cefd")).font(Fonts.CHUNK_VIEWER)
            )
            this.tdName!!.backgroundColor = Color.fromARGB(0, 0, 0, 0)
            this.tdName!!.setTransformationMatrix(
                Matrix4f().scale(1.5f).rotate(AxisAngle4f(Math.toRadians(-180.0).toFloat(), 0f, 1f, 0f))
            )
            this.tdName!!.brightness = Display.Brightness(15, 15)
            SelectorShaderMarkers.mark(this.tdName!!)

            val lines = wrapText(this.chunk.description, 30)

            var space = 0.0

            lines.forEach { l ->
                space += 0.25
                val descLoc = loc.clone().subtract(0.0, space, 0.0)
                val tdDesc = descLoc.world.spawnEntity(descLoc, EntityType.TEXT_DISPLAY) as TextDisplay
                tdDesc.text(Component.text(l!!).font(Fonts.CHUNK_VIEWER))
                tdDesc.backgroundColor = Color.fromARGB(0, 0, 0, 0)
                tdDesc.setTransformationMatrix(
                    Matrix4f().scale(1f).rotate(AxisAngle4f(Math.toRadians(-180.0).toFloat(), 0f, 1f, 0f))
                )
                tdDesc.brightness = Display.Brightness(15, 15)
                SelectorShaderMarkers.mark(tdDesc)
                this.tdDesc.add(tdDesc)
            }

        } else {
            this.clearFocusDisplays()
        }

        return true
    }

    private fun clearFocusDisplays() {
        this.tdName?.remove()
        this.tdName = null
        this.tdDesc.forEach { it.remove() }
        this.tdDesc.clear()
    }

    fun wrapText(input: String, maxLineLength: Int): MutableList<String?> {
        val lines: MutableList<String?> = ArrayList()
        val current = StringBuilder()

        for (word in input.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
            if (current.isEmpty()) {
                current.append(word)
            } else if (current.length + 1 + word.length <= maxLineLength) {
                current.append(' ').append(word)
            } else {
                lines.add(current.toString())
                current.setLength(0)
                current.append(word)
            }
        }

        if (!current.isEmpty()) {
            lines.add(current.toString())
        }

        return lines
    }
}
