package bellmanford

import Utils.Edge
import java.util.LinkedList
import java.util.Queue

fun broadcastDistanceVector(source: Int, edges: List<Edge>): Map<Int, MutableMap<Int, Pair<Int, Int>>> {
    val routingTables = mutableMapOf<Int, MutableMap<Int, Pair<Int, Int>>>()

    // Initialize tables (Each node knows itself)
    val nodes = edges.flatMap { listOf(it.src, it.dest) }.toSet()
    nodes.forEach { node ->
        routingTables[node] = mutableMapOf(node to (node to 0))  // Self-entry
    }

    val queue: Queue<Pair<Int, Int>> = LinkedList()  // (node, sender)
    queue.add(source to -1) // Start broadcasting from source

    while (queue.isNotEmpty()) {
        val (currentNode, sender) = queue.poll()

        for (edge in edges.filter { it.src == currentNode || it.dest == currentNode }) {
            val neighbor = if (edge.src == currentNode) edge.dest else edge.src
            if (neighbor == sender) continue // Don't send back to sender

            val currentTable = routingTables[currentNode]!!
            val neighborTable = routingTables[neighbor]!!

            var updated = false
            for ((dest, entry) in currentTable) {
                val newCost = entry.second + edge.cost
                if (!neighborTable.containsKey(dest) || newCost < neighborTable[dest]!!.second) {
                    neighborTable[dest] = currentNode to newCost
                    updated = true
                }
            }

            if (updated) queue.add(neighbor to currentNode)
        }
    }

    return routingTables
}
