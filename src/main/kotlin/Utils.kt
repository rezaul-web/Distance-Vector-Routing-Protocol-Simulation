object Utils {

    // package Utils // Or your chosen package

    const val INF = Int.MAX_VALUE / 2 // Use a large but safe value to avoid overflow

    data class Node(
        val id: Int,
        val x: Float, // For UI rendering
        val y: Float  // For UI rendering
    )

    data class Edge(
        val src: Int,
        val dest: Int,
        val cost: Int
    )


    val nodes = listOf(
        // Original Rectangle
        Node(0, 200f, 150f), // Top-Left
        Node(1, 500f, 150f), // Top-Right
        Node(2, 500f, 450f), // Bottom-Right
        Node(3, 200f, 450f), // Bottom-Left

        // Added Nodes
        Node(4, 350f, 300f), // Center Node
        Node(5, 650f, 300f)  // Node further Right
    )

    val initialEdges = listOf(
        // --- Original Edges ---
        Edge(0, 1, 1), Edge(1, 0, 1),   // Link 0-1 Cost 1
        Edge(0, 3, 7), Edge(3, 0, 7),   // Link 0-3 Cost 7
        Edge(1, 2, 1), Edge(2, 1, 1),   // Link 1-2 Cost 1
        Edge(2, 3, 2), Edge(3, 2, 2),   // Link 2-3 Cost 2
        // Edge(0, 2, 3), Edge(2, 0, 3),   // Link 0-2 Cost 3 (Original Diagonal) - Let's remove this to force routing via center

        // --- Edges involving Center Node (4) ---
        Edge(0, 4, 2), Edge(4, 0, 2),   // Link 0-4 Cost 2
        Edge(1, 4, 2), Edge(4, 1, 2),   // Link 1-4 Cost 2
        Edge(2, 4, 2), Edge(4, 2, 2),   // Link 2-4 Cost 2
        Edge(3, 4, 2), Edge(4, 3, 2),   // Link 3-4 Cost 2 (Connects all original nodes to center)

        // --- Edges involving Node 5 ---
        Edge(1, 5, 4), Edge(5, 1, 4),   // Link 1-5 Cost 4
        Edge(2, 5, 3), Edge(5, 2, 3),   // Link 2-5 Cost 3
     //   Edge(4, 5, 5), Edge(5, 4, 5)    // Link 4-5 Cost 5 (Connect center to node 5)
    )

    // Expose initial edges for the Composable
    val edges: List<Edge> = initialEdges
}