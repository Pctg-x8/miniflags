package com.cterm2.miniflags

// World renderer
import cpw.mods.fml.common.eventhandler._
import net.minecraftforge.client.event._
import net.minecraftforge.event.world._
import net.minecraft.world.WorldServer
import net.minecraft.client.multiplayer.WorldClient
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.Tessellator
import scalaz._, Scalaz._
import cpw.mods.fml.relauncher.{SideOnly, Side}

object WorldEvents
{
    import org.lwjgl.opengl.GL11._

	@SideOnly(Side.CLIENT)
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
		ClientLinkManager.links foreach
		{
			case Link(Coordinate(sx, sy, sz), Coordinate(dx, dy, dz)) =>
				Tessellator.instance.addVertex(sx.toDouble + 0.5d, sy.toDouble, sz.toDouble + 0.5d)
				Tessellator.instance.addVertex(dx.toDouble + 0.5d, dy.toDouble, dz.toDouble + 0.5d)
		}
        Tessellator.instance.draw()
		glEnable(GL_TEXTURE_2D)
        glPopMatrix()
    }

	@SideOnly(Side.SERVER)
	@SubscribeEvent
	def onWorldLoad(event: WorldEvent.Load)
	{
		// Load event fired only in server
		// load manager explicitly
		ObjectManager.instanceForWorld(event.world.asInstanceOf[WorldServer])
	}
}
