package com.cterm2.miniflags

import cpw.mods.fml.relauncher._
import net.minecraft.item.Item
import net.minecraft.world.World

package object flag
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

	object Parameters
	{
		final val Space = 0.125f
		final val InvSpace = 1.0f - Space
		final val BaseHeight = 0.125f
		final val Pole = 1.5f / 16.0f
		final val FlagThickness = 0.75f / 16.0f
	}
	object ItemKeys
	{
		final val ReservedLinkingDestinationCoordinate = "ReservedLinkingDestinationCoordinate"
	}

	object BlockSummoner
	{
		val retrieveReservedLinkingDestinationCoordinate: ItemStack => Option[List[Int]] =
			stack => Option(stack.getTagCompound) >>= (_[NBTTagCompound](ItemKeys.ReservedLinkingDestinationCoordinate)) >>=
			(tag => (tag[Int]("x") :: tag[Int]("y") :: tag[Int]("z") :: Nil).sequence)

		def reserveLinkingDestinationCoordinate(world: World, stack: ItemStack, x: Int, y: Int, z: Int, player: EntityPlayer)
		{
			val tagDestCoord = new NBTTagCompound
			tagDestCoord("x") = x
			tagDestCoord("y") = y
			tagDestCoord("z") = z

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
		def placeBlockAt(world: World, x: Int, y: Int, z: Int, block: Block, meta: Int, player: EntityPlayer, stack: ItemStack) =
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
			if(!world.isRemote)
			{
				if(!player.capabilities.isCreativeMode)
				{
					// Make ItemStack(with Name and LinkDestination) for dropping
					(Option(world.getTileEntity(x, y, z)) >>=
					{
						case tile: TileData => ObjectManager.instanceForWorld(world.asInstanceOf[WorldServer]) >>= (this.getDropItem(world, x, y, z, meta, tile, _))
						case _ => None
					}) foreach (this.dropBlockAsItem(world, x, y, z, _))
				}
			}
		}
		private def unregisterBlock(world: WorldServer, x: Int, y: Int, z: Int)
		{
			ObjectManager.instanceForWorld(world) foreach (_.unregister(world.provider.dimensionId, x, y, z))
		}
		override def breakBlock(world: World, x: Int, y: Int, z: Int, block: Block, meta: Int)
		{
			// ModInstance.logger.info("BreakBlock")
			if(!world.isRemote) unregisterBlock(world.asInstanceOf[WorldServer], x, y, z)
			super.breakBlock(world, x, y, z, block, meta)
		}
		// Make ItemStack(with Name and LinkDestination) for dropping
		private def getDropItem(world: World, x: Int, y: Int, z: Int, meta: Int, tile: TileData, mgr: ObjectManager) =
			Option(new ItemStack(itemBlockSummoner, 1, meta)) map (stack =>
			{
				Option(new NBTTagCompound) map (tag =>
				{
					mgr.getLinkDestinationFrom(x, y, z) map
					{
						case Coordinate(dx, dy, dz) => Option(new NBTTagCompound) map (tag_c =>
						{
							tag_c("x") = dx; tag_c("y") = dy; tag_c("z") = dz
							tag_c
						})
					} foreach (tag(ItemKeys.ReservedLinkingDestinationCoordinate) = _)
					tag
				}) foreach stack.setTagCompound
				ModInstance.logger.info(s"TileName: ${tile.name}")
				tile.name foreach stack.setStackDisplayName
				stack
			})

		// No items is dropped
		override def getDrops(world: World, x: Int, y: Int, z: Int, meta: Int, fortune: Int) =
			new java.util.ArrayList[ItemStack]
		override def getPickBlock(target: MovingObjectPosition, world: World, x: Int, y: Int, z: Int, player: EntityPlayer) =
			new ItemStack(Item.getItemFromBlock(Block0), 1, world.getBlockMetadata(x, y, z))
		override def getSubBlocks(source: Item, tab: CreativeTabs, list: java.util.List[_])
		{
			for(x <- 0 until 16 map (x => new ItemStack(source, 1, x))) list.asInstanceOf[java.util.List[ItemStack]].add(x)
		}
	}
	object Block0 extends AbstractBlock
	object Block90 extends AbstractBlock
	object Block180 extends AbstractBlock
	object Block270 extends AbstractBlock

	// Simple Name Holder
	class TileData extends TileEntity
	{
		import mc.network.play.server.S35PacketUpdateTileEntity
		import mc.network.NetworkManager

		var _name: Option[String] = None
		def name = this._name
		def name_=(str: String)
		{
			this._name = Some(str); this.markDirty(); this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord)
		}
		def hashID = ((this.yCoord.toLong & 0xff) << 56) | ((this.xCoord.toLong & 0xfffffff) << 28) | (this.zCoord.toLong & 0xfffffff)
		def coord = Coordinate(xCoord, yCoord, zCoord)

		def hasCustomName = this.name.isDefined
		def nameOrDefaultLocalized = this.name | Block0.getLocalizedName()

		override def writeToNBT(tag: NBTTagCompound)
		{
			super.writeToNBT(tag)
			tag("CustomName") = name
		}
		override def readFromNBT(tag: NBTTagCompound)
		{
			super.readFromNBT(tag)
			this._name = tag[String]("CustomName")
		}
		override def getDescriptionPacket() =
			(Option(new NBTTagCompound) >>=
			{ x =>
				{
					x("CustomName") = this.name
					Option(new S35PacketUpdateTileEntity(this.xCoord, this.yCoord, this.zCoord, 1, x))
				}
			}) | null
		override def onDataPacket(network: NetworkManager, packet: S35PacketUpdateTileEntity)
		{
			for(x <- Option(packet) >>= (x => Option(x.func_148857_g)))
			{
				this._name = x[String]("CustomName")
			}
		}
	}

	// --- Container and GUIs ---
	import net.minecraft.inventory.Container
	import net.minecraft.client.gui._
	import net.minecraft.client.gui.inventory.GuiContainer
	import net.minecraft.util.{Vec3, ResourceLocation}
	import com.cterm2.tetra.LocalTranslationUtils._

	final class FlagSettingsContainer(val tile: TileData) extends Container
	{
		override def canInteractWith(player: EntityPlayer) =
			player.getDistanceSq(tile.xCoord + 0.5d, tile.yCoord + 0.5d, tile.zCoord + 0.5d) <= 64.0d
	}
	// Ref: GuiRepair(Interface of Anvil)
	@SideOnly(Side.CLIENT)
	final class FlagSettingsInterface(val world: WorldClient, val tile: TileData) extends GuiContainer(new FlagSettingsContainer(tile))
	{ CommonInterface =>
		import org.lwjgl.input.Keyboard
		import org.lwjgl.opengl.GL11._

		// Interface Commons
		private val resource = new ResourceLocation(ModInstance.ID, "guiBase.png")
		private var nameInputField: GuiTextField = null
		private val nameLabel = t"gui.labels.FlagName"
		private val hashLabel =
		{
			val base = t"gui.labels.FlagHash"
			s"$base: #${java.lang.Long.toUnsignedString(tile.hashID)}"
		}

		private trait IInterface
		{
			val titleLocalized = ""

			def onKeyType(chr: Char, rep: Int): Boolean
			def updateScreen()
			def onMouseClicked(x: Int, y: Int, button: Int)
			def drawControls()
			def drawLabels()
		}
		// Only can be constructed in initGui()
		private final class UnlinkedFlagInterface extends IInterface
		{
			override val titleLocalized = t"gui.labels.UnlinkedFlags"
			private val linkTargetInputField =
			{
				val o = new GuiTextField(CommonInterface.fontRendererObj, CommonInterface.guiLeft + 10, CommonInterface.guiTop + 58, CommonInterface.xSize - 39, 12)
				o.setEnableBackgroundDrawing(false)
				o.setMaxStringLength(20)
				o
			}

			def onKeyType(chr: Char, rep: Int) = this.linkTargetInputField.textboxKeyTyped(chr, rep)
			def updateScreen() { this.linkTargetInputField.updateCursorCounter() }
			def onMouseClicked(x: Int, y: Int, button: Int) { this.linkTargetInputField.mouseClicked(x, y, button) }
			def drawControls()
			{
				this.linkTargetInputField.drawTextBox()
				CommonInterface.mc.renderEngine bindTexture CommonInterface.resource
				CommonInterface.drawTexturedModalRect(CommonInterface.guiLeft + CommonInterface.xSize - 25, CommonInterface.guiTop + 54,
					CommonInterface.xSize, 0, 17, 16)
			}
			def drawLabels()
			{
				CommonInterface.fontRendererObj.drawString(t"gui.labels.LinkTo", 4, 46, 0x404040)
			}
		}
		private final class LinkedFlagInterface extends IInterface
		{
			override val titleLocalized = t"gui.labels.LinkedFlags"

			def onKeyType(chr: Char, rep: Int) = false
			def updateScreen() {}
			def onMouseClicked(x: Int, y: Int, button: Int) {}
			def drawControls() {}
			def drawLabels() {}
		}
		private var interfaceHandler: IInterface = null

		private def updateHasLinked() { this.initGui() }
		override def initGui()
		{
			val hasLinked = ClientLinkManager.getLinkDestination(tile.coord).isDefined

			super.initGui()

			// Common Gui Initializer
			Keyboard.enableRepeatEvents(true)
			this.nameInputField =
			{
				val o = new GuiTextField(this.fontRendererObj, this.guiLeft + 10, this.guiTop + 32, this.xSize - 20, 12)
				o.setEnableBackgroundDrawing(false)
				o.setMaxStringLength(40)
				o.setText(tile.nameOrDefaultLocalized)
				o
			}

			this.interfaceHandler = if(!hasLinked) new UnlinkedFlagInterface else new LinkedFlagInterface
		}
		override def onGuiClosed()
		{
			super.onGuiClosed()
			Keyboard.enableRepeatEvents(false)
		}
		override def keyTyped(chr: Char, rep: Int)
		{
			// Dispatching Key Event
			if(this.nameInputField.textboxKeyTyped(chr, rep))
			{
				intercommands.UpdateFlagName(tile.coord, this.nameInputField.getText).dispatchToServer()
			}
			else if(!this.interfaceHandler.onKeyType(chr, rep)) super.keyTyped(chr, rep)
		}
		override def updateScreen()
		{
			super.updateScreen()
			this.nameInputField.updateCursorCounter()
			this.interfaceHandler.updateScreen()
		}
		override def mouseClicked(x: Int, y: Int, b: Int)
		{
			super.mouseClicked(x, y, b)
			this.nameInputField.mouseClicked(x, y, b)
			this.interfaceHandler.onMouseClicked(x, y, b)
		}
		override def drawScreen(p1: Int, p2: Int, p3: Float)
		{
			super.drawScreen(p1, p2, p3)
			Seq(GL_LIGHTING, GL_BLEND) foreach glDisable
			this.nameInputField.drawTextBox()
			this.interfaceHandler.drawControls()
			Seq(GL_LIGHTING, GL_BLEND) foreach glEnable
		}

		override def drawGuiContainerBackgroundLayer(p1: Float, p2: Int, p3: Int)
		{
			this.mc.renderEngine bindTexture resource
			this.drawTexturedModalRect(this.guiLeft, this.guiTop, 0, 0, this.xSize, this.ySize)
		}
		override def drawGuiContainerForegroundLayer(p1: Int, p2: Int)
		{
			val headerText = this.interfaceHandler.titleLocalized
			val headerWidth = this.fontRendererObj getStringWidth headerText
			val disableSwitches = Seq(GL_LIGHTING, GL_BLEND)

			disableSwitches foreach glDisable
			this.fontRendererObj.drawString(headerText, (this.xSize - headerWidth) / 2, 6, 0x404040)
			this.fontRendererObj.drawString(t"gui.labels.FlagName", 4, 20, 0x404040)
			this.fontRendererObj.drawString(hashLabel, this.xSize - 3 - this.fontRendererObj.getStringWidth(hashLabel), 20, 0x404040)
			this.interfaceHandler.drawLabels()
			disableSwitches foreach glEnable
		}
	}
}
