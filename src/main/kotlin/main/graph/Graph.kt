package main.graph

/**
 * Graph with immutable structure but mutable fields associated to vertices and edges.
 * The structure is immutable but fields used by algorithms might be mutable and have to be reset before use.
 */
class Graph(
    val name: String,
    val vertices: List<Vertex>,
    val depots: List<Depot>,
    val customers: List<Customer>,
    val edges: List<Edge>,
    val arcs: List<Arc>
) {

    // preprocessing data
    private val arcIncluded = Array(arcs.size) { BooleanArray(depots.size) { true } }
    private val customerIncluded = Array(customers.size) { BooleanArray(depots.size) { true } }

    /** Marks an arc [arc] to be excluded for the subtree of [depot]. */
    fun excludeArc(arc: Arc, depot: Depot) {
        arcIncluded[arc.arcId][depot.depotId] = false
    }

    /** Marks a customer [customer] to be excluded for the subtree of [depot]. */
    fun excludeCustomer(customer: Customer, depot: Depot) {
        customerIncluded[customer.customerId][depot.depotId] = false
    }

    /** Indicates whether the arc [arc] is excluded for [depot]. */
    fun includesArc(arc: Arc, depot: Depot) = arcIncluded[arc.arcId][depot.depotId]

    /** Indicates whether the edge [edge] is excluded for [depot]. */
    fun includesEdge(edge: Edge, depot: Depot) = edge.arcs.any { includesArc(it, depot) }

    /** Indicates whether the customer [customer] is excluded for [depot]. */
    fun includesCustomer(customer: Customer, depot: Depot) = customerIncluded[customer.customerId][depot.depotId]

    /** Returns the amount of arcs times depots minus the amount of excluded arcs for the depots. */
    fun includedArcCount() = arcIncluded.sumOf { it.count { it } }

    /** Creates a [MutableGraph] copy of this [Graph] that has the same structure and depots. */
    fun makeMutableCopy(): MutableGraph {
        val mutableGraph = MutableGraph(name)
        val vertexMap = HashMap<Vertex, MutableVertex>()
        vertices.forEach {
            val vertex = mutableGraph.addVertex(it.x, it.y)
            vertexMap[it] = vertex
            if (it is Depot) vertex.capacity = it.capacity
        }
        edges.forEach { mutableGraph.addEdge(vertexMap[it.endpoints[0]]!!, vertexMap[it.endpoints[1]]!!, it.weight) }
        return mutableGraph
    }

}

/**
 * Vertex of [Graph] which can be one of [Customer] or [Depot].
 */
sealed class Vertex(val vertexId: Int, val x: Double, val y: Double) {

    /** List of edges adjacent to this vertex. */
    val adj: List<Edge> = ArrayList()

    /** List of arcs entering this vertex. */
    val adjIn: List<Arc> = ArrayList()

    /** List of arcs leaving this vertex. */
    val adjOut: List<Arc> = ArrayList()

    // fields used in algorithms on this graph
    var visited = false
    var associatedDepotIndex = -1

}

/**
 * Represents a depot vertex with a capacity.
 */
class Depot(val depotId: Int, val capacity: Double, vertexId: Int, x: Double, y: Double) : Vertex(vertexId, x, y) {

    override fun toString() = "v($x, $y) d$depotId|$vertexId c$capacity"

}

/**
 * Represents a customer vertex.
 */
class Customer(val customerId: Int, vertexId: Int, x: Double, y: Double) : Vertex(vertexId, x, y) {

    override fun toString() = "v($x, $y) c$customerId|$vertexId"

}

/**
 * Represents an edge in [Graph] with two [Vertex] endpoints and a weight.
 */
class Edge(val edgeId: Int, val endpoints: List<Vertex>, val weight: Int) {

    /** List of arcs (i, j) and (j, i) associated to this edge {i, j} */
    val arcs = listOf(Arc(2 * edgeId, this, endpoints[0], endpoints[1]), Arc(2 * edgeId + 1, this, endpoints[1], endpoints[0])).also {
        it[0].opposite = it[1]
        it[1].opposite = it[0]
    }

    // fields used in algorithms on this graph
    var inMST = false
    var associatedDepotIndex = -1

    override fun toString() = "e{${endpoints[0].vertexId}, ${endpoints[1].vertexId}}"

}

/**
 * Represents a directed arc associated to an [Edge].
 */
class Arc(val arcId: Int, val edge: Edge, val s: Vertex, val t: Vertex) {

    /** Returns (t, s) for this arc (s, t). */
    lateinit var opposite: Arc

    operator fun component1() = s
    operator fun component2() = t

    override fun toString() = "a(${s.vertexId}, ${t.vertexId})"

}