package com.cterm2.miniflags

import cpw.mods.fml.common.network.simpleimpl.{IMessage, IMessageHandler, MessageContext}
import io.netty.buffer.ByteBuf
import cpw.mods.fml.common.network.ByteBufUtils._
import net.minecraftforge.common.DimensionManager
import net.minecraft.entity.player.EntityPlayerMP
import org.apache.commons.io.Charsets
import common.EnumColor

// Common BufferUtils
package object BufferUtils
{
	implicit class Methods(val buffer: ByteBuf) extends AnyVal
	{
		def readInt() = readVarInt(buffer, 5)
		def writeInt(v: Int) = { writeVarInt(buffer, v, 5); this }
		def readShort() = readVarShort(buffer)
		def writeShort(v: Int) = { writeVarShort(buffer, v); this }
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
	// The message sent when a new term is registered
	object NewTerm
	{
		final class Message(var pos: Coordinate, var color: EnumColor.Type) extends IMessage
		{
			import BufferUtils._

			def this() = this(Coordinate(0, 0, 0), EnumColor.White)
			override def fromBytes(buffer: ByteBuf)
			{
				this.pos = buffer.readCoordinate()
				this.color = EnumColor fromValue buffer.readShort()
			}
			override def toBytes(buffer: ByteBuf)
			{
				buffer.writeCoordinate(this.pos).writeShort(color.value)
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
				ClientLinkManager.addTerm(msg.pos, msg.color)
				null
			}
		}

		def apply(pos: Coordinate, color: EnumColor.Type) = new Message(pos, color)
	}
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
			def broadcastIn(dim: Int) { ModInstance.network.sendToDimension(this, dim) }
			def dispatchTo(player: EntityPlayerMP) { ModInstance.network.sendTo(this, player) }
		}
		final class Handler extends IMessageHandler[Message, IMessage]
		{
			override def onMessage(msg: Message, context: MessageContext) =
			{
				// Server Side Update and Reflect to All
				ObjectManager.instanceForWorld(context.getServerHandler.playerEntity.worldObj) foreach (_.updateName(msg.pos, msg.newName))
				UpdateFlagName(msg.pos, msg.newName) broadcastIn context.getServerHandler.playerEntity.dimension
				null
			}
		}
		final class ClientHandler extends IMessageHandler[Message, IMessage]
		{
			override def onMessage(msg: Message, context: MessageContext) =
			{
				ClientLinkManager.updateName(msg.pos, msg.newName)
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
		ModInstance.network.registerMessage(classOf[UpdateFlagName.ClientHandler], classOf[UpdateFlagName.Message], 5, Side.CLIENT)
		ModInstance.network.registerMessage(classOf[NewTerm.Handler], classOf[NewTerm.Message], 6, Side.CLIENT)
	}
}
