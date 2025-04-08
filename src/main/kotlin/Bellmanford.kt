

import Utils.Edge
import Utils.INF
import Utils.Node

/**
 * Initializes the distance vector tables for all nodes.
 * Each node initially knows only the cost to itself (0) and its direct neighbors.
 */
fun initializeDvTables(nodes: List<Node>, edges: List<Edge>): Array<MutableMap<Int, RoutingTableEntry>> {
    val n = nodes.size
    // Array where index = node ID, value = Map<DestinationID, RoutingTableEntry>
    val initialTables = Array(n) { mutableMapOf<Int, RoutingTableEntry>() }

    for (node in nodes) {
        val nodeId = node.id
        // Cost to self is 0, next hop is self (or -1, convention varies)
        initialTables[nodeId][nodeId] = RoutingTableEntry(nodeId, nodeId, 0)

        // Add direct neighbors
        val neighborEdges = edges.filter { it.src == nodeId }
        for (edgeToNeighbor in neighborEdges) {
            val neighborId = edgeToNeighbor.dest
            initialTables[nodeId][neighborId] = RoutingTableEntry(
                destination = neighborId,
                nextHop = neighborId, // Initially, next hop to neighbor is neighbor itself
                cost = edgeToNeighbor.cost
            )
        }

        // Fill remaining destinations with INF cost
        for (destNode in nodes) {
            if (destNode.id !in initialTables[nodeId]) {
                initialTables[nodeId][destNode.id] = RoutingTableEntry(destNode.id, -1, INF)
            }
        }
    }
    return initialTables
}

/**
 * Performs one step of the Distance Vector Routing algorithm for all nodes.
 * Each node calculates its new table based on advertisements received from neighbors
 * in the *previous* state.
 *
 * @param currentTables The current state of all nodes' routing tables.
 * @param nodes The list of all nodes in the network.
 * @param currentEdges The list of *currently active* edges and their costs.
 * @return A Pair containing:
 *           - The newly calculated tables for all nodes.
 *           - A Boolean indicating if any table entry changed during this step.
 */
fun runDVRStep(
    currentTables: Array<MutableMap<Int, RoutingTableEntry>>,
    nodes: List<Node>,
    currentEdges: List<Edge>
): Pair<Array<MutableMap<Int, RoutingTableEntry>>, Boolean> {

    val n = nodes.size
    // Create a deep copy to read from (representing neighbors' *previous* state)
    // This is crucial for simulating simultaneous updates based on prior info.
    val previousTables = currentTables.map { it.toMap() }.toTypedArray() // Array<Map<Int, RoutingTableEntry>>

    // Prepare the structure for the new tables calculated in this step
    val newTables = Array(n) { mutableMapOf<Int, RoutingTableEntry>() }
    var anyTableChanged = false

    // --- Calculation Phase ---
    // Iterate through each node X, calculating its NEW table
    for (nodeX in nodes) {
        val nodeXId = nodeX.id
        val newTableForX = mutableMapOf<Int, RoutingTableEntry>()

        // 1. Cost to self is always 0
        newTableForX[nodeXId] = RoutingTableEntry(nodeXId, nodeXId, 0)

        // 2. Iterate through all possible destinations Y
        for (nodeY in nodes) {
            val nodeYId = nodeY.id
            if (nodeXId == nodeYId) continue // Already handled cost to self

            var minCostToY = INF
            var bestNextHopToY = -1

            // 3. Consider path via each neighbor V of node X
            val neighborsOfX = currentEdges.filter { it.src == nodeXId }
            if (neighborsOfX.isEmpty()) { // Handle isolated nodes
                // Stay INF unless node Y is node X itself (handled above)
            } else {
                for (edgeXtoV in neighborsOfX) {
                    val neighborVId = edgeXtoV.dest
                    val costXtoV = edgeXtoV.cost

                    // Get neighbor V's previously advertised cost to Y
                    val costVtoY = previousTables.getOrNull(neighborVId)?.get(nodeYId)?.cost ?: INF

                    if (costXtoV < INF && costVtoY < INF) { // Check for potential overflow before adding
                        val totalCostViaV = costXtoV + costVtoY
                        if (totalCostViaV < minCostToY) {
                            minCostToY = totalCostViaV
                            bestNextHopToY = neighborVId // Next hop from X is neighbor V
                        } else if (totalCostViaV == minCostToY) {
                            // Tie-breaking (optional): prefer lower neighbor ID, or existing route if stable
                            val currentEntry = previousTables.getOrNull(nodeXId)?.get(nodeYId)
                            if (currentEntry?.nextHop == neighborVId) {
                                // Keep existing route if cost is same
                                bestNextHopToY = neighborVId
                            } else if (neighborVId < (bestNextHopToY ?: INF)) {
                                // Prefer lower neighbor ID as a stable tie-breaker
                                // bestNextHopToY = neighborVId // Uncomment for specific tie-breaking
                            }
                        }
                    }
                }
            }
            // Store the calculated best entry for destination Y
            // Ensure not to report self as next hop unless cost is 0
            val finalNextHop = if (minCostToY == 0 && nodeXId == nodeYId) nodeXId else if (minCostToY == INF) -1 else bestNextHopToY
            val calculatedEntry = RoutingTableEntry(nodeYId, finalNextHop, minCostToY)
            newTableForX[nodeYId] = calculatedEntry

        } // End loop destinations Y

        // Assign the calculated table to the result for node X
        newTables[nodeXId] = newTableForX

        // Check if this node's table has changed compared to the previous iteration
        if (!anyTableChanged) { // Only check if we haven't already found a change
            if (previousTables.getOrNull(nodeXId) != newTableForX) {
                // Basic check: compare maps. More detailed check might compare entry by entry.
                anyTableChanged = true
            }
        }

    } // End loop nodes X

    // Return the newly computed tables and the change flag
    return Pair(newTables, anyTableChanged)
}