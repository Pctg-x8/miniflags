package com.cterm2.miniflags

// Base Class of All Flag Blocks

import net.minecraft.init.Blocks
import net.minecraft.block.material.Material
import com.cterm2.miniflags.common.Metrics
import net.minecraft.creativetab.CreativeTabs
import net.minecraft.entity.Entity
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.{item, block, util, world, nbt}
import item._, block._, util._, world._, nbt._
import scalaz._, Scalaz._
import com.cterm2.miniflags.common.ItemKeys
import com.cterm2.tetra.ActiveNBTRecord._
import common._

// Containment Object Representation
final class BlockSummoner(val block: FlagBlockBase) extends ItemBlock(block)
{
	import BlockSummoner._

	this.setMaxStackSize(1).setHasSubtypes(true).setMaxDamage(0)

	override def getIconFromDamage(damage: Int) = block.getIcon(2, damage)
	override def getMetadata(meta: Int) = meta

	override def onItemUse(stack: ItemStack, player: EntityPlayer, world: World, x: Int, y: Int, z: Int, side: Int, hitX: Float, hitY: Float, hitZ: Float) =
		(onPlacingAction(stack, player, world, hitX, hitY, hitZ) _).tupled(positionDetection(world, x, y, z, side))
	override def onItemRightClick(stack: ItemStack, world: World, player: EntityPlayer) =
	{
		if(!world.isRemote && player.isSneaking) onSneakingAction(stack, player)
		stack
	}

	// Custom Information Line(LinkDestination)
	override def addInformation(stack: ItemStack, player: EntityPlayer, lines: java.util.List[_], b: Boolean)
	{
		(BlockSummoner.retrieveReservedLinkingDestinationCoordinate(stack) >>=
		{
			case List(dx, dy, dz) => Some((dx, dy, dz))
			case _ => None
		}) foreach { case (dx, dy, dz) => lines.asInstanceOf[java.util.List[String]].add(s"Reserved linking to ($dx, $dy, $dz)") }
	}

	// onItemUse without Sneaking State
	private def onPlacingAction(stack: ItemStack, player: EntityPlayer, world: World, hitX: Float, hitY: Float, hitZ: Float)(x: Int, y: Int, z: Int, side: Int) =
	{
		// Block Direction by RotationYaw
		val blk = this.block.blockDirectionProvider.getBlockByDirection(MathHelper.floor_double((player.rotationYaw / 90.0f).toDouble + 0.5d) & 3)

		// validation
		val stackHasItem = stack.stackSize > 0
		val playerEditable = player.canPlayerEdit(x, y, z, side, stack)
		val isHeightLimit = y >= 255
		val isSolidBlock = blk.getMaterial.isSolid
		val canPlaceEntityOnSide = world.canPlaceEntityOnSide(blk, x, y, z, false, side, player, stack)
		if(stackHasItem && playerEditable && !(isHeightLimit && isSolidBlock) && canPlaceEntityOnSide)
		{
			val meta = this.getMetadata(stack.getItemDamage)
			val newMeta = blk.onBlockPlaced(world, x, y, z, side, hitX, hitY, hitZ, meta)
			BlockSummoner.placeBlockAt(world, x, y, z, blk, newMeta, player, stack)
		}
		else false
	}
}
object BlockSummoner
{
	// Helper Routines

	// onItemUse with Sneaking State
	private def onSneakingAction(stack: ItemStack, player: EntityPlayer) =
		Option(stack.getTagCompound) match
		{
			case Some(x) if x hasKey ItemKeys.ReservedLinkingDestinationCoordinate =>
				x.removeTag(ItemKeys.ReservedLinkingDestinationCoordinate)
				player.addChatComponentMessage(new ChatComponentText("Cleared reserved linking"))
				true
			case _ => false
		}

