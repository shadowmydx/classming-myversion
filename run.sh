java -Xms128m -Xmx20480m -XX:OnOutOfMemoryError="kill -9 %p && bash run.sh"  -Dfile.encoding=UTF-8 -classpath /home/mingyuan/evo-generation-ant/out/production/classming-myversion/:/home/mingyuan/sootclasses-trunk-jar-with-dependencies.jar:/usr/lib/jvm/java-8-oracle/jre/lib/rt.jar com.classming.coevolution.EvolutionFrameworkResumable

