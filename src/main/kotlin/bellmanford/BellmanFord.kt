package bellmanford



import Utils.edges
import Utils.nodes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.collections.indices
import kotlin.collections.toList
import kotlin.let


@Composable
fun BellmanFordRoutingUI() {


    var routingTables by remember { mutableStateOf<Array<Array<RoutingTableEntry>>?>(null) }

    Column(Modifier.fillMaxSize().padding(5.dp)) {
        Button(onClick = { routingTables = bellmanFordAllPairsRoutingTable(nodes, edges) }) {
            Text("Compute Routing Tables")
        }

        routingTables?.let { tables ->
            LazyColumn {
                items(tables.indices.toList()) { node ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        elevation = 4.dp
                    ) {
                        Column(Modifier.padding(8.dp)) {
                            Text("Routing Table for Node $node", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Spacer(Modifier.height(8.dp))
                            TableLayout(tables[node], modifier = Modifier)
                        }
                    }
                }
            }
        }
    }
}

