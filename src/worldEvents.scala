package com.cterm2.miniflags

// World renderer
import cpw.mods.fml.common.eventhandler._
import net.minecraftforge.client.event._
import net.minecraftforge.event.world._
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.shader.TesselatorVertexState

object WorldEvents
{
    import org.lwjgl.opengl.GL11._

    final case class CoordinatePair(val x1: Int, val y1: Int, val z1: Int, val x2: Int, val y2: Int, val z2: Int)
    private var pairs = Seq[CoordinatePair]()
    def registerCoordinatePair(x1: Int, y1: Int, z1: Int, x2: Int, y2: Int, z2: Int)
    {
        this.pairs = this.pairs :+ CoordinatePair(x1, y1, z1, x2, y2, z2)
    }
	def breakFromTerminal(x: Int, y: Int, z: Int)
	{
		this.pairs = this.pairs filterNot
		{
			p => ((p.x1, p.y1, p.z1) == (x, y, z)) || ((p.x2, p.y2, p.z2) == (x, y, z))
		}
	}

    @SubscribeEvent
    def renderEvent(event: RenderWorldLastEvent)
    {
        val player = Minecraft.getMinecraft.thePlayer
		def applyPartialTicks(prev: Double, cur: Double) = prev + (cur - prev) * event.partialTicks

        glPushMatrix()
		glLineWidth(1.5f)
        glTranslated(-applyPartialTicks(player.prevPosX, player.posX),
			-applyPartialTicks(player.prevPosY, player.posY),
			-applyPartialTicks(player.prevPosZ, player.posZ))
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f)
		glDisable(GL_TEXTURE_2D)
		Tessellator.instance.startDrawing(GL_LINES)
        Tessellator.instance.setColorOpaque_F(1.0f, 1.0f, 1.0f)
		this.pairs foreach
		{
			case CoordinatePair(sx, sy, sz, dx, dy, dz) =>
				Tessellator.instance.addVertex(sx.toDouble + 0.5d, sy.toDouble, sz.toDouble + 0.5d)
				Tessellator.instance.addVertex(dx.toDouble + 0.5d, dy.toDouble, dz.toDouble + 0.5d)
		}
        Tessellator.instance.draw()
		glEnable(GL_TEXTURE_2D)
        glPopMatrix()
    }

	@SubscribeEvent
	def onWorldLoad(event: WorldEvent.Load)
	{
		// load manager explicitly
		ObjectManager.instanceForWorld(event.world)
	}
}
