package com.cterm2.miniflags

import cpw.mods.fml.common.eventhandler._
import cpw.mods.fml.common.gameevent.PlayerEvent

object FMLEvents
{
	@SubscribeEvent
	def onPlayerJoin(event: PlayerEvent.PlayerLoggedInEvent)
	{
		// ModInstance.logger.info("Player Logged In")
		ObjectManager.instanceForWorld(event.player.worldObj) foreach (_.synchronizeAllLinks(event.player))
	}
}
