package main.lp

import java.text.DecimalFormat

/**
 * Class used to model relaxations for MCRFPP. Used to gather results.
 */
abstract class Relaxation {

    // mapping of profile names to used up processing times
    private val time = mutableMapOf<String, Double>()
    // time of last [profile] call
    private var startTime = 0L
    // name of last [profile] call
    private var prevProfileName: String? = null

    lateinit var result: RelaxationResult
        private set

    /**
     * Sets up and solves the relaxation.
     * Calls [reportResult] after objective value is computed.
     * Generates one or multiple plots after if [plot] is not [PlotOption.NO_PLOT].
     */
    abstract fun solve(plot: PlotOption)

    /**
     * Marks the start of a profiling interval.
     * Records used processing time until next call of [profile] or [reportResult] under the specified [name].
     */
    protected fun profile(name: String?) {
        val endTime = System.nanoTime()
        val timeSpent = (endTime - startTime) / 1_000_000_000.0
        if (prevProfileName != null)
            time[prevProfileName!!] = time.getOrDefault(prevProfileName, 0.0) + timeSpent
        prevProfileName = name
        startTime = System.nanoTime()
    }

    /** Called within [solve] once [objectiveValue] has been computed. */
    protected fun reportResult(objectiveValue: Double) {
        profile(null)
        result = RelaxationResult(objectiveValue, time)
    }

}

/**
 * Represents the result of a [Relaxation] giving an objective result and time profiling information.
 */
data class RelaxationResult(val objectiveValue: Double, val time: Map<String, Double>) {
    override fun toString(): String {
        val df = DecimalFormat("0.0")
        return "obj ${objectiveValue.toInt()} ${time.map { (n, t) -> "$n ${df.format(t)}" }.joinToString(" ")}"
    }
}

enum class PlotOption { NO_PLOT, PLOT_COARSE, PLOT_DETAILED }