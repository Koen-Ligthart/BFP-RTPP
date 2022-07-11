package test

import main.CUSTOMER_COLOR
import main.EDGE_COLOR
import main.GEOMETRY_CUT_DEGREE
import main.GEOMETRY_CUT_SCALE
import main.graph.readTSPFile
import main.util.geometryBasedCut
import main.util.show
import java.nio.file.Paths
import kotlin.io.path.listDirectoryEntries

fun main() {
    // show results of geometric edge restrictions for each graph
    for (file in Paths.get("data").listDirectoryEntries()) {
        val mutableGraph = readTSPFile(file)
        geometryBasedCut(mutableGraph, GEOMETRY_CUT_SCALE, GEOMETRY_CUT_DEGREE)
        mutableGraph.finalize().show(vertexColoring = { CUSTOMER_COLOR }, edgeColoring = { EDGE_COLOR }, name = "${mutableGraph.name} vertex geometric preprocessed")
    }
}