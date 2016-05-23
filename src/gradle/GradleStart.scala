import scala.util.control.Exception._
import scala.collection.convert.WrapAsScala._

import java.io.{File, FileInputStream, FileReader, IOException}
import java.lang.reflect.Field
import java.math.BigInteger
import java.net.Proxy
import java.security.{DigestInputStream, MessageDigest}
import java.util.{HashMap, List, Map}

import net.minecraftforge.gradle.GradleStartCommon

import com.google.common.base.{Strings, Throwables}
import com.google.common.io.Files
import com.google.gson.{Gson, GsonBuilder, JsonIOException, JsonSyntaxException}
import com.mojang.authlib.Agent
import com.mojang.authlib.exceptions.AuthenticationException
import com.mojang.authlib.yggdrasil.{YggdrasilAuthenticationService, YggdrasilUserAuthentication}

// companion
object GradleStart
{
	private val GSON = 
	{
		val builder = new GsonBuilder()
		builder.enableComplexMapKeySerialization()
		builder.setPrettyPrinting()
		builder.create()
	}
	
	// hack natives and launch
	def main(args: Array[String]): Unit =
	{
		// hack natives
		hackNatives()
		
		// launch
		(new GradleStart()).launch(args)
	}
	
	private def hackNatives() =
	{
		val nativesDir = System.getProperty("user.home") + "/.gradle/caches/minecraft/net/minecraft/minecraft_natives/1.7.10"
		val pathJavaLibraries = System.getProperty("java.library.path")
		val paths = if(Strings.isNullOrEmpty(pathJavaLibraries)) nativesDir else pathJavaLibraries + File.pathSeparator + nativesDir
		
		System.setProperty("java.library.path", paths)
		
		// hack the classloader now
		try
		{
			val sysPathsField = classOf[ClassLoader].getDeclaredField("sys_paths")
			sysPathsField.setAccessible(true)
			sysPathsField.set(null, null)
		}
		catch { case t: Throwable => {} }
	}
}

// avoiding compiler bug: SI-2034
object AssetIndex
{
	class AssetEntry { var hash: String = _ }
}
class AssetIndex
{
	import AssetIndex._
	
	var virtual: Boolean = _
	var objects: Map[String, AssetEntry] = _
}

class GradleStart extends GradleStartCommon
{
	import GradleStart._
	
	override def getBounceClass() = "net.minecraft.launchwrapper.Launch" // @@BOUNCERCLIENT@@
	override def getTweakClass() = "cpw.mods.fml.common.launcher.FMLTweaker" // @@CLIENTTWEAKER@@
	override def setDefaultArguments(argMap: Map[String, String]) =
	{
		argMap.put("version", "1.7.10")		// @@MCVERSION@@
		argMap.put("assetIndex", "1.7.10")	// @@ASSETINDEX@@
		argMap.put("assetsDir", "/home/pctgx8/.gradle/caches/minecraft/assets")	// @@ASSETSDIR@@
		argMap.put("accessToken", "FML")
		argMap.put("userProperties", "{}")
		argMap.put("username", null)
		argMap.put("password", null)
	}
	
	override def preLaunch(argMap: Map[String, String], extras: List[String]) =
	{
		if(!Strings.isNullOrEmpty(argMap.get("password")))
		{
			GradleStartCommon.LOGGER.info("Password found, attempting login")
			this.attemptLogin(argMap)
		}
		
		if(!Strings.isNullOrEmpty(argMap.get("assetIndex")))
		{
			this.setupAssets(argMap)
		}
	}
	
