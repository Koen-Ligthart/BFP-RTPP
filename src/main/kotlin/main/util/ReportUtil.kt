package main.util

import main.GEOMETRY_CUT_DEGREE
import main.GEOMETRY_CUT_SCALE
import main.graph.Graph
import main.graph.readTSPFile
import main.lp.PlotOption
import main.lp.Relaxation
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.listDirectoryEntries

/**
 * Gather results for a [relaxation].
 * Problem instances from [data] are preprocessed using [excludeDijkstra] and [excludeTriangle].
 * If [geometryCut] it trims the problem instances of the [data] using [geometryBasedCut].
 * If [plot] makes plots for the fractional solutions.
 */
fun solveAll(data: List<Path> = Paths.get("data").listDirectoryEntries(), relaxation: (Graph) -> Relaxation, geometryCut: Boolean, plot: PlotOption = PlotOption.NO_PLOT) {
    for (file in data) {
        for (depotCount in listOf(1, 2, 4, 8)) {

            // prepare graph
            val mutableGraph = readTSPFile(file)

            assignDepots(mutableGraph, depotCount)

            if (geometryCut)
                geometryBasedCut(mutableGraph, GEOMETRY_CUT_SCALE, GEOMETRY_CUT_DEGREE)

            val graph = mutableGraph.finalize()

            // preprocess
            excludeDijkstra(graph)
            excludeTriangle(graph)

            // compute solution using method
            val instance = relaxation(graph)
            instance.solve(plot)

            log("${graph.name}: ${instance.result}")
        }
    }
}