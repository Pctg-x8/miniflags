package com.cterm2.miniflags.render

import net.minecraft.block.Block
import net.minecraft.init.Blocks
import net.minecraft.world.IBlockAccess
import net.minecraft.client.renderer._
import com.cterm2.tetra.StaticMeshData._
import com.cterm2.miniflags.common.Metrics

// Triangle Flag Block Renderer
object TriBlockRenderer extends cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler
{
	import com.cterm2.miniflags.triflag._

	implicit class MeshConstructorHelper(val mesh: StaticMesh) extends AnyVal
	{
		def renderBase =
			mesh.setRenderBounds(Metrics.Space, 0.0f, Metrics.Space, Metrics.InvSpace, Metrics.BaseHeight, Metrics.InvSpace).
			renderFaceYPos.renderFaceXZ
		def renderPole =
			mesh.setRenderBounds(0.5f - Metrics.Pole, Metrics.BaseHeight, 0.5f - Metrics.Pole, 0.5f + Metrics.Pole, 1.0f, 0.5f + Metrics.Pole).
			renderFaceXZ
		def renderFlagPart(n: Int) =
			mesh.setRenderBounds(0.5f + Metrics.Pole + n * 1.25f / 16.0f, 1.0f - (3.0f + 1.0f + 1.0f + 3.0f - n) / 16.0f, 0.5f - Metrics.FlagThickness, 0.5f + Metrics.Pole + (1 + n) * 1.25f / 16.0f, 1.0f - (n + 1.0f) / 16.0f, 0.5f + Metrics.FlagThickness).
			renderFaceYPos.renderFaceYNeg.renderFaceXPos.renderFaceZNeg.renderFaceZPos
		def renderFlagPart90(n: Int) =
			mesh.setRenderBounds(0.5f - Metrics.FlagThickness, 1.0f - (3.0f + 1.0f + 1.0f + 3.0f - n) / 16.0f, 0.5f + Metrics.Pole + n * 1.25f / 16.0f, 0.5f + Metrics.FlagThickness, 1.0f - (n + 1.0f) / 16.0f, 0.5f + Metrics.Pole + (1 + n) * 1.25f / 16.0f).
			renderFaceYPos.renderFaceYNeg.renderFaceZPos.renderFaceXNeg.renderFaceXPos
		def renderFlagPartInv(n: Int) =
			mesh.setRenderBounds(0.5f - Metrics.Pole - (n + 1.0f) * 1.25f / 16.0f, 1.0f - (3.0f + 1.0f + 1.0f + 3.0f - n) / 16.0f, 0.5f - Metrics.FlagThickness, 0.5f - Metrics.Pole - n * 1.25f / 16.0f, 1.0f - (n + 1.0f) / 16.0f, 0.5f + Metrics.FlagThickness).
			renderFaceYPos.renderFaceYNeg.renderFaceXNeg.renderFaceZNeg.renderFaceZPos
		def renderFlagPart90Inv(n: Int) =
			mesh.setRenderBounds(0.5f - Metrics.FlagThickness, 1.0f - (3.0f + 1.0f + 1.0f + 3.0f - n) / 16.0f, 0.5f - Metrics.Pole - (n + 1.0f) * 1.25f / 16.0f, 0.5f + Metrics.FlagThickness, 1.0f - (n + 1.0f) / 16.0f, 0.5f - Metrics.Pole - n * 1.25f / 16.0f).
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

	override def getRenderId() = TriRenderID
	override def shouldRender3DInInventory(model: Int) = true
	override def renderInventoryBlock(block: Block, meta: Int, model: Int, renderer: RenderBlocks)
	{
		import org.lwjgl.opengl.GL11._

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
		val (baseR, baseG, baseB) = ((cm >> 16 & 0xff).toFloat / 255.0f, (cm >> 8 & 0xff).toFloat / 255.0f, (cm & 0xff).toFloat / 255.0f)
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
			renderer.setRenderBounds(0.5f - Metrics.Pole, Metrics.BaseHeight, 0.5f - Metrics.Pole, 0.5f + Metrics.Pole, 1.0f, 0.5f + Metrics.Pole)
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
