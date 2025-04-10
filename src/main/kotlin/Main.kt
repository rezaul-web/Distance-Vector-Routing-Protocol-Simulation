

import Utils.Edge
import Utils.INF
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.*
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
import kotlinx.coroutines.Job
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
    val dvExchangeAnimations = remember { mutableStateMapOf<Pair<Int, Int>, Animatable<Float, AnimationVector1D>>() } // Keep this
    val coroutineScope = rememberCoroutineScope() // Keep this

    // --- NEW State for Sequential Visualization ---
    var currentlyAdvertisingNode by remember { mutableStateOf<Int?>(null) } // Track which node is "sending"
    var isVisualizingStep by remember { mutableStateOf(false) }
    // --- Helper Function to Trigger SINGLE Node's DV Exchange Visuals ---
    // Returns a list of Jobs for the launched animations for this sender
    fun triggerSingleNodeDvVisuals(senderId: Int, currentEdges: List<Edge>): List<Job> {
        val neighbors = currentEdges.filter { it.src == senderId }
        val animationJobs = mutableListOf<Job>()

        neighbors.forEach { edgeToNeighbor ->
            val neighborId = edgeToNeighbor.dest
            val animatable = Animatable(0f)
            val key = senderId to neighborId
            // Stop and remove any existing animation for this specific link first
            // This prevents artifacts if a step is triggered quickly
            coroutineScope.launch { dvExchangeAnimations[key]?.stop() }
            dvExchangeAnimations.remove(key) // Remove immediately

            // Add the new animation
            dvExchangeAnimations[key] = animatable

            val job = coroutineScope.launch {
                try {
                    animatable.animateTo(
                        1f,
                        // Make animation slightly slower to be noticeable
                        tween(durationMillis = 1000, easing = LinearEasing)
                    )
                    // Keep the animation in the map until the *next* node starts
                    // or the sequence ends. Removal will be handled by clear() or overwrite.
                } catch (e: Exception) {
                    // Animation cancelled, likely by clear() or stop()
                    dvExchangeAnimations.remove(key) // Ensure removal on cancellation
                }
            }
            animationJobs.add(job)
        }
        return animationJobs
    }

    // --- Helper Function to CLEAR all DV Exchange Animations ---
    fun clearDvExchangeVisuals() {
        // Cancel running animations first
        dvExchangeAnimations.values.forEach { anim ->
            coroutineScope.launch { // Launch cancellation in parallel
                try { anim.stop() } catch (_: Exception) {}
            }
        }
        // Clear the map
        dvExchangeAnimations.clear()
        currentlyAdvertisingNode = null // Also reset the advertiser state
    }
    // 1. Store previous table state for selected node (for highlighting later)
    val previousSelectedTable = selectedNode?.let { allNodeTables.getOrNull(it)?.toMap() }
    var previousTableForSelectedNode = selectedNode?.let { allNodeTables.getOrNull(it)?.toMap() }


    // Auto-Stepping Timer Effect
    // Auto-Stepping Timer Effect
    LaunchedEffect(autoStepEnabled) {
        if (autoStepEnabled) {
            while (autoStepEnabled) { // Loop while enabled

                // --- Wait for any ongoing visualization from manual step/previous auto-step ---
                while (isVisualizingStep && autoStepEnabled) {
                    delay(200) // Check frequently if visualization is done or paused
                }
                // Exit loop if autoStep got disabled while waiting
                if (!autoStepEnabled) break
                // Exit loop if converged
                if (isConverged) break

                // --- Start the next auto-step (similar logic to runSingleStep) ---
                isVisualizingStep = true // Lock

                val previousSelectedTable = selectedNode?.let { allNodeTables.getOrNull(it)?.toMap() }
                val (newTables, changed) = runDVRStep(allNodeTables, nodes, mutableEdges)

                // Launch visualization (runs sequentially inside)
                val vizJob = coroutineScope.launch {
                    try {
                        if (changed) {
                            validityMessage = "Step $simulationStep (Auto): Visualizing..." to Color.Blue
                            clearDvExchangeVisuals()

                            for (node in nodes) {
                                // Check frequently if auto-step was disabled during visualization
                                if (!autoStepEnabled || !isVisualizingStep) break
                                currentlyAdvertisingNode = node.id
                                triggerSingleNodeDvVisuals(node.id, mutableEdges)
                                delay(1200) // Delay between each node's advertisement viz
                            }
                            if (autoStepEnabled && isVisualizingStep) { // Check flags again
                                currentlyAdvertisingNode = null
                                validityMessage = "Step $simulationStep (Auto): Applying Updates..." to Color.Blue
                                delay(400)
                            }
                        } else {
                            currentlyAdvertisingNode = null
                            clearDvExchangeVisuals()
                        }
                    } finally {
                        // Visualization part is done (or cancelled), but don't unlock outer flag yet
                        currentlyAdvertisingNode = null
                    }
                }

                // Wait for the visualization job to complete before applying updates
                vizJob.join()

                // --- Apply updates only if auto-step is still enabled ---
                if (autoStepEnabled) {
                    allNodeTables = newTables
                    previousTableForSelectedNode = previousSelectedTable
                    simulationStep++

                    if (!changed) {
                        isConverged = true
                        validityMessage = "DVR Converged!" to Color.Green
                        autoStepEnabled = false // Stop auto-stepping on convergence
                    } else {
                        isConverged = false
                        validityMessage = "Step $simulationStep (Auto) Completed." to Color.DarkGray
                    }
                    isVisualizingStep = false // Unlock *after* updates applied
                    delay(1500) // Wait before starting the *next* calculation cycle
                } else {
                    // Auto-step was disabled during visualization/update
                    isVisualizingStep = false // Ensure unlocked
                    validityMessage = "Auto-Step Paused." to Color.Gray
                    clearDvExchangeVisuals() // Clean up visuals if paused
                    break // Exit the while loop
                }
            } // End while(autoStepEnabled)
        } else {
            // Cleanup if auto-step is manually turned off
            if (isVisualizingStep) {
                // If visualization was running, signal it to stop (by setting flag)
                isVisualizingStep = false // Coroutines check this flag
                clearDvExchangeVisuals()
                validityMessage = "Auto-Step Paused." to Color.Gray
            }
        }
    }

    // Function to manually run one step
    fun runSingleStep() {
        // Prevent running if already converged or visualization is in progress
        if (isConverged || isVisualizingStep) {
            if (isConverged) validityMessage = "Already Converged." to Color.Blue
            return
        }

        isVisualizingStep = true // Lock buttons


        // 2. Calculate the result of the step (Bellman-Ford update)
        //    This doesn't change the actual state yet.
        val (newTables, changed) = runDVRStep(allNodeTables, nodes, mutableEdges)

        // 3. Launch the Sequential Visualization Coroutine
        coroutineScope.launch {
            try {
                if (changed) {
                    validityMessage = "Step $simulationStep: Visualizing Advertisements..." to Color.Blue
                    clearDvExchangeVisuals() // Clear any previous visuals first

                    // Iterate through each node to visualize its advertisement sending
                    for (node in nodes) {
                        currentlyAdvertisingNode = node.id // Set who is advertising now

                        // Trigger animations for this node sending to its neighbors
                        val jobs = triggerSingleNodeDvVisuals(node.id, mutableEdges)
                        // jobs.joinAll() // Option 1: Wait for this node's animations to complete (might be too slow if many neighbors)
                        delay(1200)      // Option 2: Wait a fixed delay (adjust as needed, should be >= animation duration)

                        // Optimization: If auto-stepping was disabled during viz, stop early
                        if (!isVisualizingStep) break // Check flag (might be set by Reset/Pause)
                    }

                    // Short pause after all visualizations before applying table updates
                    if (isVisualizingStep) { // Check flag again
                        currentlyAdvertisingNode = null // Clear advertiser
                        validityMessage = "Step $simulationStep: Applying Updates..." to Color.Blue
                        delay(400)
                    }
                } else {
                    currentlyAdvertisingNode = null // Ensure cleared even if no changes
                    clearDvExchangeVisuals()
                }

                // --- Only proceed if visualization wasn't cancelled ---
                if (isVisualizingStep) {
                    // 4. Apply the Calculated Updates to the Main State *NOW*
                    allNodeTables = newTables
                    previousTableForSelectedNode = previousSelectedTable // Update the 'previous' state for the *next* step's comparison
                    simulationStep++

                    // 5. Check Convergence & Update Message
                    if (!changed) {
                        isConverged = true
                        validityMessage = "DVR Converged!" to Color.Green
                    } else {
                        isConverged = false
                        // Message was handled during visualization stages
                        validityMessage = "Step $simulationStep Completed." to Color.DarkGray // Final step message
                    }
                }

            } finally {
                // 6. Unlock Buttons regardless of success/cancellation
                isVisualizingStep = false
                currentlyAdvertisingNode = null // Ensure cleared on exit/error
            }
        } // End of coroutine launch
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




// ... other imports like Canvas, nodes, mutableEdges, etc. ...

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Draw Edges
                        mutableEdges.forEach { edge ->
                            val startNode = nodes.getOrNull(edge.src); val endNode = nodes.getOrNull(edge.dest); if (startNode == null || endNode == null) return@forEach
                            val start = Offset(startNode.x, startNode.y); val end = Offset(endNode.x, endNode.y)

                            // --- Determine edge style (Selected > ShortestPath > Advertising > Default) ---
                            // isShortestPathAnimated check determines if the *edge* should be highlighted red
                            val isShortestPathAnimated = animatedPath.contains(edge.src to edge.dest) || animatedPath.contains(edge.dest to edge.src)
                            val isEdgeSelected = selectedEdge?.let { (it.src == edge.src && it.dest == edge.dest) || (it.src == edge.dest && it.dest == edge.src) } ?: false
                            val isAdvertisingEdge = edge.src == currentlyAdvertisingNode

                            val strokeWidth = when {
                                isEdgeSelected -> 7f
                                isShortestPathAnimated -> 6f // Highlight edge red if part of shortest path
                                isAdvertisingEdge -> 5f
                                else -> 4f
                            }
                            val color = when {
                                isEdgeSelected -> Color(0xFFFFA500)
                                isShortestPathAnimated -> Color.Red // Highlight edge red
                                isAdvertisingEdge -> Color(0xFF9C27B0)
                                else -> Color.Gray
                            }

                            // Draw the edge line
                            drawLine(start = start, end =  end, color =  color, strokeWidth =  strokeWidth)

                            // Draw edge cost
                            val midX = (start.x + end.x) / 2; val midY = (start.y + end.y) / 2
                            drawText(textMeasurer, edge.cost.toString(), Offset(midX - 8, midY - 20), TextStyle(Color.Black, 12.sp, background = Color(0xAAFFFFFF)))


                            // --- Draw Shortest Path Symbolic "Packet" ---
                            // We still use edgeAnimations for the *timing* of the packet movement
                            edgeAnimations[edge]?.value?.let { progress ->
                                // Check if this edge is ACTUALLY the one being animated in the shortest path *right now*
                                // The packet moves from u -> v in the animatedPath sequence
                                val pathSegment = animatedPath.find { (u, v) -> (u == edge.src && v == edge.dest) }
                                val reversePathSegment = animatedPath.find { (u, v) -> (u == edge.dest && v == edge.src) }

                                // Determine the actual source for this segment's packet
                                val packetSourceId = when {
                                    pathSegment != null -> pathSegment.first // Moving edge.src -> edge.dest
                                    reversePathSegment != null -> reversePathSegment.first // Moving edge.dest -> edge.src
                                    else -> null // This edge might be highlighted but not the current animation segment
                                }

                                // Check if the packet should be drawn on this edge segment
                                if (packetSourceId != null && mutableEdges.contains(edge) && progress > 0.01f && progress < 0.99f) {

                                    // Calculate current position based on progress
                                    // IMPORTANT: Ensure direction matches the animatedPath segment
                                    val packetStartPos: Offset
                                    val packetEndPos: Offset
                                    if (packetSourceId == edge.src) { // Moving start -> end
                                        packetStartPos = start
                                        packetEndPos = end
                                    } else { // Moving end -> start (packetSourceId == edge.dest)
                                        packetStartPos = end
                                        packetEndPos = start
                                    }
                                    val currentPos = Offset(
                                        packetStartPos.x + (packetEndPos.x - packetStartPos.x) * progress,
                                        packetStartPos.y + (packetEndPos.y - packetStartPos.y) * progress
                                    )

                                    // --- Draw the symbolic table (similar to DV packet, but different colors) ---
                                    val tableWidth = 26f
                                    val tableHeight = 20f
                                    val rectTopLeft = Offset(
                                        currentPos.x - tableWidth / 2,
                                        currentPos.y - tableHeight / 2
                                    )

                                    // Background rectangle (Blue/Cyan theme)
                                    drawRect(
                                        color = Color(0xFFB2EBF2), // Light Cyan background
                                        topLeft = rectTopLeft,
                                        size = Size(tableWidth, tableHeight)
                                    )
                                    // Border
                                    drawRect(
                                        color = Color(0xFF00BCD4), // Cyan border
                                        topLeft = rectTopLeft,
                                        size = Size(tableWidth, tableHeight),
                                        style = Stroke(width = 1.5f)
                                    )

                                    // Draw the Source ID inside
                                    val textStyle = TextStyle(
                                        color = Color.Black, fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                                    )
                                    val textLayoutResult = textMeasurer.measure(packetSourceId.toString(), style = textStyle)
                                    val textWidth = textLayoutResult.size.width
                                    val textHeight = textLayoutResult.size.height

                                    drawText(
                                        textLayoutResult = textLayoutResult,
                                        topLeft = Offset(
                                            rectTopLeft.x + (tableWidth - textWidth) / 2,
                                            rectTopLeft.y + (tableHeight - textHeight) / 2
                                        )
                                    )
                                }
                            } // End drawing shortest path symbolic packet


                            // --- Draw DV Advertisement Symbolic "Table" Packet (Existing logic) ---
                            val dvAnimationKey = edge.src to edge.dest
                            dvExchangeAnimations[dvAnimationKey]?.value?.let { progress ->
                                if (edge.src == currentlyAdvertisingNode && progress > 0.01f && progress < 0.99f) {
                                    val senderId = edge.src
                                    val currentPos = Offset(start.x + (end.x - start.x) * progress, start.y + (end.y - start.y) * progress)
                                    val tableWidth = 26f; val tableHeight = 20f
                                    val rectTopLeft = Offset(currentPos.x - tableWidth / 2, currentPos.y - tableHeight / 2)

                                    // Background (Purple theme)
                                    drawRect(color = Color(0xFFE1BEE7), topLeft = rectTopLeft, size = Size(tableWidth, tableHeight))
                                    // Border
                                    drawRect(color = Color(0xFF7B1FA2), topLeft = rectTopLeft, size = Size(tableWidth, tableHeight), style = Stroke(width = 1.5f))
                                    // Text
                                    val textStyle = TextStyle(color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                    val textLayoutResult = textMeasurer.measure(senderId.toString(), style = textStyle)
                                    drawText(textLayoutResult = textLayoutResult, topLeft = Offset(rectTopLeft.x + (tableWidth - textLayoutResult.size.width) / 2, rectTopLeft.y + (tableHeight - textLayoutResult.size.height) / 2))
                                }
                            } // End drawing DV symbolic table

                        } // End mutableEdges.forEach

                        // Draw Nodes (Existing logic with advertising highlight should remain)
                        // ... (Node drawing code remains the same as your last version) ...
                        nodes.forEach { node ->
                            val isSelected = node.id == selectedNode
                            val isInPath = shortestPath.contains(node.id)
                            val isAdvertising = node.id == currentlyAdvertisingNode

                            // Determine node color (Advertising > Selected > Path > Default)
                            val nodeColor = when {
                                isAdvertising -> Color(0xFFBA68C8) // Lighter Purple/Pink for advertising node base
                                isSelected -> Color.Magenta
                                isInPath && shortestPath.firstOrNull() == node.id -> Color(0xFF1B5E20)
                                isInPath && shortestPath.lastOrNull() == node.id -> Color(0xFFFF6F00)
                                isInPath -> Color(0xFF4CAF50)
                                else -> Color.Blue
                            }
                            val radius = 20f

                            // Draw outer highlights (Advertising > Selected)
                            if (isAdvertising) {
                                drawCircle(center = Offset(node.x, node.y), radius = radius + 7f, color = Color(0xFF9C27B0).copy(alpha = 0.4f), style = Stroke(width = 3f))
                            }
                            if (isSelected) {
                                drawCircle(center = Offset(node.x, node.y), radius = radius + 4f, color = Color.Yellow, style = Stroke(3f))
                            }

                            // Draw the base node circle
                            drawCircle(center = Offset(node.x, node.y), radius = radius, color = nodeColor)

                            // Draw node ID text
                            drawText(textMeasurer, node.id.toString(), Offset(node.x - 6f, node.y - 10f), TextStyle(Color.White, 16.sp, fontWeight = FontWeight.Bold))
                        } // End nodes.forEach
                    } // End Canvas
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
                                { weightInput = it.filter { c -> c.isDigit() }.takeIf { it.isNotEmpty() } ?: "1" }, // Ensure cost isn't empty, default to 1 if cleared
                                label = { Text("Cost") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), Arrangement.SpaceEvenly) { /* Buttons */
                                // --- Add Button (Existing) ---
                                Button(onClick = {
                                    val src = sourceId.toIntOrNull(); val dest = destinationId.toIntOrNull(); val cost = weightInput.toIntOrNull()
                                    if (src == null || dest == null || cost == null) validityMessage = "Invalid ID or Cost." to Color.Red
                                    else if (src == dest) validityMessage = "Nodes must be different." to Color.Red
                                    else if (src !in nodes.indices || dest !in nodes.indices) validityMessage = "Node ID out of bounds (${nodes.indices})." to Color.Red
                                    else if (cost <= 0) validityMessage = "Cost must be positive." to Color.Red
                                    else if (!mutableEdges.any { (it.src == src && it.dest == dest) || (it.src == dest && it.dest == src) }) {
                                        // Add bidirectional edge
                                        val edge1 = Edge(src, dest, cost)
                                        val edge2 = Edge(dest, src, cost)
                                        mutableEdges.add(edge1); mutableEdges.add(edge2)
                                        validityMessage = "Link added: $src <-> $dest (Cost: $cost)" to Color.Green
                                        sourceId = ""; destinationId = ""; weightInput = "1"
                                        isConverged = false
                                        // Potentially clear/reset animations if needed
                                        // edgeAnimations.remove(edge1); edgeAnimations.remove(edge2) // Remove old if somehow existed?
                                        // coroutineScope.launch { /* re-init animations if needed */ }
                                    } else validityMessage = "Link $src <-> $dest already exists." to Color.Blue
                                }) { Text("Add") }

                                // --- Remove Button (Existing) ---
                                Button(onClick = {
                                    val src = sourceId.toIntOrNull(); val dest = destinationId.toIntOrNull()
                                    if (src == null || dest == null) validityMessage = "Invalid ID." to Color.Red
                                    else if (src !in nodes.indices || dest !in nodes.indices) validityMessage = "Node ID out of bounds (${nodes.indices})." to Color.Red
                                    else {
                                        // Find the keys/edges to remove animations for *before* removing from mutableEdges
                                        val edgesToRemoveAnimations = edgeAnimations.keys.filter { (it.src == src && it.dest == dest) || (it.src == dest && it.dest == src) }
                                        // Remove edges from list
                                        val removed = mutableEdges.removeAll { (it.src == src && it.dest == dest) || (it.src == dest && it.dest == src) }

                                        validityMessage = if (removed) "Link removed: $src <-> $dest" to Color.Blue else "Link $src <-> $dest not found." to Color.Red
                                        sourceId = ""; destinationId = ""; weightInput = "1" // Reset cost input too
                                        if (removed) {
                                            isConverged = false
                                            // Remove associated animations
                                            edgesToRemoveAnimations.forEach { edgeAnimations.remove(it) }
                                            // Clear DV animations too, as structure changed
                                            clearDvExchangeVisuals() // Assumes clearDvExchangeVisuals() exists
                                            previousTableForSelectedNode = allNodeTables.getOrNull(selectedNode ?: -1)?.toMap() // Reset comparison
                                        }
                                    }
                                }) { Text("Remove") }

                                // --- NEW: Update Cost Button ---
                                Button(onClick = {
                                    val src = sourceId.toIntOrNull(); val dest = destinationId.toIntOrNull(); val newCost = weightInput.toIntOrNull()
                                    // Validation
                                    if (src == null || dest == null || newCost == null) validityMessage = "Invalid ID or Cost." to Color.Red
                                    else if (src == dest) validityMessage = "Nodes must be different." to Color.Red
                                    else if (src !in nodes.indices || dest !in nodes.indices) validityMessage = "Node ID out of bounds (${nodes.indices})." to Color.Red
                                    else if (newCost <= 0) validityMessage = "Cost must be positive." to Color.Red
                                    else {
                                        // Find the indices of the edges to update
                                        val index1 = mutableEdges.indexOfFirst { it.src == src && it.dest == dest }
                                        val index2 = mutableEdges.indexOfFirst { it.src == dest && it.dest == src }

                                        if (index1 != -1 && index2 != -1) {
                                            // Get existing edges to compare cost
                                            val oldCost = mutableEdges[index1].cost // Assuming bidirectional edges have same cost
                                            if (oldCost == newCost) {
                                                validityMessage = "Cost is already $newCost for link $src <-> $dest." to Color.Blue
                                            } else {
                                                // Create new edges with updated cost
                                                val updatedEdge1 = mutableEdges[index1].copy(cost = newCost)
                                                val updatedEdge2 = mutableEdges[index2].copy(cost = newCost)
                                                // Update the list (set replaces element at index)
                                                mutableEdges[index1] = updatedEdge1
                                                mutableEdges[index2] = updatedEdge2

                                                validityMessage = "Link cost updated: $src <-> $dest (New Cost: $newCost)" to Color.Green
                                                isConverged = false // Cost change requires reconvergence

                                                // Reset relevant animations/state
                                                // Remove old animations associated with the *specific* edge objects if map uses object identity
                                                // edgeAnimations.remove(mutableEdges[index1]) // This might not work if key relies on old object
                                                // Instead, maybe force re-initialization or clear related animations by ID pair?
                                                edgeAnimations.keys.filter { (eSrc, eDest, _) -> (eSrc == src && eDest == dest) || (eSrc == dest && eDest == src) }.forEach { edgeAnimations.remove(it) }
                                                clearDvExchangeVisuals() // Recommended as costs changed
                                                previousTableForSelectedNode = allNodeTables.getOrNull(selectedNode ?: -1)?.toMap() // Reset comparison

                                            }
                                        } else {
                                            validityMessage = "Link $src <-> $dest not found." to Color.Red
                                        }
                                        // Clear inputs after attempt
                                        sourceId = ""; destinationId = ""; weightInput = "1"
                                    }
                                }) { Text("Update Cost") } // Label for the new button
                            } // End Button Row
                        } // End ControlCard Content
                    } // End Outer Row

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

fun main() = application {
    val windowState = rememberWindowState(width = 1200.dp, height = 850.dp) // Slightly taller window maybe
    Window(onCloseRequest = ::exitApplication, title = "Distance Vector Routing Simulation", state = windowState) {
        DistanceVectorRoutingApp()
    }
}