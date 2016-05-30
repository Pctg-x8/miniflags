package com.cterm2.miniflags.triflag

// TileData of Triangle Flag

import net.minecraft.tileentity.TileEntity

// Simple Name Holder
class TileData extends TileEntity
{
	import mc.network.play.server.S35PacketUpdateTileEntity
	import mc.network.NetworkManager

	var _name: Option[String] = None
	def name = this._name
	def name_=(str: String)
	{
		this._name = Some(str); this.markDirty(); this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord)
	}
	def hashID = ((this.yCoord.toLong & 0xff) << 56) | ((this.xCoord.toLong & 0xfffffff) << 28) | (this.zCoord.toLong & 0xfffffff)
	def coord = Coordinate(xCoord, yCoord, zCoord)

	def hasCustomName = this.name.isDefined
	def nameOrDefaultLocalized = this.name | Block0.getLocalizedName()

	override def writeToNBT(tag: NBTTagCompound)
	{
		super.writeToNBT(tag)
		tag("CustomName") = name
	}
	override def readFromNBT(tag: NBTTagCompound)
	{
		super.readFromNBT(tag)
		this._name = tag[String]("CustomName")
	}
	override def getDescriptionPacket() =
		(Option(new NBTTagCompound) >>=
		{ x =>
			{
				x("CustomName") = this.name
				Option(new S35PacketUpdateTileEntity(this.xCoord, this.yCoord, this.zCoord, 1, x))
			}
		}) | null
	override def onDataPacket(network: NetworkManager, packet: S35PacketUpdateTileEntity)
	{
		for(x <- Option(packet) >>= (x => Option(x.func_148857_g)))
		{
			this._name = x[String]("CustomName")
		}
	}
}