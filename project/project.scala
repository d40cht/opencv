import sbt._
import Keys._

import org.seacourt.build._
import org.seacourt.build.NativeBuild._
import org.seacourt.build.NativeDefaultBuild._

class CMakePlatformConfig( val log : Logger, val compiler : Compiler )
{
    import PlatformChecks._
    
    private var macroMap = Map[String, Boolean]()
    
    class MacroRegistrationHelper( val state : Boolean )
    {
        def registerDefine( name : String ) =
        {
            macroMap += (name -> state)
            
            state
        }
    }
    
    implicit def macroRegistrationHelper( state : Boolean ) = new MacroRegistrationHelper( state )
    
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
                    
                    macroMap.get( symbol ) match
                    {
                        case Some(symbolStatus)    =>
                        {
                            if ( symbolStatus )
                            {
                                "#define %s 1".format( symbol )
                            }
                            else
                            {
                                "/* " + l + " */"
                            }
                        }
                        case None                   =>
                        {
                            log.warn( "define not found in CMake include file: " + symbol )
                            
                            "/* " + l + " */"
                        }
                    }
                }
                else l
            }
        
            IO.write( outputFile, transformedData.mkString("\n") )
            
            outputFile
        }.runIfNotCached( stateCacheFile, Seq(inputFile) )
    }
    
    def headerExists( fileName : String, compileType : PlatformChecks.CompileType ) = PlatformChecks.testForHeader( log, compiler, compileType, fileName )
    def functionExists( functionName : String, compileType : PlatformChecks.CompileType ) = PlatformChecks.testForSymbolDeclaration( log, compiler, compileType, functionName, Seq() )
    def moduleExists( moduleName : String, compileType : PlatformChecks.CompileType ) = true
}

object TestBuild extends NativeDefaultBuild
{
    override def checkEnvironment( log : Logger, env : Environment ) =
    {
        // Require a working c and cxx compiler
        PlatformChecks.testCCompiler( log, env.compiler )
        PlatformChecks.testCXXCompiler( log, env.compiler )
    }
    
    def OpenCVModuleStaticLibrary( name : String, baseDir : File, settings : => Seq[sbt.Project.Setting[_]] ) =
    {
        StaticLibrary( name, baseDir,
            Seq(
                includeDirectories          <++= (projectDirectory) map { pd => Seq(pd / "src") },
                exportedIncludeDirectories  <<= (projectDirectory) map { pd => Seq(pd / "include") },
                cxxSourceFiles              <<= (projectDirectory) map { pd => ((pd / "src") * "*.cpp").get },
                cxxCompileFlags             += "-D__OPENCV_BUILD=1"
            ) ++ settings,
            ignoreTestDir=true )
    }
    
    def OpenCVTest( parentProject : NativeProject, subDir : String, testDependencies : Seq[NativeProject] ) =
    {
        var np = NativeTest( parentProject.p.id + "_" + subDir, parentProject.p.base / subDir,
            Seq(
                includeDirectories          <++= (projectDirectory) map { pd => Seq(pd) },
                cxxSourceFiles              <<= (projectDirectory) map { pd => (pd * "*.cpp").get },
                cxxCompileFlags             += "-D__OPENCV_BUILD=1",
                nativeLibraries             ++= Seq("pthread", "rt", "z", "png"),
                testEnvironmentVariables    ++= Seq(
                    "OPENCV_TEST_DATA_PATH" -> "/home/alex.wilson/Devel/AW/opencv_extra/testdata" )
            ) )
    
        np = testDependencies.foldLeft(np) { case (np, extra) => np.nativeDependsOn(extra) }
        np = np.nativeDependsOn( parentProject )
        np = parentProject.dependencies.foldLeft(np) { case (np, extra) => np.nativeDependsOn(extra) }
        np
    }
    
/*
3rdparty/libwebp
3rdparty/openexr
*/

