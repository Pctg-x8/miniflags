package com.cterm2.miniflags.render

import net.minecraft.block.Block
import net.minecraft.init.Blocks
import net.minecraft.world.IBlockAccess
import net.minecraft.client.renderer._
import com.cterm2.tetra.StaticMeshData._
import com.cterm2.miniflags.common.Metrics

// Square Flag Block Renderer
object SquareBlockRenderer extends cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler
{
	import com.cterm2.miniflags.squflag._
	import scalaz._, Scalaz._, helper._

	val meshData: Block => StaticMesh = Memo.mutableHashMapMemo
	{
		case Block0 => EmptyStaticMesh.renderFlagPartSq(FlagPartNormal)
		case Block90 => EmptyStaticMesh.renderFlagPartSq(FlagPartRot90)
		case Block180 => EmptyStaticMesh.renderFlagPartSq(FlagPartInv)
		case Block270 => EmptyStaticMesh.renderFlagPartSq(FlagPartRot270)
	}

	override def getRenderId() = SquareRenderID
	override def shouldRender3DInInventory(mode: Int) = true
	override def renderInventoryBlock(block: Block, meta: Int, model: Int, renderer: RenderBlocks)
	{
		import org.lwjgl.opengl.GL11._

		glPushMatrix()
		glRotatef(90.0f, 0.0f, 1.0f, 0.0f); glScalef(1.25f, 1.25f, 1.25f); glTranslatef(-0.5f, -0.5f, -0.5f)
		meshBase(true).renderWithNormals(Blocks.stone, 0, renderer)
		meshPole(true).renderWithNormals(Blocks.planks, 0, renderer)
		meshData(Block0).renderWithNormals(Blocks.wool, meta, renderer)
		glPopMatrix()
	}
	override def renderWorldBlock(world: IBlockAccess, x: Int, y: Int, z: Int, block: Block, model: Int, renderer: RenderBlocks) =
	{
		// Render with Color Multiplier
		val tess = Tessellator.instance
		val (r, g, b) = getColorMultiplier(world, x, y, z)
		val (bLight, xLight, zLight) = (0.5f, 0.8f, 0.6f)

		// Render Polar Faces
		if(renderer.renderAllFaces || block.shouldSideBeRendered(world, x, y - 1, z, 0))
		{
			tess setBrightness block.getMixedBrightnessForBlock(world, x, y - 1, z)
			tess.setColorOpaque_F(bLight * r, bLight * g, bLight * b)
			renderer.renderFaceYNeg(block, x, y, z, Blocks.stone.getBlockTextureFromSide(0))
		}
		if(renderer.renderAllFaces || block.shouldSideBeRendered(world, x, y + 1, z, 1))
		{
			renderer.setRenderBounds(Metrics.Pole, Metrics.BaseHeight, Metrics.Pole, 1.0f - Metrics.Pole, 1.0f, 1.0f - Metrics.Pole)
			tess setBrightness block.getMixedBrightnessForBlock(world, x, y + 1, z)
			tess.setColorOpaque_F(r, g, b)
			renderer.renderFaceYPos(block, x, y, z, Blocks.planks.getBlockTextureFromSide(1))
		}

		// Render Static Meshes
		meshBase(false).render(world, Blocks.stone, 0, x, y, z, renderer, bLight, xLight, zLight, r, g, b)
		meshPole(false).render(world, Blocks.planks, 0, x, y, z, renderer, bLight, xLight, zLight, r, g, b)
		meshData(block).render(world, Blocks.wool, world.getBlockMetadata(x, y, z), x, y, z, renderer, bLight, xLight, zLight, r, g, b)
		true
	}
}
