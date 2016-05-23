package net.minecraftforge.gradle.tweakers

import java.io.File
import java.util.List

import net.minecraft.launchwrapper.{ITweaker, LaunchClassLoader}

class AccessTransformerTweaker extends ITweaker
{
	override def acceptOptions(args: List[String], gameDir: File, assetsDir: File, profile: String) = 
	{
		
	}
	override def injectIntoClassLoader(classLoader: LaunchClassLoader) =
		classLoader.registerTransformer("net.minecraftforge.gradle.GradleStartCommon$AccessTransformerTransformer")
	override def getLaunchTarget(): String = null
	override def getLaunchArguments(): Array[String] = new Array[String](0)
}