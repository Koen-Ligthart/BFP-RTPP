package main.columngeneration

import gurobi.GRB
import gurobi.GRBConstr
import main.*
import main.graph.*
import main.heuristic.greedyHeuristicInit
import main.lp.PlotOption
import main.util.*
import java.util.*

/**
 * Column generation model using subforests and customer-depot assignment.
 */
class HMRelaxation(val graph: Graph, terminationCriterion: (Double) -> Boolean) : CGRelaxation<HColumn>(graph, GRB.MAXIMIZE, terminationCriterion) {

    private lateinit var constrVIncl1: Array<Array<GRBConstr?>> // [i in C][d in D]
    private lateinit var constrVIncl2: Array<Array<Array<GRBConstr?>?>> // [e in E][e.a/e.b][d in D]
    private lateinit var constrWeight: Array<GRBConstr> // [d in D]
    private lateinit var constrEVCount: GRBConstr
    private lateinit var constrLSum: GRBConstr

    override fun generateRowsAndInitialColumns(): Set<HColumn> {

        // create constraints

        // vertex-inclusion-1
        constrVIncl1 = Array(graph.customers.size) { i ->
            Array(graph.depots.size) { d ->
                if (graph.includesCustomer(graph.customers[i], graph.depots[d]))
                    grb.addConstr(TermSum(), GRB.LESS_EQUAL, 0.0, "vertex-inclusion-1_{$i,$d}")
                else null
            }
        }

        // vertex-inclusion-2
        constrVIncl2 = Array(graph.edges.size) { e ->
            val edge = graph.edges[e]
            Array(edge.endpoints.size) { i ->
                val vertex = edge.endpoints[i]
                if (vertex is Customer) {
                    Array(graph.depots.size) { d ->
                        if (graph.includesEdge(edge, graph.depots[d]))
                            grb.addConstr(TermSum(), GRB.LESS_EQUAL, 0.0, "vertex-inclusion-2_{$e,$i,$d}")
                        else null
                    }
                } else null
            }
        }

        // weight
        constrWeight = Array(graph.depots.size) { d ->
            grb.addConstr(TermSum(), GRB.LESS_EQUAL, graph.depots[d].capacity, "weight_{$d}")
        }

        // edge-vertex-count
        constrEVCount = grb.addConstr(TermSum(), GRB.EQUAL, 0.0, "edge-vertex-count")

        // lambda-sum
        constrLSum = grb.addConstr(TermSum(), GRB.LESS_EQUAL, 1.0, "lambda-sum")

        // add column corresponding with greedy solution
        val greedyColumn = HColumn(this)
        greedyHeuristicInit(graph)
        graph.customers.filter { it.associatedDepotIndex >= 0 }.forEach { greedyColumn.addCustomer(it.customerId, it.associatedDepotIndex) }
        graph.edges.filter { it.associatedDepotIndex >= 0 }.forEach { greedyColumn.addEdge(it, it.associatedDepotIndex) }
        greedyColumn.addTerm(1.0, constrLSum)
        return setOf(greedyColumn)
    }

    override fun solvePricingProblem(): Set<HColumn> {

        // extract dual values
        val alpha = Array(graph.customers.size) { i ->
            Array(graph.depots.size) { d ->
                constrVIncl1[i][d]?.dualValue
            }
        }

        val beta = Array(graph.edges.size) { e ->
            Array(graph.edges[e].endpoints.size) { i ->
                val constrArray = constrVIncl2[e][i]
                if (constrArray == null) {
                    null
                } else {
                    Array(graph.depots.size) { d ->
                        constrArray[d]?.dualValue
                    }
                }
            }
        }

        val gamma = Array(graph.depots.size) { d ->
            constrWeight[d].dualValue
        }

        val delta = constrEVCount.dualValue

        val epsilon = constrLSum.dualValue

        val column = HColumn(this)

        var totalScore = 0.0

        // select vertices i_{A(i)}
        graph.customers.indices.forEach { i ->
            // find minimal coefficient
            var bestDepot = -1
            var bestScore = 0.0
            graph.depots.indices.forEach { d ->
                if (graph.includesCustomer(graph.customers[i], graph.depots[d])) {
                    var score = alpha[i][d]!! + delta
                    score += -graph.customers[i].adj.sumOf { e -> beta[e.edgeId][e.endpoints.indexOf(graph.customers[i])]!![d] ?: 0.0 }
                    if (score < bestScore) {
                        bestScore = score
                        bestDepot = d
                    }
                }
            }

            column.addCustomer(i, bestDepot)

            totalScore += bestScore
        }

        // compute weights of each edge in H
        val weights = Array(graph.depots.size) { d ->
            Array(graph.edges.size) { e ->
                val edge = graph.edges[e]
                if (graph.includesEdge(edge, graph.depots[d]) && edge.endpoints.all { it !is Depot || it.depotId == d }) {
                    val weight = edge.endpoints.mapIndexed { index, i -> if (i is Customer) (-alpha[i.customerId][d]!! + beta[e][index]!![d]!!) else 0.0 }.sum()
                    weight + edge.weight * gamma[d] - delta - 1
                } else {
                    0.0 // ignored as this edge is not included in G_d
                }
            }
        }

        // find minimal weighted forest in each subtree G_d of H
        graph.depots.indices.forEach { d ->
            // filter on all edges that are on C\cap\{d\}, have weight less than 0 and sort decreasing on weight
            val localWeights = weights[d]
            val edges = graph.edges.filter { localWeights[it.edgeId] < 0 }.sortedBy { localWeights[it.edgeId] }
            // perform kruskal
            val unionFind = UnionFind(graph.customers.size + 1)
            for (edge in edges) {
                val aId = edge.endpoints[0].localId
                val bId = edge.endpoints[1].localId
                if (unionFind.repr(aId) != unionFind.repr(bId)) {
                    unionFind.union(aId, bId)

                    column.addEdge(edge, d)

                    totalScore += localWeights[edge.edgeId]
                }
            }
        }

        // check for constraint violation
        println("pricing problem: $totalScore >= -$epsilon? gap: ${-epsilon - totalScore}")
        if (totalScore >= -epsilon - EPSILON_PRICING_PROBLEM)
            return emptySet()

        // add coefficient for lambda sum
        column.addTerm(1.0, constrLSum)

        return setOf(column)
    }

