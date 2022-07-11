package test

import jetbrains.letsPlot.asDiscrete
import jetbrains.letsPlot.geom.geomBar
import jetbrains.letsPlot.ggsize
import jetbrains.letsPlot.letsPlot
import main.graph.readTSPFile
import main.util.*
import java.nio.file.Paths
import kotlin.io.path.listDirectoryEntries

fun main() {
    // show effectiveness of preprocessing for all problem instances using bar charts
    newLog("preprocessing-results")
    for (geometryCut in listOf(false, true)) {
        log("geometryCut = $geometryCut")
        for (depotCount in listOf(1, 2, 4, 8)) {
            log("depotCount = $depotCount")
            val names = mutableListOf<String>()
            val stages = mutableListOf<String>()
            val values = mutableListOf<Int>()
            for (file in Paths.get("data").listDirectoryEntries()) {
                val mutableGraph = readTSPFile(file)
                assignDepots(mutableGraph, depotCount)
                val s0 = 2 * mutableGraph.edges.size * depotCount
                if (geometryCut)
                    geometryBasedCut(mutableGraph, 0.5, 3)
                val graph = mutableGraph.finalize()
                val s1 = graph.includedArcCount()
                excludeDijkstra(graph)
                val s2 = graph.includedArcCount()
                excludeTriangle(graph)
                val s3 = graph.includedArcCount()
                log("${graph.name} $s0 -> $s1 -> $s2 -> $s3")
                val name = graph.name.substring(0, graph.name.length - 2)
                names.add(name); stages.add("Remaining"); values.add(s3)
                names.add(name); stages.add("Removed by triangle exclusion"); values.add(s2 - s3)
                names.add(name); stages.add("Removed by dijkstra exclusion"); values.add(s1 - s2)
            }
            val data = mapOf(
                "names" to names,
                "stages" to stages,
                "values" to values,
            )
            val plot = letsPlot(data) + geomBar {
                x = asDiscrete("names", order = 1); fill = "stages"; weight = "values"
            } + ggsize(1000, 500)
            Plotter.openAndSavePlot(plot, "preprocessing-results-cut-$geometryCut-depots-$depotCount")
        }
    }
}