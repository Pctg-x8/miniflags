package com.cterm2.miniflags.render

// Render Objects for Triangular(Default) Flags

package object flag
{
	import com.cterm2.miniflags.flag._
	import cpw.mods.fml.client.registry.{ClientRegistry, RenderingRegistry}

	var RenderID = 0

	def init()
	{
		ClientRegistry.bindTileEntitySpecialRenderer(classOf[TileData], TERenderer)
		RenderID = RenderingRegistry.getNextAvailableRenderId
		RenderingRegistry.registerBlockHandler(BlockRenderer)
	}
}

package flag
{
	import com.cterm2.miniflags.flag._

	import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer
	import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler
	import net.minecraft.client.renderer.{RenderBlocks, Tessellator, EntityRenderer}
	import org.lwjgl.opengl.GL11._

	object BlockRenderer extends ISimpleBlockRenderingHandler
	{
		import net.minecraft.block.Block
		import net.minecraft.init.Blocks
		import net.minecraft.world.IBlockAccess
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
		import net.minecraft.tileentity.TileEntity

		override def renderTileEntityAt(tile: TileEntity, x: Double, y: Double, z: Double, p: Float)
		{
			val viewEntity = this.field_147501_a.field_147551_g
			val fontRender = this.func_147498_b()
			val contentStr = tile.asInstanceOf[TileData].nameOrDefaultLocalized
			val contentMetrics = fontRender.getStringWidth(contentStr)
			val pixelScaling = 1.6f / 60.0f

			def renderNameBase()
			{
				val tess = Tessellator.instance

				tess.startDrawingQuads()
				tess.setColorRGBA_F(0.0f, 0.0f, 0.0f, 0.375f)
				tess.addVertex(0.0d, 0.0d, 0.0d)
				tess.addVertex(0.0d, 1.0d, 0.0d)
				tess.addVertex(1.0d, 1.0d, 0.0d)
				tess.addVertex(1.0d, 0.0d, 0.0d)
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
