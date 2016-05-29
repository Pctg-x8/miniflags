package com.cterm2.miniflags

import cpw.mods.fml.common.network.IGuiHandler
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.world._
import net.minecraft.client.multiplayer.WorldClient
import scalaz._, Scalaz._

object GuiHandler extends IGuiHandler
{
	private def getFlagTileData(world: World, x: Int, y: Int, z: Int) =
		Option(world.getTileEntity(x, y, z)) collect { case x: flag.TileData => x }

	override def getServerGuiElement(id: Int, player: EntityPlayer, world: World, x: Int, y: Int, z: Int) = id match
	{
		case ModInstance.InterfaceID => (getFlagTileData(world, x, y, z) map (new flag.FlagSettingsContainer(_))) | null
		case _ => null
	}
	override def getClientGuiElement(id: Int, player: EntityPlayer, world: World, x: Int, y: Int, z: Int) = id match
	{
		case ModInstance.InterfaceID => (getFlagTileData(world, x, y, z) map (new flag.FlagSettingsInterface(world.asInstanceOf[WorldClient], _))) | null
		case _ => null
	}
}
