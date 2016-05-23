package com.cterm2.miniflags

import org.apache.logging.log4j.LogManager

import cpw.mods.fml.common.Mod
import cpw.mods.fml.common.event._
import cpw.mods.fml.common.SidedProxy
import cpw.mods.fml.relauncher._

@Mod(modid=ModInstance.ID, name=ModInstance.Name, version=ModInstance.VersionStr, modLanguage="scala")
object ModInstance
{
	final val ID = "miniflags"
	final val Name = "MiniFlags"
	final val VersionStr = "1.0-alpha"
	
	val logger = LogManager.getLogger("MiniFlags")
	logger.info(s"${Name} version ${VersionStr}")
	
	trait IProxy
	{
		def init(): Unit
	}
	@SideOnly(Side.SERVER)
	final class ServerProxy extends IProxy { def init(){} }
	@SideOnly(Side.CLIENT)
	final class ClientProxy extends IProxy
	{
		def init()
		{
			flag.registerClient
		}
	}
	@SidedProxy(modId=ModInstance.ID, serverSide="com.cterm2.miniflags.ModInstance$ServerProxy", clientSide="com.cterm2.miniflags.ModInstance$ClientProxy")
	var proxy: IProxy = null
	
	@Mod.EventHandler
	def init(e: FMLInitializationEvent)
	{
		flag.register
		proxy.init()
	}
}

object CreativeTab extends net.minecraft.creativetab.CreativeTabs(ModInstance.ID)
{
	import net.minecraft.init.Blocks, net.minecraft.item.Item
	
	override val getTabIconItem = Item.getItemFromBlock(Blocks.wool)
}