    lazy val openCVConfig = NativeProject( "opencvconfig", file("."),
        Seq(
            exportedIncludeDirectories  <++= (streams, compiler, projectDirectory, projectBuildDirectory) map
            { (s, c, pd, pbd) =>
            
                val ch = new CMakePlatformConfig( s.log, c )
                {
                    import PlatformChecks._
                    
                    val HAVE_PNG_H          = headerExists("png.h", CCTest)
                        .registerDefine("HAVE_PNG_H")
                        
                    val HAVE_PNG            = HAVE_PNG_H
                        .registerDefine("HAVE_PNG")
                        
                    val HAVE_ALLOCA_H       = headerExists("alloca.h", CCTest)
                        .registerDefine("HAVE_ALLOCA_H")
                        
                    val HAVE_ALLOCA         = functionExists("alloc", CCTest)
                        .registerDefine("HAVE_ALLOCA")
                        
                    val HAVE_CAMV4L         = headerExists("linux/videodev.h", CCTest)
                        .registerDefine("HAVE_CAMV4L")
                            
                    val HAVE_CAMV4L2        = headerExists("linux/videodev2.h", CCTest)
                        .registerDefine("HAVE_CAMV4L2")
                    
                    val HAVE_DC1394_2       = moduleExists("libdc1394-2", CCTest)
                        .registerDefine("HAVE_DC1394_2")
                        
                    val HAVE_DC1394         = moduleExists("libdc1394", CCTest)
                        .registerDefine("HAVE_DC1394")
                    
                    val HAVE_UNISTD_H       = headerExists("unistd.h", CCTest)
                        .registerDefine("HAVE_UNISTD_H")
                    
                    val HAVE_GENTOO_FFMPEG  = headerExists("libavformat/avformat.h", CCTest)
                        .registerDefine("HAVE_GENTOO_FFMPEG")
                    
                    val HAVE_FFMPEG_CODEC   = moduleExists("libavcodec", CCTest)
                        .registerDefine("HAVE_FFMPEG_CODEC")
                    
                    val HAVE_FFMPEG_FORMAT  = moduleExists("libavformat", CCTest)
                        .registerDefine("HAVE_FFMPEG_FORMAT")
                    
                    val HAVE_FFMPEG_UTIL    = moduleExists("libavutil", CCTest)
                        .registerDefine("HAVE_FFMPEG_UTIL")
                    
                    val HAVE_FFMPEG_SWSCALE = moduleExists("libswscale", CCTest)
                        .registerDefine("HAVE_FFMPEG_SWSCALE")
                    
                    val HAVE_FFMPEG_FFMPEG  = headerExists("ffmpeg/avformat.h", CCTest)
                        .registerDefine("HAVE_FFMPEG_FFMPEG")
                        
                    val HAVE_LIBPTHREAD     = headerExists("pthread.h", CCTest)
                        .registerDefine("HAVE_LIBPTHREAD")
                }
                
                
                val autoHeaderRoot = pbd / "autoHeader"
                ch.transformFile( file("cmake/templates/cvconfig.h.cmake"), autoHeaderRoot / "cvconfig.h", autoHeaderRoot / "state.cache" )
                
                val versionStringInc = autoHeaderRoot / "version_string.inc"
                if ( !versionStringInc.exists ) IO.write( versionStringInc, "\"Some blah\"" )

                Seq(autoHeaderRoot)
            }
        ) )
    
