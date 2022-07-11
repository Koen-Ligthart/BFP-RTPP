package main.lp

import gurobi.GRB
import main.*
import main.graph.Customer
import main.graph.Graph
import main.util.*
import kotlin.math.min

/**
 * Relaxation of the Multi-Commodity Flow Gavish-Graves MCRFPP formulation.
 */
class MCFGGRelaxation(private val graph: Graph) : Relaxation() {

    override fun solve(plot: PlotOption) {
        makeGurobiEnv {
            // use barrier method with no crossover
            set(GRB.IntParam.Method, GRB.METHOD_BARRIER)
            set(GRB.IntParam.Crossover, 0)

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
                val x = graph.arcs.map { a ->
                    graph.depots.map { d ->
                        if (graph.includesArc(a, d))
                            addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "x_{${a.s.vertexId},${a.t.vertexId}}^{${d.depotId}}")
                        else null
                    }
                }
                val f = graph.arcs.map { a ->
                    graph.customers.map { c ->
                        graph.depots.map { d ->
                            if (graph.includesArc(a, d) && graph.includesCustomer(c, d))
                                addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "f_{${a.s.vertexId},${a.t.vertexId}}^{${c.customerId},${d.depotId}}")
                            else null
                        }
                    }
                }

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
                        1.0 * x[a.arcId][d.depotId]
                    }, GRB.EQUAL, 1.0 * y[j.vertexId][d.depotId], "indegree-customer_{${j.vertexId},${d.depotId}}")
                }

                // indegree-depot

                for (dp in graph.depots) for (d in graph.depots) {
                    addConstr(dp.adjIn.termSumOf { a ->
                        1.0 * x[a.arcId][d.depotId]
                    }, GRB.EQUAL, 0.0, "indegree-depot_{${dp.vertexId},${d.depotId}}")
                }

                // flow-lower-bound

                for (a in graph.arcs) for (d in graph.depots) {
                    if (graph.includesArc(a, d)) {
                        addConstr(1.0 * x[a.arcId][d.depotId], GRB.LESS_EQUAL, graph.customers.termSumOf { c ->
                            1.0 * f[a.arcId][c.customerId][d.depotId]
                        }, "flow-lower-bound_{${a.s.vertexId},${a.t.vertexId},${d.depotId}}")
                    }
                }

                // flow-upper-bound

                for (a in graph.arcs) for (c in graph.customers) for (d in graph.depots) {
                    if (graph.includesArc(a, d))
                        addConstr(1.0 * f[a.arcId][c.customerId][d.depotId], GRB.LESS_EQUAL, 1.0 * x[a.arcId][d.depotId], "flow-upper-bound_{${a.s.vertexId},${a.t.vertexId},${c.customerId},${d.depotId}}")
                }

                // flow-conservation

                for (j in graph.customers) for (c in graph.customers) for (d in graph.depots) if (graph.includesCustomer(c, d)) {
                    addConstr(j.adjIn.termSumOf { a ->
                        1.0 * f[a.arcId][c.customerId][d.depotId]
                    } + j.adjOut.termSumOf { a ->
                        -1.0 * f[a.arcId][c.customerId][d.depotId]
                    }, GRB.EQUAL, if (j == c) 1.0 * y[j.vertexId][d.depotId] else 0.0 * null, "flow-conservation_{${j.vertexId},${c.customerId},${d.depotId}}")
                }

                // depot-flow

                for (c in graph.customers) for (d in graph.depots) {
                    addConstr(d.adjOut.termSumOf { a ->
                        1.0 * f[a.arcId][c.customerId][d.depotId]
                    }, GRB.EQUAL, 1.0 * y[c.vertexId][d.depotId], "depot-flow_{${c.customerId},${d.depotId}}")
                }

                // capacity

                for (d in graph.depots) {
                    addConstr(graph.arcs.termSumOf { a ->
                        a.edge.weight.toDouble() * x[a.arcId][d.depotId]
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
                                if (graph.includesArc(a, d)) getVarByName("x_{${a.s.vertexId},${a.t.vertexId}}^{${d.depotId}}").value
                                else 0.0
                            } })
                            if (utilization <= 0.0 && plot == PlotOption.PLOT_DETAILED) UNASSIGNED_EDGE_COLOR
                            else if (utilization > 0.0) colorInterpolate(PARTIALLY_ASSIGNED_EDGE_COLOR, COMPLETELY_ASSIGNED_EDGE_COLOR, utilization)
                            else null
                        },
                        name = "${graph.name} fractional mcfgg solution"
                    )
                }
            }
        }
    }

}