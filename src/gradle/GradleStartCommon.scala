package net.minecraftforge.gradle

import scala.util.{Try, Success, Failure}
import scala.collection.convert.WrapAsScala._

import java.io.{File, FileInputStream, IOException}
import java.lang.reflect.{Field, Method}
import java.net.{URL, URLClassLoader}
import java.util.{ArrayList, Collection, List, Map, Set}
import java.util.jar.{JarFile, Manifest}

import joptsimple.{NonOptionArgumentSpec, OptionParser, OptionSet}
import net.minecraft.launchwrapper.{IClassTransformer, Launch, LaunchClassLoader}
import org.apache.logging.log4j.{Level, LogManager, Logger}
import com.google.common.base.{Charsets, Joiner, Splitter, Strings}
import com.google.common.collect.{Lists, Maps, Sets}
import com.google.common.io.Files

// companion
object GradleStartCommon
{
	val LOGGER = LogManager.getLogger("GradleStart")
	private val NO_CORE_SEARCH = "noCoreSearch"
	
	val CACHE_BASE		= new File(System.getProperty("user.home") + "/.gradle/caches/minecraft/")
	val MCF_API_PATH	= new File(CACHE_BASE, "net/minecraftforge/forge/1.7.10-10.13.4.1614-1.7.10")
	val SRG_DIR			= new File(MCF_API_PATH, "srgs")			// SRGDIR
	val SRG_NOTCH_SRG	= new File(SRG_DIR, "notch-srg.srg")		// SRG_NOTCH_SRG
	val SRG_NOTCH_MCP	= new File(SRG_DIR, "notch-mcp.srg")		// SRG_NOTCH_MCP
	val SRG_SRG_MCP		= new File(SRG_DIR, "srg-mcp.srg")		// SRG_SRG_MCP
	val SRG_MCP_SRG		= new File(SRG_DIR, "mcp-srg.srg")		// SRG_MCP_SRG
	val SRG_MCP_NOTCH	= new File(SRG_DIR, "mcp-notch.srg")		// SRG_MCP_NOTCH
	val CSV_DIR			= new File(MCF_API_PATH, "unpacked/conf")			// CSVDIR
	
	// REFLECTION HELPER //
	private val MC_VERSION = "1.7.10"
	private val FML_PACK_OLD = "cpw.mods"
	private val FML_PACK_NEW = "net.minecraftforge"
	
	protected def getFmlClass(classname: String): Class[_] = getFmlClass(classname, classOf[GradleStartCommon].getClassLoader())
	def getFmlClass(classname: String, loader: ClassLoader) =
	{
		if(!classname.startsWith("fml")) throw new IllegalArgumentException("invalid FML classname")
		
		val fullClassName = (if(MC_VERSION.startsWith("1.7")) FML_PACK_OLD else FML_PACK_NEW) + "." + classname
		Class.forName(fullClassName, true, loader)
	}
	
	// COREMOD AND AT HACK //
	
	// coremod hack
	private val COREMOD_VAR = "fml.coreMods.load"
	private val COREMOD_MF = "FMLCorePlugin"
	// AT hack
	private val MOD_ATD_CLASS = "fml.common.asm.transformers.ModAccessTransformer"
	private val MOD_AT_METHOD = "addJar"
	val coreMap: Map[String, File] = Maps.newHashMap()
	
	// CUSTOM TWEAKER FOR COREMOD HACK //
	
	// here and not in the tweaker package because classloader hell
	final class AccessTransformerTransformer extends IClassTransformer
	{
		this.doStuff(getClass().getClassLoader().asInstanceOf[LaunchClassLoader])
		
		// find the instance I want. AND grab the type too, since thats better than Class.forName()
		private def findTransformerInstance(classloader: LaunchClassLoader) =
			(for(transformer <- classloader.getTransformers() if transformer.getClass().getCanonicalName().endsWith(MOD_ATD_CLASS))
				yield (transformer.getClass(), transformer)).lastOption
		
