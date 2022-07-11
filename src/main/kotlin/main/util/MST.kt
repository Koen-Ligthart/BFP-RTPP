package main.util

import main.graph.Edge
import main.graph.Graph
import main.graph.Vertex
import java.util.*

/**
 * Finds a MST of graph using Prim-Dijsktra algorithm.
 * Returns total tree weight and stores result in edge.inMST.
 */
fun primDijkstraMST(graph: Graph): Double {
    graph.edges.forEach { it.inMST = false }
    val queue = PriorityQueue<VertexWeight>()
    var totalWeight = 0.0
    // mark all vertices unvisited and add them to the queue with infinite weight
    for (vertex in graph.vertices) {
        vertex.visited = false
        queue.add(VertexWeight(vertex, null, Int.MAX_VALUE))
    }
    while (queue.isNotEmpty()) {
        // find vertex with the shortest distance to the tree
        val (vertex, edge, _) = queue.poll()
        if (vertex.visited) continue
        // add vertex to the tree
        vertex.visited = true
        if (edge != null) {
            edge.inMST = true
            totalWeight += edge.weight
        }
        // find adjacent nodes that are not in the tree
        for (arc in vertex.adjOut) {
            if (arc.t.visited) continue
            queue.add(VertexWeight(arc.t, arc.edge, arc.edge.weight))
        }
    }
    return totalWeight
}

// auxiliary class used for primDijkstraMST
private data class VertexWeight(val vertex: Vertex, val edge: Edge?, val weight: Int) : Comparable<VertexWeight> {
    override fun compareTo(other: VertexWeight) = weight.compareTo(other.weight)
}