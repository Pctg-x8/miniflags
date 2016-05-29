package com.cterm2.miniflags

import net.minecraft.world.WorldServer
import cpw.mods.fml.common.eventhandler._
import cpw.mods.fml.common.gameevent.PlayerEvent
import net.minecraft.entity.player.EntityPlayerMP
import cpw.mods.fml.relauncher.{SideOnly, Side}
import net.minecraftforge.common.DimensionManager

object FMLEvents
{
	@SubscribeEvent
	def onPlayerJoin(event: PlayerEvent.PlayerLoggedInEvent)
	{
		ModInstance.logger.info("Player logged in: Initial syncing...")
		ObjectManager.instanceForWorld(event.player.worldObj) foreach (_.synchronizeAllLinks(event.player.asInstanceOf[EntityPlayerMP]))
	}
	@SubscribeEvent
	def onPlayerChangedDimension(event: PlayerEvent.PlayerChangedDimensionEvent)
	{
		ModInstance.logger.info(s"Player Changed Dimension(${event.fromDim}->${event.toDim}): Resyncing...")
		ObjectManager.instanceForWorld(event.player.worldObj) foreach (_.synchronizeAllLinks(event.player.asInstanceOf[EntityPlayerMP]))
	}
}