		private def doStuff(classloader: LaunchClassLoader) = findTransformerInstance(classloader) match
		{
			case Some((clazz, instance)) =>
			{
				// grab the list of Modifiers I wanna mess with
				Try
				{
					// super class of ModAccessTransformer is AccessTransformer
					val f = clazz.getSuperclass().getDeclaredFields()(1)
					f.setAccessible(true)
					f.get(instance).asInstanceOf[com.google.common.collect.Multimap[String, AnyRef]].values()
				}
				match
				{
					case Success(modifiers) if !modifiers.isEmpty() =>
					{
						// grab the field I wanna hack
						Try
						{
							// get 1 from the collection
							val mod = modifiers.iterator().next()
							val nameField = mod.getClass().getFields()(0)
							nameField.setAccessible(true)
							nameField
						}
						match
						{
							case Success(nameField) =>
							{
								val nameMap: Map[String, String] = Maps.newHashMap()
								try
								{
									this.readCsv(new File(CSV_DIR, "fields.csv"), nameMap)
									this.readCsv(new File(CSV_DIR, "methods.csv"), nameMap)
									
									LOGGER.log(Level.INFO, "Remapping AccessTransformer rules...")
									
									// finally hit the modifiers
									for(modifier <- modifiers)
									{
										val name = nameField.get(modifier)
										val newName = nameMap.get(name)
										if(newName != null) nameField.set(modifier, newName)
									}
								}
								catch
								{
									case e: IOException =>
									{
										LOGGER.log(Level.ERROR, "Could not load CSV files!")
										e.printStackTrace()
									}
								}
							}
							case Failure(t) => LOGGER.log(Level.ERROR, "AccessTransformer.Modifier.name field was somehow not found...")
						}
					}
					case Success(x) => {}  // nothing to do if empty
					case Failure(t) => LOGGER.log(Level.ERROR, "AccessTransformer.modifiers field was somwhow not found...")
				}
			}
			case _ => LOGGER.log(Level.ERROR, "ModAccessTransformer was somehow not found.")
		}
		
		private def readCsv(file: File, map: Map[String, String]) =
		{
			LOGGER.log(Level.DEBUG, "Reading CSV file: {}", file)
			val split = (Splitter on ',').trimResults().limit(3)
			for(line <- Files.readLines(file, Charsets.UTF_8) if !line.startsWith("searge"))
			{
				val splits = split.splitToList(line)
				map.put(splits.get(0), splits.get(1))
			}
		}
		
		// nothing here
		override def transform(name: String, transformedName: String, basicClass: Array[Byte]) = basicClass
	}
}
abstract class GradleStartCommon
{
	import GradleStartCommon._
	
	val argMap: Map[String, String] = Maps.newHashMap()
	var extras: List[String] = Lists.newArrayList()
	
	protected def setDefaultArguments(argMap: Map[String, String]): Unit
	protected def preLaunch(argMap: Map[String, String], extras: List[String]): Unit
	def getBounceClass(): String
	def getTweakClass(): String
	
	protected def launch(args: Array[String]) =
	{
		// DEPRECATED, use the properties below instead!
		System.setProperty("net.minecraftforge.gradle.GradleStart.srgDir", SRG_DIR.getCanonicalPath())
		
		// set system vars for passwords
		System.setProperty("net.minecraftforge.gradle.GradleStart.srg.notch-srg", SRG_NOTCH_SRG.getCanonicalPath())
		System.setProperty("net.minecraftforge.gradle.GradleStart.srg.notch-mcp", SRG_NOTCH_MCP.getCanonicalPath())
		System.setProperty("net.minecraftforge.gradle.GradleStart.srg.srg-mcp", SRG_SRG_MCP.getCanonicalPath())
		System.setProperty("net.minecraftforge.gradle.GradleStart.srg.mcp-srg", SRG_MCP_SRG.getCanonicalPath())
		System.setProperty("net.minecraftforge.gradle.GradleStart.srg.mcp-notch", SRG_MCP_NOTCH.getCanonicalPath())
		System.setProperty("net.minecraftforge.gradle.GradleStart.csvDir", CSV_DIR.getCanonicalPath())
		
		// set defaults!
		this.setDefaultArguments(this.argMap)
		// parse stuff
		this.parseArgs(args)
		// now send it back for prelaunch
		this.preLaunch(this.argMap, this.extras)
		
		// because its the dev env
		System.setProperty("fml.ignoreInvalidMinecraftCertificates", "true")
		
		// coremod searching
		if(this.argMap.get(NO_CORE_SEARCH) == null) this.searchCoremods()
		else LOGGER.info("GradleStart coremod searching disabled!")
		
		// now the actual launch args
		val actualArgs = this.getArgs()
		
		// clear it out
		this.argMap.clear()
		this.extras.clear()
		
		// launch
		System.gc()
		val bounce = this.getBounceClass()
		if(bounce.endsWith("launchwrapper.Launch")) Launch.main(actualArgs)
		else Class.forName(bounce).getDeclaredMethod("main", classOf[Array[String]]).invoke(null, Array[Object](actualArgs))
	}
	
