package space.chunks.lobby.modules.chunkviewer.display

import org.bukkit.Color
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.TextDisplay

object SelectorShaderMarkers {
    private const val TEXT_OPACITY_MARKER: Byte = -2 // unsigned 254
    private const val ITEM_BLOCK_LIGHT_MARKER = 15
    private const val ITEM_SKY_LIGHT_MARKER = 14
    private val OUTLINE_COLOR_MARKER = Color.fromRGB(18, 254, 52)

    fun mark(display: TextDisplay) {
        display.textOpacity = TEXT_OPACITY_MARKER
    }

    fun mark(display: ItemDisplay) {
        display.brightness = Display.Brightness(ITEM_BLOCK_LIGHT_MARKER, ITEM_SKY_LIGHT_MARKER)
        display.glowColorOverride = OUTLINE_COLOR_MARKER
    }
}
