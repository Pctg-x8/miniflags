package com.cterm2.miniflags

// Render Objects for Triangular(Default) Flags

package render
{
	package object nameTag
	{
		// TileRenderer
		import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer
		import net.minecraft.client.renderer._
		import org.lwjgl.opengl.GL11._

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
}
