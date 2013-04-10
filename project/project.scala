import sbt._
import Keys._

import org.seacourt.build._
import org.seacourt.build.NativeBuild._
import org.seacourt.build.NativeDefaultBuild._

/*
3rdparty/libtiff
3rdparty/libjpeg
3rdparty/libwebp
3rdparty/libjasper
3rdparty/libpng
3rdparty/openexr
*/

object TestBuild extends NativeDefaultBuild
{
    lazy val libtiff = StaticLibrary( "libtiff", file( "3rdparty/libtiff" ),
        Seq(
            includeDirectories  <<= (projectDirectory) map { pd => Seq(pd) },
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
        
        
    /*lazy val modulesCore = StaticLibrary( "modules_core", file( "./modules/core" ),
        Seq(
            sourceDirectories  <<= (projectDirectory) map { pd => Seq(pd / "src") }
        ) )*/
}
