package com.example.game.pathfinding

import com.example.game.model.Building
import com.example.game.model.NaturalResource
import com.example.game.model.ResourceType
import com.example.game.model.TileType
import com.example.game.model.WorldTile
import java.util.ArrayDeque

object GridPathfinder {

    const val MAP_SIZE = 22

    private val DIRECTIONS = arrayOf(
        Pair(0, 1),
        Pair(0, -1),
        Pair(1, 0),
        Pair(-1, 0)
    )

    fun findPath(
        startX: Int,
        startY: Int,
        destX: Int,
        destY: Int,
        tiles: Array<Array<WorldTile>>,
        buildings: List<Building>,
        resources: List<NaturalResource>,
        allowTargetBlocked: Boolean = true
    ): List<Pair<Int, Int>> {
        if (startX == destX && startY == destY) return emptyList()

        val clampedStartX = startX.coerceIn(0, MAP_SIZE - 1)
        val clampedStartY = startY.coerceIn(0, MAP_SIZE - 1)
        val clampedDestX = destX.coerceIn(0, MAP_SIZE - 1)
        val clampedDestY = destY.coerceIn(0, MAP_SIZE - 1)

        val queue = ArrayDeque<Pair<Int, Int>>()
        val visited = Array(MAP_SIZE) { BooleanArray(MAP_SIZE) }
        val parent = HashMap<Pair<Int, Int>, Pair<Int, Int>>()

        val start = Pair(clampedStartX, clampedStartY)
        queue.add(start)
        visited[clampedStartX][clampedStartY] = true

        var foundGoal: Pair<Int, Int>? = null

        // Check if destination itself is an obstacle, but we want to reach adjacent tile
        val isDestWalkable = isTileWalkable(clampedDestX, clampedDestY, tiles, buildings, resources)
        val targetIsWalkable = isDestWalkable || !allowTargetBlocked

        while (queue.isNotEmpty()) {
            val current = queue.poll() ?: break

            if (current.first == clampedDestX && current.second == clampedDestY) {
                foundGoal = current
                break
            }

            // If the destination itself is not walkable (e.g. a Tree or solid Building wall),
            // arriving at an adjacent walkable tile counts as success!
            if (!isDestWalkable && allowTargetBlocked) {
                val dist = Math.abs(current.first - clampedDestX) + Math.abs(current.second - clampedDestY)
                if (dist == 1) {
                    foundGoal = current
                    break
                }
            }

            for (dir in DIRECTIONS) {
                val nx = current.first + dir.first
                val ny = current.second + dir.second

                if (nx in 0 until MAP_SIZE && ny in 0 until MAP_SIZE && !visited[nx][ny]) {
                    val isGoalCell = (nx == clampedDestX && ny == clampedDestY)
                    val walkable = isTileWalkable(nx, ny, tiles, buildings, resources) || (isGoalCell && allowTargetBlocked)

                    if (walkable) {
                        visited[nx][ny] = true
                        val next = Pair(nx, ny)
                        parent[next] = current
                        queue.add(next)
                    }
                }
            }
        }

        if (foundGoal == null) {
            // No path found, return empty
            return emptyList()
        }

        // Reconstruct path
        val path = mutableListOf<Pair<Int, Int>>()
        var curr: Pair<Int, Int>? = foundGoal
        while (curr != null && curr != start) {
            path.add(0, curr)
            curr = parent[curr]
        }

        return path
    }

    fun isTileWalkable(
        x: Int,
        y: Int,
        tiles: Array<Array<WorldTile>>,
        buildings: List<Building>,
        resources: List<NaturalResource>
    ): Boolean {
        if (x !in 0 until MAP_SIZE || y !in 0 until MAP_SIZE) return false

        val tile = tiles[x][y]
        if (!tile.type.isWalkable) return false

        // Rocks block movement
        val hasRock = resources.any { it.x == x && it.y == y && it.type == ResourceType.ROCK }
        if (hasRock) return false

        // Solid buildings block movement, but Farm fields and Town hearth center are walkable
        for (b in buildings) {
            if (x in b.x until (b.x + b.type.width) && y in b.y until (b.y + b.type.height)) {
                // Farms are always walkable so farmers can till and harvest
                if (b.type.title.contains("Ladang") || b.type.title.contains("Kebun")) {
                    return true
                }
                // Town center is walkable
                if (b.type.title.contains("Pusat Desa")) {
                    return true
                }
                // Other completed buildings are solid
                return false
            }
        }

        return true
    }

    fun findNearestWalkableTileAdjacentTo(
        targetX: Int,
        targetY: Int,
        tiles: Array<Array<WorldTile>>,
        buildings: List<Building>,
        resources: List<NaturalResource>
    ): Pair<Int, Int>? {
        for (dir in DIRECTIONS) {
            val nx = targetX + dir.first
            val ny = targetY + dir.second
            if (nx in 0 until MAP_SIZE && ny in 0 until MAP_SIZE) {
                if (isTileWalkable(nx, ny, tiles, buildings, resources)) {
                    return Pair(nx, ny)
                }
            }
        }
        return null
    }
}