    lazy val libtiff = StaticLibrary( "libtiff", file( "3rdparty/libtiff" ),
        Seq(
            includeDirectories  <++= (streams, compiler, projectDirectory, projectBuildDirectory) map
            { (s, c, pd, pbd) =>
            
                val ch = new CMakePlatformConfig( s.log, c )
                {
                    import PlatformChecks._
                    
                    val HAVE_ASSERT_H       = headerExists("assert.h", CCTest)
                        .registerDefine("HAVE_ASSERT_H")
                        
                    val HAVE_FCNTL_H        = headerExists("fcntl.h", CCTest)
                        .registerDefine("HAVE_FCNTL_H")
                        
                    val HAVE_IO_H           = headerExists("io.h", CCTest)
                        .registerDefine("HAVE_IO_H")
                        
                    val HAVE_SEARCH_H       = headerExists("search.h", CCTest)
                        .registerDefine("HAVE_SEARCH_H")
                        
                    val HAVE_STRING_H       = headerExists("string.h", CCTest)
                        .registerDefine("HAVE_STRING_H")
                        
                    val HAVE_SYS_TYPES_H    = headerExists("sys/types.h", CCTest)
                        .registerDefine("HAVE_SYS_TYPES_H")
                        
                    val HAVE_UNISTD_H       = headerExists("unistd.h", CCTest)
                        .registerDefine("HAVE_UNISTD_H")
                        
                    val HAVE_JBG_NEWLEN     = headerExists("jbg_newlen", CCTest)
                        .registerDefine("HAVE_JBG_NEWLEN")
                        
                    val HAVE_MMAP           = headerExists("mmap", CCTest)
                        .registerDefine("HAVE_MMAP")
                }
                
                val autoHeaderRoot = pbd / "autoHeader"
                
                ch.transformFile( pd / "tif_config.h.cmakein", autoHeaderRoot / "tif_config.h", autoHeaderRoot / "state.cache" )

                Seq(pd, autoHeaderRoot)
            },
            ccSourceFiles       <<= (projectDirectory, buildEnvironment) map
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
            ccSourceFiles       <<= (projectDirectory) map { pd => (pd * "*.c").get }
        ) )
        
    lazy val libpng = StaticLibrary( "libpng", file( "3rdparty/libpng" ),
        Seq(
            includeDirectories  <++= (projectDirectory) map { pd => Seq(pd) },
            ccSourceFiles       <<= (projectDirectory) map { pd => (pd * "*.c").get }
        ) )
        
    lazy val libjasper = StaticLibrary( "libjasper", file( "3rdparty/libjasper" ),
        Seq(
            includeDirectories  <++= (projectDirectory) map { pd => Seq(pd, pd / "jasper") },
            ccSourceFiles       <<= (projectDirectory) map { pd => (pd * "*.c").get }
        ) )
        
    lazy val modulesCore = OpenCVModuleStaticLibrary( "modulesCore", file( "modules/core" ), Seq() )
        .nativeDependsOn( openCVConfig )

    lazy val modulesImgproc = StaticLibrary( "modulesImgproc", file( "modules/imgproc" ),
        Seq(
            includeDirectories          <++= (projectDirectory) map { pd => Seq(pd / "src") },
            exportedIncludeDirectories  <<= (projectDirectory) map { pd => Seq(pd / "include") },
            cxxSourceFiles              <<= (projectDirectory) map { pd => ((pd / "src") * "*.cpp").get },
            cxxCompileFlags             += "-D__OPENCV_BUILD=1"
        ), ignoreTestDir=true )
        .nativeDependsOn( openCVConfig, modulesCore )
        
    lazy val modulesFlann = StaticLibrary( "modulesFlann", file( "modules/flann" ),
        Seq(
            includeDirectories          <++= (projectDirectory) map { pd => Seq(pd / "src") },
            exportedIncludeDirectories  <<= (projectDirectory) map { pd => Seq(pd / "include") },
            cxxSourceFiles              <<= (projectDirectory) map { pd => ((pd / "src") * "*.cpp").get },
            cxxCompileFlags             += "-D__OPENCV_BUILD=1"
        ), ignoreTestDir=true )
        .nativeDependsOn( openCVConfig, modulesCore )
        
