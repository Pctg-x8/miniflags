package com.cterm2.miniflags

import net.minecraft.block._
import net.minecraft.item._
import net.minecraft.util._
import net.minecraft.world.World
import net.minecraft.entity.player.EntityPlayer

// Triangle Flag
package object triflag
{
	def init()
	{
		import com.cterm2.tetra.ContentRegistry

		ContentRegistry register Block0.setCreativeTab(CreativeTab) in classOf[BlockSummoner] as "FlagBlock.tri"
		ContentRegistry register Block90 as "FlagBlock.tri.90deg"
		ContentRegistry register Block180 as "FlagBlock.tri.180deg"
		ContentRegistry register Block270 as "FlagBlock.tri.270deg"
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
		override val baseName = "FlagBlock.tri"
	}
	sealed class BlockBase extends BlockFlagBase
	{
		override def getRenderType = render.TriRenderID
		override def getPickBlock(target: MovingObjectPosition, world: World, x: Int, y: Int, z: Int, player: EntityPlayer) =
			new ItemStack(Item.getItemFromBlock(Block0), 1, world.getBlockMetadata(x, y, z))
		override val baseName = "FlagBlock.tri"
	}
	object Block0 extends BlockBase
	object Block90 extends BlockBase
	object Block180 extends BlockBase
	object Block270 extends BlockBase
}
