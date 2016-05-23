package net.minecraftforge.gradle.tweakers

import java.io.File
import java.lang.reflect.{Constructor, Field, AccessibleObject}
import java.util.{List, Map}

import net.minecraft.launchwrapper.{ITweaker, Launch, LaunchClassLoader}
import net.minecraftforge.gradle.GradleStartCommon

import org.apache.logging.log4j.{Level, LogManager, Logger}

// companion
object CoremodTweaker
{
	protected val LOGGER = LogManager.getLogger("GradleStart")
	protected val COREMOD_CLASS = "fml.relauncher.CoreModManager"
	protected val TWEAKER_SORT_FIELD = "tweakSorting"
}
class CoremodTweaker extends ITweaker
{
	import CoremodTweaker._
	
	def injectIntoClassLoader(classLoader: LaunchClassLoader) =
	{
		try
		{
			var coreModList = GradleStartCommon.getFmlClass("fml.relauncher.CoreModManager", classLoader).getDeclaredField("loadPlugins")
			coreModList.setAccessible(true)
			
			// grab constructor
			val clazz = GradleStartCommon.getFmlClass("fml.relauncher.CoreModManager$FMLPluginWrapper", classLoader).asInstanceOf[Class[ITweaker]]
			val construct = clazz.getConstructors()(0).asInstanceOf[Constructor[ITweaker]]
			construct.setAccessible(true)
			
			val fields = clazz.getDeclaredFields()
			val pluginField = fields(1)
			val fileField = fields(3)
			val listField = fields(2)
			
			AccessibleObject.setAccessible(clazz.getConstructors().asInstanceOf[Array[AccessibleObject]], true)
			AccessibleObject.setAccessible(fields.asInstanceOf[Array[AccessibleObject]], true)
			
			val oldList = coreModList.get(null).asInstanceOf[List[ITweaker]]
			
			for(i <- 0 to oldList.size() - 1)
			{
				val tweaker = oldList.get(i)
				
				if(clazz.isInstance(tweaker))
				{
					val coreMod = pluginField.get(tweaker)
					val oldFile = fileField.get(tweaker)
					val newFile = GradleStartCommon.coreMap.get(coreMod.getClass().getCanonicalName())
					
					LOGGER.info("Injecting location in coremod {}", coreMod.getClass().getCanonicalName())
					
					if(newFile != null && oldFile != null)
					{
						// build new tweaker
						oldList.set(i, construct.newInstance(Array[AnyRef](
							fields(0).get(tweaker).asInstanceOf[String],	// name
							coreMod,										// coremod
							newFile,										// location
							fields(4).getInt(tweaker).asInstanceOf[AnyRef],	// sort index?
							listField.get(tweaker).asInstanceOf[List[String]].toArray(new Array[String](0))
						)))
					}
				}
			}
		}
		catch
		{
			case t: Throwable =>
			{
				LOGGER.log(Level.ERROR, "Something went wrong with the coremod adding.")
				t.printStackTrace()
			}
		}
		
		// inject the additional AT tweaker
		val atTweaker = "net.minecraftforge.gradle.tweakers.AccessTransformerTweaker";
		val tweakclass = Launch.blackboard.get("TweakClasses").asInstanceOf[List[String]]
		tweakclass.add(atTweaker);
		
		try
		{
			val f = GradleStartCommon.getFmlClass(COREMOD_CLASS, classLoader).getDeclaredField(TWEAKER_SORT_FIELD)
			f.setAccessible(true)
			f.get(null).asInstanceOf[Map[String, Integer]].put(atTweaker, Integer.valueOf(1001))
		}
		catch
		{
			case t: Throwable =>
			{
				LOGGER.log(Level.ERROR, "Something went wrong with the coremod the AT tweaker adding.")
				t.printStackTrace()
			}
		}
	}
	
	// @formatter:off
	override def getLaunchTarget(): String = null
	override def getLaunchArguments() = new Array[String](0);
	override def acceptOptions(args: List[String], gameDir: File, assetsDir: File, profile: String) = {}
}
