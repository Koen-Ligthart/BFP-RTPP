package main.columngeneration

import gurobi.GRB
import gurobi.GRBConstr
import main.*
import main.graph.*
import main.lp.PlotOption
import main.util.*
import java.util.*

/**
 * Column generation model using spanning trees as columns.
 */
class TMRelaxation(graph: Graph, terminationCriterion: (Double) -> Boolean) : CGRelaxation<TColumn>(graph, GRB.MAXIMIZE, terminationCriterion) {

    private val tgraph: Graph
    private val d0: Depot
    init {
        val tgraphMutable = graph.makeMutableCopy()
        // create related graph by adding dummy node and connecting it to all customers
        val d0Mutable = tgraphMutable.addVertex()
        d0Mutable.capacity = Double.NEGATIVE_INFINITY
        tgraphMutable.vertices.filter { !it.isDepot }.forEach { tgraphMutable.addEdge(d0Mutable, it, 0) }
        tgraph = tgraphMutable.finalize()
        // copy exclusion information
        graph.depots.indices.forEach { d ->
            graph.arcs.indices.forEach { a ->
                if (!graph.includesArc(graph.arcs[a], graph.depots[d]))
                    tgraph.excludeArc(tgraph.arcs[a], tgraph.depots[d])
            }
            graph.customers.indices.forEach { c ->
                if (!graph.includesCustomer(graph.customers[c], graph.depots[d]))
                    tgraph.excludeCustomer(tgraph.customers[c], tgraph.depots[d])
            }
        }
        d0 = tgraph.depots.first { it.capacity == Double.NEGATIVE_INFINITY }
        if (d0 != tgraph.vertices[tgraph.vertices.size - 1]) throw IllegalStateException()
    }

    private lateinit var constrVEL1: Array<Array<Array<GRBConstr?>>> // [e in _E][e.a/e.b][d in _D]
    private lateinit var constrVEL2: Array<Array<Array<GRBConstr?>>> // [e in _E][e.a/e.b][d in _D]
    private lateinit var constrELUB: Array<GRBConstr> // [e in _E]
    private lateinit var constrDIncl: Array<Array<GRBConstr>> // [d' in _D][d in _D]
    private lateinit var constrWeight: Array<GRBConstr?> // [d in D] (null at depot 0)
    private lateinit var constrLSum: GRBConstr

    override fun generateRowsAndInitialColumns(): Set<TColumn> {

        // create variables
        val x = tgraph.edges.map { e ->
            tgraph.depots.map { d ->
                if (tgraph.includesEdge(e, d))
                    grb.addVar(0.0, Double.POSITIVE_INFINITY, if (d.depotId == d0.depotId) 0.0 else 1.0, GRB.CONTINUOUS, "x_{${e.edgeId}}^{${d.depotId}}")
                else null
            }
        }
        val y = tgraph.vertices.map { i ->
            tgraph.depots.map { d ->
                if (i !is Customer || tgraph.includesCustomer(i, d))
                    grb.addVar(0.0, 1.0, 0.0, GRB.CONTINUOUS, "y_{${i.vertexId}}^{${d.depotId}}")
                else null
            }
        }

        // create constraints

        // vertex-edge-label-1
        constrVEL1 = Array(tgraph.edges.size) { e ->
            val edge = tgraph.edges[e]
            Array(edge.endpoints.size) { i ->
                Array(tgraph.depots.size) { d ->
                    val vertex = edge.endpoints[i]
                    if (vertex !is Customer || tgraph.includesCustomer(vertex, tgraph.depots[d]))
                        grb.addConstr(-1.0 * x[e][d] + 1.0 * y[vertex.vertexId][d], GRB.LESS_EQUAL, 1.0, "vertex-edge-label-1_{$e,$i,$d}")
                    else null
                }
            }
        }

        // vertex-edge-label-2
        constrVEL2 = Array(tgraph.edges.size) { e ->
            val edge = tgraph.edges[e]
            Array(edge.endpoints.size) { i ->
                Array(tgraph.depots.size) { d ->
                    if (tgraph.includesEdge(edge, tgraph.depots[d]))
                        grb.addConstr(1.0 * x[e][d] + -1.0 * y[edge.endpoints[i].vertexId][d], GRB.LESS_EQUAL, 1.0, "vertex-edge-label-2_{$e,$i,$d}")
                    else null
                }
            }
        }

        // edge-label-upper-bound
        constrELUB = Array(tgraph.edges.size) { e ->
            grb.addConstr(tgraph.depots.indices.termSumOf { d ->
                1.0 * x[e][d]
            }, GRB.EQUAL, 0.0, "edge-label-upper-bound_{$e}")
        }

        // depot-inclusion
        constrDIncl = Array(tgraph.depots.size) { dp ->
            Array(tgraph.depots.size) { d ->
                val dpVId = tgraph.depots[dp].vertexId
                grb.addConstr(1.0 * y[dpVId][d], GRB.EQUAL, if (dp == d) 1.0 else 0.0, "depot-inclusion_{$dpVId,$d}")
            }
        }

        // weight
        constrWeight = Array(tgraph.depots.size) { d ->
            if (d == d0.depotId) {
                null
            } else {
                grb.addConstr(tgraph.edges.indices.termSumOf { e ->
                    tgraph.edges[e].weight.toDouble() * x[e][d]
                }, GRB.LESS_EQUAL, tgraph.depots[d].capacity, "weight_{$d}")
            }
        }

        // lambda-sum
        constrLSum = grb.addConstr(TermSum(), GRB.EQUAL, 1.0, "lambda-sum")

        // add lambda_t for tree that contains all edges {0, i} with i in C
        val emptyColumn = TColumn(this)
        d0.adj.forEach { emptyColumn.addEdge(it) }
        emptyColumn.addTerm(1.0, constrLSum)
        return setOf(emptyColumn)
    }

