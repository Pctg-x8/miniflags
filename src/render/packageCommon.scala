package com.cterm2.miniflags

package object render
{
	var TriRenderID = 0

	def init()
	{
		import cpw.mods.fml.client.registry.{RenderingRegistry, ClientRegistry}

		TriRenderID = RenderingRegistry.getNextAvailableRenderId
		RenderingRegistry.registerBlockHandler(TriBlockRenderer)

		ClientRegistry.bindTileEntitySpecialRenderer(classOf[TileData], nameTag.TERenderer)
	}
}
