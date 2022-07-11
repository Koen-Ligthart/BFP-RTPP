package main.graph

/**
 * Represents a graph with structure that can be modified.
 */
class MutableGraph(var name: String) {

    val vertices: List<MutableVertex> = ArrayList()
    val edges: List<MutableEdge> = ArrayList()

    /** Adds a vertex with the given coordinates to the graph. */
    fun addVertex(x: Double = 0.0, y: Double = 0.0): MutableVertex {
        val vertex = MutableVertex(this, x, y)
        (vertices as MutableList).add(vertex)
        return vertex
    }

    /** Adds an edge between [a] and [b] with the given weight. */
    fun addEdge(a: MutableVertex, b: MutableVertex, weight: Int): MutableEdge {
        if (a == b) throw IllegalStateException("no self loops")
        val edge = MutableEdge(this, a, b, weight)
        addEdge(edge)
        return edge
    }

    // adds edge object to edge list and adjacency lists
    private fun addEdge(edge: MutableEdge) {
        edge.a.adj.add(edge to edge.b)
        edge.b.adj.add(edge to edge.a)
        (edges as MutableList).add(edge)
    }

    /**
     * Makes a copy of this graph with the same structure.
     * Copies all vertices and edges and connects them in the same way as they are connected in this graph.
     */
    fun clone(): MutableGraph {
        val clone = MutableGraph(name)
        val vertexMap = HashMap<MutableVertex, MutableVertex>()
        vertices.forEach {
            val vertexClone = MutableVertex(clone, it.x, it.y)
            vertexClone.capacity = it.capacity
            (clone.vertices as MutableList).add(vertexClone)
            vertexMap[it] = vertexClone
        }
        edges.forEach { clone.addEdge(MutableEdge(clone, vertexMap[it.a]!!, vertexMap[it.b]!!, it.weight)) }
        return clone
    }

    /** Makes a [Graph] copy of this [MutableGraph] of which the structure is immutable. */
    fun finalize(): Graph {
        // initialize fields
        val vertices = ArrayList<Vertex>()
        val depots = ArrayList<Depot>()
        val customers = ArrayList<Customer>()
        val edges = ArrayList<Edge>()
        val arcs = ArrayList<Arc>()
        val vertexMap = HashMap<MutableVertex, Vertex>()
        // copy vertices and assign them the correct types
        this.vertices.forEach {
            val vertex = if (it.capacity.isNaN()) {
                val customer = Customer(customers.size, vertices.size, it.x, it.y)
                customers.add(customer)
                customer
            } else {
                val depot = Depot(depots.size, it.capacity, vertices.size, it.x, it.y)
                depots.add(depot)
                depot
            }
            vertices.add(vertex)
            vertexMap[it] = vertex
        }
        // copy edges and create arcs
        this.edges.forEach {
            val a = vertexMap[it.a]!!
            val b = vertexMap[it.b]!!
            val edge = Edge(edges.size, listOf(a, b), it.weight)
            edges.add(edge)
            arcs.addAll(edge.arcs)
            edge.arcs.forEach { arc ->
                (arc.s.adjOut as MutableList<Arc>).add(arc)
                (arc.t.adjIn as MutableList<Arc>).add(arc)
                (arc.s.adj as MutableList<Edge>).add(edge)
            }
        }
        return Graph(name, vertices, depots, customers, edges, arcs)
    }

}

/**
 * Vertex of [MutableGraph] that can be removed and which capacity can be assigned (marking it as a depot).
 */
class MutableVertex(private val graph: MutableGraph, val x: Double, val y: Double) {

    /** List of adjacent edges with corresponding other endpoints. */
    val adj = ArrayList<Pair<MutableEdge, MutableVertex>>()

    /** The capacity of this depot or NaN if this vertex represents a customer. */
    var capacity = Double.NaN

    val isDepot: Boolean
        get() = !capacity.isNaN()

    /** Removes this vertex from its [MutableGraph] removing all adjacent edges. */
    fun remove() {
        adj.map { it.first }.forEach { it.remove() }
        (graph.vertices as MutableList).remove(this)
    }

}

/**
 * Edge of [MutableGraph] that can be removed.
 */
class MutableEdge(private val graph: MutableGraph, val a: MutableVertex, val b: MutableVertex, var weight: Int) {

    /** Removes this edge from its [MutableGraph] */
    fun remove() {
        a.adj.removeIf { it.first == this }
        b.adj.removeIf { it.first == this }
        (graph.edges as MutableList).remove(this)
    }

}