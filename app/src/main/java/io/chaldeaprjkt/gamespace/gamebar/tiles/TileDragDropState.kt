/*
 * Copyright (C) 2025-2026 AxionOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.chaldeaprjkt.gamespace.gamebar.tiles

import android.content.ClipData
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toRect

enum class DragType { Move, Add }

val TilePlacementSpec: SpringSpec<IntOffset> =
    SpringSpec(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioLowBouncy)

data class ItemPosition(
    val id: String,
    val index: Int,
    val offset: IntOffset,
    val size: IntSize,
)

data class DragTileData(
    val id: String,
    val label: String,
    val icon: Int,
)

class TileDragDropState(initialTiles: List<TileAction>, val columns: Int = 2) {

    private val _tiles = initialTiles.map { it.toDragData() }.toMutableStateList()
    val tiles: List<DragTileData> get() = _tiles

    private val _itemPositions = mutableStateMapOf<String, ItemPosition>()

    var draggedTile by mutableStateOf<DragTileData?>(null)
        private set

    var dragType by mutableStateOf<DragType?>(null)
        private set

    val dragInProgress: Boolean get() = draggedTile != null

    fun isMoving(id: String): Boolean = draggedTile?.id == id

    fun updateItemPosition(id: String, index: Int, offset: IntOffset, size: IntSize) {
        _itemPositions[id] = ItemPosition(id, index, offset, size)
    }

    fun findItemAtOffset(relativeOffset: Offset): ItemPosition? {
        return _itemPositions.values.firstOrNull { item ->
            IntRect(item.offset, item.size).toRect().contains(relativeOffset)
        }
    }

    fun onStarted(tile: DragTileData, type: DragType = DragType.Move) {
        draggedTile = tile
        dragType = type
    }

    fun onTargeting(targetIndex: Int) {
        val dragged = draggedTile ?: return
        val fromIndex = _tiles.indexOfFirst { it.id == dragged.id }
        if (fromIndex == targetIndex) return
        if (fromIndex != -1) {
            val cell = _tiles.removeAt(fromIndex)
            _tiles.add(targetIndex.coerceIn(0, _tiles.size), cell)
        } else if (dragType == DragType.Add) {
            _tiles.add(targetIndex.coerceIn(0, _tiles.size), dragged)
        }
    }

    fun movedOutOfBounds() {
        val dragged = draggedTile ?: return
        if (dragType == DragType.Add) {
            _tiles.removeAll { it.id == dragged.id }
        }
    }

    fun onDrop(): List<String> {
        val result = tileIds()
        draggedTile = null
        dragType = null
        return result
    }

    fun onCancelled() {
        val dragged = draggedTile
        if (dragged != null && dragType == DragType.Add) {
            _tiles.removeAll { it.id == dragged.id }
        }
        draggedTile = null
        dragType = null
    }

    fun tileIds(): List<String> = _tiles.map { it.id }

    fun addTile(tile: DragTileData) {
        if (_tiles.none { it.id == tile.id }) {
            _tiles.add(tile)
        }
    }

    fun removeTile(id: String): DragTileData? {
        val index = _tiles.indexOfFirst { it.id == id }
        return if (index != -1) _tiles.removeAt(index) else null
    }
}

private fun TileAction.toDragData() = DragTileData(id = id, label = label, icon = icon)

private fun DragAndDropEvent.toOffset(): Offset {
    return toAndroidDragEvent().run { Offset(x, y) }
}

@Composable
fun Modifier.dragAndDropActiveGrid(
    contentOffset: () -> Offset,
    dragDropState: TileDragDropState,
    onDrop: (List<String>) -> Unit,
): Modifier {
    val target = remember(dragDropState) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {}

            override fun onMoved(event: DragAndDropEvent) {
                val offset = event.toOffset()
                val relativeOffset = offset - contentOffset()
                val targetItem = dragDropState.findItemAtOffset(relativeOffset)
                targetItem?.let { dragDropState.onTargeting(it.index) }
            }

            override fun onExited(event: DragAndDropEvent) {
                dragDropState.movedOutOfBounds()
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                val ids = dragDropState.onDrop()
                onDrop(ids)
                return true
            }

            override fun onEnded(event: DragAndDropEvent) {
                if (dragDropState.dragInProgress) {
                    dragDropState.onDrop()
                }
            }
        }
    }

    return dragAndDropTarget(
        shouldStartDragAndDrop = { event -> event.mimeTypes().contains("text/plain") },
        target = target,
    )
}

@Composable
fun Modifier.dragAndDropAvailableZone(
    dragDropState: TileDragDropState,
    onTileRemoved: (String) -> Unit,
): Modifier {
    val target = remember(dragDropState) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val dragged = dragDropState.draggedTile ?: return false
                if (dragDropState.dragType == DragType.Move) {
                    onTileRemoved(dragged.id)
                }
                dragDropState.onDrop()
                return true
            }
        }
    }

    return dragAndDropTarget(
        shouldStartDragAndDrop = { event -> event.mimeTypes().contains("text/plain") },
        target = target,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.dragAndDropTileSource(
    tileData: DragTileData,
    dragDropState: TileDragDropState,
    dragType: DragType = DragType.Move,
): Modifier {
    val state by rememberUpdatedState(dragDropState)

    return dragAndDropSource(
        block = {
            detectDragGesturesAfterLongPress(
                onDrag = { _, _ -> },
                onDragStart = {
                    state.onStarted(tileData, dragType)
                    startTransfer(
                        DragAndDropTransferData(ClipData.newPlainText("id", tileData.id))
                    )
                },
            )
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.dragAndDropAvailableTileSource(
    tileData: DragTileData,
    dragDropState: TileDragDropState,
): Modifier {
    val state by rememberUpdatedState(dragDropState)

    return dragAndDropSource(
        block = {
            detectDragGesturesAfterLongPress(
                onDrag = { _, _ -> },
                onDragStart = {
                    state.onStarted(tileData, DragType.Add)
                    startTransfer(
                        DragAndDropTransferData(ClipData.newPlainText("id", tileData.id))
                    )
                },
            )
        }
    )
}
