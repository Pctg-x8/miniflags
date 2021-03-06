package com.cterm2.miniflags

import org.apache.logging.log4j.LogManager

import cpw.mods.fml.common.{Mod, SidedProxy, FMLCommonHandler}
import cpw.mods.fml.common.event._
import cpw.mods.fml.relauncher._
import cpw.mods.fml.common.network.NetworkRegistry
import net.minecraftforge.common.MinecraftForge
import com.cterm2.tetra.ContentRegistry

@Mod(modid=ModInstance.ID, name=ModInstance.Name, version=ModInstance.VersionStr, modLanguage="scala")
object ModInstance
{
	final val ID = "miniflags"
	final val Name = "MiniFlags"
	final val VersionStr = "1.0-alpha"

	final val logger = LogManager.getLogger("MiniFlags")
	logger.info(s"${Name} version ${VersionStr}")
	final val network = NetworkRegistry.INSTANCE.newSimpleChannel(ID)

	final val InterfaceID = 1

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
			render.init()
		}
	}
	@SidedProxy(modId=ModInstance.ID, serverSide="com.cterm2.miniflags.ModInstance$ServerProxy", clientSide="com.cterm2.miniflags.ModInstance$ClientProxy")
	var proxy: IProxy = null

	@Mod.EventHandler
	def init(e: FMLInitializationEvent)
	{
		triflag.init()
		squflag.init()
		proxy.init()
		ContentRegistry register classOf[TileData] as "FlagTileData"

		intercommands.registerMessages()
		MinecraftForge.EVENT_BUS.register(WorldEvents)
		FMLCommonHandler.instance.bus.register(FMLEvents)
		NetworkRegistry.INSTANCE.registerGuiHandler(this, GuiHandler)
	}
}

object CreativeTab extends net.minecraft.creativetab.CreativeTabs(ModInstance.ID)
{
	import net.minecraft.init.Blocks, net.minecraft.item.Item

	override lazy val getTabIconItem = Item.getItemFromBlock(triflag.Block0)
}
