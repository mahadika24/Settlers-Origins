package com.example.game.pathfinding

import com.example.game.model.Building
import com.example.game.model.BuildingType
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

        val mapWidth = tiles.size
        val mapHeight = if (tiles.isNotEmpty()) tiles[0].size else MAP_SIZE

        val clampedStartX = startX.coerceIn(0, mapWidth - 1)
        val clampedStartY = startY.coerceIn(0, mapHeight - 1)
        val clampedDestX = destX.coerceIn(0, mapWidth - 1)
        val clampedDestY = destY.coerceIn(0, mapHeight - 1)

        val queue = ArrayDeque<Pair<Int, Int>>()
        val visited = Array(mapWidth) { BooleanArray(mapHeight) }
        val parent = HashMap<Pair<Int, Int>, Pair<Int, Int>>()

        val start = Pair(clampedStartX, clampedStartY)
        queue.add(start)
        visited[clampedStartX][clampedStartY] = true

        var foundGoal: Pair<Int, Int>? = null

        // Check if destination itself is an obstacle, but caller asked to stop adjacent
        val isDestWalkable = isTileWalkable(clampedDestX, clampedDestY, tiles, buildings, resources)

        while (queue.isNotEmpty()) {
            val current = queue.poll() ?: break

            if (current.first == clampedDestX && current.second == clampedDestY) {
                foundGoal = current
                break
            }

            // If the destination itself is not walkable (e.g. a Tree or solid Building wall),
            // arriving at an adjacent walkable tile counts as success when allowTargetBlocked = true!
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

                if (nx in 0 until mapWidth && ny in 0 until mapHeight && !visited[nx][ny]) {
                    // Only traverse strictly walkable tiles; NEVER step into an unwalkable obstacle cell!
                    val walkable = isTileWalkable(nx, ny, tiles, buildings, resources)

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

    /**
     * Explicit pathfinding to a known walkable tile. If the target is unwalkable, finds a path
     * to the nearest walkable tile adjacent to the destination.
     */
    fun findPathToWalkableTile(
        startX: Int,
        startY: Int,
        destX: Int,
        destY: Int,
        tiles: Array<Array<WorldTile>>,
        buildings: List<Building>,
        resources: List<NaturalResource>
    ): List<Pair<Int, Int>> {
        if (!isTileWalkable(destX, destY, tiles, buildings, resources)) {
            val adjacent = findNearestWalkableTileAdjacentTo(destX, destY, tiles, buildings, resources) ?: return emptyList()
            return findPath(startX, startY, adjacent.first, adjacent.second, tiles, buildings, resources, allowTargetBlocked = false)
        }
        return findPath(startX, startY, destX, destY, tiles, buildings, resources, allowTargetBlocked = false)
    }

    /**
     * Explicit pathfinding to a building's designated exterior entrance.
     */
    fun findPathToBuildingEntrance(
        startX: Int,
        startY: Int,
        building: Building,
        tiles: Array<Array<WorldTile>>,
        buildings: List<Building>,
        resources: List<NaturalResource>
    ): List<Pair<Int, Int>> {
        val entrance = building.getEntranceTile(tiles.size, tiles, buildings, resources)
        return findPathToWalkableTile(startX, startY, entrance.first, entrance.second, tiles, buildings, resources)
    }

    /**
     * Explicit pathfinding to a walkable tile adjacent to a target coordinate.
     */
    fun findPathToAdjacentTile(
        startX: Int,
        startY: Int,
        targetX: Int,
        targetY: Int,
        tiles: Array<Array<WorldTile>>,
        buildings: List<Building>,
        resources: List<NaturalResource>
    ): List<Pair<Int, Int>> {
        val adj = findNearestWalkableTileAdjacentTo(targetX, targetY, tiles, buildings, resources) ?: return emptyList()
        return findPath(startX, startY, adj.first, adj.second, tiles, buildings, resources, allowTargetBlocked = false)
    }

    /**
     * Explicit pathfinding to a building's exterior work point (construction, demolition, or workplace).
     */
    fun findPathToWorkPoint(
        startX: Int,
        startY: Int,
        building: Building,
        tiles: Array<Array<WorldTile>>,
        buildings: List<Building>,
        resources: List<NaturalResource>,
        slotIndex: Int = 0
    ): List<Pair<Int, Int>> {
        val workPoint = if (building.isDemolishing) {
            building.getDemolitionWorkPoint(tiles.size, tiles, buildings, resources, slotIndex)
        } else if (!building.isConstructed) {
            building.getConstructionWorkPoint(tiles.size, tiles, buildings, resources, slotIndex)
        } else {
            building.getWorkplaceEntrance(tiles.size, tiles, buildings, resources)
        }
        val directPath = findPath(startX, startY, workPoint.first, workPoint.second, tiles, buildings, resources, allowTargetBlocked = false)
        if (directPath.isNotEmpty() || (startX == workPoint.first && startY == workPoint.second)) {
            return directPath
        }

        // Fallback: evaluate other available perimeter tiles
        val perimeter = building.getPerimeterTiles(tiles.size, tiles, buildings, resources)
        for (candidate in perimeter) {
            if (candidate == workPoint) continue
            val altPath = findPath(startX, startY, candidate.first, candidate.second, tiles, buildings, resources, allowTargetBlocked = false)
            if (altPath.isNotEmpty() || (startX == candidate.first && startY == candidate.second)) {
                return altPath
            }
        }
        return emptyList()
    }

    fun isTileWalkable(
        x: Int,
        y: Int,
        tiles: Array<Array<WorldTile>>,
        buildings: List<Building>,
        resources: List<NaturalResource>
    ): Boolean {
        val mapWidth = tiles.size
        val mapHeight = if (tiles.isNotEmpty()) tiles[0].size else MAP_SIZE
        if (x !in 0 until mapWidth || y !in 0 until mapHeight) return false

        val tile = tiles[x][y]
        if (!tile.type.isWalkable) return false

        // Rocks block movement
        val hasRock = resources.any { it.x == x && it.y == y && it.type == ResourceType.ROCK }
        if (hasRock) return false

        // Solid buildings block movement, but Farm fields and Town hearth center are walkable
        for (b in buildings) {
            if (x in b.x until (b.x + b.type.width) && y in b.y until (b.y + b.type.height)) {
                // Farms and Town Hearth center are walkable
                if (b.type == BuildingType.WHEAT_FIELD || b.type == BuildingType.PUMPKIN_PATCH || b.type == BuildingType.TOWN_HEARTH) {
                    return true
                }
                // Other completed or under-construction buildings are solid
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
        val mapWidth = tiles.size
        val mapHeight = if (tiles.isNotEmpty()) tiles[0].size else MAP_SIZE
        for (dir in DIRECTIONS) {
            val nx = targetX + dir.first
            val ny = targetY + dir.second
            if (nx in 0 until mapWidth && ny in 0 until mapHeight) {
                if (isTileWalkable(nx, ny, tiles, buildings, resources)) {
                    return Pair(nx, ny)
                }
            }
        }
        return null
    }
}
