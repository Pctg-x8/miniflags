package com.cterm2.miniflags

import org.apache.logging.log4j.LogManager

import cpw.mods.fml.common.{Mod, SidedProxy, FMLCommonHandler}
import cpw.mods.fml.common.event._
import cpw.mods.fml.relauncher._
import cpw.mods.fml.common.network.NetworkRegistry
import net.minecraftforge.common.MinecraftForge

@Mod(modid=ModInstance.ID, name=ModInstance.Name, version=ModInstance.VersionStr, modLanguage="scala")
object ModInstance
{
	final val ID = "miniflags"
	final val Name = "MiniFlags"
	final val VersionStr = "1.0-alpha"

	final val logger = LogManager.getLogger("MiniFlags")
	logger.info(s"${Name} version ${VersionStr}")
	final val network = NetworkRegistry.INSTANCE.newSimpleChannel(ID)

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

		network.registerMessage(classOf[LinkEstablishedPacketHandler], classOf[PacketLinkEstablished], 1, Side.CLIENT)
		network.registerMessage(classOf[TerminalBrokenPacketHandler], classOf[PacketTerminalBroken], 2, Side.CLIENT)
		MinecraftForge.EVENT_BUS.register(WorldEvents)
		FMLCommonHandler.instance.bus.register(FMLEvents)
	}
}

object CreativeTab extends net.minecraft.creativetab.CreativeTabs(ModInstance.ID)
{
	import net.minecraft.init.Blocks, net.minecraft.item.Item

	override val getTabIconItem = Item.getItemFromBlock(Blocks.wool)
}
