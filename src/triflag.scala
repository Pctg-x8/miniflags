package com.cterm2.miniflags

// Triangle Flag
package object triflag
{
	def init()
	{
		import com.cterm2.tetra.ContentRegistry

		ContentRegistry register Block0 in classOf[BlockSummoner] as "FlagBlock"
		ContentRegistry register Block90 as "FlagBlock.90deg"
		ContentRegistry register Block180 as "FlagBlock.180deg"
		ContentRegistry register Block270 as "FlagBlock.270deg"
	}

	object BlockDirectionProvider extends IDirectionProvider
	{
		def getBlockByDirection(dir: Int) = dir match
		{
			case 0 => Block0
			case 1 => Block90
			case 2 => Block180
			case 3 => Block270
		}
	}
	object Block0 extends FlagBlockBase(BlockDirectionProvider)
	object Block90 extends FlagBlockBase(BlockDirectionProvider)
	object Block180 extends FlagBlockBase(BlockDirectionProvider)
	object Block270 extends FlagBlockBase(BlockDirectionProvider)
}
