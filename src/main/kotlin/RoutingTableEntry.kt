import Utils.INF

data class RoutingTableEntry(
    val destination: Int,
    val nextHop: Int, // -1 if no path or self
    val cost: Int
) {
    // Optional: Helper to display nicely in the table
    override fun toString(): String {
        val hopStr = if (nextHop == -1 && cost != 0) "-" else nextHop.toString()
        val costStr = when (cost) {
            0 -> "0"
            INF -> "INF"
            else -> cost.toString()
        }
        return "Dest: $destination, NextHop: $hopStr, Cost: $costStr"
    }
}