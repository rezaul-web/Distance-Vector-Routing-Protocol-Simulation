import Utils.INF
import java.util.PriorityQueue
import kotlin.collections.filter
import kotlin.collections.forEach
import kotlin.collections.isNotEmpty
import kotlin.collections.last
import kotlin.collections.reversed
import kotlin.to

fun dijkstraShortestPath(nodes: List<Utils.Node>, edges: List<Utils.Edge>, start: Int, end: Int): List<Int> {
    val dist = IntArray(nodes.size) { INF }
    val prev = IntArray(nodes.size) { -1 }
    val pq = PriorityQueue<Pair<Int, Int>>(compareBy { it.second })

    dist[start] = 0
    pq.add(start to 0)

    while (pq.isNotEmpty()) {
        val (u, _) = pq.poll()

        edges.filter { it.src == u }.forEach { edge ->
            val v = edge.dest
            val weight = edge.cost

            if (dist[u] + weight < dist[v]) {
                dist[v] = dist[u] + weight
                prev[v] = u
                pq.add(v to dist[v])
            }
        }
    }

    val path = mutableListOf<Int>()
    var current = end
    while (current != -1) {
        path.add(current)
        current = prev[current]
    }

    return if (path.last() == start) path.reversed() else emptyList()
}