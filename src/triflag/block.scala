package com.cterm2.miniflags.triflag

// Block and ItemBlock of Triangle Flag

import com.cterm2.miniflags.common.ItemKeys

object BlockSummoner
{
	def retrieveReservedLinkingDestinationCoordinate(stack: ItemStack) =
		Option(stack.getTagCompound) >>= (_[NBTTagCompound](ItemKeys.ReservedLinkingDestinationCoordinate)) >>=
		(tag => (tag[Int]("x") :: tag[Int]("y") :: tag[Int]("z") :: Nil).sequence)

	def reserveLinkingDestinationCoordinate(world: World, stack: ItemStack, x: Int, y: Int, z: Int, player: EntityPlayer)
	{
		val tagDestCoord = new NBTTagCompound
		tagDestCoord("x") = x; tagDestCoord("y") = y; tagDestCoord("z") = z

		val tag = Option(stack.getTagCompound) getOrElse { val v = new NBTTagCompound; stack.setTagCompound(v); v }
		tag(ItemKeys.ReservedLinkingDestinationCoordinate) = tagDestCoord
		player.addChatComponentMessage(new ChatComponentText(s"Reserved Linking to ($x, $y, $z)"))
		playLinkedSound(world, x, y, z)
	}
	private def postPlaceAction(world: WorldServer, x: Int, y: Int, z: Int, stack: ItemStack, player: EntityPlayer)
	{
		// Register Object
		ObjectManager.instanceForWorld(world) foreach (_.register(x, y, z))
		// Naming
		if(stack.hasDisplayName) Option(world.getTileEntity(x, y, z)) foreach { case x: TileData => x.name = stack.getDisplayName }
		// Do reserved linking
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

		// Successfully placed block in server: Do post action
		if(blockValidation && !world.isRemote) postPlaceAction(world.asInstanceOf[WorldServer], x, y, z, stack, player)
		// Play Sound and decrease stack if placing block has succeeded
		if(blockPlaceResult)
		{
			world.playSoundEffect(x.toDouble + 0.5d, y.toDouble + 0.5d, z.toDouble + 0.5d, block.stepSound.func_150496_b,
				(block.stepSound.getVolume + 1.0f) / 2.0f, block.stepSound.getPitch * 0.8f)
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
class BlockSummoner(block: Block) extends ItemBlock(block)
{
	import BlockSummoner._

	setMaxStackSize(1).setHasSubtypes(true).setMaxDamage(0)

	override def getIconFromDamage(damage: Int) = block.getIcon(2, damage)
	override def getMetadata(meta: Int) = meta

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
	// onItemUse without Sneaking State
	private def onPlacingAction(stack: ItemStack, player: EntityPlayer, world: World, hitX: Float, hitY: Float, hitZ: Float)(x: Int, y: Int, z: Int, side: Int) =
	{
		// Block Direction by RotationYaw
		val blk = MathHelper.floor_double((player.rotationYaw / 90.0f).toDouble + 0.5d) & 3 match
		{
			case 0 => Block0
			case 1 => Block90
			case 2 => Block180
			case 3 => Block270
		}

		// validation
		val stackHasItem = stack.stackSize > 0
		val playerEditable = player.canPlayerEdit(x, y, z, side, stack)
		val isHeightLimit = y >= 255
		val isSolidBlock = blk.getMaterial.isSolid
		val canPlaceEntityOnSide = world.canPlaceEntityOnSide(blk, x, y, z, false, side, player, stack)
		if(stackHasItem && playerEditable && !(isHeightLimit && isSolidBlock) && canPlaceEntityOnSide)
		{
			val meta = this.getMetadata(stack.getItemDamage)
			val actionResult = blk.onBlockPlaced(world, x, y, z, side, hitX, hitY, hitZ, meta)
			BlockSummoner.placeBlockAt(world, x, y, z, blk, actionResult, player, stack)
		}
		else false
	}
	override def onItemUse(stack: ItemStack, player: EntityPlayer, world: World, x: Int, y: Int, z: Int, side: Int, hitX: Float, hitY: Float, hitZ: Float) =
		(onPlacingAction(stack, player, world, hitX, hitY, hitZ) _).tupled(positionDetection(world, x, y, z, side))
	override def onItemRightClick(stack: ItemStack, world: World, player: EntityPlayer) =
	{
		if(!world.isRemote && player.isSneaking) onSneakingAction(stack, player)
		stack
	}

	override def addInformation(stack: ItemStack, player: EntityPlayer, lines: java.util.List[_], b: Boolean)
	{
		BlockSummoner.retrieveReservedLinkingDestinationCoordinate(stack) foreach
		{
			case List(dx, dy, dz) => lines.asInstanceOf[java.util.List[String]].add(s"Reserved linking to ($dx, $dy, $dz)")
			case _ => /* Nothing to do */
		}
	}
}
sealed abstract class AbstractBlock extends BlockContainer(Material.rock)
{
	this.setHardness(0.0f)
	this.setBlockBounds(Parameters.Space, 0.0f, Parameters.Space, Parameters.InvSpace, 1.0f, Parameters.InvSpace)

	override val isOpaqueCube = false
	override val isNormalCube = false
	override val renderAsNormalBlock = false
	override def getRenderType = render.flag.RenderID
	override def getIcon(side: Int, meta: Int) = Blocks.planks.getIcon(side, 0)
	override val canSilkHarvest = false
	override def getHarvestLevel(meta: Int) = 0
	override def getHarvestTool(meta: Int) = null
	override val getMobilityFlag = 2

	// Collision Box for Visibility Testing(getCollisionBoundingBoxFromPool)
	// Collision Test against Entities
	override def addCollisionBoxesToList(world: World, x: Int, y: Int, z: Int, mask: AxisAlignedBB, boxes: java.util.List[_], entity: Entity)
	{
		Option(AxisAlignedBB.getBoundingBox(x.toDouble + this.minX, y.toDouble, z.toDouble + this.minZ,
			x.toDouble + this.maxX, (y + Parameters.BaseHeight).toDouble, z.toDouble + this.maxZ)) filter (mask intersectsWith _) foreach
		{
			bb => boxes.asInstanceOf[java.util.List[AxisAlignedBB]].add(bb)
		}
	}

	override def createNewTileEntity(world: World, meta: Int) = new TileData
	override def onBlockActivated(world: World, x: Int, y: Int, z: Int, player: EntityPlayer, side: Int, hitX: Float, hitY: Float, hitZ: Float) =
	{
		if(!world.isRemote)
		{
			val activateWith = Option(player.inventory.getCurrentItem) filter (_.getItem == itemBlockSummoner)

			activateWith match
			{
				case Some(t: ItemStack) => BlockSummoner.reserveLinkingDestinationCoordinate(world, t, x, y, z, player)
				case _ => player.openGui(ModInstance, ModInstance.InterfaceID, world, x, y, z)
			}
			true
		}
		else world.isRemote
	}
	override def onBlockHarvested(world: World, x: Int, y: Int, z: Int, meta: Int, player: EntityPlayer)
	{
		// ModInstance.logger.info("onBlockHarvested")
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
	private def unregisterBlock(world: World, x: Int, y: Int, z: Int)
	{
		assert(!world.isRemote)
		ObjectManager.instanceForWorld(world) foreach (_.unregister(world.provider.dimensionId, x, y, z))
	}
	override def breakBlock(world: World, x: Int, y: Int, z: Int, block: Block, meta: Int)
	{
		// ModInstance.logger.info("BreakBlock")
		if(!world.isRemote) unregisterBlock(world, x, y, z)
		super.breakBlock(world, x, y, z, block, meta)
	}
	// Make ItemStack(with Name and LinkDestination) for dropping
	private def getDropItem(world: World, x: Int, y: Int, z: Int, meta: Int, tile: TileData, mgr: ObjectManager) =
		Option(new ItemStack(itemBlockSummoner, 1, meta)) map { stack =>
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
			ModInstance.logger.info(s"TileName: ${tile.name}")
			tile.name foreach stack.setStackDisplayName
			stack
		}

	// No items are dropped
	override def getDrops(world: World, x: Int, y: Int, z: Int, meta: Int, fortune: Int) = new java.util.ArrayList[ItemStack]
	override def getPickBlock(target: MovingObjectPosition, world: World, x: Int, y: Int, z: Int, player: EntityPlayer) =
		new ItemStack(Item.getItemFromBlock(Block0), 1, world.getBlockMetadata(x, y, z))
	override def getSubBlocks(source: Item, tab: CreativeTabs, list: java.util.List[_])
	{
		val listRef = list.asInstanceOf[java.util.List[ItemStack]]
			0 until 16 map (new ItemStack(source, 1, _)) foreach listRef.add
	}
}
object Block0 extends AbstractBlock
object Block90 extends AbstractBlock
object Block180 extends AbstractBlock
object Block270 extends AbstractBlock
