package com.cterm2.miniflags

import cpw.mods.fml.relauncher._
import net.minecraft.item.Item
import net.minecraft.world.World

package object flag
{
	import com.cterm2.tetra.ContentRegistry
	import cpw.mods.fml.client.registry.{ClientRegistry, RenderingRegistry}

	var RenderID = 0
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
	@SideOnly(Side.CLIENT)
	def registerClient
	{
		ClientRegistry.bindTileEntitySpecialRenderer(classOf[TileData], TERenderer)
		RenderID = RenderingRegistry.getNextAvailableRenderId
		RenderingRegistry.registerBlockHandler(BlockRenderer)
	}

	def playLinkedSound(world: World, x: Int, y: Int, z: Int)
	{
		world.playSoundEffect((x.toFloat + 0.5f).toDouble, (y.toFloat + 0.5f).toDouble, (z.toFloat + 0.5f).toDouble,
			"random.orb", 0.25f, 0.5f)
	}
}

package flag
{
	import net.{minecraft => mc}
	import mc.item.ItemBlock
	import mc.block.{Block, BlockContainer}, mc.block.material.Material
	import mc.world.World, mc.entity.player.EntityPlayer
	import mc.tileentity.TileEntity, mc.client.renderer.tileentity.TileEntitySpecialRenderer
	import mc.util._, mc.entity.{Entity, EntityLivingBase}
	import mc.item.ItemStack
	import scalaz._, Scalaz._
	import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler
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

	class BlockSummoner(block: Block) extends ItemBlock(block)
	{
		setMaxStackSize(1).setHasSubtypes(true).setMaxDamage(0)

		override def getIconFromDamage(damage: Int) = block.getIcon(2, damage)
		override def getMetadata(meta: Int) = meta

		override def onItemUse(stack: ItemStack, player: EntityPlayer, world: World, x: Int, y: Int, z: Int, side: Int,
			hitX: Float, hitY: Float, hitZ: Float) =
		{
			// Block Direction by RotationYaw
			val blk = (MathHelper.floor_double((player.rotationYaw / 90.0f).toDouble + 0.5d) & 3) match
			{
				case 0 => Block0
				case 1 => Block90
				case 2 => Block180
				case 3 => Block270
			}

			// displacement check
			val (finalY, finalZ, finalX, finalSide) = world.getBlock(x, y, z) match
			{
				case Blocks.snow if (world.getBlockMetadata(x, y, z) & 7) < 1 => (y, z, x, 1)
				case Blocks.vine | Blocks.tallgrass | Blocks.deadbush => (y, z, x, side)
				case b: Block if b.isReplaceable(world, x, y, z) => (y, z, x, side)
				case _ =>
				(
					if(side == 0) y - 1 else if(side == 1) y + 1 else y,
					if(side == 2) z - 1 else if(side == 3) z + 1 else z,
					if(side == 4) x - 1 else if(side == 5) x + 1 else x,
					side
				)
			}

			// validation
			val stackHasItem = stack.stackSize > 0
			val playerEditable = player.canPlayerEdit(finalX, finalY, finalZ, finalSide, stack)
			val heightLimitation = finalY >= 255
			val isSolidBlock = blk.getMaterial.isSolid
			val canPlaceEntityOnSide = world.canPlaceEntityOnSide(blk, finalX, finalY, finalZ, false, finalSide, player, stack)
			if(stackHasItem && playerEditable && !(heightLimitation && isSolidBlock) && canPlaceEntityOnSide)
			{
				val meta = this.getMetadata(stack.getItemDamage)
				val actionResult = blk.onBlockPlaced(world, finalX, finalY, finalZ, finalSide, hitX, hitY, hitZ, meta)

				BlockSummoner.placeBlockAt(world, finalX, finalY, finalZ, blk, actionResult, player, stack)
			}
			else false
		}
	}
	object BlockSummoner
	{
		def reserveLinkingDestinationCoordinate(world: World, stack: ItemStack, x: Int, y: Int, z: Int, player: EntityPlayer)
		{
			val tagDestCoord = new NBTTagCompound
			tagDestCoord("x") = x
			tagDestCoord("y") = y
			tagDestCoord("z") = z

			val tag = Option(stack.getTagCompound) getOrElse { val v = new NBTTagCompound; stack.setTagCompound(v); v }
			tag("ReservedLinkingDestinationCoordinate") = tagDestCoord
			player.addChatComponentMessage(new ChatComponentText(s"Reserved Linking to ($x, $y, $z)"))
			playLinkedSound(world, x, y, z)
		}
		def placeBlockAt(world: World, x: Int, y: Int, z: Int, block: Block, meta: Int, player: EntityPlayer, stack: ItemStack) =
		{
			val blockPlaceResult = world.setBlock(x, y, z, block, meta, 3)
			val blockValidation = blockPlaceResult && world.getBlock(x, y, z) == block

			// Successfully placed block in server: Do post action
			if(blockValidation && !world.isRemote)
			{
				val tile = Option(world.getTileEntity(x, y, z))

				// Register Object
				ObjectManager.instanceForWorld(world) foreach (_.register(x, y, z))
				// Naming
				if(stack.hasDisplayName) tile foreach { case x: TileData => x.name = Some(stack.getDisplayName) }
				// Do reserved linking
				(Option(stack.getTagCompound) >>= (x => x[NBTTagCompound]("ReservedLinkingDestinationCoordinate")) >>=
					(tag => (tag[Int]("x") :: tag[Int]("y") :: tag[Int]("z") :: Nil).sequence)) foreach
				{
					case List(dx, dy, dz) => tile match
					{
						case Some(tile: TileData) => ObjectManager.instanceForWorld(world) foreach (_.link(world, player, x, y, z, dx, dy, dz))
						case _ => player.addChatComponentMessage(new ChatComponentText("Destination is not found or removed"))
					}
					case _ => ModInstance.logger.warn("ReservedLinkingDestinationCoordinate data is broken")
				}

				// Sync TileData
				world.markBlockForUpdate(x, y, z)
			}
			// Play Sound and decrease stack if placing block has succeeded
			if(blockPlaceResult)
			{
				world.playSoundEffect(x.toDouble + 0.5d, y.toDouble + 0.5d, z.toDouble + 0.5d, block.stepSound.func_150496_b,
					(block.stepSound.getVolume + 1.0f) / 2.0f, block.stepSound.getPitch * 0.8f)
				stack.stackSize = stack.stackSize - 1
			}
			true
		}
	}
	sealed abstract class AbstractBlock extends BlockContainer(Material.rock)
	{
		this.setHardness(0.125f)
		this.setHarvestLevel(null, 0)
		this.setBlockBounds(Parameters.Space, 0.0f, Parameters.Space, Parameters.InvSpace, 1.0f, Parameters.InvSpace)

		override val isOpaqueCube = false
		override val isNormalCube = false
		override val renderAsNormalBlock = false
		override def getRenderType = RenderID
		override def getIcon(side: Int, meta: Int) = Blocks.planks.getIcon(side, 0)

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
				val activateWith = Option(player.inventory.getCurrentItem)

				activateWith filter (_.getItem == itemBlockSummoner) foreach
				{
					s => BlockSummoner.reserveLinkingDestinationCoordinate(world, s, x, y, z, player)
				}
				true
			}
			else world.isRemote
		}
		override def breakBlock(world: World, x: Int, y: Int, z: Int, block: Block, meta: Int)
		{
			if(!world.isRemote)
			{
				ObjectManager.instanceForWorld(world) foreach (_.unregister(x, y, z))
			}
			super.breakBlock(world, x, y, z, block, meta)
		}

		override def getDrops(world: World, x: Int, y: Int, z: Int, meta: Int, fortune: Int) =
		{
			val items = new java.util.ArrayList[ItemStack]
			items.add(new ItemStack(itemBlockSummoner, 1, world.getBlockMetadata(x, y, z)))
			items
		}
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

	class TileData extends TileEntity
	{
		import mc.network.play.server.S35PacketUpdateTileEntity
		import mc.network.NetworkManager

		var name: Option[String] = None

		def hasCustomName = this.name.isDefined
		def onBreak()
		{
			// Unlink from others
			// this.linkedFrom foreach { x => x.linkTo = None }
			// this.linkTo foreach { x => x.linkedFrom = x.linkedFrom filter (_ != this) }
			// Tell breaking terminal to client
			// if(linkTo.isDefined || !linkedFrom.isEmpty) ModInstance.network.sendToAll(new PacketTerminalBroken(this.xCoord, this.yCoord, this.zCoord))
		}

		override def writeToNBT(tag: NBTTagCompound)
		{
			super.writeToNBT(tag)
			tag("CustomName") = name
		}
		override def readFromNBT(tag: NBTTagCompound)
		{
			super.readFromNBT(tag)
			this.name = tag[String]("CustomName")
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
				this.name = x[String]("CustomName")
			}
		}
	}
	object BlockRenderer extends ISimpleBlockRenderingHandler
	{
		import mc.block.Block
		import mc.client.renderer.{RenderBlocks, Tessellator, EntityRenderer}
		import mc.world.IBlockAccess
		import org.lwjgl.opengl.GL11._
		import com.cterm2.tetra.StaticMeshData._

		implicit class MeshConstructorHelper(val mesh: StaticMesh) extends AnyVal
		{
			def renderBase() = mesh.setRenderBounds(Parameters.Space, 0.0f, Parameters.Space, Parameters.InvSpace, Parameters.BaseHeight, Parameters.InvSpace).
				renderFaceYPos.renderFaceXZ
			def renderPole() = mesh.setRenderBounds(0.5f - Parameters.Pole, Parameters.BaseHeight, 0.5f - Parameters.Pole, 0.5f + Parameters.Pole, 1.0f, 0.5f + Parameters.Pole).
				renderFaceXZ
			def renderFlagPart(n: Int) = mesh.setRenderBounds(0.5f + Parameters.Pole + n * 1.25f / 16.0f, 1.0f - (3.0f + 1.0f + 1.0f + 3.0f - n) / 16.0f, 0.5f - Parameters.FlagThickness,
				0.5f + Parameters.Pole + (1 + n) * 1.25f / 16.0f, 1.0f - (n + 1.0f) / 16.0f, 0.5f + Parameters.FlagThickness).
				renderFaceYPos.renderFaceYNeg.renderFaceXPos.renderFaceZNeg.renderFaceZPos
			def renderFlagPart90(n: Int) = mesh.setRenderBounds(0.5f - Parameters.FlagThickness, 1.0f - (3.0f + 1.0f + 1.0f + 3.0f - n) / 16.0f, 0.5f + Parameters.Pole + n * 1.25f / 16.0f,
				0.5f + Parameters.FlagThickness, 1.0f - (n + 1.0f) / 16.0f, 0.5f + Parameters.Pole + (1 + n) * 1.25f / 16.0f).
				renderFaceYPos.renderFaceYNeg.renderFaceZPos.renderFaceXNeg.renderFaceXPos
			def renderFlagPartInv(n: Int) = mesh.setRenderBounds(0.5f - Parameters.Pole - (n + 1.0f) * 1.25f / 16.0f, 1.0f - (3.0f + 1.0f + 1.0f + 3.0f - n) / 16.0f, 0.5f - Parameters.FlagThickness,
				0.5f - Parameters.Pole - n * 1.25f / 16.0f, 1.0f - (n + 1.0f) / 16.0f, 0.5f + Parameters.FlagThickness).
				renderFaceYPos.renderFaceYNeg.renderFaceXNeg.renderFaceZNeg.renderFaceZPos
			def renderFlagPart90Inv(n: Int) = mesh.setRenderBounds(0.5f - Parameters.FlagThickness, 1.0f - (3.0f + 1.0f + 1.0f + 3.0f - n) / 16.0f, 0.5f - Parameters.Pole - (n + 1.0f) * 1.25f / 16.0f,
				0.5f + Parameters.FlagThickness, 1.0f - (n + 1.0f) / 16.0f, 0.5f - Parameters.Pole - n * 1.25f / 16.0f).
				renderFaceYPos.renderFaceYNeg.renderFaceZNeg.renderFaceXNeg.renderFaceXPos
		}
		val meshBase = EmptyStaticMesh.renderBase
		val meshPole = EmptyStaticMesh.renderPole
		val meshBase2 = EmptyStaticMesh.renderBase.renderFaceYNeg
		val meshPole2 = EmptyStaticMesh.renderPole.renderFaceYPos
		val meshdata = EmptyStaticMesh.renderFlagPart(0).renderFlagPart(1).renderFlagPart(2).renderFlagPart(3)
		val meshdata90 = EmptyStaticMesh.renderFlagPart90(0).renderFlagPart90(1).renderFlagPart90(2).renderFlagPart90(3)
		val meshdata180 = EmptyStaticMesh.renderFlagPartInv(0).renderFlagPartInv(1).renderFlagPartInv(2).renderFlagPartInv(3)
		val meshdata270 = EmptyStaticMesh.renderFlagPart90Inv(0).renderFlagPart90Inv(1).renderFlagPart90Inv(2).renderFlagPart90Inv(3)
		val meshdata2 = EmptyStaticMesh.renderFlagPart(0).renderFlagPart(1).renderFlagPart(2).renderFlagPart(3)

		override def getRenderId() = RenderID
		override def shouldRender3DInInventory(model: Int) = true
		override def renderInventoryBlock(block: Block, meta: Int, model: Int, renderer: RenderBlocks)
		{
			glPushMatrix()
			glRotatef(90.0f, 0.0f, 1.0f, 0.0f)
			glScalef(1.25f, 1.25f, 1.25f)
			glTranslatef(-0.5f, -0.5f, -0.5f)
			meshBase2.renderWithNormals(Blocks.stone, 0, renderer)
			meshPole2.renderWithNormals(Blocks.planks, 0, renderer)
			meshdata2.renderWithNormals(Blocks.wool, meta, renderer)
			glPopMatrix()
		}
		override def renderWorldBlock(world: IBlockAccess, x: Int, y: Int, z: Int, block: Block, model: Int, renderer: RenderBlocks) =
		{
			// Color Multiplier
			val cm = block.colorMultiplier(world, x, y, z)
			val (baseR, baseG, baseB) = ((cm >> 16 & 255).toFloat / 255.0f, (cm >> 8 & 255).toFloat / 255.0f, (cm & 255).toFloat / 255.0f)
			val (r, g, b) = if(EntityRenderer.anaglyphEnable) ((baseR * 30.0f + baseG * 59.0f + baseB * 11.0f) / 100.0f, (baseR * 30.0f + baseG * 70.0f) / 100.0f, (baseR * 30.0f + baseB * 70.0f) / 100.0f)
				else (baseR, baseG, baseB)

			// Render with Color Multiplier
			val tess = Tessellator.instance
			val (upLight, xLight, zLight) = (0.5f, 0.8f, 0.6f)

			// Render Base Bottom
			if(renderer.renderAllFaces || block.shouldSideBeRendered(world, x, y - 1, z, 0))
			{
				tess.setBrightness(block.getMixedBrightnessForBlock(world, x, y - 1, z))
				tess.setColorOpaque_F(upLight * r, upLight * g, upLight * b)
				renderer.renderFaceYNeg(block, x, y, z, Blocks.stone.getBlockTextureFromSide(0))
			}
			// Render Pole Top
			if(renderer.renderAllFaces || block.shouldSideBeRendered(world, x, y + 1, z, 1))
			{
				renderer.setRenderBounds(0.5f - Parameters.Pole, Parameters.BaseHeight, 0.5f - Parameters.Pole, 0.5f + Parameters.Pole, 1.0f, 0.5f + Parameters.Pole)
				tess.setBrightness(block.getMixedBrightnessForBlock(world, x, y + 1, z))
				tess.setColorOpaque_F(r, g, b)
				renderer.renderFaceYPos(block, x, y, z, Blocks.planks.getBlockTextureFromSide(1))
			}

			// Render static meshes
			meshBase.render(world, Blocks.stone, 0, x, y, z, renderer, upLight, xLight, zLight, r, g, b)
			meshPole.render(world, Blocks.planks, 0, x, y, z, renderer, upLight, xLight, zLight, r, g, b)
			val mesh = block match
			{
				case Block0 => meshdata
				case Block90 => meshdata90
				case Block180 => meshdata180
				case Block270 => meshdata270
			}
			mesh.render(world, Blocks.wool, world.getBlockMetadata(x, y, z), x, y, z, renderer, upLight, xLight, zLight, r, g, b)
			true
		}
	}
	object TERenderer extends TileEntitySpecialRenderer
	{
		import org.lwjgl.opengl.GL11._
		import mc.client.renderer.Tessellator

		val baseVertices =
		{
			val tess = new Tessellator

			tess.setColorRGBA_F(0.0f, 0.0f, 0.0f , 0.375f)
			tess.addVertex(0.0d, 0.0d, 0.0d)
			tess.addVertex(0.0d, 1.0d, 0.0d)
			tess.addVertex(1.0d, 1.0d, 0.0d)
			tess.addVertex(1.0d, 0.0d, 0.0d)
			tess.getVertexState(0.0f, 0.0f, 0.0f)
		}

		override def renderTileEntityAt(tile: TileEntity, x: Double, y: Double, z: Double, p: Float)
		{
			val viewEntity = this.field_147501_a.field_147551_g
			val fontRender = this.func_147498_b()
			val contentStr = tile.asInstanceOf[TileData].name | Block0.getLocalizedName()
			val contentMetrics = fontRender.getStringWidth(contentStr)
			val pixelScaling = 1.6f / 60.0f

			def renderNameBase()
			{
				val tess = Tessellator.instance

				tess.startDrawingQuads()
				tess.setVertexState(this.baseVertices)
				tess.draw()
			}
			def renderName() { fontRender.drawString(contentStr, 0, 0, 0xffffffff) }
			def renderNamePlate()
			{
				glPushMatrix()
				glScalef(contentMetrics, -8.0f, 1.0f)
				glTranslated(-0.5d, 0.0d, 0.0d)
				glEnable(GL_BLEND)
				glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
				Seq(GL_DEPTH_TEST, GL_TEXTURE_2D, GL_CULL_FACE, GL_LIGHTING) foreach glDisable
				glDepthMask(false)
				renderNameBase()
				glEnable(GL_TEXTURE_2D)
				glPopMatrix()
				glTranslated(-contentMetrics * 0.5d, -8.0d, 0.0d)
				renderName()
				glDepthMask(true)
				glDisable(GL_BLEND)
				Seq(GL_DEPTH_TEST, GL_CULL_FACE, GL_LIGHTING) foreach glEnable
			}

			glPushMatrix()
			glTranslated(x + 0.5d, y + 1.0d, z + 0.5d)
			glRotatef(-viewEntity.rotationYaw, 0.0f, 1.0f, 0.0f)
			glRotatef(viewEntity.rotationPitch, 1.0f, 0.0f, 0.0f)
			glScalef(-pixelScaling, -pixelScaling, pixelScaling)
			glColor4f(1.0f, 1.0f, 1.0f, 1.0f)
			renderNamePlate()
			glPopMatrix()
		}
	}
}
