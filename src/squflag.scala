package com.cterm2.miniflags

import net.minecraft.{block, item, util}
import block._, item._, util._
import net.minecraft.world.World
import net.minecraft.entity.player.EntityPlayer

// Square Flag
package object squflag
{
	def init()
	{
		import com.cterm2.tetra.ContentRegistry

		ContentRegistry register Block0.setCreativeTab(CreativeTab) in classOf[BlockSummoner] as "FlagBlock.squ"
		ContentRegistry register Block90 as "FlagBlock.squ.90deg"
		ContentRegistry register Block180 as "FlagBlock.squ.180deg"
		ContentRegistry register Block270 as "FlagBlock.squ.270deg"
	}

	final class BlockSummoner(block: Block) extends ItemFlagBase(block)
	{
		override def getBlockByDirection(dir: Int) = dir match
		{
			case 0 => Block0
			case 1 => Block90
			case 2 => Block180
			case 3 => Block270
		}
		override val baseName = "FlagBlock.squ"
	}
	sealed class BlockBase extends BlockFlagBase
	{
		override def getRenderType = render.SquareRenderID
		override def getPickBlock(target: MovingObjectPosition, world: World, x: Int, y: Int, z: Int, player: EntityPlayer) =
			new ItemStack(Item.getItemFromBlock(Block0), 1, world.getBlockMetadata(x, y, z))
		override val baseName = "FlagBlock.squ"
	}
	object Block0 extends BlockBase
	object Block90 extends BlockBase
	object Block180 extends BlockBase
	object Block270 extends BlockBase
}
