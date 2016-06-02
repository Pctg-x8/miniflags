package com.cterm2.miniflags

package object render
{
	import scalaz._, Scalaz._
	import com.cterm2.tetra.StaticMeshData._
	import net.minecraft.world.IBlockAccess
	import net.minecraft.client.renderer.EntityRenderer

	var TriRenderID = 0
	var SquareRenderID = 0

	def init()
	{
		import cpw.mods.fml.client.registry.{RenderingRegistry, ClientRegistry}

		TriRenderID = RenderingRegistry.getNextAvailableRenderId
		RenderingRegistry.registerBlockHandler(TriBlockRenderer)
		SquareRenderID = RenderingRegistry.getNextAvailableRenderId
		RenderingRegistry.registerBlockHandler(SquareBlockRenderer)

		ClientRegistry.bindTileEntitySpecialRenderer(classOf[TileData], nameTag.TERenderer)
	}

	sealed abstract class FlagPartMode
	case object FlagPartNormal extends FlagPartMode
	case object FlagPartRot90 extends FlagPartMode
	case object FlagPartInv extends FlagPartMode
	case object FlagPartRot270 extends FlagPartMode

	// Common Mesh Data/Constructor
	object helper
	{
		implicit class MeshConstructorHelper(val mesh: StaticMesh) extends AnyVal
		{
			import com.cterm2.miniflags.common.Metrics

			def renderBase =
				mesh.setXZCenterBoxBounds(Metrics.Space, Metrics.Space, 0.0f, Metrics.BaseHeight).renderFaceYPos.renderFaceXZ
			def renderPole =
				mesh.setXZCenterBoxBounds(Metrics.Pole, Metrics.Pole, Metrics.BaseHeight, 1.0f).renderFaceXZ
			def renderFlagPart(mode: FlagPartMode) = mesh ++ (0 until 4 flatMap { n => mode match
			{
				case FlagPartNormal =>
					EmptyStaticMesh.setRenderBoundsWHD(1.0f - Metrics.Pole + n * 1.25f / 16.0f, 1.0f - (8.0f - n) / 16.0f, 0.5f - Metrics.FlagThickness, 1.25f / 16.0f, (1 + (3 - n) * 2.0f) / 16.0f, Metrics.FlagThickness * 2.0f).
					renderFaceYPos.renderFaceYNeg.renderFaceXPos.renderFaceZNeg.renderFaceZPos
				case FlagPartRot90 =>
					EmptyStaticMesh.setRenderBoundsWHD(0.5f - Metrics.FlagThickness, 1.0f - (8.0f - n) / 16.0f, 1.0f - Metrics.Pole + n * 1.25f, Metrics.FlagThickness * 2.0f, (1 + (3 - n) * 2.0f) / 16.0f, 1.25f / 16.0f).
					renderFaceYPos.renderFaceYNeg.renderFaceZPos.renderFaceXNeg.renderFaceXPos
				case FlagPartInv =>
					EmptyStaticMesh.setRenderBoundsWHD(Metrics.Pole - (n + 1.0f) * 1.25f / 16.0f, 1.0f - (8.0f - n) / 16.0f, 0.5f - Metrics.FlagThickness, 1.25f / 16.0f, (1 + (3 - n) * 2.0f) / 16.0f, Metrics.FlagThickness * 2.0f).
					renderFaceYPos.renderFaceYNeg.renderFaceXNeg.renderFaceZNeg.renderFaceZPos
				case FlagPartRot270 =>
					EmptyStaticMesh.setRenderBoundsWHD(0.5f - Metrics.FlagThickness, 1.0f - (8.0f - n) / 16.0f, Metrics.Pole - (n + 1.0f) * 1.25f / 16.0f, Metrics.FlagThickness * 2.0f, (1 + (3 - n) * 2.0f) / 16.0f, 1.25f / 16.0f).
					renderFaceYPos.renderFaceYNeg.renderFaceZNeg.renderFaceXNeg.renderFaceXPos
			}})
			def renderFlagPartSq(mode: FlagPartMode) = mode match
			{
				case FlagPartNormal =>
					mesh.setRenderBoundsWHD(1.0f - Metrics.Pole, 1.0f - 5.5f / 16.0f, 0.5f - Metrics.FlagThickness, Metrics.Pole - Metrics.Space, 4.5f / 16.0f, Metrics.FlagThickness * 2.0f).
					renderFaceYPos.renderFaceYNeg.renderFaceXPos.renderFaceZNeg.renderFaceZPos
				case FlagPartRot90 =>
					mesh.setRenderBoundsWHD(0.5f - Metrics.FlagThickness, 1.0f - 5.5f / 16.0f, 1.0f - Metrics.Pole, Metrics.FlagThickness * 2.0f, 4.5f / 16.0f, Metrics.Pole - Metrics.Space).
					renderFaceYPos.renderFaceYNeg.renderFaceZPos.renderFaceXNeg.renderFaceXPos
				case FlagPartInv =>
					mesh.setRenderBoundsWHD(Metrics.Space, 1.0f - 5.5f / 16.0f, 0.5f - Metrics.FlagThickness, Metrics.Pole - Metrics.Space, 4.5f / 16.0f, Metrics.FlagThickness * 2.0f).
					renderFaceYPos.renderFaceYNeg.renderFaceXNeg.renderFaceZNeg.renderFaceZPos
				case FlagPartRot270 =>
					mesh.setRenderBoundsWHD(0.5f - Metrics.FlagThickness, 1.0f - 5.5f / 16.0f, Metrics.Space, Metrics.FlagThickness * 2.0f, 4.5f / 16.0f, Metrics.Pole - Metrics.Space).
					renderFaceYPos.renderFaceYNeg.renderFaceZNeg.renderFaceXNeg.renderFaceXPos
			}
		}
	}
	import helper._

	// Argument: with polar face(Boolean)
	val meshBase: Boolean => StaticMesh = Memo.mutableHashMapMemo
	{
		case false => EmptyStaticMesh.renderBase
		case true => EmptyStaticMesh.renderBase.renderFaceYNeg
	}
	val meshPole: Boolean => StaticMesh = Memo.mutableHashMapMemo
	{
		case false => EmptyStaticMesh.renderPole
		case true => EmptyStaticMesh.renderPole.renderFaceYPos
	}

	def getColorMultiplier(world: IBlockAccess, x: Int, y: Int, z: Int) =
	{
		def rationate(args: (Float, Float)*) = (args map { case (v, r) => v * r } reduceLeft (_ + _)) / (args map { case (_, r) => r } reduceLeft (_ + _))

		// Color Multiplier
		val cm = world.getBlock(x, y, z).colorMultiplier(world, x, y, z)
		val (baseR, baseG, baseB) = (((cm >> 16) & 0xff) / 255.0f, ((cm >> 8) & 0xff) / 255.0f, (cm & 0xff) / 255.0f)
		if(EntityRenderer.anaglyphEnable)
			(rationate(baseR -> 30.0f, baseG -> 59.0f, baseB -> 11.0f), rationate(baseR -> 30.0f, baseG -> 70.0f), rationate(baseR -> 30.0f, baseB -> 70.0f))
		else
			(baseR, baseG, baseB)
	}
}
