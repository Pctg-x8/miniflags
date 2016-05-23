package com.cterm2.miniflags

import cpw.mods.fml.relauncher._

package object flag
{
	import com.cterm2.tetra.ContentRegistry
	import cpw.mods.fml.client.registry.{ClientRegistry, RenderingRegistry}
	
	var RenderID = 0
	
	def register
	{
		ContentRegistry register Block.setCreativeTab(CreativeTab) as "FlagBlock"
		ContentRegistry register classOf[TileData] as "FlagTileData"
	}
	@SideOnly(Side.CLIENT)
	def registerClient
	{
		ClientRegistry.bindTileEntitySpecialRenderer(classOf[TileData], TERenderer)
		RenderID = RenderingRegistry.getNextAvailableRenderId
		RenderingRegistry.registerBlockHandler(BlockRenderer)
	}
}

package flag
{
	import net.{minecraft => mc}
	import mc.block.BlockContainer, mc.block.material.Material
	import mc.tileentity.TileEntity, mc.client.renderer.tileentity.TileEntitySpecialRenderer
	import mc.world.World
	import mc.util.AxisAlignedBB, mc.entity.{Entity, EntityLivingBase}
	import mc.item.ItemStack
	import scalaz._, Scalaz._
	import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler
	
	object Parameters
	{
		final val Space = 0.125f
		final val InvSpace = 1.0f - Space
		final val BaseHeight = 0.125f
		final val Pole = 1.0f / 16.0f
		final val FlagThickness = 0.75f / 16.0f
	}
	
	object Block extends BlockContainer(Material.rock)
	{
		this.setHardness(1.0f)
		this.setBlockBounds(Parameters.Space, 0.0f, Parameters.Space, Parameters.InvSpace, 1.0f, Parameters.InvSpace)
		
		override val isOpaqueCube = false
		override val isNormalCube = false
		override val renderAsNormalBlock = false
		override def getRenderType() = RenderID
		
		// Collision Box for Visibility Testing(getCollisionBoundingBoxFromPool)
		// Collision Test against Entities
		override def addCollisionBoxesToList(world: World, x: Int, y: Int, z: Int, mask: AxisAlignedBB, boxes: java.util.List[_], entity: Entity)
		{
			Option(AxisAlignedBB.getBoundingBox(x.toDouble + this.minX, y.toDouble, z.toDouble + this.minZ,
				x.toDouble + this.maxX, (y + Parameters.BaseHeight).toDouble, z.toDouble + this.maxZ)) match
			{
				case Some(bb) if mask intersectsWith bb => boxes.asInstanceOf[java.util.List[AnyRef]].add(bb)
				case _ => /* Nothing to do */
			}
		}
		
		override def createNewTileEntity(world: World, meta: Int) = new TileData
		
		override def onBlockPlacedBy(world: World, x: Int, y: Int, z: Int, actor: EntityLivingBase, stack: ItemStack)
		{
			if(!world.isRemote)
			{
				if(stack.hasDisplayName)
				{
					// ModInstance.logger.info("onBlockPlacedBy with DisplayName({})", stack.getDisplayName)
					for(x <- Option(world.getTileEntity(x, y, z)) >>= (x => Option(x.asInstanceOf[TileData])))
					{
						x.name = Some(stack.getDisplayName)
					}
				}
				else
				{
					// ModInstance.logger.info("onBlockPlacedBy")
				}
			}
			else
			{
				// Fetch TileData from Server
				world.markBlockForUpdate(x, y, z)
			}
		}
	}
	class TileData extends TileEntity
	{
		import mc.nbt.NBTTagCompound
		import com.cterm2.tetra.ActiveNBTRecord._
		import mc.network.play.server.S35PacketUpdateTileEntity
		import mc.network.NetworkManager
		
		var name: Option[String] = None
		
		def hasCustomName = this.name.isDefined
		
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
			for(x <- Option(packet) >>= (x => Option(x.func_148857_g())))
			{
				this.name = x[String]("CustomName")
			}
		}
	}
	object BlockRenderer extends ISimpleBlockRenderingHandler
	{
		import mc.block.Block
		import mc.client.renderer.{RenderBlocks, Tessellator}
		import mc.world.IBlockAccess
		import org.lwjgl.opengl.GL11._
		
		override def getRenderId() = RenderID
		override def shouldRender3DInInventory(model: Int) = true
		override def renderInventoryBlock(block: Block, meta: Int, model: Int, renderer: RenderBlocks)
		{
			// render plate
			glPushMatrix()
			glRotatef(90.0f, 0.0f, 1.0f, 0.0f)
			glTranslatef(-0.5f, -0.5f, -0.5f)
			renderer.setRenderBounds(Parameters.Space, 0.0f, Parameters.Space, Parameters.InvSpace, Parameters.BaseHeight, Parameters.InvSpace)
			Tessellator.instance.startDrawingQuads()
			renderer.renderFaceYNeg(block, 0, 0, 0, block.getBlockTextureFromSide(0))
			Tessellator.instance.draw()
			Tessellator.instance.startDrawingQuads()
			renderer.renderFaceYPos(block, 0, 0, 0, block.getBlockTextureFromSide(1))
			Tessellator.instance.draw()
			Tessellator.instance.startDrawingQuads()
			renderer.renderFaceZNeg(block, 0, 0, 0, block.getBlockTextureFromSide(2))
			Tessellator.instance.draw()
			Tessellator.instance.startDrawingQuads()
			renderer.renderFaceZPos(block, 0, 0, 0, block.getBlockTextureFromSide(3))
			Tessellator.instance.draw()
			Tessellator.instance.startDrawingQuads()
			renderer.renderFaceXNeg(block, 0, 0, 0, block.getBlockTextureFromSide(4))
			Tessellator.instance.draw()
			Tessellator.instance.startDrawingQuads()
			renderer.renderFaceXPos(block, 0, 0, 0, block.getBlockTextureFromSide(5))
			Tessellator.instance.draw()
			// render pole
			renderer.setRenderBounds(0.5f - Parameters.Pole, Parameters.BaseHeight, 0.5f - Parameters.Pole,
				0.5f + Parameters.Pole, 1.0f, 0.5f + Parameters.Pole)
			Tessellator.instance.startDrawingQuads()
			renderer.renderFaceYPos(block, 0, 0, 0, block.getBlockTextureFromSide(1))
			Tessellator.instance.draw()
			Tessellator.instance.startDrawingQuads()
			renderer.renderFaceZNeg(block, 0, 0, 0, block.getBlockTextureFromSide(2))
			Tessellator.instance.draw()
			Tessellator.instance.startDrawingQuads()
			renderer.renderFaceZPos(block, 0, 0, 0, block.getBlockTextureFromSide(3))
			Tessellator.instance.draw()
			Tessellator.instance.startDrawingQuads()
			renderer.renderFaceXNeg(block, 0, 0, 0, block.getBlockTextureFromSide(4))
			Tessellator.instance.draw()
			Tessellator.instance.startDrawingQuads()
			renderer.renderFaceXPos(block, 0, 0, 0, block.getBlockTextureFromSide(5))
			// render flag
			for(i <- 0 until 4)
			{
				renderer.setRenderBounds(0.5f + Parameters.Pole + i * 1.25f / 16.0f, 1.0f - (8.0f - i) / 16.0f, 0.5f - Parameters.FlagThickness,
					0.5f + Parameters.Pole + (1.0f + i) * 1.25f / 16.0f, 1.0f - (1.0f + i) / 16.0f, 0.5f + Parameters.FlagThickness)
				renderer.renderFaceYNeg(block, 0, 0, 0, block.getBlockTextureFromSide(0))
				renderer.renderFaceYPos(block, 0, 0, 0, block.getBlockTextureFromSide(1))
				renderer.renderFaceZNeg(block, 0, 0, 0, block.getBlockTextureFromSide(2))
				renderer.renderFaceZPos(block, 0, 0, 0, block.getBlockTextureFromSide(3))
				renderer.renderFaceXPos(block, 0, 0, 0, block.getBlockTextureFromSide(5))
			}
			Tessellator.instance.draw()
			glPopMatrix()
		}
		override def renderWorldBlock(world: IBlockAccess, x: Int, y: Int, z: Int, block: Block, model: Int, renderer: RenderBlocks) =
		{
			// render plate
			renderer.setRenderBounds(Parameters.Space, 0.0f, Parameters.Space, Parameters.InvSpace, Parameters.BaseHeight, Parameters.InvSpace)
			renderer.renderFaceYNeg(block, x, y, z, block.getBlockTextureFromSide(0))
			renderer.renderFaceYPos(block, x, y, z, block.getBlockTextureFromSide(1))
			renderer.renderFaceZNeg(block, x, y, z, block.getBlockTextureFromSide(2))
			renderer.renderFaceZPos(block, x, y, z, block.getBlockTextureFromSide(3))
			renderer.renderFaceXNeg(block, x, y, z, block.getBlockTextureFromSide(4))
			renderer.renderFaceXPos(block, x, y, z, block.getBlockTextureFromSide(5))
			// render pole
			renderer.setRenderBounds(0.5f - Parameters.Pole, Parameters.BaseHeight, 0.5f - Parameters.Pole,
				0.5f + Parameters.Pole, 1.0f, 0.5f + Parameters.Pole)
			renderer.renderFaceYPos(block, x, y, z, block.getBlockTextureFromSide(1))
			renderer.renderFaceZNeg(block, x, y, z, block.getBlockTextureFromSide(2))
			renderer.renderFaceZPos(block, x, y, z, block.getBlockTextureFromSide(3))
			renderer.renderFaceXNeg(block, x, y, z, block.getBlockTextureFromSide(4))
			renderer.renderFaceXPos(block, x, y, z, block.getBlockTextureFromSide(5))
			// render flag
			for(i <- 0 until 4)
			{
				renderer.setRenderBounds(0.5f + Parameters.Pole + i * 1.25f / 16.0f, 1.0f - (8.0f - i) / 16.0f, 0.5f - Parameters.FlagThickness,
					0.5f + Parameters.Pole + (1.0f + i) * 1.25f / 16.0f, 1.0f - (1.0f + i) / 16.0f, 0.5f + Parameters.FlagThickness)
				renderer.renderFaceYNeg(block, x, y, z, block.getBlockTextureFromSide(0))
				renderer.renderFaceYPos(block, x, y, z, block.getBlockTextureFromSide(1))
				renderer.renderFaceZNeg(block, x, y, z, block.getBlockTextureFromSide(2))
				renderer.renderFaceZPos(block, x, y, z, block.getBlockTextureFromSide(3))
				renderer.renderFaceXPos(block, x, y, z, block.getBlockTextureFromSide(5))
			}
			false
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
			val contentStr = tile.asInstanceOf[TileData].name | Block.getLocalizedName()
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