	// Reserved Linking Destination Coordinate Utils
	def retrieveReservedLinkingDestinationCoordinate(stack: ItemStack) =
		Option(stack.getTagCompound) >>= (_[NBTTagCompound](ItemKeys.ReservedLinkingDestinationCoordinate)) >>=
		(tag => (tag[Int]("x") :: tag[Int]("y") :: tag[Int]("z") :: Nil).sequence)
	def reserveLinkingDestinationCoordinate(world: World, stack: ItemStack, x: Int, y: Int, z: Int, player: EntityPlayer)
	{
		val tagDestCoord = Option(new NBTTagCompound) map { tag =>
			tag("x") = x; tag("y") = y; tag("z") = z
			tag
		}
		val tag = Option(stack.getTagCompound) | { val v = new NBTTagCompound; stack.setTagCompound(v); v }
		tag(ItemKeys.ReservedLinkingDestinationCoordinate) = tagDestCoord
		player.addChatComponentMessage(new ChatComponentText(s"Reserved linking to ($x, $y, $z)"))
		playLinkedSound(world, x, y, z)
	}

	// called in onPlaceBlock
	private def postPlaceAction(world: World, x: Int, y: Int, z: Int, stack: ItemStack, player: EntityPlayer)
	{
		ObjectManager.instanceForWorld(world) foreach (_.register(x, y, z))
		if(stack.hasDisplayName) Option(world.getTileEntity(x, y, z)) foreach { case x: TileData => x.name = stack.getDisplayName }
		retrieveReservedLinkingDestinationCoordinate(stack) foreach
		{
			case List(dx, dy, dz) => Option(world.getTileEntity(dx, dy, dz)) match
			{
				case Some(tile: TileData) => ObjectManager.instanceForWorld(world) foreach (_.link(player, x, y, z, dx, dy, dz))
				case _ => player.addChatComponentMessage(new ChatComponentText("Destination is not found or removed"))
			}
			case _ => ModInstance.logger.warn("ReservedLinkingDestinationCoordinate data is broken")
		}
	}
	private def placeBlockAt(world: World, x: Int, y: Int, z: Int, block: Block, meta: Int, player: EntityPlayer, stack: ItemStack) =
	{
		val blockPlaceResult = world.setBlock(x, y, z, block, meta, 3)
		val blockValidation = blockPlaceResult && world.getBlock(x, y, z) == block

		if(blockValidation && !world.isRemote) postPlaceAction(world, x, y, z, stack, player)
		if(blockPlaceResult)
		{
			world.playSoundEffect(x + 0.5d, y + 0.5d, z + 0.5d, block.stepSound.func_150496_b, (block.stepSound.getVolume + 1.0f) / 2.0f, block.stepSound.getPitch * 0.8f)
			stack.stackSize = stack.stackSize - 1
		}
		true
	}
	private def positionDetection(world: World, x: Int, y: Int, z: Int, side: Int) = world.getBlock(x, y, z) match
	{
		case Blocks.snow if (world.getBlockMetadata(x, y, z) & 7) < 1 => (x, y, z, 1)
		case Blocks.vine | Blocks.tallgrass | Blocks.deadbush => (x, y, z, side)
		case b: Block if b.isReplaceable(world, x, y, z) => (x, y, z, side)
		case _ =>
		(
			if(side == 4) x - 1 else if(side == 5) x + 1 else x,
			if(side == 0) y - 1 else if(side == 1) y + 1 else y,
			if(side == 2) z - 1 else if(side == 3) z + 1 else z,
			side
		)
	}
}

// Delegated class that provides block by direction
trait IDirectionProvider
{
	def getBlockByDirection(dir: Int): FlagBlockBase
}

// Base Class of All Flag Blocks
abstract class FlagBlockBase(val blockDirectionProvider: IDirectionProvider) extends BlockContainer(Material.rock)
{
	this.setHardness(0)
	this.setBlockBounds(Metrics.Space, 0.0f, Metrics.Space, Metrics.InvSpace, 1.0f, Metrics.InvSpace)

	// Block Properties
	override val canSilkHarvest = false
	override def getHarvestLevel(meta: Int) = 0
	override def getHarvestTool(meta: Int) = null
	override val getMobilityFlag = 2		/* Cannot move by anything */

