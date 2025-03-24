import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Helper function to check if a point is near a line (edge).
 */
fun isPointNearLine(point: Offset, start: Utils.Node, end: Utils.Node, threshold: Float = 10f): Boolean {
    val a = start.y - end.y
    val b = end.x - start.x
    val c = start.x * end.y - end.x * start.y

    val distance = abs(a * point.x + b * point.y + c) / sqrt(a.pow(2) + b.pow(2))

    return distance <= threshold
}