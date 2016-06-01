package com.cterm2.miniflags

import net.minecraft.tileentity.TileEntity
import net.minecraft.nbt._
import net.minecraft.network.play.server.S35PacketUpdateTileEntity
import net.minecraft.network.NetworkManager

// Common TileData with Flag Name
final class TileData extends TileEntity
{
	import com.cterm2.tetra.ActiveNBTRecord._
	import scalaz._, Scalaz._

	// Internal Data and Read-only synthetic values
	var _name: Option[String] = None
	def hashID = ((this.yCoord.toLong & 0xff) << 56) | ((this.xCoord.toLong & 0xfffffff) << 28) | (this.zCoord.toLong & 0xfffffff)
	def coord = Coordinate(this.xCoord, this.yCoord, this.zCoord)

	// Name Utilies
	def name = this._name
	def name_=(str: String)
	{
		this._name = Some(str); this.markDirty(); this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord)
	}
	def hasCustomName = this.name.isDefined
	def nameOrDefaultLocalized = this.name | this.worldObj.getBlock(this.xCoord, this.yCoord, this.zCoord).getLocalizedName()

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
