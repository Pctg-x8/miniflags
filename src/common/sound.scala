package com.cterm2.miniflags

import net.minecraft.world._

package object common
{
	def playLinkedSound(world: World, x: Int, y: Int, z: Int)
	{
		world.playSoundEffect(x + 0.5d, y + 0.5d, z + 0.5d, "random.orb", 0.25f, 0.5f)
	}
}