    private fun HColumn.addCustomer(i: Int, d: Int) {
        // add coefficients for \1_{i_d\in f}
        if (d == -1) return
        addTerm(1.0, constrVIncl1[i][d]!!)
        graph.customers[i].adj.forEach { e ->
            constrVIncl2[e.edgeId][e.endpoints.indexOf(graph.customers[i])]!![d]?.also { addTerm(-1.0, it) }
        }
        addTerm(1.0, constrEVCount)

        customers.set(d * (graph.customers.size) + i)
    }

    private fun HColumn.addEdge(edge: Edge, d: Int) {
        // add coefficients for \1_{e_d\in f}
        edge.endpoints.forEachIndexed { index, i ->
            if (i is Customer) {
                addTerm(-1.0, constrVIncl1[i.customerId][d]!!)
                addTerm(1.0, constrVIncl2[edge.edgeId][index]!![d]!!)
            }
        }

        addTerm(edge.weight.toDouble(), constrWeight[d])
        addTerm(-1.0, constrEVCount)
        obj += 1.0

        edges.set(d * (graph.edges.size) + edge.edgeId)
    }

    private val Vertex.localId: Int
        inline get() = if (this is Customer) customerId + 1 else 0

    override fun plot(plot: PlotOption) {
        if (plot != PlotOption.PLOT_DETAILED) throw IllegalStateException("option $plot unsupported")

        // obtain values from relaxation solution
        val customers = DoubleArray(graph.customers.size)
        val edges = DoubleArray(graph.edges.size)
        for (variable in grb.vars) {
            val name = variable.name
            val value = variable.value
            if (name.startsWith("c") && value > 0) {
                val column = columns[name.substring(1).toInt()]!!
                column.customers.stream().forEach { customers[it % graph.customers.size] += value }
                column.edges.stream().forEach { edges[it % graph.edges.size] += value }
            }
        }

        // create a plot of the resulting fractional solution
        graph.show(vertexColoring = {
            if (it is Customer) {
                val utilization = customers[it.customerId]
                if (utilization == 0.0) UNASSIGNED_CUSTOMER_COLOR
                else colorInterpolate(PARTIALLY_ASSIGNED_CUSTOMER_COLOR, COMPLETELY_ASSIGNED_CUSTOMER_COLOR, utilization)
            } else DEPOT_COLOR
        }, edgeColoring = {
            val utilization = edges[it.edgeId]
            if (utilization == 0.0) UNASSIGNED_EDGE_COLOR
            else colorInterpolate(PARTIALLY_ASSIGNED_EDGE_COLOR, COMPLETELY_ASSIGNED_EDGE_COLOR, utilization)
        }, name = "${graph.name} fractional hm solution")
    }

}

/**
 * Column corresponding to one entry of F, which is defined by [customers] and [edges].
 */
class HColumn(private val relaxation: HMRelaxation) : Column(relaxation) {
    val customers = BitSet()
    val edges = BitSet()
    fun hasCustomer(i: Int, d: Int) = customers[d * relaxation.graph.customers.size + i]
    fun hasEdge(e: Int, d: Int) = edges[d * relaxation.graph.edges.size + e]
}