	// Rendering Properties
	override val isOpaqueCube = false
	override val isNormalCube = false
	override val renderAsNormalBlock = false
	override def getIcon(side: Int, meta: Int) = Blocks.planks.getIcon(side, 0)

	// getCollisionBoundingBoxFromPool: Collision Box for Visibility Testing
	// Collision Test against Entities
	override def addCollisionBoxesToList(world: World, x: Int, y: Int, z: Int, mask: AxisAlignedBB, boxes: java.util.List[_], entity: Entity)
	{
		val boxList = boxes.asInstanceOf[java.util.List[AxisAlignedBB]]

		Option(AxisAlignedBB.getBoundingBox(x.toDouble + this.minX, y.toDouble, z.toDouble + this.minZ,
			x.toDouble + this.maxX, (y + Metrics.BaseHeight).toDouble, z.toDouble + this.maxZ)) filter (mask intersectsWith _) foreach
		{
			boxList add _
		}
	}

	// Common TileData and Reacts
	override def createNewTileEntity(world: World, meta: Int) = new TileData
	override def onBlockActivated(world: World, x: Int, y: Int, z: Int, player: EntityPlayer, side: Int, hitX: Float, hitY: Float, hitZ: Float) =
	{
		if(!world.isRemote)
		{
			def isBlockSummonerItem(s: ItemStack) = Option(s.getItem) collect { case x: BlockSummoner => x } isDefined
			val activateWith = Option(player.inventory.getCurrentItem)

			activateWith match
			{
			case Some(t: ItemStack) if isBlockSummonerItem(t) => BlockSummoner.reserveLinkingDestinationCoordinate(world, t, x, y, z, player)
			case _ => player.openGui(ModInstance, ModInstance.InterfaceID, world, x, y, z)
			}
			true
		}
		else world.isRemote
	}
	override def onBlockHarvested(world: World, x: Int, y: Int, z: Int, meta: Int, player: EntityPlayer)
	{
		if(!world.isRemote && !player.capabilities.isCreativeMode)
		{
			// Make ItemStack(with Name and LinkDestination) for dropping
			(Option(world.getTileEntity(x, y, z)) >>=
			{
				case tile: TileData => ObjectManager.instanceForWorld(world) >>= (this.getDropItem(world, x, y, z, meta, tile, _))
				case _ => None
			}) foreach (this.dropBlockAsItem(world, x, y, z, _))
		}
	}
	override def breakBlock(world: World, x: Int, y: Int, z: Int, block: Block, meta: Int)
	{
		if(!world.isRemote) ObjectManager.instanceForWorld(world) foreach (_.unregister(x, y, z))
		super.breakBlock(world, x, y, z, block, meta)
	}

	// Make ItemStack(with Name and LinkDestination) for dropping
	private def getDropItem(world: World, x: Int, y: Int, z: Int, meta: Int, tile: TileData, mgr: ObjectManager) =
		Option(new ItemStack(Item.getItemFromBlock(this), 1, meta)) map { stack =>
			Option(new NBTTagCompound) map { tag =>
				mgr.getLinkDestinationFrom(x, y, z) map
				{
					case Coordinate(dx, dy, dz) => Option(new NBTTagCompound) map { tag_c =>
						tag_c("x") = dx; tag_c("y") = dy; tag_c("z") = dz
						tag_c
					}
				} foreach (tag(ItemKeys.ReservedLinkingDestinationCoordinate) = _)
				tag
			} foreach stack.setTagCompound
			tile.name foreach stack.setStackDisplayName
			stack
		}

	// No items are dropped(by default dropper routine)
	override def getDrops(world: World, x: Int, y: Int, z: Int, meta: Int, fortune: Int) = new java.util.ArrayList[ItemStack]
	override def getSubBlocks(source: Item, tab: CreativeTabs, list: java.util.List[_])
	{
		val listRef = list.asInstanceOf[java.util.List[ItemStack]]
		0 until 16 map (new ItemStack(source, 1, _)) foreach listRef.add
	}
}
