package main.graph

import main.util.primDijkstraMST
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.createDirectories
import kotlin.io.path.extension
import kotlin.io.path.outputStream
import kotlin.math.sqrt

/**
 * Reads a .tsp file and converts it to the corresponding graph object.
 */
fun readTSPFile(path: Path): MutableGraph {
    val graph = MutableGraph(path.fileName.toString().removeSuffix(".${path.extension}"))
    // read coordinates of vertices and add them to graph
    var readingCoordinates = false
    val lines = Files.readAllLines(path)
    for (line in lines) {
        when (line) {
            "NODE_COORD_SECTION" -> readingCoordinates = true
            "EOF" -> break
            else -> if (readingCoordinates) {
                val split = line.trim().split(" +".toRegex())
                graph.addVertex(split[1].toDouble(), split[2].toDouble())
            }
        }
    }
    // add edges to make graph a complete graph
    graph.vertices.forEachIndexed { i, a ->
        graph.vertices.filterIndexed { j, _ -> i < j }.forEach { b ->
            val dx = a.x - b.x
            val dy = a.y - b.y
            // edge weight is euclidean distance rounded to the nearest integer
            graph.addEdge(a, b, (sqrt(dx * dx + dy * dy) + 0.5).toInt())
        }
    }
    return graph
}

/**
 * Reads a .txt file and converts it to the corresponding graph object with assigned depots.
 */
fun readTxtFile(path: Path): MutableGraph {
    Scanner(Files.newBufferedReader(path)).use { sc ->
        // read counts
        val vertexCount = sc.nextInt()
        val depotCount = sc.nextInt()
        val edgeCount = sc.nextInt()
        // create graph with correct name
        val graph = MutableGraph("${path.fileName.toString().substring(0, path.fileName.toString().indexOf('.'))}_$depotCount")
        // vertex ids of soon to be depots
        val depotVertexIds = IntArray(depotCount) { sc.nextInt() - 1 } // file uses 1-based indices
        // skip next depotCount tokens (those are magic weight threshold numbers)
        repeat(depotCount) { sc.next() }
        // add vertices
        repeat(vertexCount) { graph.addVertex(sc.nextDouble(), sc.nextDouble()) }
        // add edges
        repeat(edgeCount) {
            val a = graph.vertices[sc.nextInt() - 1]
            val b = graph.vertices[sc.nextInt() - 1]
            val w = sc.nextInt()
            if (a != b)
                graph.addEdge(a, b, w)
        }
        // assign depots
        val capacity = primDijkstraMST(graph.finalize()) / (5 * depotCount)
        depotVertexIds.forEach { graph.vertices[it].capacity = capacity }
        return graph
    }
}

/**
 * Writes a graph object to a file in the .txt format.
 */
fun writeTxtFile(graph: Graph, file: Path) {
    file.parent.createDirectories()
    PrintWriter(file.outputStream()).use { out ->
        // print counts
        out.println("${graph.vertices.size} ${graph.depots.size} ${graph.edges.size}")
        // rearranges depots to have ids strictly lower than customer ids
        val map = IntArray(graph.vertices.size)
        graph.depots.forEach { map[it.vertexId] = it.depotId }
        graph.customers.forEach { map[it.vertexId] = graph.depots.size + it.customerId }
        // print depot ids
        out.println(graph.depots.map { map[it.vertexId] + 1 }.joinToString(" "))
        // print depot capacities
        out.println(graph.depots.map { it.capacity }.joinToString(" "))
        // print vertex coordinates
        map.map { graph.vertices[it] }.forEach { out.println("${it.x} ${it.y}") }
        // print edge info
        graph.edges.forEach { out.println("${map[it.endpoints[0].vertexId] + 1} ${map[it.endpoints[1].vertexId] + 1} ${it.weight}") }
        out.flush()
    }
}