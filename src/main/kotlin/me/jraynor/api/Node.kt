package me.jraynor.api

import imgui.ImGui
import imgui.extension.nodeditor.NodeEditor
import me.jraynor.api.enums.Mode
import me.jraynor.api.nodes.NodeData
import me.jraynor.util.putClass
import me.jraynor.util.whenClient
import net.minecraft.client.Minecraft
import net.minecraft.nbt.CompoundNBT
import net.minecraft.world.World
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn
import net.minecraftforge.common.util.INBTSerializable
import java.lang.Exception

/**
 * This is the base node. Every single type of node will extend off of this. This will store critical methods for things
 * such as saving and loading, which allows for serialization from the server to the client. We also much have [ImGui]
 * rendering calls, but only for the client.
 */
abstract class Node(
    /**This can be used as way to check if the node is properly serialized/deserialized. This should be unique and always present.**/
    var id: Int? = null,
    /**This is used to keep track of the position on screen of the node.**/
    var pos: Pair<Float, Float>? = null,
    /**This helps to keep track of the local state. This shouldn't be transferred to/from the network**/
    var initialized: Boolean = false,
    /**This is the list of ports of the node.**/
    val pins: MutableList<Pin> = ArrayList(),
    /**Convince reference to the parent**/
    var parent: Graph? = null,
    /**This is a callback. When this is called it means we should push a update to the server**/
    var updateCallback: ((Node) -> Unit)? = null,
    /**This is called whenever you're looking to select a block**/
    var blockCallback: ((Node) -> Unit)? = null,
) : INBTSerializable<CompoundNBT> {
    /***This allows us to get and set the pos**/
    var x: Float
        get() = NodeEditor.getNodePositionX(id!!.toLong())
        set(value) = NodeEditor.setNodePosition(id!!.toLong(), value, y)

    /***This allows us to get and set the pos**/
    var y: Float
        get() = NodeEditor.getNodePositionY(id!!.toLong())
        set(value) = NodeEditor.setNodePosition(id!!.toLong(), x, value)

    /**This is a helper variable to convert the canvas x to a screen x**/
    var screenX: Float
        get() = NodeEditor.toScreenX(x)
        set(value) {
            this.x = NodeEditor.toCanvasX(value)
        }

    /**This is a helper variable to convert the canvas x to a screen x**/
    var screenY: Float
        get() = NodeEditor.toScreenX(x)
        set(value) {
            this.y = NodeEditor.toCanvasY(value)
        }

    /**Keeps track of the pin size**/
    protected var pinSize = 24f

    /**This is a getter for the world on the client.**/
    protected val world: World
        @OnlyIn(Dist.CLIENT)
        get() = Minecraft.getInstance().world!!

    /**
     * This will call the [updateCallback] if possible, which should update the node.
     */
    protected fun pushUpdate() {
        updateCallback?.let {
            it(this)
        } //This should be called from the server.
    }

    /**
     * This will call the [blockCallback] if possible, which should update the node.
     */
    protected fun pushFaceSelect() {
        blockCallback?.let {
            it(this)
        } //This should be called from the server.
    }

    /***
     * This will allow us to tick the given node
     */
    open fun onTick(world: World, graph: Graph) {}

    /**
     * This will be called
     */
    open fun onInput(mode: Mode, graph: Graph) {}

    /**
     * This will find the pin with the given label
     */
    protected fun findPinWithLabel(label: String): Pin? {
        for (pin in pins)
            if (pin.label == label) return pin
        return null
    }

    /**
     * This should only be called from the client.
     * This code should not exist on the server.
     * (or at least should be called)
     */
    @OnlyIn(Dist.CLIENT) abstract fun render()

    /**
     * This is used as the code that will render on the side view
     */
    @OnlyIn(Dist.CLIENT) abstract fun renderEx()

    /**
     * This will simply render each of the ports. It can be called from where ever you'd like.
     */
    @OnlyIn(Dist.CLIENT) protected fun renderPorts() {
        pins.forEach { it.render() }
    }

    /**
     * This called when the node is added to the graph
     */
    open fun onAdd(graph: Graph) {}

    /**
     * This is called in the constructor of the node. it will
     */
    abstract fun addPins()

    /**
     * This will add a port. TODO: check to see if we need to make sure the port doesn't exits?
     * This may be okay simply because the id's should always be uniquely generated
     */
    fun add(pin: Pin): Node {
        pin.nodeId = this.id
        pins.add(pin)
        return this
    }

    /**
     * This will get the port at the given idnex
     */
    operator fun get(index: Int): Pin {
        return pins[index]
    }

    /**
     * This should serialize the tag.
     */
    override fun serializeNBT(): CompoundNBT {
        val tag = CompoundNBT()
        id ?: throw NodeException("Failed to serialize node because there is no node id present.")
        if (pos != null) {
            tag.putFloat("client_x", pos!!.first)
            tag.putFloat("client_y", pos!!.second)
        }
        tag.putClass("node_type", javaClass)
        tag.putInt("id", id!!)
        tag.put("pins", serializePins())
        return tag
    }

    /***
     * THis will write all of the ports to the tag
     */
    private fun serializePins(): CompoundNBT {
        val tag = CompoundNBT()
        tag.putInt("size", pins.size)
        for (i in 0 until pins.size)
            tag.put("pin_$i", pins[i].serializeNBT())
        return tag
    }

    /**
     * This should deserialize the node.
     */
    override fun deserializeNBT(tag: CompoundNBT) {
        if (tag.contains("client_x") && tag.contains("client_y"))
            this.pos = Pair(tag.getFloat("client_x"), tag.getFloat("client_y"))
        this.id = tag.getInt("id")
        deserializePorts(tag.getCompound("pins"))
    }

    /***
     * THis will read all of the ports to the tag
     */
    private fun deserializePorts(tag: CompoundNBT) {
        this.pins.clear()
        val size = tag.getInt("size")
        for (i in 0 until size) {
            val port = Pin()
            port.deserializeNBT(tag.getCompound("pin_$i"))
            add(port)
        }

    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        other as Node
        if (id != other.id) return false
        if (pins != other.pins) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id ?: 0
        result = 31 * result + pins.hashCode()
        return result
    }

    override fun toString(): String {
        return "${javaClass.simpleName}(id=$id, ports=$pins, pinSize=$pinSize)"
    }

    /**
     * This is a simple exception wrapper. It's fine being internal
     */
    internal class NodeException(message: String) : Exception(message)


}