	private def getArgs() =
	{
		val list = new ArrayList[String](22)
		
		for(e <- this.argMap.entrySet())
		{
			val v = e.getValue()
			if(!Strings.isNullOrEmpty(v))
			{
				list.add("--" + e.getKey())
				list.add(v)
			}
		}
		
		// grab tweakClass
		if(!Strings.isNullOrEmpty(this.getTweakClass()))
		{
			list.add("--tweakClass")
			list.add(this.getTweakClass())
		}
		if(this.extras != null) list.addAll(this.extras)
		
		val out = list.toArray(new Array[String](0))
		
		// final logging.
		val b = new StringBuilder()
		b.append('[')
		var x = 0
		while(x < out.length)
		{
			b.append(out(x)).append(", ")
			if("--accessToken".equalsIgnoreCase(out(x)))
			{
				b.append("{REDACTED}, ")
				x = x + 1
			}
			x = x + 1
		}
		b.replace(b.length() - 2, b.length(), "")
		b.append(']')
		LOGGER.info("Running with arguments: " + b.toString())
		
		out
	}
	
	private def parseArgs(args: Array[String]) =
	{
		val parser = new OptionParser()
		parser.allowsUnrecognizedOptions()
		
		for(key <- this.argMap.keySet()) parser.accepts(key).withRequiredArg().ofType(classOf[String])
		// accept the noCoreSearch thing
		parser.accepts(NO_CORE_SEARCH)
		
		val nonOption = parser.nonOptions()
		
		val options = parser.parse(args: _*)
		for(key <- this.argMap.keySet())
		{
			if(options.hasArgument(key))
			{
				val value = options.valueOf(key).asInstanceOf[String]
				this.argMap.put(key, value)
				if(!"password".equalsIgnoreCase(key)) LOGGER.info(key + ":" + value)
			}
		}
		
		if(options.has(NO_CORE_SEARCH)) argMap.put(NO_CORE_SEARCH, "")
		
		this.extras = Lists.newArrayList(nonOption.values(options))
		LOGGER.info("Extra: " + extras)
	}
	
	// REFLECTION HELPER(implemented in companion object) //
	// COREMOD AND AT HACK //
	
	private def searchCoremods() =
	{
		// initialize AT hack Method
		val atRegister = Try { getFmlClass(MOD_ATD_CLASS).getDeclaredMethod(MOD_AT_METHOD, classOf[JarFile]) }
		
		for(url <- classOf[GradleStartCommon].getClassLoader().asInstanceOf[URLClassLoader].getURLs()) if(!url.getProtocol().startsWith("file"))
		{
			val coreMod = new File(url.toURI().getPath())
			if(coreMod.exists())
			{
				var manifest: Option[Manifest] = None
				if(coreMod.isDirectory())
				{
					val manifestMF = new File(coreMod, "META-INF/MENIFEST.MF")
					if(manifestMF.exists())
					{
						val stream = new FileInputStream(manifestMF)
						manifest = Some(new Manifest(stream))
						stream.close()
					}
				}
				else if(coreMod.getName().endsWith("jar"))
				{
					val jar = new JarFile(coreMod)
					val man = jar.getManifest()
					if(man != null)
					{
						atRegister.foreach(_.invoke(null, jar))
						manifest = Some(jar.getManifest())
					}
					jar.close()
				}
				
				manifest.foreach(m =>
				{
					val clazz = m.getMainAttributes().getValue(COREMOD_MF)
					if(!Strings.isNullOrEmpty(clazz))
					{
						LOGGER.info("Found and added coremod: " + clazz)
						coreMap.put(clazz, coreMod)
					}
				})
			}
		}
		
		// set property
		val coremodsSet: Set[String] = Sets.newHashSet()
		if(!Strings.isNullOrEmpty(System.getProperty(COREMOD_VAR)))
		{
			coremodsSet.addAll(Splitter.on(',').splitToList(System.getProperty(COREMOD_VAR)))
		}
		coremodsSet.addAll(coreMap.keySet())
		System.setProperty(COREMOD_VAR, Joiner.on(',').join(coremodsSet))
		
		// ok... tweaker hack now
		if(!Strings.isNullOrEmpty(this.getTweakClass()))
		{
			this.extras.add("--tweakClass")
			this.extras.add("net.minecraftforge.gradle.tweakers.CoremodTweaker")
		}
	}
	
	// CUSTOM TWEAKER FOR COREMOD HACK //
}
