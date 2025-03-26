import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt


import kotlin.math.*

fun isPointNearLine(point: Offset, start: Utils.Node, end: Utils.Node, threshold: Float = 10f): Boolean {
    val startOffset = Offset(start.x, start.y)
    val endOffset = Offset(end.x, end.y)

    val lineLengthSquared = (endOffset - startOffset).getDistanceSquared()
    if (lineLengthSquared == 0f) return point.getDistance(startOffset) <= threshold  // Handle point-like segment

    val t = max(0f, min(1f, ((point - startOffset) dot (endOffset - startOffset)) / lineLengthSquared))
    val closestPoint = startOffset + (endOffset - startOffset) * t  // Projection on segment

    return point.getDistance(closestPoint) <= threshold
}

// Extension function to compute dot product of vectors
private infix fun Offset.dot(other: Offset): Float = this.x * other.x + this.y * other.y

// Extension function to compute squared distance between two points
private fun Offset.getDistanceSquared(): Float = this.x.pow(2) + this.y.pow(2)

// Extension function to compute Euclidean distance
private fun Offset.getDistance(other: Offset): Float =
    sqrt((this.x - other.x).pow(2) + (this.y - other.y).pow(2))