	private def attemptLogin(argMap: Map[String, String]): Unit =
	{
		val auth = (new YggdrasilAuthenticationService(Proxy.NO_PROXY, "1")).
			createUserAuthentication(Agent.MINECRAFT).asInstanceOf[YggdrasilUserAuthentication]
		auth.setUsername(argMap.get("username"))
		auth.setPassword(argMap.get("password"))
		argMap.put("password", null)
		
		try { auth.logIn() }
		catch
		{
			case e: AuthenticationException =>
			{
				GradleStartCommon.LOGGER.error("-- Login failed!  " + e.getMessage())
				Throwables.propagate(e)
				return
			}
		}
		
		GradleStartCommon.LOGGER.info("Login Succesful!")
		argMap.put("accessToken", auth.getAuthenticatedToken())
		argMap.put("uuid", auth.getSelectedProfile().getId().toString().replace("-", ""))
		argMap.put("username", auth.getSelectedProfile().getName())
	}
	
	private def setupAssets(argMap: Map[String, String]) =
	{
		if(Strings.isNullOrEmpty(argMap.get("assetsDir"))) throw new IllegalArgumentException("assetsDir is null when assetIndex is not! THIS IS BAD COMMAND LINE ARGUMENTS, fix them")
		val assets = new File(argMap.get("assetsDir"))
		val objects = new File(assets, "objects")
		val assetIndex = new File(new File(assets, "indexes"), argMap.get("assetIndex") + ".json")
		
		try
		{
			val index = loadAssetsIndex(assetIndex)
			if(index.virtual)
			{
				val assetVirtual = new File(new File(assets, "virtual"), argMap.get("assetIndex"))
				argMap.put("assetsDir", assetVirtual.getAbsolutePath())
				GradleStartCommon.LOGGER.info("Setting up virtual assets in: " + assetVirtual.getAbsolutePath())
				val existing = gatherFiles(assetVirtual)
				for(e <- index.objects.entrySet())
				{
					val key = e.getKey()
					val hash = e.getValue().hash.toLowerCase()
					val virtual = new File(assetVirtual, key)
					val source = new File(new File(objects, hash.substring(0, 2)), hash)
					
					if(existing.containsKey(key))
					{
						if(existing.get(key).equals(hash)) existing.remove(key)
						else
						{
							GradleStartCommon.LOGGER.info("  " + key + ": INVALID HASH")
							virtual.delete()
						}
					}
					else
					{
						if(!source.exists()) GradleStartCommon.LOGGER.info("  " + key + ": NEW MISSING " + hash)
						else
						{
							GradleStartCommon.LOGGER.info("  " + key + ": NEW ")
							val parent = virtual.getParentFile()
							if(!parent.exists()) parent.mkdirs()
							Files.copy(source, virtual)
						}
					}
				}
				
				for(key <- existing.keySet())
				{
					GradleStartCommon.LOGGER.info("  " + key + ": REMOVED")
					val virtual = new File(assetVirtual, key)
					virtual.delete()
				}
			}
		}
		catch
		{
			case t: Throwable => Throwables.propagate(t)
		}
	}
	
	private def loadAssetsIndex(json: File) =
	{
		val reader = new FileReader(json)
		val a = GSON.fromJson(reader, classOf[AssetIndex])
		reader.close()
		a
	}
	
	private def getDigest(file: File) =
	{
		var input: DigestInputStream = null
		try
		{
			input = new DigestInputStream(new FileInputStream(file), MessageDigest.getInstance("SHA"))
			val buffer = new Array[Byte](65536)
			var read: Int = 0
			do read = input.read(buffer) while(read > 0)
			input
		}
		catch
		{
			case ignored: Exception => {}
		}
		finally
		{
			if(input != null) try { input.close() } catch { case e: Exception => {} }
		}
		String.format("%1$040x", new BigInteger(1, input.getMessageDigest().digest()))
	}
	
	private def gatherFiles(base: File) =
	{
		val ret = new HashMap[String, String]()
		gatherDir(ret, base, base)
		ret
	}
	private def gatherDir(map: Map[String, String], base: File, target: File): Unit = if(target.exists() && target.isDirectory())
	{
		for(f <- target.listFiles())
		{
			if(f.isDirectory()) gatherDir(map, base, f)
			else
			{
				val path = base.toURI().relativize(f.toURI()).getPath().replace("\\", "/")
				val checksum = this.getDigest(f).toLowerCase()
				map.put(path, checksum)
			}
		}
	}
}
