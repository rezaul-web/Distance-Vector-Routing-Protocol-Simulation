package bellmanford


import Utils.Edge
import Utils.INF
import Utils.Node
import kotlin.collections.indices

fun bellmanFordAllPairsRoutingTable(nodes: List<Utils.Node>, edges: List<Utils.Edge>): Array<Array<RoutingTableEntry>> {
    val n = nodes.size
    val tables = Array(n) { Array(n) { RoutingTableEntry(-1, -1, INF) } }

    for (src in nodes.indices) {
        val (dist, nextHop) = bellmanFord(nodes, edges, src)
        tables[src] = Array(n) { dest -> RoutingTableEntry(dest, nextHop[dest], dist[dest]) }
    }

    return tables
}
fun bellmanFord(nodes: List<Node>, edges: List<Edge>, src: Int): Pair<IntArray, IntArray> {
    val n = nodes.size
    val dist = IntArray(n) { INF }
    val nextHop = IntArray(n) { -1 }

    dist[src] = 0

    // Initialize nextHop: Direct neighbors of src should point to themselves as next hop
    for (edge in edges) {
        if (edge.src == src) {
            nextHop[edge.dest] = edge.dest
        }
    }

    repeat(n - 1) {
        for (edge in edges) {
            if (dist[edge.src] < INF && dist[edge.src] + edge.cost < dist[edge.dest]) {
                dist[edge.dest] = dist[edge.src] + edge.cost

                // If updating distance, set next hop
                if (edge.src == src) {
                    nextHop[edge.dest] = edge.dest
                } else {
                    nextHop[edge.dest] = nextHop[edge.src]
                }
            }
        }
    }
    return dist to nextHop
}