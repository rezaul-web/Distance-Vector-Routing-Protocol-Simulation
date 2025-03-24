package bellmanford

data class RoutingTableEntry(val destination: Int, val nextHop: Int, val cost: Int)