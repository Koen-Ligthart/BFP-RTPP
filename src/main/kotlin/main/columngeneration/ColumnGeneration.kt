package main.columngeneration

import gurobi.*
import main.graph.Graph
import main.lp.PlotOption
import main.lp.Relaxation
import main.util.*

/**
 * Represents a linear program that can dynamically generate a part of its columns.
 */
abstract class CGRelaxation<T : Column>(private val graph: Graph, private val objectiveSense: Int, val terminationCriterion: (Double) -> Boolean) : Relaxation() {
    lateinit var grb: GRBModel
    var columnNumber = 0
    val columns = mutableMapOf<Int, T>()
    abstract fun generateRowsAndInitialColumns(): Set<T>
    abstract fun solvePricingProblem(): Set<T>
    open fun removeColumns(): Set<T> = emptySet()
    abstract fun plot(plot: PlotOption)

    override fun solve(plot: PlotOption) {
        makeGurobiEnv {
            // use simplex on the primal RMP
            set(GRB.IntParam.Method, GRB.METHOD_PRIMAL)

            makeModel {
                grb = this
                // initialize model

                profile("minit")

                setObjective(TermSum(), objectiveSense)
                for (column in generateRowsAndInitialColumns()) {
                    column.createGrbVar(this)
                    columns[column.id] = column
                }

                val progressData = ArrayList<Pair<Double, Double>>()

                // add / remove columns until optimal
                while (true) {
                    profile("grb")
                    optimize()
                    profile("pr")
                    progressData.add(columnNumber.toDouble() to objectiveValue)

                    // add columns
                    val newColumns = solvePricingProblem()
                    if (newColumns.isEmpty() || terminationCriterion(objectiveValue)) {
                        reportResult(objectiveValue)
                        if (plot != PlotOption.NO_PLOT) {
                            plot(plot)
                            if (plot == PlotOption.PLOT_DETAILED) {
                                Plotter.openAndSavePlot(Plotter.plotSeries(progressData, "Iteration", "Objective"), "${graph.name} cg progress")
                            }
                        }
                        break
                    }
                    for (column in newColumns) {
                        column.createGrbVar(this)
                        columns[column.id] = column
                    }
                    // remove columns
                    val excessColumns = removeColumns()
                    for (column in excessColumns) {
                        grb.remove(column.grbVar)
                        columns.remove(column.id)
                    }
                }
            }
        }
    }
}

/**
 * Represents a variable with corresponding column coefficients for a [CGRelaxation].
 */
open class Column(relaxation: CGRelaxation<*>, var obj: Double = 0.0, private val lb: Double = 0.0, private val ub: Double = Double.POSITIVE_INFINITY) {
    val id: Int = relaxation.columnNumber++
    var grbVar: GRBVar? = null
    private val map = mutableMapOf<GRBConstr, Double>()
    fun addTerm(coefficient: Double, constr: GRBConstr) {
        val value = map[constr]
        if (value == null)
            map[constr] = coefficient
        else
            map[constr] = value + coefficient
    }
    // creates an actual GRBVar for the given model and adds it to the program
    fun createGrbVar(model: GRBModel) {
        val column = GRBColumn()
        for ((constr, coefficient) in map)
            column.addTerm(coefficient, constr)
        grbVar = model.addVar(lb, ub, obj, GRB.CONTINUOUS, column, "c$id")
    }
}