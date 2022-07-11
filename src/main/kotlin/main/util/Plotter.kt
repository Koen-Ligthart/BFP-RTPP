package main.util

import jetbrains.datalore.plot.PlotSvgExport
import jetbrains.letsPlot.coordFixed
import jetbrains.letsPlot.export.ggsave
import jetbrains.letsPlot.geom.geomLine
import jetbrains.letsPlot.geom.geomPoint
import jetbrains.letsPlot.geom.geomSegment
import jetbrains.letsPlot.geom.geomText
import jetbrains.letsPlot.ggsize
import jetbrains.letsPlot.intern.Plot
import jetbrains.letsPlot.intern.toSpec
import jetbrains.letsPlot.letsPlot
import jetbrains.letsPlot.scale.scaleXContinuous
import jetbrains.letsPlot.scale.scaleYContinuous
import jetbrains.letsPlot.theme
import main.EPSILON_COLOR_INTERPOLATE
import main.graph.Edge
import main.graph.Graph
import main.graph.Vertex
import java.awt.Desktop
import java.io.File

/**
 * Utility object to contain code for generating plots.
 */
object Plotter {

    /** Whether to open created plots in the browser. If false, only saves plots in file system. */
    var openInBrowser = true

    // internal value used to be able to open multiple plots (results in different local filenames)
    private var plotCount = 0

    /** Create a plot from a graph using given coloring functions. */
    fun plotGraph(graph: Graph, vertexColoring: (Vertex) -> Int?, edgeColoring: (Edge) -> Int?): Plot {
        var xRange = range(graph) { x }
        var yRange = range(graph) { y }
        val vX: Vertex.() -> Double
        val vY: Vertex.() -> Double
        // if figure would be very tall, instead flip x and y
        if (xRange.second - xRange.first < yRange.second - yRange.first) {
            vX = { y }; vY = { x }
            val t = xRange; xRange = yRange; yRange = t
        } else {
            vX = { x }; vY = { y }
        }
        var plot = letsPlot()
        // draw points for each vertex
        for (vertex in graph.vertices) {
            val color = vertexColoring(vertex)
            if (color != null) {
                plot += geomPoint(
                    x = vertex.vX(),
                    y = vertex.vY(),
                    size = 5,
                    alpha = 0.5,
                    color = color
                )
            }
        }
        // draw line segments for each edge
        for (edge in graph.edges) {
            val color = edgeColoring(edge)
            if (color != null)
                plot += geomSegment(
                    x = edge.endpoints[0].vX(),
                    y = edge.endpoints[0].vY(),
                    xend = edge.endpoints[1].vX(),
                    yend = edge.endpoints[1].vY(),
                    color = color
                )
        }
        // compute plot range and axis tick separation
        val gridDist = (yRange.second - yRange.first).toInt() / 10
        xRange = xRange.first - gridDist to xRange.second + gridDist
        yRange = yRange.first - (if (graph.depots.isEmpty()) 1 else 2) * gridDist to yRange.second + gridDist
        // apply computed ranges and separation to plot
        plot += coordFixed(
            ratio = 1.0,
            xlim = xRange,
            ylim = yRange
        )
        plot += ggsize(800, 800)
        val xBreaks = breaks(xRange, gridDist)
        val yBreaks = breaks(yRange, gridDist)
        plot += scaleXContinuous(breaks = xBreaks)
        plot += scaleYContinuous(breaks = yBreaks)
        plot += theme(axisTextX = "blank", axisTextY = "blank", axisLineX = "blank", axisTicksX = "blank", axisTitleX = "blank", axisTitleY = "blank")
        if (graph.depots.isNotEmpty()) {
            val c = graph.depots.first().capacity
            val d = gridDist.toDouble() / 4.0
            val y = yRange.first + gridDist
            val largeCapacity = c > (xBreaks.last() - xBreaks.first()) / 2
            val xR = xBreaks.last() + 2 * d
            val xL = xR - if (largeCapacity) c / 2 else c
            plot += geomText(label = if (largeCapacity) "Half capacity" else "Capacity", x = xL - 2 * d, y = y - 2 * d, hjust = "right", size = 12)
            plot += geomSegment(x = xL, y = y - 2 * d, xend = xR, yend = y - 2 * d, color = "black")
            plot += geomSegment(x = xL, y = y - 3 * d, xend = xL, yend = y - d, color = "black")
            plot += geomSegment(x = xR, y = y - 3 * d, xend = xR, yend = y - d, color = "black")
        }
        return plot
    }

