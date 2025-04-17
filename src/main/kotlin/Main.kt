

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

// Assume Utils.nodes, Utils.edges, initializeDvTables, runDVRStep, RoutingTableEntry,
// TableLayout, ControlCard, isPointNearLine are defined elsewhere and imported correctly.
// NO function implementations (like initializeDvTables, runDVRStep, etc.) will be included here.

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
    var allNodeTables by remember { mutableStateOf(initializeDvTables(nodes, mutableEdges)) } // Assumes function exists elsewhere
    var isConverged by remember { mutableStateOf(false) }
    var autoStepEnabled by remember { mutableStateOf(false) } // For auto-advancing simulation

    // --- State for Animation Speed ---
    var animationSpeedFactor by remember { mutableStateOf(1.0f) } // ADDED: 1.0f = normal, <1.0 = slower, >1.0 = faster

    // --- State for UI Feedback / Animation ---
    var shortestPath by remember { mutableStateOf<List<Int>>(emptyList()) }
    var animatedPath by remember { mutableStateOf<List<Pair<Int, Int>>>(emptyList()) }
    val edgeAnimations = remember { mutableStateMapOf<Edge, Animatable<Float, AnimationVector1D>>() }
    val textMeasurer = rememberTextMeasurer()

    // --- Derived State for Display ---
    val currentTableForDisplay: Array<RoutingTableEntry> by remember(selectedNode, allNodeTables) {
        derivedStateOf {
            val safeSelectedNode = selectedNode?.takeIf { it >= 0 && it < allNodeTables.size } ?: 0
            allNodeTables.getOrNull(safeSelectedNode)?.values?.sortedBy { it.destination }?.toTypedArray()
                ?: emptyArray() // Assumes RoutingTableEntry exists elsewhere
        }
    }
    val dvExchangeAnimations = remember { mutableStateMapOf<Pair<Int, Int>, Animatable<Float, AnimationVector1D>>() }
    val coroutineScope = rememberCoroutineScope()

    // --- State for Sequential Visualization ---
    var currentlyAdvertisingNode by remember { mutableStateOf<Int?>(null) }
    var isVisualizingStep by remember { mutableStateOf(false) }
    var previousTableForSelectedNode by remember(selectedNode) { mutableStateOf( selectedNode?.let { allNodeTables.getOrNull(it)?.toMap() }) }


    // --- Helper Function to Trigger SINGLE Node's DV Exchange Visuals ---
    // MODIFIED: Uses animationSpeedFactor
    fun triggerSingleNodeDvVisuals(senderId: Int, currentEdges: List<Edge>): List<Job> {
        val neighbors = currentEdges.filter { it.src == senderId }
        val animationJobs = mutableListOf<Job>()
        // Calculate duration based on speed factor
        val dvPacketDurationMillis = (1000 / animationSpeedFactor).toInt().coerceAtLeast(50) // Min duration 50ms

        neighbors.forEach { edgeToNeighbor ->
            val neighborId = edgeToNeighbor.dest
            val animatable = Animatable(0f)
            val key = senderId to neighborId
            coroutineScope.launch { dvExchangeAnimations[key]?.stop() }
            dvExchangeAnimations.remove(key)
            dvExchangeAnimations[key] = animatable

            val job = coroutineScope.launch {
                try {
                    animatable.animateTo(
                        1f,
                        tween(durationMillis = dvPacketDurationMillis, easing = LinearEasing) // USE calculated duration
                    )
                } catch (e: Exception) {
                    dvExchangeAnimations.remove(key)
                }
            }
            animationJobs.add(job)
        }
        return animationJobs
    }

    // --- Helper Function to CLEAR all DV Exchange Animations ---
    // (No changes needed here, logic is independent of speed)
    fun clearDvExchangeVisuals() {
        dvExchangeAnimations.values.forEach { anim ->
            coroutineScope.launch { try { anim.stop() } catch (_: Exception) {} }
        }
        dvExchangeAnimations.clear()
        currentlyAdvertisingNode = null
    }

    // --- Update previous table state when selected node changes ---
    LaunchedEffect(selectedNode, allNodeTables) {
        previousTableForSelectedNode = selectedNode?.let { allNodeTables.getOrNull(it)?.toMap() }
    }

    // Auto-Stepping Timer Effect
    // MODIFIED: Reacts to animationSpeedFactor and uses it for delays
    LaunchedEffect(autoStepEnabled, animationSpeedFactor) { // ADDED animationSpeedFactor to key
        if (autoStepEnabled) {
            while (autoStepEnabled) {
                while (isVisualizingStep && autoStepEnabled) {
                    delay(200)
                }
                if (!autoStepEnabled || isConverged) break

                isVisualizingStep = true
                val previousSelectedTableLocal = selectedNode?.let { allNodeTables.getOrNull(it)?.toMap() }
                val (newTables, changed) = runDVRStep(allNodeTables, nodes, mutableEdges) // Assumes function exists elsewhere

                // Calculate delays based on speed factor
                val interNodeVizDelayMillis = (1200 / animationSpeedFactor).toLong().coerceAtLeast(100) // Min delay 100ms
                val applyUpdateDelayMillis = (400 / animationSpeedFactor).toLong().coerceAtLeast(50)   // Min delay 50ms
                val nextCycleDelayMillis = (1500 / animationSpeedFactor).toLong().coerceAtLeast(200)  // Min delay 200ms

                val vizJob = coroutineScope.launch {
                    try {
                        if (changed) {
                            validityMessage = "Step $simulationStep (Auto): Visualizing..." to Color.Blue
                            clearDvExchangeVisuals()
                            for (node in nodes) {
                                if (!autoStepEnabled || !isVisualizingStep) break
                                currentlyAdvertisingNode = node.id
                                triggerSingleNodeDvVisuals(node.id, mutableEdges) // Uses speed internally
                                delay(interNodeVizDelayMillis) // USE calculated delay
                            }
                            if (autoStepEnabled && isVisualizingStep) {
                                currentlyAdvertisingNode = null
                                validityMessage = "Step $simulationStep (Auto): Applying Updates..." to Color.Blue
                                delay(applyUpdateDelayMillis) // USE calculated delay
                            }
                        } else {
                            currentlyAdvertisingNode = null
                            clearDvExchangeVisuals()
                        }
                    } finally {
                        currentlyAdvertisingNode = null
                    }
                }
                vizJob.join()

                if (autoStepEnabled) {
                    allNodeTables = newTables
                    previousTableForSelectedNode = previousSelectedTableLocal
                    simulationStep++
                    if (!changed) {
                        isConverged = true
                        validityMessage = "DVR Converged!" to Color.Green
                        autoStepEnabled = false
                    } else {
                        isConverged = false
                        validityMessage = "Step ${simulationStep - 1} (Auto) Completed." to Color.DarkGray
                    }
                    isVisualizingStep = false
                    delay(nextCycleDelayMillis) // USE calculated delay
                } else {
                    isVisualizingStep = false
                    validityMessage = "Auto-Step Paused." to Color.Gray
                    clearDvExchangeVisuals()
                    break
                }
            }
        } else {
            if (isVisualizingStep) {
                isVisualizingStep = false
                clearDvExchangeVisuals()
                validityMessage = "Auto-Step Paused." to Color.Gray
            }
        }
    }

    // Function to manually run one step
    // MODIFIED: Uses animationSpeedFactor for delays
    fun runSingleStep() {
        if (isConverged || isVisualizingStep) {
            if (isConverged) validityMessage = "Already Converged." to Color.Blue
            return
        }
        isVisualizingStep = true

        val previousSelectedTableLocal = selectedNode?.let { allNodeTables.getOrNull(it)?.toMap() }
        val (newTables, changed) = runDVRStep(allNodeTables, nodes, mutableEdges) // Assumes function exists elsewhere

        // Calculate delays based on speed factor
        val interNodeVizDelayMillis = (1200 / animationSpeedFactor).toLong().coerceAtLeast(100) // Min delay 100ms
        val applyUpdateDelayMillis = (400 / animationSpeedFactor).toLong().coerceAtLeast(50)   // Min delay 50ms

        coroutineScope.launch {
            try {
                if (changed) {
                    validityMessage = "Step $simulationStep: Visualizing Advertisements..." to Color.Blue
                    clearDvExchangeVisuals()
                    for (node in nodes) {
                        currentlyAdvertisingNode = node.id
                        triggerSingleNodeDvVisuals(node.id, mutableEdges) // Uses speed internally
                        delay(interNodeVizDelayMillis) // USE calculated delay
                        if (!isVisualizingStep) break
                    }
                    if (isVisualizingStep) {
                        currentlyAdvertisingNode = null
                        validityMessage = "Step $simulationStep: Applying Updates..." to Color.Blue
                        delay(applyUpdateDelayMillis) // USE calculated delay
                    }
                } else {
                    currentlyAdvertisingNode = null
                    clearDvExchangeVisuals()
                    validityMessage = "No changes in Step $simulationStep." to Color.Gray
                }

                if (isVisualizingStep) {
                    allNodeTables = newTables
                    previousTableForSelectedNode = previousSelectedTableLocal
                    simulationStep++
                    if (!changed) {
                        isConverged = true
                        validityMessage = "DVR Converged!" to Color.Green
                    } else {
                        isConverged = false
                        validityMessage = "Step ${simulationStep - 1} Completed." to Color.DarkGray
                    }
                } else {
                    clearDvExchangeVisuals()
                }
            } finally {
                isVisualizingStep = false
                currentlyAdvertisingNode = null
            }
        }
    }

    // Edge Link Animation Effect (Handles packets moving ON links visually)
    // MODIFIED: Reacts to animationSpeedFactor and uses it for duration
    LaunchedEffect(mutableEdges, animationSpeedFactor) { // ADDED animationSpeedFactor to key
        val currentEdgeSet = mutableEdges.toSet()

        // Stop and remove animations for edges no longer in the list
        val edgesToRemove = edgeAnimations.keys.filterNot { currentEdgeSet.contains(it) }
        edgesToRemove.forEach { edge ->
            coroutineScope.launch { edgeAnimations[edge]?.stop() }
            edgeAnimations.remove(edge)
        }

        // Add or update animations for current edges
        for (edge in mutableEdges) {
            // Calculate duration based on speed factor and cost
            val baseDuration = 1500; val costFactor = 150
            val scaledDuration = ((baseDuration + edge.cost * costFactor) / animationSpeedFactor).toInt().coerceAtLeast(100) // Min duration 100ms

            val existingAnimatable = edgeAnimations[edge]
            var needsRestart = false

            // Check if animation exists and needs restart due to speed change
            if (existingAnimatable != null) {
                // Simplistic check: if effect reruns due to speed change, restart.
                // A more robust method might store the speed with the animation.
                needsRestart = true // Assume restart needed if speed changes trigger recomposition
            }

            if (existingAnimatable == null || needsRestart) {
                if (needsRestart && existingAnimatable != null) {
                    coroutineScope.launch { existingAnimatable.stop() } // Stop old one
                    edgeAnimations.remove(edge) // Remove old entry
                }

                // Create and start animation (new or restarted)
                val animatable = Animatable(0f)
                edgeAnimations[edge] = animatable // Add/replace in map
                launch {
                    try {
                        animatable.animateTo(
                            1f,
                            infiniteRepeatable(
                                tween(scaledDuration, easing = LinearEasing), // USE calculated duration
                                RepeatMode.Restart
                            )
                        )
                    } catch (_: Exception) { /* Ignore cancellation */ }
                    finally {
                        edgeAnimations.remove(edge) // Clean up map on completion/cancellation
                    }
                }
            }
        }
    }

    // Shortest Path Calculation/Display Effect (Shows result of clicking Simulate)
    // MODIFIED: Reacts to animationSpeedFactor and uses it for delay
    LaunchedEffect(shortestPath, animationSpeedFactor) { // ADDED animationSpeedFactor to key
        animatedPath = emptyList()
        if (shortestPath.size >= 2) {
            validityMessage = "Simulating path: ${shortestPath.joinToString(" → ")}" to Color(0xFF006400)
            // Calculate delay based on speed factor
            val segmentHighlightDelayMillis = (600 / animationSpeedFactor).toLong().coerceAtLeast(80) // Min delay 80ms

            val pathToShow = shortestPath.toList()
            var currentAnimated = emptyList<Pair<Int, Int>>()

            for (i in 0 until pathToShow.size - 1) {
                if (shortestPath != pathToShow) break // Stop if path changed

                val u = pathToShow[i]
                val v = pathToShow[i + 1]
                val pathEdgeExists = mutableEdges.any { (it.src == u && it.dest == v) || (it.src == v && it.dest == u) }

                if (!pathEdgeExists) {
                    validityMessage = "Path segment $u-$v no longer exists!" to Color.Red
                    animatedPath = emptyList()
                    break
                }

                currentAnimated = currentAnimated + (u to v)
                animatedPath = currentAnimated
                delay(segmentHighlightDelayMillis) // USE calculated delay
            }
        }
    }


    // --- UI ---
    MaterialTheme { // Assuming MaterialTheme is used
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
                // (Canvas Box and pointerInput remain unchanged from your original code)
                Box(
                    modifier = Modifier
                        .align(Alignment.Bottom) // This alignment seems odd, usually Top or Center. Keeping as is.
                        .weight(0.65f)
                        .fillMaxHeight()
                        .padding(end = 8.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { offset ->
                                    val tappedNode = nodes.firstOrNull { node -> sqrt((offset.x - node.x).pow(2) + (offset.y - node.y).pow(2)) <= 25f }
                                    if (tappedNode != null) {
                                        selectedNode = tappedNode.id; selectedEdge = null; validityMessage = "Selected Node ${tappedNode.id}" to Color.Blue
                                    } else {
                                        selectedEdge = mutableEdges.firstOrNull { edge -> nodes.getOrNull(edge.src)?.let { s -> nodes.getOrNull(edge.dest)?.let { e -> isPointNearLine(offset, s, e) } } ?: false } // Assumes isPointNearLine exists
                                        if (selectedEdge != null) {
                                            selectedNode = null; validityMessage = "Selected Edge ${selectedEdge!!.src}<->${selectedEdge!!.dest}" to Color.Blue
                                        } else {
                                            selectedNode = null; selectedEdge = null
                                        }
                                    }
                                    if (tappedNode == null && selectedEdge == null) {
                                        shortestPath = emptyList()
                                        animatedPath = emptyList() // Also clear visual path
                                    }
                                }
                            )
                        }
                ) {
                    // --- Canvas Drawing ---
                    // (Canvas drawing logic remains unchanged from your original code)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Draw Edges
                        mutableEdges.forEach { edge ->
                            val startNode = nodes.getOrNull(edge.src); val endNode = nodes.getOrNull(edge.dest); if (startNode == null || endNode == null) return@forEach
                            val start = Offset(startNode.x, startNode.y); val end = Offset(endNode.x, endNode.y)

                            val isShortestPathAnimated = animatedPath.contains(edge.src to edge.dest) || animatedPath.contains(edge.dest to edge.src)
                            val isEdgeSelected = selectedEdge?.let { (it.src == edge.src && it.dest == edge.dest) || (it.src == edge.dest && it.dest == edge.src) } ?: false
                            val isAdvertisingEdge = edge.src == currentlyAdvertisingNode

                            val strokeWidth = when { isEdgeSelected -> 7f; isShortestPathAnimated -> 6f; isAdvertisingEdge -> 5f; else -> 4f }
                            val color = when { isEdgeSelected -> Color(0xFFFFA500); isShortestPathAnimated -> Color.Red; isAdvertisingEdge -> Color(0xFF9C27B0); else -> Color.Gray }

                            drawLine(start = start, end = end, color = color, strokeWidth = strokeWidth)
                            val midX = (start.x + end.x) / 2; val midY = (start.y + end.y) / 2
                            drawText(textMeasurer, edge.cost.toString(), Offset(midX - 8, midY - 20), TextStyle(Color.Black, 12.sp, background = Color(0xAAFFFFFF)))

                            // Draw Shortest Path Symbolic "Packet"
                            edgeAnimations[edge]?.value?.let { progress ->
                                val pathSegment = animatedPath.find { (u, v) -> (u == edge.src && v == edge.dest) }
                                val reversePathSegment = animatedPath.find { (u, v) -> (u == edge.dest && v == edge.src) }
                                val packetSourceId = when { pathSegment != null -> pathSegment.first; reversePathSegment != null -> reversePathSegment.first; else -> null }

                                if (packetSourceId != null && mutableEdges.contains(edge) && progress > 0.01f && progress < 0.99f) {
                                    val packetStartPos: Offset; val packetEndPos: Offset
                                    if (packetSourceId == edge.src) { packetStartPos = start; packetEndPos = end } else { packetStartPos = end; packetEndPos = start }
                                    val currentPos = Offset(packetStartPos.x + (packetEndPos.x - packetStartPos.x) * progress, packetStartPos.y + (packetEndPos.y - packetStartPos.y) * progress)
                                    val tableWidth = 26f; val tableHeight = 20f; val rectTopLeft = Offset(currentPos.x - tableWidth / 2, currentPos.y - tableHeight / 2)
                                    drawRect(color = Color(0xFFB2EBF2), topLeft = rectTopLeft, size = Size(tableWidth, tableHeight))
                                    drawRect(color = Color(0xFF00BCD4), topLeft = rectTopLeft, size = Size(tableWidth, tableHeight), style = Stroke(width = 1.5f))
                                    val textStyle = TextStyle(color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                    // Draw original path source ID, not segment source ID
                                    val originalSourceText = shortestPath.firstOrNull()?.toString() ?: packetSourceId.toString()
                                    val textLayoutResult = textMeasurer.measure(originalSourceText, style = textStyle)
                                    drawText(textLayoutResult = textLayoutResult, topLeft = Offset(rectTopLeft.x + (tableWidth - textLayoutResult.size.width) / 2, rectTopLeft.y + (tableHeight - textLayoutResult.size.height) / 2))
                                }
                            }

                            // Draw DV Advertisement Symbolic "Table" Packet
                            val dvAnimationKey = edge.src to edge.dest
                            dvExchangeAnimations[dvAnimationKey]?.value?.let { progress ->
                                if (edge.src == currentlyAdvertisingNode && progress > 0.01f && progress < 0.99f) {
                                    val senderId = edge.src; val currentPos = Offset(start.x + (end.x - start.x) * progress, start.y + (end.y - start.y) * progress)
                                    val tableWidth = 26f; val tableHeight = 20f; val rectTopLeft = Offset(currentPos.x - tableWidth / 2, currentPos.y - tableHeight / 2)
                                    drawRect(color = Color(0xFFE1BEE7), topLeft = rectTopLeft, size = Size(tableWidth, tableHeight))
                                    drawRect(color = Color(0xFF7B1FA2), topLeft = rectTopLeft, size = Size(tableWidth, tableHeight), style = Stroke(width = 1.5f))
                                    val textStyle = TextStyle(color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                    val textLayoutResult = textMeasurer.measure(senderId.toString(), style = textStyle)
                                    drawText(textLayoutResult = textLayoutResult, topLeft = Offset(rectTopLeft.x + (tableWidth - textLayoutResult.size.width) / 2, rectTopLeft.y + (tableHeight - textLayoutResult.size.height) / 2))
                                }
                            }
                        } // End mutableEdges.forEach

                        // Draw Nodes
                        nodes.forEach { node ->
                            val isSelected = node.id == selectedNode; val isInPath = shortestPath.contains(node.id); val isAdvertising = node.id == currentlyAdvertisingNode
                            val isPathStart = isInPath && shortestPath.firstOrNull() == node.id; val isPathEnd = isInPath && shortestPath.lastOrNull() == node.id
                            val nodeColor = when { isAdvertising -> Color(0xFFBA68C8); isSelected -> Color.Magenta; isPathStart -> Color(0xFF1B5E20); isPathEnd -> Color(0xFFFF6F00); isInPath -> Color(0xFF4CAF50); else -> Color.Blue }
                            val radius = 20f
                            if (isAdvertising) drawCircle(center = Offset(node.x, node.y), radius = radius + 7f, color = Color(0xFF9C27B0).copy(alpha = 0.4f), style = Stroke(width = 3f))
                            else if (isSelected) drawCircle(center = Offset(node.x, node.y), radius = radius + 4f, color = Color.Yellow, style = Stroke(3f)) // Show selection only if not advertising
                            drawCircle(center = Offset(node.x, node.y), radius = radius, color = nodeColor)
                            drawText(textMeasurer, node.id.toString(), Offset(node.x - 6f, node.y - 10f), TextStyle(Color.White, 16.sp, fontWeight = FontWeight.Bold))
                        } // End nodes.forEach
                    } // End Canvas

                    // Link Management Card originally positioned strangely with align + padding - KEEPING AS IS
                    Row(modifier = Modifier.align(Alignment.BottomStart).padding(top=100.dp,start=100.dp,end=100.dp)) {
                        ControlCard(title = "Link Management") { // Assumes ControlCard exists
                            OutlinedTextField(sourceId, { sourceId = it.filter { c -> c.isDigit() } }, label = { Text("Node 1 ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            OutlinedTextField(destinationId, { destinationId = it.filter { c -> c.isDigit() } }, label = { Text("Node 2 ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            OutlinedTextField(weightInput, { weightInput = it.filter { c -> c.isDigit() }.takeIf { it.isNotEmpty() } ?: "1" }, label = { Text("Cost") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), Arrangement.SpaceEvenly) {
                                // Add Button Logic (unchanged from your original)
                                Button(onClick = {
                                    val src = sourceId.toIntOrNull(); val dest = destinationId.toIntOrNull(); val cost = weightInput.toIntOrNull()
                                    if (src == null || dest == null || cost == null) validityMessage = "Invalid ID or Cost." to Color.Red
                                    else if (src == dest) validityMessage = "Nodes must be different." to Color.Red
                                    else if (src !in nodes.indices || dest !in nodes.indices) validityMessage = "Node ID out of bounds (${nodes.indices})." to Color.Red
                                    else if (cost <= 0) validityMessage = "Cost must be positive." to Color.Red
                                    else if (!mutableEdges.any { (it.src == src && it.dest == dest) || (it.src == dest && it.dest == src) }) {
                                        val edge1 = Edge(src, dest, cost); val edge2 = Edge(dest, src, cost)
                                        mutableEdges.add(edge1); mutableEdges.add(edge2)
                                        validityMessage = "Link added: $src <-> $dest (Cost: $cost)" to Color.Green
                                        sourceId = ""; destinationId = ""; weightInput = "1"; isConverged = false
                                    } else validityMessage = "Link $src <-> $dest already exists." to Color.Blue
                                }) { Text("Add") }
                                // Remove Button Logic (unchanged from your original)
                                Button(onClick = {
                                    val src = sourceId.toIntOrNull(); val dest = destinationId.toIntOrNull()
                                    if (src == null || dest == null) validityMessage = "Invalid ID." to Color.Red
                                    else if (src !in nodes.indices || dest !in nodes.indices) validityMessage = "Node ID out of bounds (${nodes.indices})." to Color.Red
                                    else {
                                        val edgesToRemove = mutableEdges.filter { (it.src == src && it.dest == dest) || (it.src == dest && it.dest == src) }
                                        val removed = mutableEdges.removeAll(edgesToRemove)
                                        validityMessage = if (removed) "Link removed: $src <-> $dest" to Color.Blue else "Link $src <-> $dest not found." to Color.Red
                                        sourceId = ""; destinationId = ""; weightInput = "1"
                                        if (removed) {
                                            isConverged = false
                                            edgesToRemove.forEach { edge -> coroutineScope.launch{ edgeAnimations[edge]?.stop() }; edgeAnimations.remove(edge) }
                                            clearDvExchangeVisuals()
                                            previousTableForSelectedNode = allNodeTables.getOrNull(selectedNode ?: -1)?.toMap()
                                            if (selectedEdge in edgesToRemove) selectedEdge = null // Deselect if removed
                                        }
                                    }
                                }) { Text("Remove") }
                                // Update Cost Button Logic (unchanged from your original)
                                Button(onClick = {
                                    val src = sourceId.toIntOrNull(); val dest = destinationId.toIntOrNull(); val newCost = weightInput.toIntOrNull()
                                    if (src == null || dest == null || newCost == null) validityMessage = "Invalid ID or Cost." to Color.Red
                                    else if (src == dest) validityMessage = "Nodes must be different." to Color.Red
                                    else if (src !in nodes.indices || dest !in nodes.indices) validityMessage = "Node ID out of bounds (${nodes.indices})." to Color.Red
                                    else if (newCost <= 0) validityMessage = "Cost must be positive." to Color.Red
                                    else {
                                        val index1 = mutableEdges.indexOfFirst { it.src == src && it.dest == dest }
                                        val index2 = mutableEdges.indexOfFirst { it.src == dest && it.dest == src }
                                        if (index1 != -1 && index2 != -1) {
                                            val oldEdge1 = mutableEdges[index1]; val oldEdge2 = mutableEdges[index2]
                                            if (oldEdge1.cost == newCost) { validityMessage = "Cost is already $newCost." to Color.Blue }
                                            else {
                                                coroutineScope.launch { edgeAnimations[oldEdge1]?.stop() }; edgeAnimations.remove(oldEdge1)
                                                coroutineScope.launch { edgeAnimations[oldEdge2]?.stop() }; edgeAnimations.remove(oldEdge2)
                                                mutableEdges[index1] = oldEdge1.copy(cost = newCost); mutableEdges[index2] = oldEdge2.copy(cost = newCost)
                                                validityMessage = "Link cost updated: $src <-> $dest (New Cost: $newCost)" to Color.Green
                                                isConverged = false; clearDvExchangeVisuals()
                                                previousTableForSelectedNode = allNodeTables.getOrNull(selectedNode ?: -1)?.toMap()
                                            }
                                        } else { validityMessage = "Link $src <-> $dest not found." to Color.Red }
                                        sourceId = ""; destinationId = ""; weightInput = "1"
                                    }
                                }) { Text("Update Cost") }
                            } // End Button Row
                        } // End ControlCard
                    } // End Row for Link Management Card
                } // --- End Canvas Box ---

                // --- Right Side: Column for Controls and Table ---
                // (Structure unchanged from your original)
                Column(
                    modifier = Modifier
                        .weight(0.35f)
                        .fillMaxHeight()
                        .padding(start = 8.dp)
                ) {
                    // --- Simulation Control Buttons ---
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(onClick = ::runSingleStep, enabled = !isConverged && !autoStepEnabled && !isVisualizingStep) { Text("Step") } // Added !isVisualizingStep enable check
                        Button(onClick = { autoStepEnabled = !autoStepEnabled }, enabled = !isConverged) { Text(if (autoStepEnabled) "Pause" else "Auto Step") }
                        Button(onClick = {
                            // Reset logic (unchanged from your original, ensures animations are stopped)
                            autoStepEnabled = false; isVisualizingStep = false
                            clearDvExchangeVisuals()
                            coroutineScope.launch { edgeAnimations.values.forEach { try { it.stop() } catch (_: Exception) {} }; edgeAnimations.clear() }
                            mutableEdges.clear(); mutableEdges.addAll(Utils.edges)
                            allNodeTables = initializeDvTables(nodes, mutableEdges)
                            simulationStep = 0; isConverged = false
                            selectedNode = null; selectedEdge = null
                            shortestPath = emptyList(); animatedPath = emptyList()
                            validityMessage = "Simulation Reset." to Color.Blue
                        }) { Text("Reset Sim") }
                    }

                    // --- ADDED: Animation Speed Slider ---
                    // Placed here, after sim controls, before the next card
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text("Animation Speed: ${"%.1fx".format(animationSpeedFactor)}", fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                        Slider(
                            value = animationSpeedFactor,
                            onValueChange = { animationSpeedFactor = it },
                            valueRange = 0.2f..3.0f, // Example range: 0.2x to 3.0x speed
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp)) // Add spacer after slider

                    // --- Find Path Section ---
                    // (Unchanged from your original code)
                    ControlCard(title = "Find Path (Using Current Tables)") { // Assumes ControlCard exists
                        OutlinedTextField(packetSource, { packetSource = it.filter{c->c.isDigit()} }, label = { Text("Source ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(packetDestination, { packetDestination = it.filter{c->c.isDigit()} }, label = { Text("Dest ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Button( onClick = {
                            shortestPath = emptyList(); animatedPath = emptyList() // Clear previous path first
                            val start = packetSource.toIntOrNull(); val end = packetDestination.toIntOrNull()

                            if (start == null || end == null) { validityMessage = "Invalid ID." to Color.Red }
                            else if (start == end) { validityMessage = "Source and destination are same." to Color.Blue }
                            else if (start !in nodes.indices || end !in nodes.indices) { validityMessage = "ID out of bounds." to Color.Red }
                            else {
                                val path = mutableListOf<Int>(); var current: Int? = start; val visited = mutableSetOf<Int>(); var pathPossible = true
                                while (current != null && current != end && current !in visited) {
                                    path.add(current); visited.add(current)
                                    val currentTable = allNodeTables.getOrNull(current)
                                    val entryToEnd = currentTable?.get(end)
                                    if (currentTable == null || entryToEnd == null || entryToEnd.cost == INF || entryToEnd.nextHop == -1) {
                                        validityMessage = "No path from $current to $end in current tables." to Color.Red
                                        path.clear(); pathPossible = false; break
                                    }
                                    val nextHopNodeId = entryToEnd.nextHop
                                    if(nextHopNodeId == -1 || nextHopNodeId == current) {
                                        validityMessage = "Routing error/no next hop at $current for $end." to Color.Red
                                        path.clear(); pathPossible = false; break
                                    }
                                    current = nextHopNodeId
                                }
                                if (pathPossible) {
                                    if (current == end) { path.add(end); shortestPath = path } // Let LaunchedEffect handle message
                                    else if (current in visited) { validityMessage = "Routing loop detected." to Color.Red; shortestPath = emptyList() }
                                    else { validityMessage = "Path finding failed unexpectedly." to Color.Red; shortestPath = emptyList() }
                                } else { shortestPath = emptyList() }
                            }
                            packetSource = ""; packetDestination = ""
                        } ) { Text("Show Path") }
                    }

                    Spacer(Modifier.height(8.dp))

                    // --- Validity Message Area ---
                    // (Unchanged from your original code)
                    Box(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 24.dp).padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        validityMessage?.let { (message, color) -> Text( text = message, color = color, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold) }
                    }

                    Spacer(Modifier.height(16.dp))

                    // --- Display the routing table ---
                    // (Unchanged from your original code)
                    TableLayout( // Assumes TableLayout exists
                        selectedNode = selectedNode ?: 0,
                        entries = currentTableForDisplay,
                        timer = simulationStep,
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