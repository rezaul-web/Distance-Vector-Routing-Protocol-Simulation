import Utils.Edge
import Utils.edges
import Utils.nodes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import bellmanford.RoutingTableEntry
import bellmanford.TableLayout
import bellmanford.bellmanFordAllPairsRoutingTable
import bellmanford.broadcastDistanceVector
import kotlinx.coroutines.delay
import kotlin.math.pow
import kotlin.math.sqrt

const val INF = Int.MAX_VALUE / 2


@Composable
fun DistanceVectorRoutingApp() {
    var sourceId by remember { mutableStateOf("") }
    var destinationId by remember { mutableStateOf("") }
    var shortestPath by remember { mutableStateOf<List<Int>>(emptyList()) }
    var animatedPath by remember { mutableStateOf<List<Int>>(emptyList()) }
    var mutableEdges by remember { mutableStateOf(edges.toMutableStateList()) }

    var selectedNode by remember { mutableStateOf<Int?>(null) }
    var selectedEdge by remember { mutableStateOf<Edge?>(null) }

    var packetSource by remember { mutableStateOf("") }
    var packetDestination by remember { mutableStateOf("") }

    var validity by remember { mutableStateOf("") }

    // Ensure routing table updates when mutableEdges change
    val table by remember { derivedStateOf { bellmanFordAllPairsRoutingTable(nodes, mutableEdges) } }
    var currRouter by remember { mutableStateOf(table[0]) }

    var nextHopes by remember { mutableStateOf(mutableSetOf<Int>()) }


    // Update currRouter when selectedNode changes
    LaunchedEffect(selectedNode,table) {
        currRouter = selectedNode?.let { table[it] } ?: table[0]
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = sourceId, onValueChange = { sourceId = it },
                label = { Text(text = "Source") }
            )

            OutlinedTextField(
                value = destinationId, onValueChange = { destinationId = it },
                label = { Text(text = "Destination") }
            )

            Button(
                onClick = {
                    val start = sourceId.toIntOrNull()
                    val end = destinationId.toIntOrNull()




                    if (start != null && end != null) {
                        val newEdges = mutableEdges.toMutableList().apply {
                            removeAll { (it.src == start && it.dest == end) || (it.src == end && it.dest == start) }
                        }
                        mutableEdges.clear()
                        mutableEdges.addAll(newEdges)

                    } else {
                        println("Invalid input! Please enter valid numbers for source and destination.")
                        validity = "Invalid input! Please enter valid numbers for source and destination."
                    }
                    destinationId = ""
                    sourceId = ""
                },
                modifier = Modifier
            ) {
                Text("Remove Link")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {


            OutlinedTextField(
                value = packetSource, onValueChange = { packetSource = it },
                label = { Text(text = "Source") }
            )

            OutlinedTextField(
                value = packetDestination, onValueChange = { packetDestination = it },
                label = { Text(text = "Destination") }
            )

            Button(
                onClick = {
                    val start = packetSource.toIntOrNull()
                    val end = packetDestination.toIntOrNull()

                    if (start != null && end != null && start != end) {
                        val path = mutableListOf<Int>()
                        var current = start

                        while (current != end) {
                            path.add(current!!)
                            val nextHop = table[current].find { it.destination == end }?.nextHop

                            if (nextHop == null || nextHop == -1) {
                                validity = "No valid route found!"
                                path.clear()
                                break
                            }

                            current = nextHop
                        }

                        if (path.isNotEmpty()) {
                            path.add(end) // Add the final destination
                            shortestPath = path
                        }
                    } else {
                        validity = "Invalid source or destination!"
                    }
                    packetSource = ""
                    packetDestination = ""
                }
            ) {
                Text("Send Packet")
            }
        }
        if (validity.isNotEmpty()) {
            Text(
                text = validity,
                color = Color.Red,
                modifier = Modifier.padding(8.dp),
                fontSize = 24.sp
            )
        }



        LaunchedEffect(shortestPath) {
            animatedPath = emptyList()
            for (node in shortestPath) {
                animatedPath = animatedPath + node
                delay(500)  // Simulating packet transmission delay
            }
        }
        LaunchedEffect(Unit) {
            nextHopes = mutableSetOf() // Clear previous highlights

            for (node in nodes.indices) {
                delay(1000)
                // Iterate over all routers (nodes)
                val newHopes = mutableSetOf<Int>()

                val routingTable = table[node] // Get the routing table for current node
                for (entry in routingTable) {
                    if (entry.nextHop != -1) {
                        newHopes.add(entry.nextHop)
                    }
                }

               nextHopes=newHopes

            }
            nextHopes.clear()
        }





        val textMeasurer = rememberTextMeasurer()

        Box(modifier = Modifier.fillMaxSize().align(Alignment.CenterHorizontally)) {
            Canvas(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { offset ->
                                val tappedEdge = mutableEdges.find { edge ->
                                    val start = nodes[edge.src]
                                    val end = nodes[edge.dest]
                                    isPointNearLine(offset, start, end)
                                }

                                tappedEdge?.let {
                                    mutableEdges.remove(it)  // Remove the edge if double-clicked
                                    mutableEdges.remove(it.copy(src = it.dest, dest = it.src))
                                }

                            },
                            onTap = { offset ->
                                selectedNode = nodes.find { node ->
                                    val distance = sqrt((offset.x - node.x).pow(2) + (offset.y - node.y).pow(2))
                                    distance <= 20f
                                }?.id

                                selectedEdge = mutableEdges.find { edge ->
                                    val start = nodes[edge.src]
                                    val end = nodes[edge.dest]
                                    isPointNearLine(offset, start, end)
                                }
                            },

                            )
                    }

            ) {
                // Draw edges
                mutableEdges.forEach { edge ->
                    val start = nodes[edge.src]
                    val end = nodes[edge.dest]

                    val isHighlighted = animatedPath.contains(edge.src) && animatedPath.contains(edge.dest)
                    val isSelected = edge == selectedEdge
                    val isInHopes = edge.src in nextHopes


                    val color = when {
                        isSelected -> Color(0xFF39FF14)
                        isHighlighted -> Color.Red
//                        isInHopes->Color.Yellow
                        else -> Color.LightGray
                    }

                    drawLine(
                        start = Offset(start.x, start.y),
                        end = Offset(end.x, end.y),
                        color = color,
                        strokeWidth = if (isSelected) 6f else 4f
                    )

                    val midX = (end.x + start.x) / 2
                    val midY = (end.y + start.y) / 2
                    drawText(
                        textMeasurer = textMeasurer,
                        text = edge.cost.toString(),
                        topLeft = Offset(midX + 10, midY + 10),
                    )
                }

                // Draw nodes
                nodes.forEach { node ->
                    val isSelected = node.id == selectedNode
                    drawCircle(
                        center = Offset(node.x, node.y),
                        radius = 20f,
                        color = when {
                            isSelected -> Color.Magenta
                            animatedPath.contains(node.id) -> Color.Green
                            else -> Color.Blue
                        }
                    )

                    drawText(
                        textMeasurer = textMeasurer,
                        text = node.id.toString(),
                        topLeft = Offset(node.x - 10, node.y - 10),
                        style = TextStyle(color = Color.White, fontSize = 16.sp),
                    )
                }
            }

            // Display the routing table
            TableLayout(
                selectedNode = selectedNode ?: 0,
                entries = currRouter,
                modifier = Modifier.align(Alignment.BottomEnd)
            )

        }
    }
}

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Distance Vector Routing Simulation") {
        DistanceVectorRoutingApp()
    }
}

