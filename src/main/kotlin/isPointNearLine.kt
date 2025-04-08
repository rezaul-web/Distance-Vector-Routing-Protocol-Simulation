import androidx.compose.ui.geometry.Offset
import kotlin.ranges.coerceIn

fun isPointNearLine(point: Offset, start: Utils.Node, end: Utils.Node, threshold: Float = 10f): Boolean {
    val startOffset = Offset(start.x, start.y); val endOffset = Offset(end.x, end.y)
    val lineVec = endOffset - startOffset; val pointVec = point - startOffset
    val lineLenSq = lineVec.getDistanceSquared()
    if (lineLenSq < 0.1f) return (point - startOffset).getDistanceSquared() < threshold * threshold
    val t = (pointVec.x * lineVec.x + pointVec.y * lineVec.y) / lineLenSq
    val projectionT = t.coerceIn(0f, 1f); val closestPoint = startOffset + lineVec * projectionT
    return (point - closestPoint).getDistanceSquared() < threshold * threshold
}