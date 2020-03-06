# classming-myversion

### 注意事项

1. 若要运行com.classming.rf.cluster.ClassesCluster, 需要在根目录(classming-myversion)下创建两个文件夹。
   其中一个testResources, 下面存放了所有的待测的class文件
   另外一个是environment。将sootOutput里面的每个project里的所有文件(夹)抽取出来放在environment文件夹下即可。因为soot在resolve某个类的时候需要以特定的项目的文件夹为根目录。然而测试文件是不确定的，且根据测试文件自动探测出所在的根目录是较为困难的。因此需要将所有的文件和文件夹都放在environment下，这样可以保证以该目录为起点可以找到任何所需要的路径。