package com.cterm2.miniflags

import cpw.mods.fml.common.network.simpleimpl.{IMessage, IMessageHandler, MessageContext}
import io.netty.buffer.ByteBuf
import cpw.mods.fml.common.network.ByteBufUtils._
import net.minecraftforge.common.DimensionManager
import net.minecraft.entity.player.EntityPlayerMP
import org.apache.commons.io.Charsets

// Common BufferUtils
package object BufferUtils
{
	implicit class Methods(val buffer: ByteBuf) extends AnyVal
	{
		def readInt() = readVarInt(buffer, 5)
		def writeInt(v: Int) = { writeVarInt(buffer, v, 5); this }
		def readCoordinate() =
		{
			val x = readInt()
			val y = readInt()
			val z = readInt()
			Coordinate(x, y, z)
		}
		def writeCoordinate(c: Coordinate) =
		{
			val Coordinate(x, y, z) = c
			writeInt(x); writeInt(y); writeInt(z)
			this
		}
		def readString() = readUTF8String(buffer)
		def writeString(str: String) = { writeUTF8String(buffer, new String(str.getBytes(Charsets.UTF_8), Charsets.UTF_8)) }
	}
}

package intercommands
{
	// The message sent when a new link is established
	object NewLink
	{
		final class Message(var src: Coordinate, var dest: Coordinate) extends IMessage
		{
			import BufferUtils._

			def this() = this(Coordinate(0, 0, 0), Coordinate(0, 0, 0))
			override def fromBytes(buffer: ByteBuf)
			{
				this.src = buffer.readCoordinate()
				this.dest = buffer.readCoordinate()
			}
			override def toBytes(buffer: ByteBuf)
			{
				buffer.writeCoordinate(this.src).writeCoordinate(this.dest)
			}

			def dispatchTo(player: EntityPlayerMP)
			{
				ModInstance.network.sendTo(this, player)
			}
			def broadcastIn(dim: Int)
			{
				ModInstance.network.sendToDimension(this, dim)
			}
		}
		final class Handler extends IMessageHandler[Message, IMessage]
		{
			override def onMessage(msg: Message, context: MessageContext) =
			{
				ClientLinkManager.addLink(msg.src, msg.dest)
				null
			}
		}

		def apply(src: Coordinate, dest: Coordinate) = new Message(src, dest)
	}
	// The message sent when terminal(flag) was broken
	object UnregisterTerm
	{
		final class Message(var pos: Coordinate) extends IMessage
		{
			import BufferUtils._

			def this() = this(Coordinate(0, 0, 0))
			override def fromBytes(buffer: ByteBuf)
			{
				this.pos = buffer.readCoordinate()
			}
			override def toBytes(buffer: ByteBuf)
			{
				buffer.writeCoordinate(this.pos)
			}

			def broadcastIn(dim: Int)
			{
				ModInstance.network.sendToDimension(this, dim)
			}
		}
		final class Handler extends IMessageHandler[Message, IMessage]
		{
			override def onMessage(msg: Message, context: MessageContext) =
			{
				ClientLinkManager.unregisterTerm(msg.pos)
				null
			}
		}

		def apply(pos: Coordinate) = new Message(pos)
	}
	// The message sent when player is logging in a new world
	object InitializeDimension
	{
		final class Message extends IMessage
		{
			import BufferUtils._

			override def fromBytes(buffer: ByteBuf) {}
			override def toBytes(buffer: ByteBuf) {}
		}
		final class Handler extends IMessageHandler[Message, IMessage]
		{
			override def onMessage(msg: Message, context: MessageContext) =
			{
				ClientLinkManager.initialize()
				null
			}
		}

		def dispatchTo(player: EntityPlayerMP)
		{
			ModInstance.network.sendTo(new Message, player)
		}
	}
	// The message sent when name of flag is changed
	object UpdateFlagName
	{
		final class Message(var pos: Coordinate, var newName: String) extends IMessage
		{
			import BufferUtils._

			def this() = this(Coordinate(0, 0, 0), "")
			override def fromBytes(buffer: ByteBuf)
			{
				this.pos = buffer.readCoordinate()
				this.newName = buffer.readString()
			}
			override def toBytes(buffer: ByteBuf)
			{
				buffer.writeCoordinate(this.pos).writeString(this.newName)
			}

			def dispatchToServer()
			{
				ModInstance.network.sendToServer(this)
			}
		}
		final class Handler extends IMessageHandler[Message, IMessage]
		{
			override def onMessage(msg: Message, context: MessageContext) =
			{
				val Coordinate(x, y, z) = msg.pos
				Option(context.getServerHandler().playerEntity.worldObj.getTileEntity(x, y, z)) match
				{
				case Some(tile: flag.TileData) => tile.name = msg.newName
				case _ => ModInstance.logger.warn("Invalid message")
				}
				null
			}
		}

		def apply(pos: Coordinate, newName: String) = new Message(pos, newName)
	}
}

package object intercommands
{
	import cpw.mods.fml.relauncher.Side

	def registerMessages()
	{
		ModInstance.network.registerMessage(classOf[NewLink.Handler], classOf[NewLink.Message], 1, Side.CLIENT)
		ModInstance.network.registerMessage(classOf[UnregisterTerm.Handler], classOf[UnregisterTerm.Message], 2, Side.CLIENT)
		ModInstance.network.registerMessage(classOf[InitializeDimension.Handler], classOf[InitializeDimension.Message], 3, Side.CLIENT)
		ModInstance.network.registerMessage(classOf[UpdateFlagName.Handler], classOf[UpdateFlagName.Message], 4, Side.SERVER)
	}
}
