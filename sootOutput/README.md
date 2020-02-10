# Benchmark Usage


Benchmark | Local CMD (under classpath) 
:-: | :-: 
avrora | java avrora.Main -action=cfg example.asm 
eclipse | java org.eclipse.core.runtime.adaptor.EclipseStarter<br/>java org.eclipse.core.runtime.adaptor.EclipseStarter -debug 
fop | java -Xbootclasspath/a:../../dependencies/xmlgraphics-commons-1.3.1.jar;../../dependencies/commons-logging.jar;../../dependencies/avalon-framework-4.2.0.jar;../../dependencies/batik-all.jar;../../dependencies/commons-io-1.3.1.jar org.apache.fop.cli.Main -xml name.xml -xsl name2fo.xsl -pdf name.pdf 
jython | java -Xbootclasspath/a:../../dependencies/guava-r07.jar;../../dependencies/constantine.jar;../../dependencies/jnr-posix.jar;../../dependencies/jaffl.jar;../../dependencies/jline-0.9.95-SNAPSHOT.jar;../../dependencies/antlr-3.1.3.jar;../../dependencies/asm-3.1.jar org.python.util.jython hello.py 
pmd | java -Xbootclasspath/a:../../dependencies/jaxen-1.1.1.jar;../../dependencies/asm-3.1.jar net.sourceforge.pmd.PMD Hello.java text unusedcode 
sunflow | java -Xbootclasspath/a:../../dependencies/janino-2.5.15.jar org.sunflow.Benchmark -bench 2 256 



