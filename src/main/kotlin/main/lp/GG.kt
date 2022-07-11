package main.lp

import gurobi.GRB
import main.*
import main.graph.Customer
import main.graph.Graph
import main.util.*
import kotlin.math.min

/**
 * Relaxation of the decomposed Gavish-Graves MCRFPP formulation.
 */
class GGRelaxation(private val graph: Graph) : Relaxation() {

    override fun solve(plot: PlotOption) {
        makeGurobiEnv {
            // use simplex on the primal program
            set(GRB.IntParam.Method, GRB.METHOD_PRIMAL)

            makeModel {

                profile("minit")

                // create variables
                val y = graph.vertices.map { i ->
                    graph.depots.map { d ->
                        if (i !is Customer || graph.includesCustomer(i, d))
                            addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "y_{${i.vertexId}}^{${d.depotId}}")
                        else null
                    }
                }
                val rayVariables = listOf("\\alpha", "\\beta").map { name ->
                    graph.arcs.map { a ->
                        graph.depots.map { d ->
                            if (graph.includesArc(a, d))
                                addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "${name}_{${a.s.vertexId},${a.t.vertexId}}^{${d.depotId}}")
                            else null
                        }
                    }
                }
                val alpha = rayVariables[0]
                val beta = rayVariables[1]

                // create objective
                setObjective(graph.depots.termSumOf { d -> graph.customers.termSumOf { i ->
                    1.0 * y[i.vertexId][d.depotId]
                } }, GRB.MAXIMIZE)

                // create constraints

                // disjoint-trees
                for (i in graph.vertices) {
                    addConstr(graph.depots.termSumOf { d ->
                        1.0 * y[i.vertexId][d.depotId]
                    }, GRB.LESS_EQUAL, 1.0, "disjoint-trees_{${i.vertexId}}")
                }

                // depot-inclusion
                for (d in graph.depots) {
                    addConstr(1.0 * y[d.vertexId][d.depotId], GRB.EQUAL, 1.0, "depot-inclusion_{${d.depotId}}")
                }

                // indegree-customer

                for (j in graph.customers) for (d in graph.depots) if (graph.includesCustomer(j, d)) {
                    addConstr(j.adjIn.termSumOf { a ->
                        1.0 * alpha[a.arcId][d.depotId] + 1.0 * beta[a.arcId][d.depotId]
                    }, GRB.EQUAL, 1.0 * y[j.vertexId][d.depotId], "indegree-customer_{${j.vertexId},${d.depotId}}")
                }

                // indegree-depot

                for (dp in graph.depots) for (d in graph.depots) {
                    addConstr(dp.adjIn.termSumOf { a ->
                        1.0 * alpha[a.arcId][d.depotId] + 1.0 * beta[a.arcId][d.depotId]
                    }, GRB.EQUAL, 0.0, "indegree-depot_{${dp.vertexId},${d.depotId}}")
                }

                // flow-conservation

                for (j in graph.customers) for (d in graph.depots) if (graph.includesCustomer(j, d)) {
                    addConstr(j.adjIn.termSumOf { a ->
                        1.0 * alpha[a.arcId][d.depotId] + graph.customers.size.toDouble() * beta[a.arcId][d.depotId]
                    } + j.adjOut.termSumOf { a ->
                        -1.0 * alpha[a.arcId][d.depotId] + -graph.customers.size.toDouble() * beta[a.arcId][d.depotId]
                    }, GRB.EQUAL, 1.0 * y[j.vertexId][d.depotId], "flow-conservation_{${j.vertexId},${d.depotId}}")
                }

                // depot-flow

                for (d in graph.depots) {
                    addConstr(d.adjOut.termSumOf { a ->
                        1.0 * alpha[a.arcId][d.depotId] + graph.customers.size.toDouble() * beta[a.arcId][d.depotId]
                    }, GRB.EQUAL, graph.customers.termSumOf { i ->
                        1.0 * y[i.vertexId][d.depotId]
                    }, "depot-flow_{${d.depotId}}")
                }

                // capacity

                for (d in graph.depots) {
                    addConstr(graph.arcs.termSumOf { a ->
                        a.edge.weight.toDouble() * alpha[a.arcId][d.depotId] + a.edge.weight.toDouble() * beta[a.arcId][d.depotId]
                    }, GRB.LESS_EQUAL, d.capacity, "capacity_{${d.depotId}}")
                }

                profile("grb")

                optimize()

                reportResult(objectiveValue)

                if (plot != PlotOption.NO_PLOT) {
                    // create a plot of the resulting fractional solution
                    graph.show(
                        vertexColoring = {
                            if (it is Customer) {
                                val utilization = min(1.0, graph.depots.sumOf { d ->
                                    if (graph.includesCustomer(it, d)) getVarByName("y_{${it.vertexId}}^{${d.depotId}}").value
                                    else 0.0
                                })
                                if (utilization <= 0.0) UNASSIGNED_CUSTOMER_COLOR
                                else colorInterpolate(PARTIALLY_ASSIGNED_CUSTOMER_COLOR, COMPLETELY_ASSIGNED_CUSTOMER_COLOR, utilization)
                            } else {
                                DEPOT_COLOR
                            }
                        },
                        edgeColoring = {
                            val utilization = min(1.0, it.arcs.sumOf { a -> graph.depots.sumOf { d ->
                                if (graph.includesArc(a, d)) getVarByName("\\alpha_{${a.s.vertexId},${a.t.vertexId}}^{${d.depotId}}").value + getVarByName("\\beta_{${a.s.vertexId},${a.t.vertexId}}^{${d.depotId}}").value
                                else 0.0
                            } })
                            if (utilization <= 0.0 && plot == PlotOption.PLOT_DETAILED) UNASSIGNED_EDGE_COLOR
                            else if (utilization > 0.0) colorInterpolate(PARTIALLY_ASSIGNED_EDGE_COLOR, COMPLETELY_ASSIGNED_EDGE_COLOR, utilization)
                            else null
                        },
                        name = "${graph.name} fractional gg solution"
                    )
                }
            }
        }
    }

}