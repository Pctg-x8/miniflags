package com.cterm2.miniflags

import cpw.mods.fml.common.network.simpleimpl.{IMessage, IMessageHandler, MessageContext}
import io.netty.buffer.ByteBuf
import cpw.mods.fml.common.network.ByteBufUtils._

// Common BufferUtils
package object BufferUtils
{
	implicit class Methods(val buffer: ByteBuf) extends AnyVal
	{
		def readInt() = readVarInt(buffer, 5)
		def writeInt(v: Int) = writeVarInt(buffer, v, 5)
	}
}

// The packet sent when link was established
final case class PacketLinkEstablished(var xSrc: Int, var ySrc: Int, var zSrc: Int, var xDest: Int, var yDest: Int, var zDest: Int) extends IMessage
{
	import BufferUtils._

    def this() = this(0, 0, 0, 0, 0, 0)

    override def fromBytes(buffer: ByteBuf)
    {
		this.xSrc = buffer.readInt()
		this.ySrc = buffer.readInt()
		this.zSrc = buffer.readInt()
		this.xDest = buffer.readInt()
		this.yDest = buffer.readInt()
		this.zDest = buffer.readInt()
    }
    override def toBytes(buffer: ByteBuf)
    {
		buffer.writeInt(this.xSrc)
		buffer.writeInt(this.ySrc)
		buffer.writeInt(this.zSrc)
		buffer.writeInt(this.xDest)
		buffer.writeInt(this.yDest)
		buffer.writeInt(this.zDest)
    }
}
// The packet sent when terminal(flag) was broken
final case class PacketTerminalBroken(var x: Int, var y: Int, var z: Int) extends IMessage
{
	import BufferUtils._

	def this() = this(0, 0, 0)

	override def fromBytes(buffer: ByteBuf)
	{
		this.x = buffer.readInt()
		this.y = buffer.readInt()
		this.z = buffer.readInt()
	}
	override def toBytes(buffer: ByteBuf)
	{
		buffer.writeInt(this.x)
		buffer.writeInt(this.y)
		buffer.writeInt(this.z)
	}
}

final class LinkEstablishedPacketHandler extends IMessageHandler[PacketLinkEstablished, IMessage]
{
    override def onMessage(message: PacketLinkEstablished, context: MessageContext) =
    {
        WorldEvents.registerCoordinatePair(message.xSrc, message.ySrc, message.zSrc,
            message.xDest, message.yDest, message.zDest)
        // ModInstance.logger.info(s"Add link from (${message.xSrc}, ${message.ySrc}, ${message.zSrc}) to (${message.xDest}, ${message.yDest}, ${message.zDest})")
        null
    }
}
final class TerminalBrokenPacketHandler extends IMessageHandler[PacketTerminalBroken, IMessage]
{
	override def onMessage(message: PacketTerminalBroken, context: MessageContext) =
	{
		WorldEvents.breakFromTerminal(message.x, message.y, message.z)
		// ModInstance.logger.info(s"Break terminal at (${message.x}, ${message.y}, ${message.z})")
		null
	}
}
