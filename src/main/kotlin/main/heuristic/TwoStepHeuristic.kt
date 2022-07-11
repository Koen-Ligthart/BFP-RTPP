package main.heuristic

import main.graph.Arc
import main.graph.Customer
import main.graph.Graph
import main.graph.Vertex
import java.util.*

/**
 * Replication of two-step heuristic method.
 */
fun computeTwoStepHeuristicSolution(graph: Graph): Int {
    greedyHeuristicInit(graph)
    // localSearch(graph)
    return graph.edges.count { it.associatedDepotIndex >= 0 }
}

// first step greedy initial solution construction
fun greedyHeuristicInit(graph: Graph) {
    val queue = PriorityQueue<ComparableArc>()
    // capacity remaining for each tree
    val remainingCapacity = DoubleArray(graph.depots.size)
    // mark elements unvisited
    graph.vertices.forEach { it.associatedDepotIndex = -1 }
    graph.edges.forEach { it.associatedDepotIndex = -1 }
    // initialize queue with singleton trees
    graph.depots.forEach {
        it.associatedDepotIndex = it.depotId
        remainingCapacity[it.depotId] = it.capacity
        queue.add(ComparableArc(null, it))
    }
    while (queue.isNotEmpty()) {
        // find arc with the shortest length
        val (arc, target) = queue.poll()
        if (target is Customer && target.associatedDepotIndex >= 0) continue
        if (arc != null) {
            // attempt to connect arc.t to tree of arc.s
            val newDepot = arc.s.associatedDepotIndex
            if (remainingCapacity[newDepot] < arc.edge.weight) // verify that tree has enough capacity
                continue
            remainingCapacity[newDepot] -= arc.edge.weight.toDouble()
            target.associatedDepotIndex = newDepot
            arc.edge.associatedDepotIndex = newDepot
        }
        // continue searching through adjacent arcs
        for (newArc in target.adjOut) {
            if (newArc.t.associatedDepotIndex >= 0) continue
            queue.add(ComparableArc(newArc, newArc.t))
        }
    }
}

// used in Prim-Dijkstra algorithm variant in [greedyInit]
data class ComparableArc(val arc: Arc?, val target: Vertex) : Comparable<ComparableArc> {
    override fun compareTo(other: ComparableArc): Int {
        val w1 = arc?.edge?.weight ?: Int.MIN_VALUE
        val w2 = other.arc?.edge?.weight ?: Int.MIN_VALUE
        return w1.compareTo(w2)
    }
}