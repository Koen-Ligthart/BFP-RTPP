package test

import main.lp.MCFGGRelaxation
import main.lp.PlotOption
import main.util.newLog
import main.util.solveAll

fun main() {

    newLog("mcfgg")

    // compute bounds for all data sets and generates and stores fractional solution plots
    solveAll(relaxation = {
        MCFGGRelaxation(it)
    }, geometryCut = true, plot = PlotOption.PLOT_DETAILED)

}