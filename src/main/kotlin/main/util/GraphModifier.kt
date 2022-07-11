package main.util

import main.graph.MutableEdge
import main.graph.MutableGraph
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/** Removes edges from [graph] based on the geometry based method. */
fun geometryBasedCut(graph: MutableGraph, scale: Double, degree: Int) {
    val chosen = mutableMapOf<MutableEdge, Boolean>() // chosen[edge] if and only if edge will be included in the resulting graph
    graph.vertices.forEach { v ->
        val chosenAngles = mutableListOf<Double>()
        val chosenForThisVertex = mutableListOf<MutableEdge>() // edges chosen to be included for this vertex
        repeat(degree) {
            val maxWeight = v.adj.maxOf { (e, _) -> e.weight }.toDouble()
            // find vertex with min "geometric score"
            val best = v.adj.filterNot { (e, _) -> e in chosenForThisVertex }.minByOrNull { (e, t) ->
                val angle = atan2(t.y - v.y, t.x - v.x)
                val relWeight = e.weight / maxWeight
                scale * relWeight + chosenAngles.sumOf { cos(it - angle) }
            }
            // add vertex with min score to chosen vertices
            if (best != null) {
                chosen[best.first] = true
                chosenForThisVertex.add(best.first)
                chosenAngles.add(atan2(best.second.y - v.y, best.second.x - v.x))
            }
        }
    }
    // filter graph based on [chosen]
    graph.edges.filter { !chosen.getOrDefault(it, false) }.forEach { it.remove() }
}

/** Assigns depots to a TSPLIB graph based on a rectangular grid. */
fun assignDepots(graph: MutableGraph, depotCount: Int) {
    // compute bounding box of vertex coordinates
    var xMin = Double.POSITIVE_INFINITY
    var yMin = Double.POSITIVE_INFINITY
    var xMax = Double.NEGATIVE_INFINITY
    var yMax = Double.NEGATIVE_INFINITY
    for (vertex in graph.vertices) {
        xMin = min(xMin, vertex.x)
        yMin = min(yMin, vertex.y)
        xMax = max(xMax, vertex.x)
        yMax = max(yMax, vertex.y)
    }
    // add normalized rectangle centers assuming bounding box is [0, 1] x [0, 1]
    val centers = ArrayList<Pair<Double, Double>>()
    when (depotCount) {
        1 -> {
            centers.add(0.5 to 0.5)
        }
        2 -> {
            centers.add(0.25 to 0.5)
            centers.add(0.75 to 0.5)
        }
        4 -> {
            centers.add(0.25 to 0.25)
            centers.add(0.25 to 0.75)
            centers.add(0.75 to 0.25)
            centers.add(0.75 to 0.75)
        }
        8 -> {
            centers.add(0.125 to 0.25)
            centers.add(0.125 to 0.75)
            centers.add(0.375 to 0.25)
            centers.add(0.375 to 0.75)
            centers.add(0.625 to 0.25)
            centers.add(0.625 to 0.75)
            centers.add(0.875 to 0.25)
            centers.add(0.875 to 0.75)
        }
        else -> throw IllegalArgumentException("unsupported depot count $depotCount")
    }
    // scale and translate normalized centers to rectangle centers
    for (i in centers.indices)
        centers[i] = ((xMax - xMin) * centers[i].first + xMin) to ((yMax - yMin) * centers[i].second + yMin)
    // compute capacity
    val capacity = primDijkstraMST(graph.finalize()) / (5 * depotCount)
    // assign depots to vertices closest to rectangle centers
    for (center in centers) {
        val vertex = graph.vertices.minByOrNull {
            val dx = center.first - it.x
            val dy = center.second - it.y
            dx * dx + dy * dy
        }!!
        // ensure no vertex is closest to two centers
        if (vertex.isDepot) throw IllegalStateException("unknown depot assigning scenario")
        vertex.capacity = capacity
    }
    // modify graph name to include depot count
    graph.name += "_$depotCount"
}