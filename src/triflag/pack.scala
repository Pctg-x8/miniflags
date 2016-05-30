package com.cterm2.miniflags

package object triflags
{
	import com.cterm2.tetra.ContentRegistry
	import cpw.mods.fml.client.registry.{ClientRegistry, RenderingRegistry}

	var itemBlockSummoner: BlockSummoner = null

	def register
	{
		ContentRegistry register Block0.setCreativeTab(CreativeTab) in classOf[BlockSummoner] as "FlagBlock"
		ContentRegistry register Block90 as "FlagBlock.90deg"
		ContentRegistry register Block180 as "FlagBlock.180deg"
		ContentRegistry register Block270 as "FlagBlock.270deg"
		ContentRegistry register classOf[TileData] as "FlagTileData"

		itemBlockSummoner = Item.getItemFromBlock(Block0).asInstanceOf[BlockSummoner]
	}

	def playLinkedSound(world: World, x: Int, y: Int, z: Int)
	{
		world.playSoundEffect((x.toFloat + 0.5f).toDouble, (y.toFloat + 0.5f).toDouble, (z.toFloat + 0.5f).toDouble,
			"random.orb", 0.25f, 0.5f)
	}
}