    lazy val modulesFeatures2d = StaticLibrary( "modulesFeatures2d", file( "modules/features2d" ),
        Seq(
            includeDirectories          <++= (projectDirectory) map { pd => Seq(pd / "src") },
            exportedIncludeDirectories  <<= (projectDirectory) map { pd => Seq(pd / "include") },
            cxxSourceFiles              <<= (projectDirectory) map { pd => ((pd / "src") * "*.cpp").get },
            cxxCompileFlags             += "-D__OPENCV_BUILD=1"
        ), ignoreTestDir=true )
        .nativeDependsOn( openCVConfig, modulesFlann, modulesImgproc, modulesCore )
    
    lazy val modulesCalib3d = StaticLibrary( "modulesCalib3d", file( "modules/calib3d" ),
        Seq(
            includeDirectories          <++= (projectDirectory) map { pd => Seq(pd / "src") },
            exportedIncludeDirectories  <<= (projectDirectory) map { pd => Seq(pd / "include") },
            cxxSourceFiles              <<= (projectDirectory) map { pd => ((pd / "src") * "*.cpp").get },
            cxxCompileFlags             += "-D__OPENCV_BUILD=1"
        ), ignoreTestDir=true )
        .nativeDependsOn( openCVConfig, modulesFlann, modulesFeatures2d, modulesImgproc, modulesCore )
        
    lazy val modulesMl = StaticLibrary( "modulesMl", file( "modules/ml" ),
        Seq(
            includeDirectories          <++= (projectDirectory) map { pd => Seq(pd / "src") },
            exportedIncludeDirectories  <<= (projectDirectory) map { pd => Seq(pd / "include") },
            cxxSourceFiles              <<= (projectDirectory) map { pd => ((pd / "src") * "*.cpp").get },
            cxxCompileFlags             += "-D__OPENCV_BUILD=1"
        ), ignoreTestDir=true )
        .nativeDependsOn( openCVConfig, modulesCore )
        
    lazy val modulesVideo = StaticLibrary( "modulesVideo", file( "modules/video" ),
        Seq(
            includeDirectories          <++= (projectDirectory) map { pd => Seq(pd / "src") },
            exportedIncludeDirectories  <<= (projectDirectory) map { pd => Seq(pd / "include") },
            cxxSourceFiles              <<= (projectDirectory) map { pd => ((pd / "src") * "*.cpp").get },
            cxxCompileFlags             += "-D__OPENCV_BUILD=1"
        ), ignoreTestDir=true )
        .nativeDependsOn( openCVConfig, modulesImgproc, modulesCore )
    
    
    lazy val modulesHighgui = StaticLibrary( "modulesHighgui", file( "modules/highgui" ),
        Seq(
            includeDirectories          <++= (projectDirectory) map { pd => Seq(pd / "src") },
            exportedIncludeDirectories  <<= (projectDirectory) map { pd => Seq(pd / "include") },
            cxxSourceFiles              <<= (projectDirectory) map
            { pd =>
                val rawFiles = ((pd / "src") * "*.cpp").get
                
                Seq( "src/bitstrm.cpp", "src/cap.cpp", "src/cap_images.cpp", "src/cap_ffmpeg.cpp", "src/loadsave.cpp", "src/precomp.cpp", "src/utils.cpp", "src/window.cpp" )
                    .map( f => pd / f ) ++ ((pd / "src") * "grfmt*.cpp").get
            },
            cxxCompileFlags             += "-D__OPENCV_BUILD=1"
        ), ignoreTestDir=true )
        .nativeDependsOn( openCVConfig, modulesCore, modulesImgproc )
    
       
    lazy val modulesTs = StaticLibrary( "modulesTs", file( "modules/ts" ), 
        Seq(
            includeDirectories          <++= (projectDirectory) map { pd => Seq(pd / "src") },
            exportedIncludeDirectories  <<= (projectDirectory) map { pd => Seq(pd / "include") },
            cxxSourceFiles              <<= (projectDirectory) map { pd => ((pd / "src") * "*.cpp").get },
            cxxCompileFlags             += "-D__OPENCV_BUILD=1"
        ), ignoreTestDir=true )
        .nativeDependsOn( openCVConfig, modulesCore, modulesImgproc, modulesHighgui )
    
