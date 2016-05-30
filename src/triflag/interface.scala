package com.cterm2.miniflags.triflag

// Graphics Interface of Triangle Flag

import net.minecraft.client.gui._
import net.minecraft.client.gui.inventory.GuiContainer
import net.minecraft.world.World
import net.minecraft.inventory.Container
import net.minecraft.entity.player.EntityPlayer

final class FlagSettingsContainer(val tile: TileData) extends Container
{
	override def canInteractWith(player: EntityPlayer) =
		player.getDistanceSq(tile.xCoord + 0.5d, tile.yCoord + 0.5d, tile.zCoord + 0.5d) <= 64.0d
}
// Ref: GuiRepair(Interface of Anvil)
@SideOnly(Side.CLIENT)
final class FlagSettingsInterface(val world: World, val tile: TileData) extends GuiContainer(new FlagSettingsContainer(tile))
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

	private trait IInterfacePage
	{
		val titleLocalized = ""

		def onKeyType(chr: Char, rep: Int): Boolean
		def updateScreen()
		def onMouseClicked(x: Int, y: Int, button: Int)
		def drawControls(mx: Int, my: Int)
		def drawLabels()
	}
	// Only can be constructed in initGui()
	private final class UnlinkedFlagInterface extends IInterfacePage
	{
		override val titleLocalized = t"gui.labels.UnlinkedFlags"
		private val linkTargetInputField =
		{
			val o = new GuiTextField(CommonInterface.fontRendererObj, CommonInterface.guiLeft + 10, CommonInterface.guiTop + 58, CommonInterface.xSize - 39, 12)
			o.setEnableBackgroundDrawing(false)
			o.setMaxStringLength(20)
			o
		}
		private var overingLinkButton = false

		def onKeyType(chr: Char, rep: Int) = this.linkTargetInputField.textboxKeyTyped(chr, rep)
		def updateScreen() { this.linkTargetInputField.updateCursorCounter() }
		def onMouseClicked(x: Int, y: Int, button: Int) { this.linkTargetInputField.mouseClicked(x, y, button) }
		def drawControls(mx: Int, my: Int)
		{
			this.linkTargetInputField.drawTextBox()
			glColor4f(1.0f, 1.0f, 1.0f, 1.0f)
			CommonInterface.mc.renderEngine bindTexture CommonInterface.resource
			this.overingLinkButton =
				((CommonInterface.guiLeft + CommonInterface.xSize - 25 + 1) to (CommonInterface.guiLeft + CommonInterface.xSize - 25 + 17) contains mx) &&
				((CommonInterface.guiTop + 54 + 1) to (CommonInterface.guiTop + 54 + 16) contains my)
			CommonInterface.drawTexturedModalRect(CommonInterface.guiLeft + CommonInterface.xSize - 25, CommonInterface.guiTop + 54,
				CommonInterface.xSize + (if(this.overingLinkButton) 17 else 0), 0, 17, 16)
			if(this.overingLinkButton) CommonInterface.drawHoveringText(List("Link!!"), mx, my, CommonInterface.fontRendererObj)
		}
		def drawLabels()
		{
			CommonInterface.fontRendererObj.drawString(t"gui.labels.LinkTo", 4, 46, 0x404040)
		}
	}
	private final class LinkedFlagInterface extends IInterfacePage
	{
		override val titleLocalized = t"gui.labels.LinkedFlags"

		def onKeyType(chr: Char, rep: Int) = false
		def updateScreen() {}
		def onMouseClicked(x: Int, y: Int, button: Int) {}
		def drawControls(mx: Int, my: Int) {}
		def drawLabels() {}
	}
	private var interfaceHandler: IInterfacePage = null

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
		this.interfaceHandler.drawControls(p1, p2)
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
