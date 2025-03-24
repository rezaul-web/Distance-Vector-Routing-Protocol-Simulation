package bellmanford

import Utils.INF
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.awt.Color

@Composable
fun TableLayout(entries: Array<RoutingTableEntry>, modifier: Modifier = Modifier, selectedNode: Int = 0) {
    Card(
        modifier = modifier
            .width(450.dp)
            .padding(30.dp),
        elevation = 4.dp,
        backgroundColor = androidx.compose.ui.graphics.Color.LightGray
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            Text(
                text = "Router $selectedNode",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Dest", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text("Next Hop", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text("Cost", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            }
            Divider(Modifier.padding(vertical = 4.dp))

            entries.forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(entry.destination.toString(), fontSize = 20.sp, modifier = Modifier.weight(1f))
                    Text(if (entry.nextHop == -1) "-" else entry.nextHop.toString(), fontSize = 20.sp, modifier = Modifier.weight(1f))
                    Text(if (entry.cost == INF) "Link down" else entry.cost.toString(), fontSize = 20.sp, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}