    lazy val modulesCoreTest            = OpenCVTest( modulesCore, "test", Seq(modulesTs) )
    lazy val modulesCorePerfTest        = OpenCVTest( modulesCore, "perf", Seq(modulesTs) )
    lazy val modulesImgprocTest         = OpenCVTest( modulesImgproc, "test", Seq(modulesTs, modulesHighgui) )
    lazy val modulesImgprocPerfTest     = OpenCVTest( modulesImgproc, "perf", Seq(modulesTs, modulesHighgui) )
    lazy val modulesFlannTest           = OpenCVTest( modulesFlann, "test", Seq(modulesTs) )
    //lazy val modulesFlannPerfTest       = OpenCVTest( modulesFlann, "perf", Seq(modulesTs) )
    lazy val modulesFeatures2dTest      = OpenCVTest( modulesFeatures2d, "test", Seq(modulesTs, modulesHighgui) )
    lazy val modulesFeatures2dPerfTest  = OpenCVTest( modulesFeatures2d, "perf", Seq(modulesTs, modulesHighgui) )
    lazy val modulesCalib3dTest         = OpenCVTest( modulesCalib3d, "test", Seq(modulesTs, modulesHighgui) )
    lazy val modulesCalib3dPerfTest     = OpenCVTest( modulesCalib3d, "perf", Seq(modulesTs, modulesHighgui) )
    lazy val modulesMlTest              = OpenCVTest( modulesMl, "test", Seq(modulesTs) )
    //lazy val modulesMlPerfTest          = OpenCVTest( modulesMl, "perf", Seq(modulesTs) )
    lazy val modulesVideoTest           = OpenCVTest( modulesVideo, "test", Seq(modulesTs, modulesHighgui) )
    lazy val modulesVideoPerfTest       = OpenCVTest( modulesVideo, "perf", Seq(modulesTs, modulesHighgui) )
    //lazy val modulesHighgui
    
    /* lazy val modulesLegacy = StaticLibrary( "modulesLegacy", file( "modules/legacy" ),
        Seq(
            includeDirectories          <++= (projectDirectory) map { pd => Seq(pd / "src") },
            exportedIncludeDirectories  <<= (projectDirectory) map { pd => Seq(pd / "include") },
            sourceFiles                 <<= (projectDirectory) map { pd => ((pd / "src") * "*.cpp").get },
            compileFlags                += "-D__OPENCV_BUILD=1"
        ), ignoreTestDir=true )
        .nativeDependsOn( openCVConfig, modulesCore, modulesFlann, modulesImgproc, modulesFeatures2d, modulesCalib3d, modulesMl, modulesVideo )

    
        
    */
        
    /*lazy val modulesCoreTest = NativeTest( "modulesCoreTest", file( "modules/core/test"),
        Seq(
            includeDirectories  <++= (projectDirectory) map { pd => Seq(pd) },
            sourceFiles         <<= (projectDirectory) map { pd => (pd * "*.cpp").get },
            compileFlags        += "-D__OPENCV_BUILD=1",
            nativeLibraries     ++= Seq("pthread", "rt", "z")
        ) )
        .nativeDependsOn(openCVConfig, modulesCore, modulesTs)
        
    lazy val modulesCorePerfTest = NativeTest( "modulesCorePerfTest", file( "modules/core/perf"),
        Seq(
            includeDirectories  <++= (projectDirectory) map { pd => Seq(pd) },
            cxxSourceFiles      <<= (projectDirectory) map { pd => (pd * "*.cpp").get },
            cxxCompileFlags     += "-D__OPENCV_BUILD=1",
            nativeLibraries     ++= Seq("pthread", "rt", "z")
        ) )
        .nativeDependsOn(openCVConfig, modulesTs, modulesCore)*/
        
}
