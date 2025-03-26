object Utils {
    const val INF=Int.MAX_VALUE/2
    val nodes = generateRectangleLayout(
        rows = 3, cols = 5,
        startX = 100f, startY = 100f,
        spacingX = 180f, spacingY = 180f
    )

    val edges = listOf(
//        Edge(0, 1, 5), Edge(1, 0, 5),
        Edge(1, 2, 10), Edge(2, 1, 10),
        Edge(2, 3, 15), Edge(3, 2, 15),
        Edge(3, 4, 20), Edge(4, 3, 20),

        Edge(5, 6, 5), Edge(6, 5, 5),
        Edge(6, 7, 10), Edge(7, 6, 10),
        Edge(7, 8, 15), Edge(8, 7, 15),
        Edge(8, 9, 20), Edge(9, 8, 20),

        Edge(10, 11, 5), Edge(11, 10, 5),
        Edge(11, 12, 10), Edge(12, 11, 10),
        Edge(12, 13, 15), Edge(13, 12, 15),
        Edge(13, 14, 20), Edge(14, 13, 20),

        Edge(0, 5, 8), Edge(5, 0, 8),
        Edge(1, 6, 8), Edge(6, 1, 8),
        Edge(2, 7, 8), Edge(7, 2, 8),
        Edge(3, 8, 8), Edge(8, 3, 8),
        Edge(4, 9, 1), Edge(9, 4, 1),

        Edge(5, 10, 8), Edge(10, 5, 8),
        Edge(6, 11, 8), Edge(11, 6, 8),
        Edge(7, 12, 8), Edge(12, 7, 8),
        Edge(8, 13, 8), Edge(13, 8, 8),
        Edge(9, 14, 8), Edge(14, 9, 8)
    )
    data class Node(val id: Int, val x: Float, val y: Float){
    }
    data class Edge(var src: Int, var dest: Int, var cost: Int)

    fun generateRectangleLayout(
        rows: Int,
        cols: Int,
        startX: Float,
        startY: Float,
        spacingX: Float,
        spacingY: Float
    ): List<Node> {
        return List(rows * cols) { i ->
            val x = startX + (i % cols) * spacingX
            val y = startY + (i / cols) * spacingY
            Node(i, x, y)
        }
    }
}