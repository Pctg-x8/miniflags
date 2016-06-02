package com.cterm2.miniflags

import net.minecraft.item.Item
import net.minecraft.tileentity.TileEntity
import net.minecraft.nbt._
import net.minecraft.network.play.server.S35PacketUpdateTileEntity
import net.minecraft.network.NetworkManager
import net.minecraft.world.World

// Common TileData with Flag Name
final class TileData extends TileEntity
{
	import com.cterm2.tetra.ActiveNBTRecord._
	import scalaz._, Scalaz._

	// Internal Data and Read-only synthetic values
	var _name: Option[String] = None
	def hashID = TileData.makeID(this.xCoord, this.yCoord, this.zCoord)
	def coord = Coordinate(this.xCoord, this.yCoord, this.zCoord)

	// Name Utilies
	def name = this._name
	def name_=(str: String)
	{
		this._name = Some(str); this.markDirty(); this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord)
	}
	def hasCustomName = this.name.isDefined
	def nameOrDefaultLocalized = this.name |
	{
		val block = this.worldObj.getBlock(this.xCoord, this.yCoord, this.zCoord).asInstanceOf[BlockFlagBase]
		block.getLocalizedNameFromMetaValue(this.worldObj.getBlockMetadata(this.xCoord, this.yCoord, this.zCoord))
	}

	// Data Synchronizations
	override def writeToNBT(tag: NBTTagCompound)
	{
		super.writeToNBT(tag)
		tag("CustomName") = this.name
	}
	override def readFromNBT(tag: NBTTagCompound)
	{
		super.readFromNBT(tag)
		this._name = tag[String]("CustomName")
	}
	override def getDescriptionPacket() =
		(Option(new NBTTagCompound) >>= { x =>
			x("CustomName") = this.name
			Option(new S35PacketUpdateTileEntity(this.xCoord, this.yCoord, this.zCoord, 1, x))
		}) | null
	override def onDataPacket(network: NetworkManager, packet: S35PacketUpdateTileEntity)
	{
		for(x <- Option(packet) >>= (x => Option(x.func_148857_g)))
		{
			this._name = x[String]("CustomName")
		}
	}
}
object TileData
{
	// Utilities for TileData
	def makeID(x: Int, y: Int, z: Int) = ((y.toLong & 0xff) << 56) | ((x.toLong & 0xfffffff) << 28) | (z.toLong & 0xfffffff)
	def xCoordFromID(id: Long) = toIntWithSignificantExtension((id >> 28).toInt & 0xfffffff)
	def yCoordFromID(id: Long) = ((id >> 56).toInt & 0xff).toInt
	def zCoordFromID(id: Long) = toIntWithSignificantExtension(id.toInt & 0xfffffff)
	private def toIntWithSignificantExtension(id: Int) = if((id & 0x8000000) != 0) -(id & 0x7ffffff) else id
}
