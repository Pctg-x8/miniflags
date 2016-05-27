package com.cterm2.miniflags

import java.io.{DataInputStream, DataOutputStream}
import net.minecraft.nbt._
import net.minecraft.world.{World, WorldSavedData}
import net.minecraft.entity.player.{EntityPlayer, EntityPlayerMP}
import net.minecraft.util.ChatComponentText

object ObjectManager
{
	final val ID = ModInstance.ID + "_FlagObjectManager"

	def instanceForWorld(world: World) = Option(world.loadItemData(classOf[ObjectManager], ID)) collect { case x: ObjectManager => x }
}
// Object(Flag) Manager
final class ObjectManager(id: String) extends WorldSavedData(id)
{
	import com.cterm2.tetra.ActiveNBTRecord._
	import scalaz._, Scalaz._

	final case class Coordinate(x: Int, y: Int, z: Int)
	final case class Link(src: Coordinate, dest: Coordinate)

	private def getTag(c: Any) = c match
	{
		case Coordinate(x, y, z) =>
			val tag = new NBTTagCompound
			tag("x") = x; tag("y") = y; tag("z") = z
			tag
		case Link(Coordinate(sx, sy, sz), Coordinate(dx, dy, dz)) =>
			val tag = new NBTTagCompound
			tag("xSrc") = sx; tag("ySrc") = sy; tag("zSrc") = sz
			tag("xDest") = dx; tag("yDest") = dy; tag("zDest") = dz
			tag
	}
	private def getCoordinateFromTag(tag: NBTTagCompound) =
		(tag[Int]("x") :: tag[Int]("y") :: tag[Int]("z") :: Nil).sequence map
		{
			case List(x, y, z) => Coordinate(x, y, z)
		}
	private def getLinkFromTag(tag: NBTTagCompound) =
		(tag[Int]("xSrc") :: tag[Int]("ySrc") :: tag[Int]("zSrc") :: tag[Int]("xDest") :: tag[Int]("yDest") :: tag[Int]("zDest") :: Nil).sequence map
		{
			case List(sx, sy, sz, dx, dy, dz) => Link(Coordinate(sx, sy, sz), Coordinate(dx, dy, dz))
		}

	private var terminalCoordinates = Seq[Coordinate]()
	private var links = Seq[Link]()

	def register(x: Int, y: Int, z: Int)
	{
		// ModInstance.logger.info(s"Register Object at ($x, $y, $z)")
		this.terminalCoordinates = this.terminalCoordinates :+ Coordinate(x, y, z)
		this.markDirty()
	}
	def unregister(x: Int, y: Int, z: Int)
	{
		// ModInstance.logger.info(s"Unregister Object at ($x, $y, $z)")
		this.terminalCoordinates = this.terminalCoordinates filterNot { case Coordinate(xx, yy, zz) => xx == x && yy == y && zz == z }
		this.links = this.links filterNot
		{
			case Link(Coordinate(sx, sy, sz), Coordinate(dx, dy, dz)) => (sx == x && sy == y && sz == z) || (dx == x && dy == y && dz == z)
		}
		ModInstance.network.sendToAll(new PacketTerminalBroken(x, y, z))
		this.markDirty()
	}
	def link(world: World, player: EntityPlayer, sx: Int, sy: Int, sz: Int, dx: Int, dy: Int, dz: Int)
	{
		val validation1 = this.terminalCoordinates find { case Coordinate(x, y, z) => sx == x && sy == y && sz == z }
		val validation2 = this.terminalCoordinates find { case Coordinate(x, y, z) => dx == x && dy == y && dz == z }

		(validation1, validation2) match
		{
		case (Some(s), Some(d)) =>
			this.links = this.links :+ Link(s, d)
			ModInstance.network.sendToAll(new PacketLinkEstablished(sx, sy, sz, dx, dy, dz))
			player.addChatComponentMessage(new ChatComponentText(s"Successfully linked from ($sx, $sy, $sz) to ($dx, $dy, $dz)"))
			flag.playLinkedSound(world, sx, sy, sz)
			this.markDirty()
		case _ => ModInstance.logger.warn(s"Invalid Linking from ($sx, $sy, $sz) to ($dx, $dy, $dz)")
		}
	}

	override def writeToNBT(tag: NBTTagCompound)
	{
		ModInstance.logger.info("Saving World Flag Data...")

		val tagTerminals = new NBTTagList
		val tagLinks = new NBTTagList

		this.terminalCoordinates map getTag foreach tagTerminals.appendTag
		this.links map getTag foreach tagLinks.appendTag
		tag("Terminals") = tagTerminals
		tag("Links") = tagLinks
	}
	override def readFromNBT(tag: NBTTagCompound)
	{
		ModInstance.logger.info("Loading World Flag Data...")

		for(terminals <- tag[NBTTagList]("Terminals"))
		{
			this.terminalCoordinates = ((0 until terminals.tagCount).toList map terminals.getCompoundTagAt map getCoordinateFromTag).sequence | Nil
		}
		for(links <- tag[NBTTagList]("Links"))
		{
			this.links = ((0 until links.tagCount).toList map links.getCompoundTagAt map getLinkFromTag).sequence | Nil
		}
	}
	def synchronizeAllLinks(player: EntityPlayer)
	{
		for(Link(Coordinate(sx, sy, sz), Coordinate(dx, dy, dz)) <- this.links)
		{
			ModInstance.network.sendTo(new PacketLinkEstablished(sx, sy, sz, dx, dy, dz), player.asInstanceOf[EntityPlayerMP])
		}
	}
}
