package cn.starmarket.gui

import cn.starmarket.platform.Text
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.entity.Player

/** Optional CE pack glyphs. No NMS: retitle the same open container without closing the session. */
object RecyclingSkin {
    private val legacy = LegacyComponentSerializer.builder().hexColors().useUnusualXRepeatedCharacterHexFormat().build()
    private const val DIGITS = "0123456789./%"
    private fun glyph(code: Int) = String(Character.toChars(code))
    private fun shift(pixels: Int): String = buildString {
        var remaining = kotlin.math.abs(pixels)
        for (bit in 0..9) if (remaining and (1 shl bit) != 0)
            append(glyph((if (pixels >= 0) 0xFA980 else 0xFA990) + bit))
    }
    private fun number(text: String, base: Int, center: Int): String {
        require(text.all { it in DIGITS })
        val left = center - text.length * 3
        return shift(left) + text.map { glyph(base + DIGITS.indexOf(it)) }.joinToString("") + shift(-left - text.length * 6)
    }
    private fun halfShift(halfPixels: Int): String = shift(halfPixels / 2) + when (halfPixels % 2) {
        1 -> glyph(0xFA9A0)
        -1 -> glyph(0xFA9A1)
        else -> ""
    }
    private fun segment(text: String, characters: String, base: Int, left: Int, advance: (Char) -> Int): String {
        if (text.isEmpty()) return ""
        return halfShift(left) + text.map { glyph(base + characters.indexOf(it).coerceAtLeast(0)) }.joinToString("") +
            halfShift(-left - text.sumOf(advance) * 2)
    }
    /** Vanilla 16px Unifont cells render at 10px with an 11px advance. */
    private fun name(text: String, characters: String): String {
        if (characters.isEmpty()) return ""
        val visible = text.take(9)
        return segment(visible, characters, 0xFAA00, 218 - (visible.length * 11 - 1)) { 11 }
    }
    private fun price(value: String, format: String, characters: String): Map<String, String> {
        if (format.isEmpty() || characters.isEmpty()) return mapOf("pixel-price" to number(value, 0xFA930, 126))
        require(format.split("{price}").size == 2) { "gui/recycle.yml pixel-price-format must contain one {price}" }
        val advance: (Char) -> Int = { if (it == ' ') 4 else if (it.code < 128) 5 else 9 }
        var prefix = format.substringBefore("{price}")
        var suffix = format.substringAfter("{price}")
        // Keep the complete amount visible if a very large price needs the full row.
        if ((prefix + value + suffix).sumOf(advance) > 104) { prefix = ""; suffix = "" }
        val left = 218 - ((prefix + value + suffix).sumOf(advance) - 1)
        return mapOf(
            "pixel-price-prefix" to segment(prefix, characters, 0xFAB00, left, advance),
            "pixel-price" to segment(value, characters, 0xFAB00, left + prefix.sumOf(advance) * 2, advance),
            "pixel-price-suffix" to segment(suffix, characters, 0xFAB00, left + (prefix + value).sumOf(advance) * 2, advance)
        )
    }
    fun values(values: Map<String, String>, nameCharacters: String = "", emptyName: String = "",
               priceFormat: String = "", priceCharacters: String = ""): Map<String, String> = mapOf(
        "pixel-name" to name(values["name"] ?: emptyName, nameCharacters),
        "pixel-quantity" to number("${values["quantity"] ?: "0"}/${values["available"] ?: "0"}", 0xFA910, 88),
        "pixel-total" to number(values["total"] ?: "0", 0xFA950, 110)
    ) + price(values["price"] ?: "0", priceFormat, priceCharacters)
    @Suppress("DEPRECATION")
    fun refreshTitle(player: Player, title: String) {
        // Paper 26.2/26.3 setTitle sends a title update for this container; it does not call closeInventory.
        player.openInventory.setTitle(legacy.serialize(Text.render(title)))
    }
}
