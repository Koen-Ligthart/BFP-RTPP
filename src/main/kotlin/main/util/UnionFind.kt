package main.util

/**
 * Union find data structure for integers 0, 1, ..., size - 1.
 */
class UnionFind(size: Int) {

    private val nodes = Array(size) { Node(it) }

    /** Return representative of component of x. */
    fun repr(x: Int) = nodes[x].repr().value

    /** Merge two components of x and y and ensure they have the same representative. */
    fun union(x: Int, y: Int) {
        val a = nodes[x].repr()
        val b = nodes[y].repr()
        // perform union-by-rank
        if (a.rank > b.rank) {
            b.parent = a
        } else if (a.rank < b.rank) {
            a.parent = b
        } else {
            a.parent = b
            b.rank++
        }
    }

    private class Node(val value: Int) {

        var parent = this
        var rank = 0

        fun repr(): Node {
            // path compression
            if (this != parent) parent = parent.repr()
            return parent
        }

    }

}