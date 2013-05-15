import sbt._
import Keys._

import org.seacourt.build._
import org.seacourt.build.NativeBuild._
import org.seacourt.build.NativeDefaultBuild._

class CMakePlatformConfig( val log : Logger, val compiler : Compiler )
{
    import PlatformChecks._
    
    private var macroMap = Map[String, Boolean]()
    private var substitutionMap = Map[String, String]()
    
    private val outer = this
    class MacroRegistrationHelper( val state : Boolean )
    {
        def registerDefine( name : String ) = 
        {
            outer.registerDefine(name, state)
            state
        }
    }
    
    def registerDefine( name : String, state : Boolean ) =
    {
        macroMap += (name -> state)
    }
    
    
    def registerSubstitution( key : String, value : String ) =
    {
        substitutionMap += (key -> value)
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
            
                var tl = if ( l.startsWith(pattern) )
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

                for ( (k, v) <- substitutionMap )
                {
                    tl = tl.replace( "@%s@".format(k), v )
                }
                
                tl
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
    override def checkConfiguration( log : Logger, config : BuildConfiguration ) =
    {
        import PlatformChecks._
        
        super.checkConfiguration( log, config )
        
        // Check for a few headers to ensure various prerequisites are present
        PlatformChecks.requireHeader( log, config.compiler, CXXTest, "boost/scoped_ptr.hpp" )
        PlatformChecks.requireHeader( log, config.compiler, CXXTest, "gtest/gtest.h" )
        
        // Need find_package from CMake
    }
    
    lazy val defaultSettings = Seq(
        includeDirectories  += file("/usr/include/python2.7"),
        includeDirectories  <+= (baseDirectory) map { _ / "include" },
        nativeLibraries     ++= Seq("boost_date_time", "boost_python-py27", "boost_regex", "boost_serialization", "boost_system", "boost_thread", "python2.7", "pthread"),
        cxxCompileFlags     += "-Dcells_ectomodule_EXPORTS"
    )
    
    lazy val ectoConfig = NativeProject( "ectoConfig", file("."),
        Seq(
            exportedIncludeDirectories  <++= (streams, compiler, projectDirectory, projectBuildDirectory) map
            { (s, c, pd, pbd) =>
            
                val ch = new CMakePlatformConfig( s.log, c )
                {
                    import PlatformChecks._

                    def boostFeatureCheck( featureName : String ) =
                    {
                        val codeLines = ("#define " + featureName) +: IO.readLines( pd / "cmake" / "boost_checks.cpp" )
                        val success = tryCompile( s.log, c, CXXTest, codeLines.mkString("\n") )
                        log.info( featureName + ": " + success.toString )
                        success.registerDefine( featureName )
                    }
                    
                    boostFeatureCheck("ECTO_EXCEPTION_SHARED_POINTERS_ARE_CONST")
                    boostFeatureCheck("ECTO_EXCEPTION_DIAGNOSTIC_IMPL_TAKES_CHARSTAR")
                    boostFeatureCheck("ECTO_EXCEPTION_RELEASE_RETURNS_VOID")
                    boostFeatureCheck("ECTO_EXCEPTION_TAG_TYPE_NAME_RETURNS_STRING")
                    boostFeatureCheck("ECTO_EXCEPTION_TYPE_INFO_NESTED")
                    boostFeatureCheck("ECTO_EXCEPTION_CONTAINER_WITHOUT_CLONE")
                    
                    registerDefine("ECTO_EXCEPTION_HAS_CLONE", true)
                    
                    registerSubstitution("ECTO_MAJOR_VERSION", "0")
                    registerSubstitution("ECTO_MINOR_VERSION", "4")
                    registerSubstitution("ECTO_PATCH_VERSION", "6")
                    
                    //set(ECTO_SOVERSION ${ECTO_MAJOR_VERSION}.${ECTO_MINOR_VERSION})
                    //set(ECTO_VERSION ${ECTO_MAJOR_VERSION}.${ECTO_MINOR_VERSION}.${ECTO_PATCH_VERSION})
                    //set(ECTO_CODE_NAME "amoeba")
                }
                
                val autoHeaderRoot = pbd / "autoHeader"
                val buildStateCache = autoHeaderRoot / "state.cache"
                
                ch.transformFile( file("cmake/config.hpp.in"), autoHeaderRoot / "ecto" / "config.hpp", buildStateCache )
                ch.transformFile( file("cmake/version.hpp.in"), autoHeaderRoot / "ecto" / "version.hpp", buildStateCache )
                ch.transformFile( file("cmake/boost-config.hpp.in"), autoHeaderRoot / "ecto" / "boost-config.hpp", buildStateCache )
                
                Seq( autoHeaderRoot )
            }
        )
    )
    
    lazy val ectoLib = SharedLibrary( "ectoLib", file( "src/lib" ), defaultSettings ++ Seq
    (
        includeDirectories  <++= (projectDirectory) map { pd => Seq(pd) },
        cxxCompileFlags     <+= (projectDirectory) map { pd => "-DSOURCE_DIR=\"" + pd + "\"" },
        cxxSourceFiles      <<= (projectDirectory) map { pd => (pd ** "*.cpp").get },
        linkFlags           += "-export-dynamic"
    ) ).nativeDependsOn( ectoConfig )
    
    lazy val ectoPyBindings = SharedLibrary( "ecto_main_ectomodule", file("src/pybindings"), defaultSettings ++ Seq
    (
        cxxSourceFiles      <<= (projectDirectory) map { pd => (pd ** "*.cpp").get },
        linkFlags           += "-export-dynamic"
    ) ).nativeDependsOn( ectoConfig, ectoLib )
    
    lazy val cellsTest = SharedLibrary( "ecto_test_ectomodule", file("test/cells"), defaultSettings ++ Seq
    (
        cxxCompileFlags     += "-Decto_test_ectomodule_EXPORTS",
        cxxSourceFiles      <<= (projectDirectory) map { pd => (pd ** "*.cpp").get },
        linkFlags           += "-export-dynamic"
    ) ).nativeDependsOn( ectoConfig, ectoLib, ectoPyBindings )
    
    lazy val cppTest = NativeTest( "ecto-test", file("test/cpp"), defaultSettings ++ Seq
    (
        cxxSourceFiles              <<= (projectDirectory) map
        { pd =>
        
            Seq(    "main.cpp", "tendril.cpp", "tendrils.cpp", "spore.cpp", "exceptions.cpp",
                    "graph.cpp", "profile.cpp", "serialization.cpp", "strands.cpp", "scheduler.cpp",
                    "clone.cpp", "static.cpp" ).map( f => pd / f )
        },
        nativeLibraries             += "gtest",
        testEnvironmentVariables    <+= (exportedLibDirectories in ectoLib, exportedLibDirectories in ectoPyBindings, exportedLibDirectories in cellsTest, state) map
        { (l1, l2, l3, s) =>
        
            s.log.info( "Python path: " + ( l1 ++ l2 ++ l3 ).mkString(":") )
            "PYTHONPATH" -> ( l1 ++ l2 ).mkString(":")
        }
    ) ).nativeDependsOn( ectoConfig, ectoLib, cellsTest )
    
}


