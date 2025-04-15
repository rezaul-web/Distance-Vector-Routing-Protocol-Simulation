

// Assume INF is defined, e.g., in Utils or here
import Utils.INF // If INF is in Utils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


object TableColors { // Keep or adjust colors
    val cardBackground = Color(0xFFF5F5F5)
    val headerBackground = Color(0xFFE0E0E0)
    val primaryText = Color.Black
    val secondaryText = Color.DarkGray
    val linkDownText = Color(0xFFB71C1C)
    val noHopText = Color.Gray
    val divider = Color.LightGray
}

@Composable
fun TableLayout(
    entries: Array<RoutingTableEntry>, // Expects an Array
    modifier: Modifier = Modifier,
    selectedNode: Int = 0,
    timer: Int // Or simulation step count
) {
    Card(
        modifier = modifier, // Parent provides size constraints (e.g., weight, fillMaxSize)
        elevation = 6.dp,
        shape = RoundedCornerShape(12.dp),
        backgroundColor = TableColors.cardBackground
    ) {
        val s = when(timer) {
            0->"Zero"
            1 -> "First"
            2 -> "Second"
            3 -> "Third"
            4 -> "Fourth"
            5 -> "Fifth"
            6 -> "Sixth"
            7 -> "Seventh"
            8 -> "Eighth"
            9 -> "Ninth"
            10 -> "Tenth"
            11 -> "Eleventh"
            12 -> "Twelfth"
            13 -> "Thirteenth"
            14 -> "Fourteenth"
            15 -> "Fifteenth"
            16 -> "Sixteenth"
            17 -> "Seventeenth"
            18 -> "Eighteenth"
            19 -> "Nineteenth"
            20 -> "Twentieth"
            else -> "kdfgk"
        }

        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth() // Fill width of parent Card
            // Height is constrained by parent (e.g., Box with weight)
        ) {
            // --- Title (Fixed at top) ---
            Text(
                // text = "Router $selectedNode Table (Step: $timer)", // Use step/timer
                text = "Router $selectedNode Table (After $s Exchange)",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                color = TableColors.primaryText,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 8.dp)
            )

            // --- Header Row (Fixed at top) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(TableColors.headerBackground)
                    .padding(vertical = 8.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TableCell(text = "Dest", weight = 1f, isHeader = true)
                TableCell(text = "Next Hop", weight = 1f, isHeader = true)
                TableCell(text = "Cost", weight = 1f, isHeader = true)
            }

            Divider(
                color = TableColors.divider,
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 6.dp)
            )

            // --- Scrollable Data Rows Area ---
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false) // Takes available vertical space, but scrolls if needed
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (entries.isEmpty()) {
                    Text( /* No entries message */
                        text = "Calculating...", // Or "No routing entries."
                        fontSize = 14.sp,
                        color = TableColors.secondaryText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                    )
                } else {
                    // Sort entries by destination for consistent display
                    entries.sortedBy { it.destination }.forEach { entry ->
                        Row( /* Row per entry */
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TableCell(text = entry.destination.toString(), weight = 1f)

                            val nextHopText = if (entry.cost == 0) "-" // Cost 0 is self
                            else if (entry.nextHop == -1) "-" // Unreachable
                            else entry.nextHop.toString()
                            val nextHopColor = if (entry.cost == 0 || entry.nextHop == -1) TableColors.noHopText
                            else TableColors.secondaryText
                            TableCell(text = nextHopText, weight = 1f, color = nextHopColor)

                            val costText = if (entry.cost == INF) "INF" else entry.cost.toString()
                            val costColor = if (entry.cost == INF) TableColors.linkDownText else TableColors.secondaryText
                            val costWeight = if (entry.cost == INF) FontWeight.Bold else FontWeight.Normal
                            TableCell(text = costText, weight = 1f, color = costColor, fontWeight = costWeight)
                        }
                        Divider(color = TableColors.divider.copy(alpha = 0.5f), thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 8.dp))
                    }
                }
            } // --- End Scrollable Column ---
        } // --- End Main Column in Card ---
    } // --- End Card ---
}

// Helper Composable for table cells
@Composable
fun RowScope.TableCell(
    text: String,
    weight: Float,
    color: Color = LocalContentColor.current,
    textAlign: TextAlign = TextAlign.Center,
    fontSize: Int = 14,
    fontWeight: FontWeight = FontWeight.Normal,
    isHeader: Boolean = false
) {
    Text( /* Cell Text */
        text = text,
        modifier = Modifier.weight(weight).padding(horizontal = 4.dp),
        fontSize = if (isHeader) 14.sp else fontSize.sp,
        fontWeight = if (isHeader) FontWeight.Bold else fontWeight,
        color = if (isHeader) TableColors.primaryText else color,
        textAlign = textAlign
    )
}