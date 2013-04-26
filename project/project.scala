import sbt._
import Keys._

import org.seacourt.build._
import org.seacourt.build.NativeBuild._
import org.seacourt.build.NativeDefaultBuild._



class CMakeConfigHolder( val log : Logger, val compiler : Compiler )
{
    import PlatformChecks._
    
    private var macroMap = Map[String, Boolean]()
    
    
    def check_include_file( headerName : String, macroName : String )
    {
        macroMap += (macroName -> testForHeader( log, compiler, CCTest, headerName ))
    }
    
    def check_function_exists( functionName : String, macroName : String )
    {
        macroMap += (macroName -> testForSymbolDeclaration( log, compiler, CCTest, functionName, Seq() ))
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
                nativeLibraries             ++= Seq("pthread", "rt", "z"),
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
                
                val versionStringInc = autoHeaderRoot / "version_string.inc"
                if ( !versionStringInc.exists ) IO.write( versionStringInc, "\"Some blah\"" )

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
