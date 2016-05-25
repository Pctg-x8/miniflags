package com.cterm2.miniflags

// World renderer
import cpw.mods.fml.common.eventhandler._
import net.minecraftforge.client.event._
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.Tessellator

object WorldRenderer
{
    import org.lwjgl.opengl.GL11._

    final case class CoordinatePair(val x1: Int, val y1: Int, val z1: Int, val x2: Int, val y2: Int, val z2: Int)
    private var pairs = Seq[CoordinatePair]()
    def registerCoordinatePair(x1: Int, y1: Int, z1: Int, x2: Int, y2: Int, z2: Int)
    {
        this.pairs = this.pairs :+ CoordinatePair(x1, y1, z1, x2, y2, z2)
    }

    @SubscribeEvent
    def renderWorldLstEvent(event: RenderWorldLastEvent)
    {
        val player = Minecraft.getMinecraft.thePlayer
        val (xd, yd, zd) = (player.posX, player.posY, player.posZ)
        val tess = Tessellator.instance

        glPushMatrix()
        glTranslated(-xd, -yd, -zd)
        glColor3f(1.0f, 1.0f, 1.0f)
        tess.startDrawing(GL_LINES)
        tess.setColorOpaque_F(1.0f, 1.0f, 1.0f)
        this.pairs foreach
        {
            case CoordinatePair(sx, sy, sz, dx, dy, dz) =>
                tess.addVertex(sx.toDouble + 0.5d, sy, sz.toDouble + 0.5d)
                tess.addVertex(dx.toDouble + 0.5d, dy, dz.toDouble + 0.5d)
        }
        tess.draw()
        glPopMatrix()
    }
}
