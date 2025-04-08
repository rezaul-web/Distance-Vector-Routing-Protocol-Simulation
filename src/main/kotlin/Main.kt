// Keep existing imports
import Utils.Edge
import Utils.Node
import Utils.INF
import Utils.edges // Assuming these provide initial state
import Utils.nodes // Assuming these provide initial state
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.sqrt

@Composable
fun DistanceVectorRoutingApp() {
    // --- State for UI Interaction ---
    var sourceId by remember { mutableStateOf("") }
    var destinationId by remember { mutableStateOf("") }
    var weightInput by remember { mutableStateOf("1") }
    var selectedNode by remember { mutableStateOf<Int?>(null) }
    var selectedEdge by remember { mutableStateOf<Edge?>(null) }
    var packetSource by remember { mutableStateOf("") }
    var packetDestination by remember { mutableStateOf("") }
    var validityMessage by remember { mutableStateOf<Pair<String, Color>?>(null) }

    // --- State for Simulation Core ---
    val nodes = remember { Utils.nodes } // Keep node list stable
    var mutableEdges by remember { mutableStateOf(Utils.edges.toMutableStateList()) }
    var simulationStep by remember { mutableStateOf(0) } // Use step count instead of timer directly
    var allNodeTables by remember { mutableStateOf(initializeDvTables(nodes, mutableEdges)) }
    var isConverged by remember { mutableStateOf(false) }
    var autoStepEnabled by remember { mutableStateOf(false) } // For auto-advancing simulation

    // --- State for UI Feedback / Animation ---
    var shortestPath by remember { mutableStateOf<List<Int>>(emptyList()) }
    var animatedPath by remember { mutableStateOf<List<Pair<Int, Int>>>(emptyList()) }
    val edgeAnimations = remember { mutableStateMapOf<Edge, Animatable<Float, AnimationVector1D>>() }
    val textMeasurer = rememberTextMeasurer()

    // --- Derived State for Display ---
    // Gets the table for the selected node from the main state
    val currentTableForDisplay: Array<RoutingTableEntry> by remember(selectedNode, allNodeTables) {
        derivedStateOf {
            val safeSelectedNode = selectedNode?.takeIf { it >= 0 && it < allNodeTables.size } ?: 0
            // Convert Map<Int, RoutingTableEntry> to Array<RoutingTableEntry> for TableLayout
            allNodeTables.getOrNull(safeSelectedNode)?.values?.sortedBy { it.destination }?.toTypedArray()
                ?: emptyArray()
        }
    }

    // --- Effects ---

    // Auto-Stepping Timer Effect
    LaunchedEffect(autoStepEnabled) {
        if (autoStepEnabled) {
            while (true) {
                delay(2000) // Delay between auto-steps (adjust as needed)
                if (isConverged || !autoStepEnabled) break // Stop if converged or disabled

                // --- Run one DVR step ---
                val (newTables, changed) = runDVRStep(allNodeTables, nodes, mutableEdges)
                allNodeTables = newTables // Update the main state
                simulationStep++
                if (!changed) {
                    isConverged = true
                    validityMessage = "DVR Converged!" to Color.Green
                    autoStepEnabled = false // Stop auto-stepping
                } else {
                    isConverged = false // Reset convergence flag if changes occurred
                }
                // --- End DVR step ---
            }
        }
    }

    // Function to manually run one step
    fun runSingleStep() {
        if (isConverged) {
            validityMessage = "Already Converged." to Color.Blue
            return
        }
        val (newTables, changed) = runDVRStep(allNodeTables, nodes, mutableEdges)
        allNodeTables = newTables
        simulationStep++
        if (!changed) {
            isConverged = true
            validityMessage = "DVR Converged!" to Color.Green
        } else {
            isConverged = false
            // validityMessage = "Step $simulationStep completed." to Color.DarkGray // Optional step message
        }
    }


    // Edge Link Animation Effect (Handles packets moving ON links visually)
    LaunchedEffect(mutableEdges) {
        val currentEdgeSet = mutableEdges.toSet()
        val edgesToRemove = edgeAnimations.keys.filterNot { currentEdgeSet.contains(it) }
        edgesToRemove.forEach { edgeAnimations.remove(it) }

        for (edge in mutableEdges) {
            if (!edgeAnimations.containsKey(edge)) {
                val animatable = Animatable(0f)
                edgeAnimations[edge] = animatable
                // Only launch if not already running to avoid duplicates?
                // This logic might need refinement if edges are rapidly added/removed
                launch {
                    try {
                        animatable.animateTo(
                            1f,
                            infiniteRepeatable(
                                tween(1500 + (edge.cost * 150), easing = LinearEasing), // Faster animation
                                RepeatMode.Restart // Packet goes one way
                            )
                        )
                    } catch (_: Exception) { /* Ignore cancellation */ }
                }
            }
        }
    }

    // Shortest Path Calculation/Display Effect (Shows result of clicking Simulate)
    LaunchedEffect(shortestPath) {
        animatedPath = emptyList()
        if (shortestPath.size >= 2) {
            validityMessage = "Simulating path: ${shortestPath.joinToString(" → ")}" to Color(0xFF006400) // Dark Green
            for (i in 0 until shortestPath.size - 1) {
                val u = shortestPath[i]
                val v = shortestPath[i + 1]
                // Check if edge still exists *now* before highlighting
                val pathEdgeExists = mutableEdges.any { (it.src == u && it.dest == v) || (it.src == v && it.dest == u) }
                if (!pathEdgeExists) {
                    validityMessage = "Path segment $u-$v no longer exists!" to Color.Red
                    // shortestPath = emptyList() // Maybe keep showing the intended path but mark error?
                    animatedPath = emptyList()
                    break // Stop animation
                }
                animatedPath = animatedPath + (u to v)
                delay(600) // Delay between highlighting each segment
            }
            // Optionally clear message after animation, or keep path displayed
            // validityMessage = null
        }
    }


    // --- UI ---
    MaterialTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text("Distance Vector Routing Simulation") }) }
        ) { scaffoldPadding ->
            Row( // Main layout: Canvas Left, Controls/Table Right
                modifier = Modifier
                    .fillMaxSize()
                    .padding(scaffoldPadding)
                    .padding(16.dp)
            ) {

                // --- Left Side: Canvas Area ---
                Box(
                    modifier = Modifier
                        .align(Alignment.Bottom)
                        .weight(0.65f) // Adjust weight as needed
                        .fillMaxHeight()
                        .padding(end = 8.dp)
                        .pointerInput(Unit) { /* detectTapGestures for selecting nodes/edges */
                            detectTapGestures(
                                onTap = { offset ->
                                    val tappedNode = nodes.firstOrNull { node -> sqrt((offset.x - node.x).pow(2) + (offset.y - node.y).pow(2)) <= 25f }
                                    if (tappedNode != null) { selectedNode = tappedNode.id; selectedEdge = null; validityMessage = "Selected Node ${tappedNode.id}" to Color.Blue }
                                    else {
                                        selectedEdge = mutableEdges.firstOrNull { edge -> nodes.getOrNull(edge.src)?.let { s -> nodes.getOrNull(edge.dest)?.let { e -> isPointNearLine(offset, s, e) } } ?: false }
                                        if (selectedEdge != null) { selectedNode = null; validityMessage = "Selected Edge ${selectedEdge!!.src}<->${selectedEdge!!.dest}" to Color.Blue }
                                        else { selectedNode = null; selectedEdge = null }
                                    }
                                    // Clear shortest path animation when clicking elsewhere
                                    if (tappedNode == null && selectedEdge == null) {
                                        shortestPath = emptyList()
                                        // validityMessage = null // Optional: clear info messages too
                                    }
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Draw Edges
                        mutableEdges.forEach { edge ->
                            val startNode = nodes.getOrNull(edge.src); val endNode = nodes.getOrNull(edge.dest); if (startNode == null || endNode == null) return@forEach
                            val start = Offset(startNode.x, startNode.y); val end = Offset(endNode.x, endNode.y)
                            // Check if this specific edge segment is part of the animated path
                            val isAnimated = animatedPath.contains(edge.src to edge.dest) || animatedPath.contains(edge.dest to edge.src)
                            val isSelected = selectedEdge?.let { (it.src == edge.src && it.dest == edge.dest) || (it.src == edge.dest && it.dest == edge.src) } ?: false
                            val strokeWidth = when { isSelected -> 7f; isAnimated -> 6f; else -> 4f }; val color = when { isSelected -> Color(0xFFFFA500); isAnimated -> Color.Red; else -> Color.Gray }
                            drawLine(start = start, end =  end, color =  color, strokeWidth =  strokeWidth)
                            val midX = (start.x + end.x) / 2; val midY = (start.y + end.y) / 2
                            drawText(textMeasurer, edge.cost.toString(), Offset(midX - 8, midY - 20), TextStyle(Color.Black, 12.sp, background = Color(0xAAFFFFFF)))
                            // Draw animated "packet" dot moving along the line
                            edgeAnimations[edge]?.value?.let { progress -> if (mutableEdges.contains(edge) && progress > 0.01f && progress < 0.99f) { drawCircle(color=Color(0xFF00BCD4), center =  Offset(start.x + (end.x - start.x) * progress, start.y + (end.y - start.y) * progress), radius =  8f) } }
                        }
                        // Draw Nodes
                        nodes.forEach { node ->
                            val isSelected = node.id == selectedNode
                            // Check if this node is part of the animated path *sequence*
                            val isInPath = shortestPath.contains(node.id)
                            // Highlight based on sequence for clarity
                            val nodeColor = when {
                                isSelected -> Color.Magenta
                                isInPath && shortestPath.firstOrNull() == node.id -> Color(0xFF1B5E20) // Darker Green for start
                                isInPath && shortestPath.lastOrNull() == node.id -> Color(0xFFFF6F00) // Amber/Orange for end
                                isInPath -> Color(0xFF4CAF50) // Green for intermediate
                                else -> Color.Blue
                            }
                            val radius = 20f
                            if (isSelected) { drawCircle(center = Offset(node.x, node.y), radius = radius + 4f, color =  Color.Yellow, style = Stroke(3f)) }
                            drawCircle(center = Offset(node.x, node.y), radius = radius, color = nodeColor)
                            drawText(textMeasurer, node.id.toString(), Offset(node.x - 6f, node.y - 10f), TextStyle(Color.White, 16.sp, fontWeight = FontWeight.Bold))
                        }
                    }
                  Row(modifier = Modifier.align(Alignment.BottomStart).padding(top=100.dp,start=100.dp,end=100.dp))  {
                        ControlCard(title = "Link Management") {
                            OutlinedTextField(
                                sourceId,
                                { sourceId = it.filter { c -> c.isDigit() } },
                                label = { Text("Node 1 ID") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                destinationId,
                                { destinationId = it.filter { c -> c.isDigit() } },
                                label = { Text("Node 2 ID") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                weightInput,
                                { weightInput = it.filter { c -> c.isDigit() } },
                                label = { Text("Cost") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), Arrangement.SpaceEvenly) { /* Buttons */
                                Button(onClick = {
                                    // ... Add Link Logic (validation first) ...
                                    val src = sourceId.toIntOrNull();
                                    val dest = destinationId.toIntOrNull();
                                    val cost = weightInput.toIntOrNull()
                                    if (src == null || dest == null || cost == null) validityMessage =
                                        "Invalid ID or Cost." to Color.Red
                                    else if (src == dest) validityMessage = "Nodes must be different." to Color.Red
                                    else if (src !in nodes.indices || dest !in nodes.indices) validityMessage =
                                        "Node ID out of bounds." to Color.Red
                                    else if (cost <= 0) validityMessage = "Cost must be positive." to Color.Red
                                    else if (!mutableEdges.any { (it.src == src && it.dest == dest) || (it.src == dest && it.dest == src) }) { // Check both directions
                                        mutableEdges.add(Edge(src, dest, cost)); mutableEdges.add(Edge(dest, src, cost))
                                        validityMessage = "Link added: $src <-> $dest (Cost: $cost)" to Color.Green
                                        sourceId = ""; destinationId = ""; weightInput = "1"
                                        isConverged = false // Adding link requires reconvergence
                                    } else validityMessage = "Link $src <-> $dest already exists." to Color.Blue
                                }) { Text("Add") }
                                Button(onClick = {
                                    // ... Remove Link Logic (validation first) ...
                                    val src = sourceId.toIntOrNull();
                                    val dest = destinationId.toIntOrNull()
                                    if (src == null || dest == null) validityMessage = "Invalid ID." to Color.Red
                                    else if (src !in nodes.indices || dest !in nodes.indices) validityMessage =
                                        "Node ID out of bounds." to Color.Red
                                    else {
                                        val removed =
                                            mutableEdges.removeAll { (it.src == src && it.dest == dest) || (it.src == dest && it.dest == src) }
                                        validityMessage =
                                            if (removed) "Link removed: $src <-> $dest" to Color.Blue else "Link $src <-> $dest not found." to Color.Red
                                        sourceId = ""; destinationId = ""
                                        if (removed) isConverged = false // Removing link requires reconvergence
                                    }
                                }) { Text("Remove") }
                            }
                        }
                    }

                } // --- End Canvas Box ---

                // --- Right Side: Column for Controls and Table ---
                Column(
                    modifier = Modifier
                        .weight(0.35f) // Adjust weight as needed
                        .fillMaxHeight()
                        .padding(start = 8.dp)
                ) {
                    // --- Simulation Control Buttons ---
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(onClick = { runSingleStep() }, enabled = !isConverged && !autoStepEnabled) {
                            Text("Step")
                        }
                        Button(onClick = { autoStepEnabled = !autoStepEnabled }, enabled = !isConverged) {
                            Text(if (autoStepEnabled) "Pause" else "Auto Step")
                        }
                        Button(onClick = {
                            // Reset state
                            mutableEdges.clear()
                            mutableEdges.addAll(Utils.edges) // Re-add initial edges
                            allNodeTables = initializeDvTables(nodes, mutableEdges)
                            simulationStep = 0
                            isConverged = false
                            autoStepEnabled = false
                            selectedNode = null
                            selectedEdge = null
                            shortestPath = emptyList()
                            validityMessage = "Simulation Reset." to Color.Blue
                        }) {
                            Text("Reset Sim")
                        }
                    }

                    // --- Link Management Section ---



                    // --- Find Path Section ---
                    ControlCard(title = "Find Path (Using Current Tables)") {
                        OutlinedTextField(packetSource, { packetSource = it.filter{c->c.isDigit()} }, label = { Text("Source ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(packetDestination, { packetDestination = it.filter{c->c.isDigit()} }, label = { Text("Dest ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Button(  onClick = {
                            // --- Find Path Logic using allNodeTables ---
                            shortestPath = emptyList() // Clear previous path first
                            val start = packetSource.toIntOrNull()
                            val end = packetDestination.toIntOrNull()

                            if (start == null || end == null) { validityMessage = "Invalid ID." to Color.Red }
                            else if (start == end) { validityMessage = "Source and destination are same." to Color.Blue }
                            else if (start !in nodes.indices || end !in nodes.indices) { validityMessage = "ID out of bounds." to Color.Red }
                            else {
                                val path = mutableListOf<Int>()
                                var current = start
                                val visited = mutableSetOf<Int>() // Loop detection
                                var pathPossible = true

                                while (current != end && current !in visited) {
                                    path.add(current!!)
                                    visited.add(current)

                                    val currentTable = allNodeTables.getOrNull(current) // Get table for current node
                                    val entryToEnd = currentTable?.get(end)

                                    if (currentTable == null || entryToEnd == null || entryToEnd.cost == INF || entryToEnd.nextHop == -1) {
                                        validityMessage = "No path from $current to $end in current tables." to Color.Red
                                        path.clear()
                                        pathPossible = false
                                        break
                                    }

                                    // Find the node corresponding to the next hop ID
                                    val nextHopNodeId = entryToEnd.nextHop
                                    if(nextHopNodeId == -1 || nextHopNodeId == current) { // Should be covered by cost check, but safer
                                        validityMessage = "Routing error/no next hop at $current for $end." to Color.Red
                                        path.clear()
                                        pathPossible = false
                                        break
                                    }
                                    // Check for immediate loop back (e.g. A->B, B's next hop for A is B) - shouldn't happen in correct DVR
                                    // if(allNodeTables.getOrNull(nextHopNodeId)?.get(current)?.nextHop == current && path.size > 0) { ... }

                                    current = nextHopNodeId // Move to the next hop
                                }

                                if (pathPossible) {
                                    if (current == end) {
                                        path.add(end)
                                        shortestPath = path // Set state to trigger path animation effect
                                        // Message is set in the LaunchedEffect now
                                    } else if (current in visited) {
                                        validityMessage = "Routing loop detected while finding path." to Color.Red
                                        shortestPath = emptyList()
                                    } else { // Should not happen if INF check works
                                        validityMessage = "Path finding failed unexpectedly." to Color.Red
                                        shortestPath = emptyList()
                                    }
                                } else {
                                    shortestPath = emptyList() // Ensure path is empty if impossible
                                }
                            }
                            packetSource = ""; packetDestination = ""
                        } ) { Text("Show Path") } // Changed text
                    }

                    Spacer(Modifier.height(8.dp))

                    // --- Validity Message Area ---
                    Box( /* ... unchanged ... */
                        modifier = Modifier.fillMaxWidth().heightIn(min = 24.dp).padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        validityMessage?.let { (message, color) ->
                            Text( text = message, color = color, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // --- Display the routing table ---
                    TableLayout(
                        selectedNode = selectedNode ?: 0,
                        entries = currentTableForDisplay, // Use the derived state
                        timer = simulationStep, // Show simulation step count
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    )

                } // --- End Right Side Column ---

            } // End Main Row
        } // End Scaffold
    } // End MaterialTheme
}

// ... (ControlCard, isPointNearLine, main function - unchanged) ...
@Composable
fun ControlCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(elevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
            Divider(); content()
        }
    }
}
fun isPointNearLine(point: Offset, start: Node, end: Node, threshold: Float = 10f): Boolean {
    val startOffset = Offset(start.x, start.y); val endOffset = Offset(end.x, end.y)
    val lineVec = endOffset - startOffset; val pointVec = point - startOffset
    val lineLenSq = lineVec.getDistanceSquared()
    if (lineLenSq < 0.1f) return (point - startOffset).getDistanceSquared() < threshold * threshold
    val t = (pointVec.x * lineVec.x + pointVec.y * lineVec.y) / lineLenSq
    val projectionT = t.coerceIn(0f, 1f); val closestPoint = startOffset + lineVec * projectionT
    return (point - closestPoint).getDistanceSquared() < threshold * threshold
}
fun main() = application {
    val windowState = rememberWindowState(width = 1200.dp, height = 850.dp) // Slightly taller window maybe
    Window(onCloseRequest = ::exitApplication, title = "Distance Vector Routing Simulation", state = windowState) {
        DistanceVectorRoutingApp()
    }
}