package com.cterm2.miniflags

import net.minecraft.nbt._
import net.minecraft.world._
import net.minecraft.client.multiplayer.WorldClient
import net.minecraft.entity.player.{EntityPlayer, EntityPlayerMP}
import net.minecraft.util.ChatComponentText
import cpw.mods.fml.relauncher.{Side, SideOnly}
import net.minecraftforge.common.DimensionManager
import scalaz._, Scalaz._
import common.EnumColor

final case class Coordinate(x: Int, y: Int, z: Int)
final case class Link(src: Coordinate, dest: Coordinate)
final case class Term(pos: Coordinate, color: EnumColor.Type, var name: Option[String])

// Client Side Structure of Links between Flags
object ClientLinkManager
{
	import scala.collection.mutable._

	private var _terms = MutableList[Term]()
	private var _links = MutableList[Link]()
	def links = this._links					//* readonly "links"
	def terms = this._terms					//* readonly "terms"

	def addTerm(pos: Coordinate, color: EnumColor.Type)
	{
		ModInstance.logger.info(s"Added Terminal at (${pos.x}, ${pos.y}, ${pos.z}) with color ${color.getClass.getSimpleName}")
		this._terms += Term(pos, color, None)
	}
	def updateName(pos: Coordinate, str: String)
	{
		ModInstance.logger.info(s"Client Side UpdateName: $str")
		this._terms filter { case Term(tp, _, _) => tp == pos } foreach (_.name = Some(str))
	}
	def addLink(src: Coordinate, dest: Coordinate)
	{
		this._links += Link(src, dest)
	}
	def unregisterTerm(p: Coordinate)
	{
		this._links = this.links filterNot { case Link(s, d) => s == p || d == p }
		this._terms = this.terms filterNot { case Term(tp, _, _) => tp == p }
	}
	def initialize()
	{
		this._links = MutableList[Link]()
		this._terms = MutableList[Term]()
	}

	def getLinkDestination(coord: Coordinate) = this.links collect { case Link(cs, d) if cs == coord => d } headOption
	def getTermFromID(world: World, id: Long) =
	{
		val coord = Coordinate(TileData.xCoordFromID(id), TileData.yCoordFromID(id), TileData.zCoordFromID(id))
		this.terms find { case Term(pos, _, _) => coord == pos }
	}
	def getTermFromIDStr(world: World, id: String) = getTermFromID(world, java.lang.Long.parseUnsignedLong(id))
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
		case Term(Coordinate(x, y, z), color, name) =>
			val tag = new NBTTagCompound
			tag("x") = x; tag("y") = y; tag("z") = z; tag("cint") = color.value; tag("name") = name
			tag
		case Link(Coordinate(sx, sy, sz), Coordinate(dx, dy, dz)) =>
			val tag = new NBTTagCompound
			tag("xSrc") = sx; tag("ySrc") = sy; tag("zSrc") = sz
			tag("xDest") = dx; tag("yDest") = dy; tag("zDest") = dz
			tag
	}
	private def getTermFromTag(tag: NBTTagCompound) =
		(tag[Int]("x") :: tag[Int]("y") :: tag[Int]("z") :: tag[Int]("cint") :: Nil).sequence map
		{
			case List(x, y, z, cint) => Term(Coordinate(x, y, z), EnumColor fromValue cint, tag[String]("name"))
		}
	private def getLinkFromTag(tag: NBTTagCompound) =
		(tag[Int]("xSrc") :: tag[Int]("ySrc") :: tag[Int]("zSrc") :: tag[Int]("xDest") :: tag[Int]("yDest") :: tag[Int]("zDest") :: Nil).sequence map
		{
			case List(sx, sy, sz, dx, dy, dz) => Link(Coordinate(sx, sy, sz), Coordinate(dx, dy, dz))
		}

	private var _terminals = Seq[Term]()
	private var _links = Seq[Link]()
	// Read-only Uniform Accessing and Auto Marking as Dirty
	def terminals = this._terminals
	def links = this._links
	private def terminals_=(list: Seq[Term]) { this._terminals = list; this.markDirty() }
	private def links_=(list: Seq[Link]) { this._links = list; this.markDirty() }

	def register(x: Int, y: Int, z: Int, color: EnumColor.Type)
	{
		this.terminals = this.terminals :+ Term(Coordinate(x, y, z), color, None)
		ModInstance.logger.info(s"Registered Terminal at ($x, $y, $z) with color ${color.getClass.getSimpleName}")
		intercommands.NewTerm(Coordinate(x, y, z), color) broadcastIn worldObj.provider.dimensionId
	}
	def updateName(pos: Coordinate, str: String)
	{
		this.terminals filter { case Term(tp, _, _) => tp == pos } foreach (_.name = Some(str))
		Option(this.worldObj.getTileEntity(pos.x, pos.y, pos.z)) foreach
		{
			case x: TileData => x.name = str
			case _ => ModInstance.logger.warn("TileData not found")
		}
	}
	def unregister(x: Int, y: Int, z: Int)
	{
		val crd = Coordinate(x, y, z)
		this.terminals = this.terminals filterNot { case Term(pos, _, _) => pos == crd }
		this.links = this.links filterNot { case Link(cs, cd) => cs == crd || cd == crd }
		intercommands.UnregisterTerm(crd) broadcastIn worldObj.provider.dimensionId
	}
	def link(player: EntityPlayer, sx: Int, sy: Int, sz: Int, dx: Int, dy: Int, dz: Int)
	{
		val (src, dst) = (Coordinate(sx, sy, sz), Coordinate(dx, dy, dz))
		val termCoords = this.terminals map { case Term(pos, _, _) => pos }

		if((termCoords contains src) && (termCoords contains dst))
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

		this.terminals map getTag foreach tagTerminals.appendTag
		this.links map getTag foreach tagLinks.appendTag
		tag("Terminals") = tagTerminals
		tag("Links") = tagLinks
	}
	override def readFromNBT(tag: NBTTagCompound)
	{
		// ModInstance.logger.info("Loading World Flag Data...")

		for(terminals <- tag[NBTTagList]("Terminals"))
		{
			this.terminals = ((0 until terminals.tagCount).toList map terminals.getCompoundTagAt map getTermFromTag).sequence | Nil
		}
		for(links <- tag[NBTTagList]("Links"))
		{
			this.links = ((0 until links.tagCount).toList map links.getCompoundTagAt map getLinkFromTag).sequence | Nil
		}
	}
	def synchronizeAllLinks(player: EntityPlayerMP)
	{
		intercommands.InitializeDimension dispatchTo player
		for(Term(p, c, n) <- this.terminals)
		{
			intercommands.NewTerm(p, c) dispatchTo player
			n foreach { intercommands.UpdateFlagName(p, _) dispatchTo player }
		}
		for(Link(src, dest) <- this.links)
		{
			intercommands.NewLink(src, dest) dispatchTo player
		}
	}
}