    override fun solvePricingProblem(): Set<TColumn> {

        // extract dual values
        val alpha = Array(tgraph.edges.size) { e ->
            val edge = tgraph.edges[e]
            Array(edge.endpoints.size) { i ->
                Array(tgraph.depots.size) { d ->
                    constrVEL1[e][i][d]?.dualValue
                }
            }
        }

        val beta = Array(tgraph.edges.size) { e ->
            val edge = tgraph.edges[e]
            Array(edge.endpoints.size) { i ->
                Array(tgraph.depots.size) { d ->
                    constrVEL2[e][i][d]?.dualValue
                }
            }
        }

        val gamma = Array(tgraph.edges.size) { e ->
            constrELUB[e].dualValue
        }

        val zeta = constrLSum.dualValue

        val column = TColumn(this)

        // compute weights of each edge in G
        val weights = Array(tgraph.edges.size) { e ->
            val weight = tgraph.edges[e].endpoints.indices.sumOf { i ->
                tgraph.depots.indices.sumOf { d ->
                    (alpha[e][i][d] ?: 0.0) + (beta[e][i][d] ?: 0.0)
                }
            }
            weight - gamma[e]
        }

        // find minimum spanning tree in G (starting from super node s and forcibly including \delta'(s)) using modified Prim Dijkstra
        var totalScore = 0.0
        val queue = PriorityQueue<DoubleVertexWeight>()
        tgraph.vertices.forEach { it.visited = false }
        // forcibly connect depots to super node (through null edges)
        tgraph.depots.forEach { queue.add(DoubleVertexWeight(it, null, Double.NEGATIVE_INFINITY)) }
        while (queue.isNotEmpty()) {
            val (vertex, edge, weight) = queue.poll()
            if (vertex.visited) continue
            vertex.visited = true
            if (edge != null) {
                // add coefficients for edge
                column.addEdge(edge)
                totalScore += weight
            }
            for (arc in vertex.adjOut) {
                if (arc.t.visited) continue
                val newWeight = weights[arc.edge.edgeId]
                queue.add(DoubleVertexWeight(arc.t, arc.edge, newWeight))
            }
        }
        // check for constraint violation
        println("pricing problem: $totalScore >= -$zeta? gap: ${-zeta - totalScore}")
        if (totalScore >= -zeta - EPSILON_PRICING_PROBLEM)
            return emptySet()

        // add coefficient for lambda sum
        column.addTerm(1.0, constrLSum)

        return setOf(column)
    }

    private fun TColumn.addEdge(edge: Edge) {
        val e = edge.edgeId
        // add coefficients for \1_{e\in t}
        tgraph.depots.indices.forEach { d ->
            edge.endpoints.indices.forEach { index ->
                constrVEL1[e][index][d]?.also { addTerm(1.0, it) }
                constrVEL2[e][index][d]?.also { addTerm(1.0, it) }
            }
        }
        addTerm(-1.0, constrELUB[e])

        edges.set(e)
    }

    override fun plot(plot: PlotOption) {
        if (plot != PlotOption.PLOT_DETAILED) throw IllegalStateException("option $plot unsupported")

        // obtain values from relaxation solution
        val edges = DoubleArray(tgraph.edges.size)
        for (column in columns.values) {
            val value = column.grbVar!!.value
            column.edges.stream().forEach { edges[it] += value }
        }

        // create a plot showing superposition of spanning trees over T
        tgraph.show(vertexColoring = {
            if (it is Depot) DEPOT_COLOR else null
        }, edgeColoring = {
            val v = edges[it.edgeId]
            if (v == 0.0) UNASSIGNED_EDGE_COLOR else colorInterpolate(PARTIALLY_ASSIGNED_EDGE_COLOR, COMPLETELY_ASSIGNED_EDGE_COLOR, v)
        }, name = "${tgraph.name} tm spanning tree superposition")

        // create a plot of the resulting fractional solution
        tgraph.show(vertexColoring = { i ->
            if (i is Customer) {
                val utilization = minOf(tgraph.depots.sumOf { d -> if (d == d0 || !tgraph.includesCustomer(i, d)) 0.0 else grb.getVarByName("y_{${i.vertexId}}^{${d.depotId}}").value }, 1.0)
                if (utilization == 0.0) UNASSIGNED_CUSTOMER_COLOR
                else colorInterpolate(PARTIALLY_ASSIGNED_CUSTOMER_COLOR, COMPLETELY_ASSIGNED_CUSTOMER_COLOR, utilization)
            } else {
                if (i == d0) null else DEPOT_COLOR
            }
        }, edgeColoring = { e ->
            val utilization = tgraph.depots.sumOf { d -> if (d == d0 || !tgraph.includesEdge(e, d)) 0.0 else grb.getVarByName("x_{${e.edgeId}}^{${d.depotId}}").value }
            if (!e.endpoints.all { it != d0 }) null
            else if (utilization == 0.0) UNASSIGNED_EDGE_COLOR
            else colorInterpolate(PARTIALLY_ASSIGNED_EDGE_COLOR, COMPLETELY_ASSIGNED_EDGE_COLOR, utilization)
        }, name = "${tgraph.name} fractional tm solution")
    }

}

/**
 * Column corresponding to one entry of T, which is defined by [edges].
 */
class TColumn(relaxation: TMRelaxation) : Column(relaxation) {
    val edges = BitSet()
    fun hasEdge(e: Int) = edges[e]
}

// auxiliary class used for TM pricing problem
private data class DoubleVertexWeight(val vertex: Vertex, val edge: Edge?, val weight: Double) : Comparable<DoubleVertexWeight> {
    override fun compareTo(other: DoubleVertexWeight) = weight.compareTo(other.weight)
}