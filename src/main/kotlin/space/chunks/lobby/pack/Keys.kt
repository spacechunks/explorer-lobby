package space.chunks.lobby.pack

import org.bukkit.NamespacedKey

object Textures {
    val LOGO_WIDE = NamespacedKey.fromString("spacechunks:explorer/chunk_viewer/logo")
    val STONE_1 = NamespacedKey.fromString("spacechunks:explorer/chunk_viewer/stone1")
    val STONE_2 = NamespacedKey.fromString("spacechunks:explorer/chunk_viewer/stone2")
    val STONE_3 = NamespacedKey.fromString("spacechunks:explorer/chunk_viewer/stone3")
    val STONE_4 = NamespacedKey.fromString("spacechunks:explorer/chunk_viewer/stone4")
    val ARROW_UP = NamespacedKey.fromString("spacechunks:explorer/chunk_viewer/arrow_up")
    val ARROW_DOWN = NamespacedKey.fromString("spacechunks:explorer/chunk_viewer/arrow_down")
    val ARROW_RIGHT = NamespacedKey.fromString("spacechunks:explorer/chunk_viewer/arrow_right")
    val SATELLITE = NamespacedKey.fromString("spacechunks:explorer/chunk_viewer/satellite")

    // PLACEHOLDERS FOR TESTING
    val PH_THUMBNAIL_1 = NamespacedKey.fromString("spacechunks:explorer/chunk_viewer/chunk_thumbnail")
    val PH_THUMBNAIL_2 = NamespacedKey.fromString("spacechunks:explorer/chunk_viewer/chunk_thumbnail2")
    val PH_THUMBNAIL_3 = NamespacedKey.fromString("spacechunks:explorer/chunk_viewer/chunk_thumbnail3")
}

object Sounds {
    val CLICK = "spacechunks.explorer.chunk_viewer.click"
    val CLICK_ERR = "spacechunks.explorer.chunk_viewer.click_err"
    val JOIN_SOUND = "spacechunks-extended:lobby.join"
}

object Fonts {
    val CHUNK_VIEWER = NamespacedKey.fromString("spacechunks:chunk_viewer")
}

object Items {
    val TELEPORTER = NamespacedKey.fromString("nexo:compass")
    val PARTY = NamespacedKey.fromString("nexo:balloons")
    val SETTINGS = NamespacedKey.fromString("nexo:gear")
    val INVENTORY = NamespacedKey.fromString("nexo:crate")
    val MAP = NamespacedKey.fromString("nexo:map")
}

object Models {
    val ROBO_BLUE = "robo"
}
