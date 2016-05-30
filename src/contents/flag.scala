package com.cterm2.miniflags

import cpw.mods.fml.relauncher._
import net.minecraft.item.Item
import net.minecraft.world.World
import collection.JavaConversions._


package flag
{
	import com.cterm2.miniflags.render.flag._

	import net.{minecraft => mc}
	import mc.item.ItemBlock
	import mc.block.{Block, BlockContainer}, mc.block.material.Material
	import mc.world._, mc.entity.player.EntityPlayer
	import mc.client.multiplayer.WorldClient
	import mc.tileentity.TileEntity
	import mc.util._, mc.entity.{Entity, EntityLivingBase}
	import mc.item.ItemStack
	import scalaz._, Scalaz._
	import mc.init.Blocks, mc.creativetab.CreativeTabs
	import mc.nbt.NBTTagCompound
	import com.cterm2.tetra.ActiveNBTRecord._

	// --- Container and GUIs ---
	import net.minecraft.inventory.Container
	import net.minecraft.client.gui._
	import net.minecraft.client.gui.inventory.GuiContainer
	import net.minecraft.util.{Vec3, ResourceLocation}
	import com.cterm2.tetra.LocalTranslationUtils._

}
