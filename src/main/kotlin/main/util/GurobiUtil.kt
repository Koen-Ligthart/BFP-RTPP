package main.util

import gurobi.*

// contains many utility extension functions to make working with Gurobi in Kotlin easier

/** Variable name passed when creating this variable (using [GRBModel.addVar]). */
val GRBVar.name: String
    get() = get(GRB.StringAttr.VarName)

/** Assigned value to this variable (after [GRBModel.optimize]). */
val GRBVar.value: Double
    get() = get(GRB.DoubleAttr.X)

/** The optimal objective value of the model (after [GRBModel.optimize]). */
val GRBModel.objectiveValue: Double
    get() = get(GRB.DoubleAttr.ObjVal)

/** Value of the corresponding dual variable in the solution. */
val GRBConstr.dualValue: Double
    inline get() = get(GRB.DoubleAttr.Pi)

/** Creates a GRBEnv with a default log file and cleans up the environment after use. */
inline fun makeGurobiEnv(use: GRBEnv.() -> Unit) {
    val env = GRBEnv(true)
    env.set("logFile", "gurobi.log")
    env.start()
    use(env)
    env.dispose()
}

/** Creates a GRBModel and cleans up the model after use. */
inline fun GRBEnv.makeModel(use: GRBModel.() -> Unit) {
    val model = GRBModel(this)
    use(model)
    model.dispose()
}

// overloads for GRBModel functions that take GRBLinExprs
fun GRBModel.setObjective(termSum: TermSum, sense: Int) = setObjective(termSum.toGRBLinExpr(), sense)
fun GRBModel.addConstr(lhs: TermSum, sense: Char, rhs: TermSum, name: String): GRBConstr = addConstr(lhs.toGRBLinExpr(), sense, rhs.toGRBLinExpr(), name)
fun GRBModel.addConstr(lhs: TermSum, sense: Char, rhs: Double, name: String): GRBConstr = addConstr(lhs.toGRBLinExpr(), sense, rhs, name)

/**
 * Creates a single term [TermSum] using a given coefficient and variable.
 * If [variable] is null, creates an empty [TermSum] instead, acting as if the variable is restricted to be zero.
 */
operator fun Double.times(variable: GRBVar?) = if (variable == null) TermSum() else TermSum(mutableListOf(this), mutableListOf(variable))

/**
 * Class to store linear combinations of [GRBVar]s and modify them using operator overloaded functions. Also contains an offset term b.
 */
class TermSum(private val coefficients: MutableList<Double> = ArrayList(), private val variables: MutableList<GRBVar> = ArrayList(), private var b: Double = 0.0) {

    /** Constructs a [GRBLinExpr] using the data from this TermSum. */
    fun toGRBLinExpr(): GRBLinExpr {
        val linExpr = GRBLinExpr()
        for (i in 0 until coefficients.size)
            linExpr.addTerm(coefficients[i], variables[i])
        linExpr.addConstant(b)
        return linExpr
    }

    /** Returns a new object with the terms from this sum and [termSum]. */
    operator fun plus(termSum: TermSum): TermSum {
        val new = TermSum(ArrayList(coefficients), ArrayList(variables), b)
        new.coefficients.addAll(termSum.coefficients)
        new.variables.addAll(termSum.variables)
        new.b += termSum.b
        return new
    }

    /** Returns a new object with the terms from this sum and an additional constant [b]. */
    operator fun plus(b: Double): TermSum {
        val new = TermSum(ArrayList(coefficients), ArrayList(variables), b)
        new.b += b
        return new
    }

    /** Adds terms from [term] by modifying this object. */
    operator fun plusAssign(term: TermSum) {
        coefficients.addAll(term.coefficients)
        variables.addAll(term.variables)
        b += term.b
    }

}

/** [Iterable.sumOf] equivalent for [TermSum]. */
inline fun <T> Iterable<T>.termSumOf(selector: (T) -> TermSum): TermSum {
    val sum = TermSum()
    for (element in this) sum.plusAssign(selector(element))
    return sum
}