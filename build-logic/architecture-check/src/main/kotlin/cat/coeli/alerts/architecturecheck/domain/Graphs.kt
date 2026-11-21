package cat.coeli.alerts.architecturecheck.domain

/**
 * The groups of two or more nodes that all reach each other, each sorted, in a stable order: every
 * package cycle, however many packages take part in it.
 */
internal fun cyclesOf(edges: Map<String, Set<String>>): List<List<String>> {
    val reverse =
        edges
            .flatMap { (from, targets) -> targets.map { it to from } }
            .groupBy({ it.first }, { it.second })
    return (edges.keys + edges.values.flatten())
        .sorted()
        .fold(emptyList<Set<String>>()) { components, node ->
            if (components.any { node in it }) components
            else
                components +
                    listOf(reachableFrom(node, edges) intersect reachableFrom(node, reverse))
        }
        .filter { it.size > 1 }
        .map { it.sorted() }
        .sortedBy { it.joinToString() }
}

private fun reachableFrom(start: String, edges: Map<String, Collection<String>>): Set<String> {
    val reached = mutableSetOf(start)
    val pending = ArrayDeque(listOf(start))
    while (pending.isNotEmpty()) {
        edges[pending.removeFirst()]
            .orEmpty()
            .filter { reached.add(it) }
            .forEach { pending.addLast(it) }
    }
    return reached
}

/**
 * The deepest layer each class needs: the deepest of the libraries it reaches ([libraryLayers],
 * absent when it reaches none) and of what the classes it depends on ([dependencies]) need in turn.
 * A class reaching nothing needs [Layer.Helper].
 */
internal fun neededLayersOf(
    libraryLayers: Map<String, Layer>,
    dependencies: Map<String, Set<String>>,
): Map<String, Layer> {
    val needed =
        (libraryLayers.keys + dependencies.keys + dependencies.values.flatten())
            .associateWith { libraryLayers[it] ?: Layer.Helper }
            .toMutableMap()
    val dependents =
        dependencies
            .flatMap { (from, targets) -> targets.map { it to from } }
            .groupBy({ it.first }, { it.second })
    val pending = ArrayDeque(needed.keys)
    while (pending.isNotEmpty()) {
        val changed = pending.removeFirst()
        dependents[changed].orEmpty().forEach { dependent ->
            if (needed.getValue(dependent) < needed.getValue(changed)) {
                needed[dependent] = needed.getValue(changed)
                pending.addLast(dependent)
            }
        }
    }
    return needed
}
