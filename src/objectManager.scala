package com.cterm2.miniflags

import net.minecraft.nbt._
import net.minecraft.world._
import net.minecraft.client.multiplayer.WorldClient
import net.minecraft.entity.player.{EntityPlayer, EntityPlayerMP}
import net.minecraft.util.ChatComponentText
import cpw.mods.fml.relauncher.{Side, SideOnly}
import net.minecraftforge.common.DimensionManager
import scalaz._, Scalaz._

final case class Coordinate(x: Int, y: Int, z: Int)
final case class Link(src: Coordinate, dest: Coordinate)

// Client Side Structure of Links between Flags
object ClientLinkManager
{
	private var _links = Seq[Link]()
	def links = this._links					//* readonly "links"

	def addLink(src: Coordinate, dest: Coordinate)
	{
		this._links = this.links :+ Link(src, dest)
	}
	def unregisterTerm(p: Coordinate)
	{
		this._links = this.links filterNot { case Link(s, d) => s == p || d == p }
	}
	def initialize()
	{
		this._links = Seq[Link]()
	}

	def getLinkDestination(coord: Coordinate) = this.links collect { case Link(cs, d) if cs == coord => d } headOption
}

// Server Side Structure of Links and Object Coordinates of Flags
object ObjectManager
{
	final val ID = ModInstance.ID + "_FlagObjectManager"

	def instanceForWorld(world: World) =
	{
		assert(!world.isRemote)
		val mappedID = s"${ID}-${world.provider.dimensionId}"
		(Option(world.loadItemData(classOf[ObjectManager], mappedID)) match
		{
			case Some(instance: ObjectManager) => Some(instance)
			case None => Option(new ObjectManager(mappedID)) map (x => { world.setItemData(mappedID, x); x })
			case _ => None
		}) map (_.setWorld(world))
	}
}
final class ObjectManager(id: String) extends WorldSavedData(id)
{
	import com.cterm2.tetra.ActiveNBTRecord._, ObjectManager._
	import scalaz._, Scalaz._

	private var worldObj: World = null
	def setWorld(w: World) = { this.worldObj = w; this }
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

	private var _terminalCoordinates = Seq[Coordinate]()
	private var _links = Seq[Link]()
	// Read-only Uniform Accessing and Auto Marking as Dirty
	def terminalCoordinates = this._terminalCoordinates
	def links = this._links
	private def terminalCoordinates_=(list: Seq[Coordinate]) { this._terminalCoordinates = list; this.markDirty() }
	private def links_=(list: Seq[Link]) { this._links = list; this.markDirty() }

	def register(x: Int, y: Int, z: Int) { this.terminalCoordinates = this.terminalCoordinates :+ Coordinate(x, y, z) }
	def unregister(x: Int, y: Int, z: Int)
	{
		val crd = Coordinate(x, y, z)
		this.terminalCoordinates = this.terminalCoordinates filterNot (_ == crd)
		this.links = this.links filterNot { case Link(cs, cd) => cs == crd || cd == crd }
		intercommands.UnregisterTerm(crd) broadcastIn worldObj.provider.dimensionId
	}
	def link(player: EntityPlayer, sx: Int, sy: Int, sz: Int, dx: Int, dy: Int, dz: Int)
	{
		val (src, dst) = (Coordinate(sx, sy, sz), Coordinate(dx, dy, dz))

		if((this.terminalCoordinates contains src) && (this.terminalCoordinates contains dst))
		{
			this.links = this.links :+ Link(src, dst)
			intercommands.NewLink(src, dst) broadcastIn player.dimension
			player.addChatComponentMessage(new ChatComponentText(s"Successfully linked from ($sx, $sy, $sz) to ($dx, $dy, $dz)"))
			common.playLinkedSound(this.worldObj, sx, sy, sz)
		}
		else ModInstance.logger.warn(s"Invalid Linking from ($sx, $sy, $sz) to ($dx, $dy, $dz)")
	}
	def getLinkDestinationFrom(x: Int, y: Int, z: Int) = getLinkDestinationFrom_(Coordinate(x, y, z))
	def getLinkedCoordinate(x: Int, y: Int, z: Int) = getLinkedCoordinate_(Coordinate(x, y, z))

	// impl //
	private def getLinkDestinationFrom_(coord: Coordinate) = this.links find { case Link(src, _) => src == coord } map { case Link(_, t) => t }
	private def getLinkedCoordinate_(coord: Coordinate) = this.links collect
	{
		case Link(src, dest) if src == coord => dest
		case Link(src, dest) if dest == coord => src
	} headOption

	override def writeToNBT(tag: NBTTagCompound)
	{
		// ModInstance.logger.info("Saving World Flag Data...")

		val tagTerminals = new NBTTagList
		val tagLinks = new NBTTagList

		this.terminalCoordinates map getTag foreach tagTerminals.appendTag
		this.links map getTag foreach tagLinks.appendTag
		tag("Terminals") = tagTerminals
		tag("Links") = tagLinks
	}
	override def readFromNBT(tag: NBTTagCompound)
	{
		// ModInstance.logger.info("Loading World Flag Data...")

		for(terminals <- tag[NBTTagList]("Terminals"))
		{
			this.terminalCoordinates = ((0 until terminals.tagCount).toList map terminals.getCompoundTagAt map getCoordinateFromTag).sequence | Nil
		}
		for(links <- tag[NBTTagList]("Links"))
		{
			this.links = ((0 until links.tagCount).toList map links.getCompoundTagAt map getLinkFromTag).sequence | Nil
		}
	}
	def synchronizeAllLinks(player: EntityPlayerMP)
	{
		intercommands.InitializeDimension dispatchTo player
		for(Link(src, dest) <- this.links)
		{
			intercommands.NewLink(src, dest) dispatchTo player
		}
	}
}
