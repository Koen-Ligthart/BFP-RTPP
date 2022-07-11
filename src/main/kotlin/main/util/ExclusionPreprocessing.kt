package main.util

import main.EPSILON_WEIGHTS
import main.graph.*
import java.util.*

/** Exclude arc depot pairs from [graph] based on triangle exclusion rule. */
fun excludeTriangle(graph: Graph) {
    graph.depots.forEach { d ->
        d.adjOut.forEach { dj ->
            dj.t.adjIn.forEach { ij ->
                if (ij.edge.weight > dj.edge.weight) {
                    graph.excludeArc(ij, d)
                }
            }
        }
    }
}

/** Exclude arc depot and customer depot pairs from [graph] using Dijkstra exclusion rule. */
fun excludeDijkstra(graph: Graph) {
    graph.depots.forEach { depot ->
        graph.vertices.forEach { it.visited = false }
        // distArray[i] = the shortest distance to vertex i
        val distArray = IntArray(graph.vertices.size) { if (it == depot.vertexId) 0 else (Int.MAX_VALUE / 2) }
        // perform dijkstra up to distance capacity and act if other depots do not exist
        val queue = PriorityQueue<VertexDist>()
        // start Dijkstra at depot
        queue.add(VertexDist(0, depot))
        while (queue.isNotEmpty()) {
            val (dist, target) = queue.poll()
            if (distArray[target.vertexId] < dist) continue
            for (arc in target.adjOut) {
                val newDist = arc.edge.weight + dist
                if (arc.t !is Depot && newDist < distArray[arc.t.vertexId] && newDist <= depot.capacity + EPSILON_WEIGHTS) {
                    distArray[arc.t.vertexId] = newDist
                    arc.t.visited = true
                    queue.add(VertexDist(newDist, arc.t))
                }
            }
        }
        // remove arcs that cannot be reached, if an arc was to be reached, there must be a path from the source
        graph.arcs.forEach { arc ->
            if (arc.edge.weight + distArray[arc.s.vertexId] > depot.capacity + EPSILON_WEIGHTS)
                graph.excludeArc(arc, depot)
        }
        // remove unreachable vertices
        graph.customers.filter { c -> c.adjIn.none { graph.includesArc(it, depot) } }.forEach { graph.excludeCustomer(it, depot) }
    }
}

// auxiliary class used in [excludeDijkstra]
private data class VertexDist(val dist: Int, val target: Vertex) : Comparable<VertexDist> {
    override fun compareTo(other: VertexDist) = dist.compareTo(other.dist)
}