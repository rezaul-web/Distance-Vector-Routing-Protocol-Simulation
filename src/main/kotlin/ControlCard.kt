import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// ... (ControlCard, .isPointNearLine, main function - unchanged) ...
@Composable
fun ControlCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(elevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Divider(); content()
        }
    }
}