    // yields the minimum to maximum range of a vertex attribute of a graph
    private inline fun range(graph: Graph, attr: Vertex.() -> Double) = graph.vertices.minOf(attr) to graph.vertices.maxOf(attr)

    // computes the locations of all axis labels along a range using a given [gridDist]
    private fun breaks(range: Pair<Double, Double>, gridDist: Int) = (range.first.toInt() .. range.second.toInt() step gridDist).toList()

    // examples: https://github.com/JetBrains/lets-plot-kotlin/blob/master/docs/examples.md#time-series
    /** Plots a data series given a list of (x, y) plot coordinates */
    fun plotSeries(series: List<Pair<Double, Double>>, xLabel: String, yLabel: String) = letsPlot(mapOf(xLabel to series.map { it.first }, yLabel to series.map { it.second })) + geomLine(color = "blue") { x = xLabel; y = yLabel }

    // taken from https://github.com/alshan/lets-plot-mini-apps/tree/main/jvm-plot-export/src/main/kotlin
    /** Save a plot to a .svg and .html file (if [openInBrowser] also opens the .html file in the browser) */
    fun openAndSavePlot(plot: Plot, name: String = "plot") {
        plotCount++
        val dir = File(System.getProperty("user.dir"), "lets-plot-images")
        dir.mkdir()
        val file = File(dir.canonicalPath, "$name-$plotCount.html")
        ggsave(plot, path = "lets-plot-images", filename = "$name-$plotCount.svg")
        file.createNewFile()
        // taken from https://github.com/alshan/lets-plot-mini-apps/tree/main/jvm-plot-export/src/main/kotlin
        file.writeText(PlotSvgExport.buildSvgImageFromRawSpecs(plot.toSpec()))

        if (openInBrowser) Desktop.getDesktop().browse(file.toURI())
    }

}

/** Creates a plot of the graph and invokes [Plotter.openAndSavePlot] with the resulting plot. */
fun Graph.show(vertexColoring: (Vertex) -> Int?, edgeColoring: (Edge) -> Int?, name: String = "plot") = Plotter.openAndSavePlot(Plotter.plotGraph(this, vertexColoring, edgeColoring), name)

/**
 * Linearly interpolates each of the color components (RGB) individually based on scale parameter [f].
 * The result is (r_l, g_l, b_l) + f * ((r_h, g_h, b_h) - (r_l, g_l, b_l)).
 */
fun colorInterpolate(l: Int, h: Int, f: Double): Int {
    // compensate for numerical inaccuracies
    var f = if (f > 1.0 && f < 1.0 + EPSILON_COLOR_INTERPOLATE) 1.0 else f
    f = if (f < 0.0 && f > -EPSILON_COLOR_INTERPOLATE) 0.0 else f
    // verify range
    if (f !in 0.0..1.0)
        throw IllegalArgumentException("f: $f not in [0, 1]")
    var result = 0
    var p = 1 // offset of primary color value location in color representations
    for (i in 0..2) {
        // interpolate component and store in result
        val lb = (l ushr (8 * i)) and 0xff
        val hb = (h ushr (8 * i)) and 0xff
        val v = ((hb - lb) * f).toInt() + lb
        result += p * v
        p *= 0x100
    }
    return result
}