package me.jraynor.api.nodes

import imgui.ImColor
import imgui.ImGui
import imgui.extension.nodeditor.NodeEditor
import imgui.flag.ImGuiCond
import imgui.type.ImBoolean
import me.jraynor.api.Graph
import me.jraynor.api.Node
import me.jraynor.api.select.BlockSelect
import me.jraynor.util.*
import net.minecraft.block.BlockState
import net.minecraft.nbt.CompoundNBT
import net.minecraft.util.Direction
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import net.minecraft.world.server.ServerWorld

/**
 * This is a block node, it will be
 */
class LinkNode(
    /**This keeps track of the linked block**/
    var blockPos: BlockPos? = null,
    /***This is the block we're currently linked to**/
    var blockFace: Direction? = null,
    /**This is used to display the current location for a linked block**/
    private var shown: ImBoolean = ImBoolean(false),
    /**This keeps track of the show color**/
    private val color: FloatArray = floatArrayOf(1f, 0f, 0f)
) : FilterableIONode() {

    /**This is the current state of the block. This is only present on the client.**/
    private val blockState: BlockState?
        get() {
            blockPos ?: return null
            return world.getBlockState(blockPos!!)
        }

    /**
     * This is called when the node is added to the graph. At this point the node's id should be set so we can
     * safely set the node id of the mode
     */
    override fun onAdd(graph: Graph) {
        modes.node = this
        this.parent = graph
    }

    /**
     * This will write the link and the face. It has null safety so if they are null they will not be written. There
     * won't be an exception the data just isn't present currently.
     */
    override fun serializeNBT(): CompoundNBT {
        val tag = super.serializeNBT()
        blockPos ?: return tag
        tag.putBlockPos("link", blockPos!!)
        blockFace ?: return tag
        tag.putEnum("face", blockFace!!)
        return tag
    }

    /**
     * This will first deserialize the node node data from the base [Node] class. Then it will deserialize the position
     * of the block and the face.
     */
    override fun deserializeNBT(tag: CompoundNBT) {
        super.deserializeNBT(tag)
        blockPos = tag.getBlockPos("link")
        blockFace = tag.getEnum("face")
        this.modes.node = this
    }

    /***
     * This will allow us to tick the given node
     */
    override fun onTick(world: World, graph: Graph) {
        doInputOutput(graph, world as ServerWorld)
    }

    /**
     * This should only be called from the client.
     * This code should not exist on the server.
     * (or at least should be called)
     */
    override fun render() {
        id ?: return
        NodeEditor.beginNode(id!!.toLong())
        ImGui.textColored(ImColor.rgbToColor("#D65076"), "Block Node")
        ImGui.sameLine()
        if (ImGui.button("Select##$id"))
            pushFaceSelect()
        ImGui.textDisabled("${blockState?.block?.translatedName?.string}, ${blockPos?.coords}")
        ImGui.dummy(0f, 6f)
        renderPorts()
        NodeEditor.endNode()
    }

    /**
     * This is used as the code that will render on the side view
     */
    override fun renderEx() {
        ImGui.setNextItemOpen(true, ImGuiCond.FirstUseEver)
        if (ImGui.collapsingHeader("Block Node")) {

            if (ImGui.checkbox("highlight face##$id", shown)) {
                blockFace ?: return
                blockPos ?: return
                val key = Pair(this.blockPos!!, this.blockFace!!)
                if (shown.get())
                    BlockSelect.showFaces[key] = this.color
                else
                    BlockSelect.showFaces.remove(key)
            }
            if (shown.get())
                ImGui.colorEdit3("face color", color)
            modes.render(this::pushUpdate)
        }
    }

    /**
     *  Keeps track of our gui data
     */
    companion object {
        private val INFO_COLOR: Int = ImColor.rgbToColor("#a75ced")
    }


}
