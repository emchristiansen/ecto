import sbt._

import org.seacourt.build._

import sbinary._
import DefaultProtocol._

abstract class CMakePlatformConfig
{
    import PlatformChecks._
    
    val log : Logger
    val cacheDirectory : File
    val compiler : Compiler
    
    protected val configCacheDir = cacheDirectory / "config_cache"
    configCacheDir.mkdirs()
    
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
    
    def writeIfChanged( fileName : File, data : String, log : Logger )
    {
        if ( !fileName.exists || IO.read(fileName) != data )
        {
            log.info( "Updating: " + fileName )
            IO.write( fileName, data )
        }
        else
        {
            log.info( "Skipping update, no changes: " + fileName )
        }
    }
    
    def transformFile( inputFile : File, outputFile : File )
    {
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
    
        writeIfChanged( outputFile, transformedData.mkString("\n"), log )
    }
    
    def testForHeader( fileName : String, compileType : PlatformChecks.CompileType ) = PlatformChecks.testForHeader( log, compiler, compileType, fileName )
    def testForSymbol( functionName : String, compileType : PlatformChecks.CompileType ) = PlatformChecks.testForSymbolDeclaration( log, compiler, compileType, functionName, Seq() )
    def testForModule( moduleName : String, compileType : PlatformChecks.CompileType ) = true
    
    def cached[T](keyName : String)( fn : => T )(implicit reader : Reads[T], writer : Writes[T]) =
    {
        import sbinary.Operations._
        
        val cacheFile = (configCacheDir / keyName)
        if ( cacheFile.exists )
        {
            fromFile[T](cacheFile)
        }
        else
        {
            val res = fn
            toFile(res)(cacheFile)
            res
        }
    }
}

