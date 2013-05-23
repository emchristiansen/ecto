import sbt._
import Keys._

import org.seacourt.build._
import org.seacourt.build.NativeBuild._
import org.seacourt.build.NativeDefaultBuild._

import sbinary._
import DefaultProtocol._


object TestBuild extends NativeDefaultBuild
{
    import NativeProject._ 
    
    override def checkConfiguration( log : Logger, config : BuildConfiguration ) =
    {
        import PlatformChecks._
        
        super.checkConfiguration( log, config )
        
        // Check for a few headers to ensure various prerequisites are present
        requireHeader( log, config.compiler, CXXTest, "boost/scoped_ptr.hpp" )
        requireHeader( log, config.compiler, CXXTest, "gtest/gtest.h" )
        
    }
    
    
    lazy val defaultSettings = inConfig(Compile)( Seq(
        includeDirectories  += file("/usr/include/python2.7"),
        includeDirectories  <+= (baseDirectory) map { _ / "include" },
        nativeLibraries     ++= Seq("boost_date_time", "boost_python-py27", "boost_regex", "boost_serialization", "boost_system", "boost_thread", "python2.7", "pthread"),
        cxxCompileFlags     += "-Dcells_ectomodule_EXPORTS"
    ) )
    
    
    class EctoConfig( override val log : Logger, override val cacheDirectory : File, override val compiler : Compiler, val projectDir : File ) extends CMakePlatformConfig
    {
        import PlatformChecks._
        
        private def boostFeatureCheck( featureName : String ) : Boolean =
        {
            val success = cached(featureName)
            {
                val codeLines = ("#define " + featureName) +: IO.readLines( projectDir / "cmake" / "boost_checks.cpp" )
                tryCompile( log, compiler, CXXTest, codeLines.mkString("\n") )
            }
            log.info( featureName + ": " + success.toString )
            success.registerDefine( featureName )
            
            success
        }
                    
        val hasBoost = cached("hasBoost")( testForHeader( "boost/scoped_ptr.hpp", CXXTest ) )

        boostFeatureCheck("ECTO_EXCEPTION_SHARED_POINTERS_ARE_CONST")
        boostFeatureCheck("ECTO_EXCEPTION_DIAGNOSTIC_IMPL_TAKES_CHARSTAR")
        boostFeatureCheck("ECTO_EXCEPTION_RELEASE_RETURNS_VOID")
        boostFeatureCheck("ECTO_EXCEPTION_TAG_TYPE_NAME_RETURNS_STRING")
        boostFeatureCheck("ECTO_EXCEPTION_TYPE_INFO_NESTED")
        boostFeatureCheck("ECTO_EXCEPTION_CONTAINER_WITHOUT_CLONE")
        
        registerDefine("ECTO_EXCEPTION_HAS_CLONE", true)
        
        registerDefine("ECTO_STRESS_TEST", false)
        registerDefine("ECTO_LOGGING", false)
        registerDefine("ECTO_TRACE_EXCEPTIONS", false)
        registerDefine("ECTO_WITH_INSTRUMENTATION", false)
        
        registerSubstitution("ECTO_MAJOR_VERSION", "0")
        registerSubstitution("ECTO_MINOR_VERSION", "4")
        registerSubstitution("ECTO_PATCH_VERSION", "6")
        registerSubstitution("ECTO_CODE_NAME", "amoeba")                   
    }
    
    lazy val ectoConfigKey = TaskKey[EctoConfig]("ecto-config")
    
    lazy val ectoConfig = NativeProject( "ectoConfig", file("."),
        baseSettings ++ Seq(
            ectoConfigKey               <<=  (compiler in Compile, stateCacheDirectory in Compile, projectDirectory in Compile, streams) map
            { (c, cacheDirectory, pd, s) => new EctoConfig( s.log, cacheDirectory, c, pd ) },
            
            exportedIncludeDirectories  <++= (ectoConfigKey, compiler in Compile, projectDirectory in Compile, projectBuildDirectory in Compile, stateCacheDirectory in Compile, streams) map
            { (ch, c, pd, pbd, cacheDirectory, s) =>
            
                val autoHeaderRoot = pbd / "autoHeader"
                val buildStateCache = autoHeaderRoot / "state.cache"
                
                assert( ch.hasBoost )
                
                ch.transformFile( file("cmake/config.hpp.in"), autoHeaderRoot / "ecto" / "config.hpp" )
                ch.transformFile( file("cmake/version.hpp.in"), autoHeaderRoot / "ecto" / "version.hpp" )
                ch.transformFile( file("cmake/boost-config.hpp.in"), autoHeaderRoot / "ecto" / "boost-config.hpp" )
                
                Seq( autoHeaderRoot )
            }
        )
    )
    
    lazy val ectoLib = NativeProject( "ectoLib", file( "src/lib" ), sharedLibrarySettings ++ defaultSettings ++ inConfig(Compile)( Seq
    (
        includeDirectories  <++= (projectDirectory) map { pd => Seq(pd) },
        cxxCompileFlags     <+= (projectDirectory) map { pd => "-DSOURCE_DIR=\"" + pd + "\"" },
        cxxSourceFiles      <<= (projectDirectory) map { pd => (pd ** "*.cpp").get },
        linkFlags           += "-export-dynamic"
    ) ) ).nativeDependsOn( ectoConfig )
    
    lazy val ectoPyBindings = NativeProject( "ecto_main_ectomodule", file("src/pybindings"), sharedLibrarySettings ++ defaultSettings ++ inConfig(Compile)( Seq
    (
        cxxSourceFiles      <<= (projectDirectory) map { pd => (pd ** "*.cpp").get },
        linkFlags           += "-export-dynamic"
    ) ) ).nativeDependsOn( ectoConfig, ectoLib )
    
    lazy val cellsTest = NativeProject( "ecto_test_ectomodule", file("test/cells"), sharedLibrarySettings ++ defaultSettings ++ inConfig(Compile)( Seq
    (
        cxxCompileFlags     += "-Decto_test_ectomodule_EXPORTS",
        cxxSourceFiles      <<= (projectDirectory) map { pd => (pd ** "*.cpp").get },
        linkFlags           += "-export-dynamic"
    ) ) ).nativeDependsOn( ectoConfig, ectoLib, ectoPyBindings )
    
    lazy val cppTest = NativeProject( "ecto-test", file("test/cpp"), baseSettings ++ defaultSettings ++ inConfig(Test)( Seq
    (
        cxxSourceFiles              <<= (projectDirectory) map
        { pd =>
        
            Seq(    "main.cpp", "tendril.cpp", "tendrils.cpp", "spore.cpp", "exceptions.cpp",
                    "graph.cpp", "profile.cpp", "serialization.cpp", "strands.cpp", "scheduler.cpp",
                    "clone.cpp", "static.cpp" ).map( f => pd / f )
        },
        nativeLibraries             += "gtest",
        environmentVariables        <+= (exportedLibDirectories in ectoLib, exportedLibDirectories in ectoPyBindings, exportedLibDirectories in cellsTest, state) map
        { (l1, l2, l3, s) =>
        
            s.log.info( "Python path: " + ( l1 ++ l2 ++ l3 ).mkString(":") )
            "PYTHONPATH" -> ( l1 ++ l2 ).mkString(":")
        }
    ) ) ).nativeDependsOn( ectoConfig, ectoLib, cellsTest )
    
}


