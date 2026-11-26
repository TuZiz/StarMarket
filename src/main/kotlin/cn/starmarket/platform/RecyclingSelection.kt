package cn.starmarket.platform

import cn.starmarket.domain.MarketRejected
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/** Immutable snapshot of storage slots only. Capture, validate and remove on the player thread. */
class RecyclingSelection private constructor(private val sample: ItemStack, private val slots: List<Slot>) {
    data class Slot(val index: Int, val expected: Int)
    val available: Long = slots.sumOf { it.expected.toLong() }

    fun check(player: Player, quantity: Long) {
        if (quantity !in 1..available) throw MarketRejected("quantity")
        var left = quantity
        for (slot in slots) {
            if (left == 0L) break
            val stack = player.inventory.getItem(slot.index)
            if (stack == null || stack.amount != slot.expected || !stack.isSimilar(sample))
                throw MarketRejected("recycle-changed")
            left -= minOf(left, slot.expected.toLong())
        }
    }

    fun remove(player: Player, quantity: Long) {
        if (!player.isOnline || !player.hasPermission("starmarket.use")) throw MarketRejected("offline")
        check(player, quantity) // Validate every affected slot before the first mutation.
        var left = quantity
        for (slot in slots) {
            if (left == 0L) break
            val take = minOf(left, slot.expected.toLong()).toInt()
            val remaining = slot.expected - take
            player.inventory.setItem(slot.index, if (remaining == 0) null else sample.clone().also { it.amount = remaining })
            left -= take
        }
    }

    companion object {
        fun capture(player: Player, sample: ItemStack): RecyclingSelection {
            val one = sample.clone().also { it.amount = 1 }
            val slots = (0..35).mapNotNull { index ->
                player.inventory.getItem(index)?.takeIf {
                    it.amount in 1..it.maxStackSize && it.isSimilar(one)
                }?.let { Slot(index, it.amount) }
            }
            return RecyclingSelection(one, slots)
        }

        const val LAST_STEP = 7

        fun percent(step: Int): Int {
            require(step in 1..LAST_STEP)
            return if (step == LAST_STEP) 100 else step * 15
        }

        fun proportion(available: Long, step: Int): Long {
            require(available > 0)
            return (available * percent(step) + 99) / 100
        }
    }
}
