import sbt._
import Keys._

import org.seacourt.build._
import org.seacourt.build.NativeBuild._
import org.seacourt.build.NativeDefaultBuild._



class CMakeConfigHolder( val log : Logger, val compiler : Compiler )
{
    private var macroMap = Map[String, Boolean]()
    
    
    def check_include_file( headerName : String, macroName : String )
    {
        macroMap += (macroName -> PlatformChecks.testForHeader( log, compiler, "test.c", headerName ))
    }
    
    def check_function_exists( functionName : String, macroName : String )
    {
        macroMap += (macroName -> PlatformChecks.testForSymbolDeclaration( log, compiler, "test.c", functionName, Seq() ))
    }
    
    // TODO: Uses pkgconfig to check if a module exists (can also speciy min/max versions)
    def check_module( moduleName : String, macroName : String )
    {
    }
    
    def transformFile( inputFile : File, outputFile : File, stateCacheFile : File )
    {
        FunctionWithResultPath( outputFile )
        { _ =>
                
            val inputData = IO.readLines( inputFile )
            
            val pattern = "#cmakedefine"
            val transformedData = inputData.map
            { l =>
            
                if ( l.startsWith(pattern) )
                {
                    val symbol = l.drop( pattern.length ).trim
                    
                    val symbolStatus = macroMap.getOrElse(symbol, false)
                    
                    if ( symbolStatus )
                    {
                        "#define %s 1".format( symbol )
                    }
                    else
                    {
                        "/* " + l + " */"
                    }
                }
                else l
            }
        
            IO.write( outputFile, transformedData.mkString("\n") )
            
            outputFile
        }.runIfNotCached( stateCacheFile, Seq(inputFile) )
    }
}

object TestBuild extends NativeDefaultBuild
{
    override def checkEnvironment( log : Logger, env : Environment ) =
    {
        // Require a working c and cxx compiler
        PlatformChecks.testCCompiler( log, env.compiler )
        PlatformChecks.testCXXCompiler( log, env.compiler )
    }
    
    
/*
3rdparty/libwebp
3rdparty/openexr
*/
    lazy val openCVConfig = NativeProject( "opencvconfig", file("."),
        Seq(
            exportedIncludeDirectories  <++= (streams, compiler, projectDirectory, projectBuildDirectory) map
            { (s, c, pd, pbd) =>
            
                val ch = new CMakeConfigHolder( s.log, c )

                ch.check_include_file("alloca.h", "HAVE_ALLOCA_H")
                ch.check_function_exists("alloca", "HAVE_ALLOCA")
                ch.check_include_file("linux/videodev.h", "HAVE_CAMV4L")
                ch.check_include_file("linux/videodev2.h", "HAVE_CAMV4L2")
                
                ch.check_module("libdc1394-2", "HAVE_DC1394_2")
                ch.check_module("libdc1394", "HAVE_DC1394")
                
                ch.check_include_file("unistd.h", "HAVE_UNISTD_H")
                
                ch.check_include_file("libavformat/avformat.h", "HAVE_GENTOO_FFMPEG")
                
                ch.check_module("libavcodec", "HAVE_FFMPEG_CODEC")
                ch.check_module("libavformat", "HAVE_FFMPEG_FORMAT")
                ch.check_module("libavutil", "HAVE_FFMPEG_UTIL")
                ch.check_module("libswscale", "HAVE_FFMPEG_SWSCALE")
                
                ch.check_include_file("ffmpeg/avformat.h", "HAVE_FFMPEG_FFMPEG")
                ch.check_include_file("pthread.h", "HAVE_LIBPTHREAD")
                
                
                val autoHeaderRoot = pbd / "autoHeader"
                
                ch.transformFile( file("cmake/templates/cvconfig.h.cmake"), autoHeaderRoot / "cvconfig.h", autoHeaderRoot / "state.cache" )

                Seq(autoHeaderRoot)
            }
        ) )
    
    lazy val libtiff = StaticLibrary( "libtiff", file( "3rdparty/libtiff" ),
        Seq(
            includeDirectories  <++= (streams, compiler, projectDirectory, projectBuildDirectory) map
            { (s, c, pd, pbd) =>
            
                val ch = new CMakeConfigHolder( s.log, c )

                ch.check_include_file("assert.h", "HAVE_ASSERT_H")
                ch.check_include_file("fcntl.h", "HAVE_FCNTL_H")
                ch.check_include_file("io.h", "HAVE_IO_H")
                ch.check_include_file("search.h", "HAVE_SEARCH_H")
                ch.check_include_file("string.h", "HAVE_STRING_H")
                ch.check_include_file("sys/types.h", "HAVE_SYS_TYPES_H")
                ch.check_include_file("unistd.h", "HAVE_UNISTD_H")
                ch.check_function_exists("jbg_newlen", "HAVE_JBG_NEWLEN")
                ch.check_function_exists("mmap", "HAVE_MMAP")
                
                
                val autoHeaderRoot = pbd / "autoHeader"
                
                ch.transformFile( pd / "tif_config.h.cmakein", autoHeaderRoot / "tif_config.h", autoHeaderRoot / "state.cache" )

                Seq(pd, autoHeaderRoot)
            },
            sourceFiles         <<= (projectDirectory, buildEnvironment) map
            { (pd, env) =>
                val allSources = (pd * "*.c").get
                
                allSources.filter
                { f =>

                    f.getName match
                    {
                        case "tif_unix.c"   => env.conf.targetPlatform == LinuxPC
                        case "tif_win32.c"  => false
                        case _              => true
                    }
                }
            }
        ) )
        
    lazy val libjpeg = StaticLibrary( "libjpeg", file( "3rdparty/libjpeg" ),
        Seq(
            includeDirectories  <++= (projectDirectory) map { pd => Seq(pd) },
            sourceFiles         <<= (projectDirectory) map { pd => (pd * "*.c").get }
        ) )
        
    lazy val libpng = StaticLibrary( "libpng", file( "3rdparty/libpng" ),
        Seq(
            includeDirectories  <++= (projectDirectory) map { pd => Seq(pd) },
            sourceFiles         <<= (projectDirectory) map { pd => (pd * "*.c").get }
        ) )
        
    lazy val libjasper = StaticLibrary( "libjasper", file( "3rdparty/libjasper" ),
        Seq(
            includeDirectories  <++= (projectDirectory) map { pd => Seq(pd, pd / "jasper") },
            sourceFiles         <<= (projectDirectory) map { pd => (pd * "*.c").get }
        ) )
        
    lazy val modulesCore = StaticLibrary( "modulesCore", file( "modules/core"),
        Seq(
            includeDirectories          <++= (projectDirectory) map { pd => Seq(pd / "src") },
            exportedIncludeDirectories  <<= (projectDirectory) map { pd => Seq(pd / "include") },
            sourceFiles                 <<= (projectDirectory) map { pd => ((pd / "src") * "*.cpp").get },
            compileFlags                += "-D__OPENCV_BUILD=1"
        ) )
        .nativeDependsOn(openCVConfig)
        
    lazy val modulesCoreTest = StaticLibrary( "modulesCoreTest", file( "modules/core/test"),
        Seq(
            includeDirectories  <++= (projectDirectory) map { pd => Seq(pd, pd / "../../ts/include") },
            sourceFiles         <<= (projectDirectory) map { pd => (pd * "*.cpp").get },
            compileFlags        += "-D__OPENCV_BUILD=1"
        ) )
        .nativeDependsOn(openCVConfig, modulesCore)
        
}
