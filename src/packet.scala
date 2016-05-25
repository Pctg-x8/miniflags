package com.cterm2.miniflags

import cpw.mods.fml.common.network.simpleimpl.{IMessage, IMessageHandler, MessageContext}
import io.netty.buffer.ByteBuf

final case class PacketLinkStateChanged(
    var xSrc: Int, var ySrc: Int, var zSrc: Int, var xDest: Int, var yDest: Int, var zDest: Int) extends IMessage
{
    import cpw.mods.fml.common.network.ByteBufUtils._

    def this() = this(0, 0, 0, 0, 0, 0)

    override def fromBytes(buffer: ByteBuf)
    {
        this.xSrc = readVarInt(buffer, 5)
        this.ySrc = readVarInt(buffer, 5)
        this.zSrc = readVarInt(buffer, 5)
        this.xDest = readVarInt(buffer, 5)
        this.yDest = readVarInt(buffer, 5)
        this.zDest = readVarInt(buffer, 5)
    }
    override def toBytes(buffer: ByteBuf)
    {
        writeVarInt(buffer, this.xSrc, 5)
        writeVarInt(buffer, this.ySrc, 5)
        writeVarInt(buffer, this.zSrc, 5)
        writeVarInt(buffer, this.xDest, 5)
        writeVarInt(buffer, this.yDest, 5)
        writeVarInt(buffer, this.zDest, 5)
    }
}

final class LinkStateChangedPacketHandler extends IMessageHandler[PacketLinkStateChanged, IMessage]
{
    override def onMessage(message: PacketLinkStateChanged, context: MessageContext) =
    {
        WorldRenderer.registerCoordinatePair(message.xSrc, message.ySrc, message.zSrc,
            message.xDest, message.yDest, message.zDest)
        ModInstance.logger.info(s"Add link from (${message.xSrc}, ${message.ySrc}, ${message.zSrc}) to (${message.xDest}, ${message.yDest}, ${message.zDest})")
        null
    }
}
