package ucar.nc2.grib;

import org.jdom2.input.SAXBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import thredds.catalog.parser.jdom.FeatureCollectionReader;
import thredds.featurecollection.FeatureCollectionConfig;
import thredds.inventory.CollectionManager;
import thredds.inventory.CollectionManagerRO;
import thredds.inventory.MFile;
import thredds.inventory.partition.DirectoryCollection;
import thredds.inventory.partition.DirectoryPartition;
import thredds.inventory.partition.DirectoryPartitionCollection;
import thredds.inventory.partition.IndexReader;
import ucar.nc2.constants.CDM;
import ucar.nc2.grib.grib1.Grib1CollectionBuilder;
import ucar.nc2.grib.grib1.Grib1TimePartitionBuilder;
import ucar.nc2.grib.grib2.Grib2TimePartition;
import ucar.nc2.grib.grib2.builder.*;
import ucar.nc2.stream.NcStream;
import ucar.unidata.io.RandomAccessFile;
import ucar.unidata.util.StringUtil2;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;

/**
 * Utilities for GribCollection ncx files.
 *
 * @author caron
 * @since 11/9/13
 */
public class GribCdmIndex implements IndexReader {
  static private final Logger logger = LoggerFactory.getLogger(GribCdmIndex.class);

  /**
   * Rewrite all the ncx indices in a directory, and the collection index for that directory
   * @param config FeatureCollectionConfig
   * @param dirPath directory path
   * @throws IOException
   */
  static public void rewriteIndexesFilesAndCollection(final FeatureCollectionConfig config, Path dirPath) throws IOException {
    long start = System.currentTimeMillis();
    String what;

    String collectionName = DirectoryCollection.makeCollectionName(config.name, dirPath);
    Path idxFile = DirectoryCollection.makeCollectionIndexPath(config.name, dirPath);
    if (Files.exists(idxFile)) {
      what = "IndexRead";
      // read collection index
      try (GribCollection gc = Grib2CollectionBuilderFromIndex.createFromIndex(collectionName, dirPath.toFile(), config.gribConfig, logger)) {
        for (MFile mfile : gc.getFiles()) {
          try( GribCollection gcNested =
                       Grib2CollectionBuilder.readOrCreateIndexFromSingleFile(mfile, CollectionManager.Force.always, config.gribConfig, logger)) {}
        }
      }

    } else {
      what = "DirectoryScan";

      /* try (DirectoryCollection controller = new DirectoryCollection(config.name, dirPath, logger)) {
        for (MFile mfile : controller) {
          try( GribCollection gcNested =
                       Grib2CollectionBuilder.readOrCreateIndexFromSingleFile(mfile, CollectionManager.Force.always, config.gribConfig, logger)) {}
        }
      } */

       // collection index doesnt exists, so we have to scan
      // this idiom keeps the iterator from escaping, so that we can use try-with-resource, and ensure it closes. like++
      // i wonder what this looks like in Java 8 closures ??
      DirectoryCollection collection = new DirectoryCollection(config.name, dirPath, logger);
      collection.iterateOverMFileCollection( new DirectoryCollection.Visitor() {
        public void consume(MFile mfile) {
          try( GribCollection gcNested =
                       Grib2CollectionBuilder.readOrCreateIndexFromSingleFile(mfile, CollectionManager.Force.always, config.gribConfig, logger)) {}
          catch (IOException e) {
            logger.error("rewriteIndexesFilesAndCollection", e);
          }
        }
      });
    }

    // redo collection index
    DirectoryCollection dpart = new DirectoryCollection( config.name, dirPath, logger);
    dpart.putAuxInfo(FeatureCollectionConfig.AUX_GRIB_CONFIG, config.gribConfig);
    try (GribCollection gcNew = Grib2CollectionBuilder.factory(dpart, CollectionManager.Force.always, logger)) {}

    long took = System.currentTimeMillis() - start;
    System.out.printf("%s %s took %s msecs%n", collectionName, what, took);
  }

  /**
   * Rewrite all the ncx indices for all the directories in a directory partition
   * @param config FeatureCollectionConfig
   * @param dirPath directory path
   * @throws IOException
   */
  static public void rewriteIndexesPartition(FeatureCollectionConfig config, Path dirPath) throws IOException {
    GribCdmIndex indexWriter = new GribCdmIndex();
    Formatter errlog = new Formatter();
    DirectoryPartitionCollection dpart = new DirectoryPartitionCollection( config, dirPath, indexWriter, errlog, logger);
    String errs = errlog.toString();
    if (errs.length() > 0)
      System.out.printf("%s%n", errs);

    dpart.putAuxInfo(FeatureCollectionConfig.AUX_GRIB_CONFIG, config.gribConfig);

    for (CollectionManagerRO part : dpart.makePartitions()) {
      Path partPath = Paths.get(part.getRoot());
      rewriteIndexesFilesAndCollection(config, partPath);
    }

    try (Grib2TimePartition tp =  Grib2TimePartitionBuilder.factory(dpart, CollectionManager.Force.always, logger)) {}
  }

  /* static public void rewriteIndexesPartitionRecursive(FeatureCollectionConfig config, DirectoryPartition dpart) throws IOException {
    GribCdmIndex indexWriter = new GribCdmIndex();
    Formatter errlog = new Formatter();
    //DirectoryPartitionCollection dpart = new DirectoryPartitionCollection( config, dirPath, indexWriter, errlog, logger);
    String errs = errlog.toString();
    if (errs.length() > 0)
      System.out.printf("rewriteIndexesPartitionRecursive2 ERRORS %s%n", errs);

    dpart.putAuxInfo(FeatureCollectionConfig.AUX_GRIB_CONFIG, config.gribConfig);

    for (DirectoryPartitionCollection part : dpart.makePartitionCollections()) {
      rewriteIndexesPartitionRecursive(config, part);
    }

    for (CollectionManagerRO part : dpart.makePartitions()) {
      Path partPath = Paths.get(part.getRoot());
      rewriteIndexesFilesAndCollection(config, partPath);
    }

    try (Grib2TimePartition tp =  Grib2TimePartitionBuilder.factory(dpart, CollectionManager.Force.always, logger)) {}
  }

  // pass in topDir, recurse through all nested directories, rewriting all ncx
  static public void rewriteIndexesPartitionRecursive(FeatureCollectionConfig config, Path dirPath) throws IOException {
    GribCdmIndex indexWriter = new GribCdmIndex();
    Formatter errlog = new Formatter();
    DirectoryPartitionCollection dpart = new DirectoryPartitionCollection( config, dirPath, indexWriter, errlog, logger);
    String errs = errlog.toString();
    if (errs.length() > 0)
      System.out.printf("rewriteIndexesPartitionRecursive ERRORS %s%n", errs);

    dpart.putAuxInfo(FeatureCollectionConfig.AUX_GRIB_CONFIG, config.gribConfig);

    for (CollectionManagerRO part : dpart.makePartitions()) {
      Path partPath = Paths.get(part.getRoot());
      rewriteIndexesPartitionRecursive(config, partPath);
    }

    try (Grib2TimePartition tp =  Grib2TimePartitionBuilder.factory(dpart, CollectionManager.Force.always, logger)) {}
  }  */


  /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


    // make Grib Collection Index for one Directory
  static public GribCollection makeGribCollectionIndexOneDirectory(FeatureCollectionConfig config, CollectionManager.Force force, Path topPath, Formatter out) throws IOException {
    DirectoryCollection dpart = new DirectoryCollection( config.name, topPath, logger);
    dpart.putAuxInfo(FeatureCollectionConfig.AUX_GRIB_CONFIG, config.gribConfig);

    return Grib2CollectionBuilder.factory(dpart, force, logger);
  }

    // make DirectoryPartition Index for one Time partition Directory and its children GribCollections
  static public Grib2TimePartition makeTimePartitionIndexOneDirectoryAll(FeatureCollectionConfig config, CollectionManager.Force force, Path topPath, Formatter out) throws IOException {
    GribCdmIndex indexWriter = new GribCdmIndex();
    DirectoryPartitionCollection dpart = new DirectoryPartitionCollection( config, topPath, indexWriter, out, logger);
    dpart.putAuxInfo(FeatureCollectionConfig.AUX_GRIB_CONFIG, config.gribConfig);

    for (CollectionManagerRO part : dpart.makePartitions()) {
      GribCollection gc = Grib2CollectionBuilder.factory(part, force, logger);
      gc.close();
    }

    return Grib2TimePartitionBuilder.factory(dpart, force, logger);
  }

    // make DirectoryPartition Index for one Time partition Directory and its children GribCollections
  static public Grib2TimePartition makeTimePartitionIndexOneDirectory(FeatureCollectionConfig config, Path topPath) throws IOException {
    GribCdmIndex indexWriter = new GribCdmIndex();

    Formatter errlog = new Formatter();
    DirectoryPartitionCollection dpart = new DirectoryPartitionCollection( config, topPath, indexWriter, errlog, logger);
    String errs = errlog.toString();
    if (errs.length() > 0)
      System.out.printf("%s%n", errs);

    dpart.putAuxInfo(FeatureCollectionConfig.AUX_GRIB_CONFIG, config.gribConfig);
    return Grib2TimePartitionBuilder.factory(dpart, CollectionManager.Force.always, logger);
  }

  ////////////////////////////////////////////////////////////

  // show DirectoryPartition Index for everything under the topDir
  static public boolean showDirectoryPartitionIndex(String collectionName, File topDir, Formatter out) throws IOException {
    DirectoryPartition builder = new DirectoryPartition(collectionName, topDir.getPath());
    builder.constructChildren(new GribCdmIndex());
    builder.show(out);
    return true;
  }


  // make DirectoryPartition Index for everything under the topDir
  static public boolean makeDirectoryPartitionIndex(FeatureCollectionConfig config, File topDir, Formatter out) throws IOException {
    GribCdmIndex indexWriter = new GribCdmIndex();
    Path topPath = Paths.get(topDir.getPath());
    DirectoryPartitionCollection dpart = new DirectoryPartitionCollection( config, topPath, indexWriter, out, logger);

    Grib2DirectoryPartitionBuilder builder = new Grib2DirectoryPartitionBuilder(dpart.getTopCollectionName(), topPath, dpart, logger);
    return builder.createPartitionedIndex(out);
  }

  // open GribCollection. caller must close
  static public GribCollection openCdmIndex(String indexFile, FeatureCollectionConfig.GribConfig config, Logger logger) throws IOException {
    GribCollection gc = null;
    String magic = null;
    RandomAccessFile raf = new RandomAccessFile(indexFile, "r");

    try {
      raf.seek(0);
      byte[] b = new byte[Grib2CollectionBuilder.MAGIC_START.getBytes().length];   // they are all the same
      raf.read(b);
      magic = new String(b);

      switch (magic) {
        case Grib2CollectionBuilder.MAGIC_START:
          gc = Grib2CollectionBuilderFromIndex.createFromIndex(indexFile, null, raf, config, logger);
          break;
        case Grib1CollectionBuilder.MAGIC_START:
          gc = Grib1CollectionBuilder.createFromIndex(indexFile, null, raf, config, logger);
          break;
        case Grib2TimePartitionBuilder.MAGIC_START:
          gc = Grib2TimePartitionBuilderFromIndex.createFromIndex(indexFile, null, raf, logger);
          break;
        case Grib1TimePartitionBuilder.MAGIC_START:
          gc = Grib1TimePartitionBuilder.createFromIndex(indexFile, null, raf, logger);
          break;
      }
    } catch (Throwable t) {
      raf.close();
      throw t;
    }

    return gc;
  }

  public enum GribCollectionType {GRIB1, GRIB2, Partition1, Partition2}

  // open GribCollection. caller must close
  static public GribCollectionType getType(RandomAccessFile raf) throws IOException {
    String magic;

      raf.seek(0);
      byte[] b = new byte[Grib2CollectionBuilder.MAGIC_START.getBytes().length];   // they are all the same
      raf.read(b);
      magic = new String(b);

      switch (magic) {
        case Grib2CollectionBuilder.MAGIC_START:
         return GribCollectionType.GRIB2;

        case Grib1CollectionBuilder.MAGIC_START:
          return GribCollectionType.GRIB1;

        case Grib2TimePartitionBuilder.MAGIC_START:
          return GribCollectionType.Partition2;

        case Grib1TimePartitionBuilder.MAGIC_START:
          return GribCollectionType.Partition1;

      }
    return null;
  }

  ///////////////////////////////////////////////////////////////////////////////////////////

    // move index to be a directory partition
  static public boolean moveCdmIndex(String indexFilename, Logger logger) throws IOException {
    RandomAccessFile raf = new RandomAccessFile(indexFilename, "r");
    RandomAccessFile newRaf = new RandomAccessFile(indexFilename+".copy", "rw");

    try {
      raf.seek(0);
      byte[] b = new byte[Grib2CollectionBuilder.MAGIC_START.getBytes().length]; // they are all the same
      raf.read(b);
      String magic = new String(b, CDM.utf8Charset);

      GribCdmIndex gci = new GribCdmIndex();

      switch (magic) {
        case Grib1CollectionBuilder.MAGIC_START:
        case Grib2CollectionBuilder.MAGIC_START:
          return gci.moveIndex(raf, newRaf, logger);

        /* case Grib1CollectionBuilder.MAGIC_START:
          gc = Grib1CollectionBuilder.createFromIndex(indexFile, null, raf, null, logger);
          break;
        case Grib2TimePartitionBuilder.MAGIC_START:
          gc = Grib2TimePartitionBuilder.createFromIndex(indexFile, null, raf, logger);
          break;
        case Grib1TimePartitionBuilder.MAGIC_START:
          gc = Grib1TimePartitionBuilder.createFromIndex(indexFile, null, raf, logger);
          break; */
      }
    } catch (Throwable t) {
      raf.close();
      throw t;

    } finally {
      if (raf != null) raf.close();
      if (newRaf != null) newRaf.close();
    }

    return false;
  }

  /////////////////////////////////////////////////////////////////////////////////////
  // manipulate the ncx without building a gc

  private byte[] magic;
  private int version;
  private GribCollectionProto.GribCollectionIndex gribCollectionIndex;


  @Override
  public boolean readChildren(Path indexFile, AddChildCallback callback) throws IOException {
    try (RandomAccessFile raf = new RandomAccessFile(indexFile.toString(), "r")) {  // note try-with-resource
      GribCollectionType type = getType(raf);
      if (type == GribCollectionType.Partition1 || type == GribCollectionType.Partition2) {
        if (openIndex(raf, logger)) {
          String dirName = gribCollectionIndex.getDirName();
          for (GribCollectionProto.Partition part : gribCollectionIndex.getPartitionsList()) {
            callback.addChild(dirName, part.getFilename(), part.getLastModified());
          }
          return true;
        }
      }
      return false;
    }
  }

  private String computeNewDir(String oldDir, List<ucar.nc2.grib.GribCollectionProto.MFile> oldFiles, List<String> newFiles) throws IOException {
    String oldDir2 = StringUtil2.replace(oldDir, '\\', "/");

    if (oldFiles.size() == 0) return null;

    ucar.nc2.grib.GribCollectionProto.MFile first = oldFiles.get(0);
    String firstS = first.getFilename();
    int pos = firstS.lastIndexOf("/");
    String oldParent = firstS.substring(0, pos+1);  // remove the filename
    int cut = oldParent.length();

    boolean ok = true;
    for (ucar.nc2.grib.GribCollectionProto.MFile f : oldFiles) {
      String fname = f.getFilename();
      if (!fname.startsWith(oldParent)) {
        System.out.printf("BAD %s not start with %s%n", fname, oldParent);
        ok = false;
      } else {
        newFiles.add(fname.substring(cut));
      }
    }

    if (!ok) return null;

    int pos0 = (oldDir2.endsWith("/") && firstS.startsWith("/")) ? 1 : 0;
    String oldReletivePath = firstS.substring(pos0,pos+1);  // remove first "/" if needed and the filename
    String newPath = oldDir2 + oldReletivePath;

    return newPath;
  }


  /*
  Grib 1 or 2 Collection

   MAGIC_START
   version
   sizeRecords
   VariableRecords (sizeRecords bytes)
   sizeIndex
   GribCollectionIndex (sizeIndex bytes)
   */

  protected boolean moveIndex(RandomAccessFile oldIndex, RandomAccessFile newIndex, Logger logger) {
    try {
      oldIndex.order(RandomAccessFile.BIG_ENDIAN);
      newIndex.order(RandomAccessFile.BIG_ENDIAN);
      oldIndex.seek(0);
      newIndex.seek(0);

      //// header message
      magic = new byte[Grib2CollectionBuilder.MAGIC_START.getBytes().length];   // they are all the same
      oldIndex.read(magic);
      newIndex.write(magic);

      version = oldIndex.readInt();
      newIndex.writeInt(version);

      long recordLength = oldIndex.readLong();
      if (recordLength > Integer.MAX_VALUE) {
        logger.error("Grib2Collection {}: invalid recordLength size {}", oldIndex.getLocation(), recordLength);
        return false;
      }
      newIndex.writeLong(recordLength);

      byte[] records = new byte[(int) recordLength];
      oldIndex.read(records);
      newIndex.write(records);

      int size = NcStream.readVInt(oldIndex);
      if ((size < 0) || (size > 100 * 1000 * 1000)) {
        logger.warn("Grib2Collection {}: invalid index size {}", oldIndex.getLocation(), size);
        return false;
      }

      byte[] m = new byte[size];
      oldIndex.readFully(m);
      gribCollectionIndex = GribCollectionProto.GribCollectionIndex.parseFrom(m);

      // LOOK version > 12
      // make mods to gribCollectionIndex
      GribCollectionProto.GribCollectionIndex.Builder builder = GribCollectionProto.GribCollectionIndex.newBuilder(gribCollectionIndex);
      System.out.printf("DirName= %s%n", builder.getDirName());

      List<String> newFiles = new ArrayList<String>( builder.getMfilesCount());
      String newDir = computeNewDir(builder.getDirName(), builder.getMfilesList(), newFiles);
      if (newDir == null) return false;
      builder.setDirName(newDir);

      builder.clearMfiles();
      for (int i=0; i<gribCollectionIndex.getMfilesCount(); i++ ) {
        ucar.nc2.grib.GribCollectionProto.MFile s = gribCollectionIndex.getMfiles(i);
        GribCollectionProto.MFile.Builder mb = GribCollectionProto.MFile.newBuilder(s);
        mb.setFilename(newFiles.get(i));
        builder.addMfiles(mb);
      }

      // barf it back out
      GribCollectionProto.GribCollectionIndex index = builder.build();
      byte[] b = index.toByteArray();
      NcStream.writeVInt(newIndex, b.length); // message size
      System.out.printf("GribCollectionIndex len=%d%n", b.length);
      newIndex.write(b);  // message  - all in one gulp  */
      newIndex.close();

      Path source = Paths.get(newIndex.getLocation());
      Path org = Paths.get(oldIndex.getLocation());
      Path dest = Paths.get(newDir, org.getFileName().toString());
      Files.move(source, dest);
      System.out.printf("moved %s to %s%n", org, dest);
      return true;

    } catch (Throwable t) {
      logger.error("Error reading index " + oldIndex.getLocation(), t);
      return false;
    }
  }

  private boolean openIndex(RandomAccessFile indexRaf, Logger logger) {
    try {
      indexRaf.order(RandomAccessFile.BIG_ENDIAN);
      indexRaf.seek(0);

      //// header message
      magic = new byte[Grib2CollectionBuilder.MAGIC_START.getBytes().length];   // they are all the same
      indexRaf.read(magic);

      version = indexRaf.readInt();

      long recordLength = indexRaf.readLong();
      if (recordLength > Integer.MAX_VALUE) {
        logger.error("Grib2Collection {}: invalid recordLength size {}", indexRaf.getLocation(), recordLength);
        return false;
      }
      indexRaf.skipBytes(recordLength);

      int size = NcStream.readVInt(indexRaf);
      if ((size < 0) || (size > 100 * 1000 * 1000)) {
        logger.warn("Grib2Collection {}: invalid index size {}", indexRaf.getLocation(), size);
        return false;
      }

      byte[] m = new byte[size];
      indexRaf.readFully(m);
      gribCollectionIndex = GribCollectionProto.GribCollectionIndex.parseFrom(m);
      return true;

    } catch (Throwable t) {
      logger.error("Error reading index " + indexRaf.getLocation(), t);
      return false;
    }
  }

  /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  private static GribCollection readGc(File dir, String filename, FeatureCollectionConfig config) throws IOException {
    long start = System.currentTimeMillis();
    RandomAccessFile raf = new RandomAccessFile(filename, "r");
    GribCollection gc = Grib2CollectionBuilderFromIndex.createFromIndex("test", dir, raf, config.gribConfig, logger);
    long took = System.currentTimeMillis() - start;
    System.out.printf("readGc GC %s took %s msecs%n", filename, took);
    return gc;
  }


  private static void rewriteGc(CollectionManager dcm, FeatureCollectionConfig config) throws IOException {
    dcm.putAuxInfo(FeatureCollectionConfig.AUX_GRIB_CONFIG, config.gribConfig);
    long start = System.currentTimeMillis();
    GribCollection gc = Grib2CollectionBuilder.factory(dcm, CollectionManager.Force.always, logger);
    long took = System.currentTimeMillis() - start;
    System.out.printf("rewriteGc GC %s took %s msecs%n", dcm.getCollectionName(), took);
    gc.close();
  }

  private static void rewriteGcFromSingle(MFile mfile, FeatureCollectionConfig config) throws IOException {
    long start = System.currentTimeMillis();
    GribCollection gc = Grib2CollectionBuilder.readOrCreateIndexFromSingleFile(mfile, CollectionManager.Force.always, config.gribConfig, logger);
    long took = System.currentTimeMillis() - start;
    System.out.printf("rewriteGcFromSingle GC %s took %s msecs%n", mfile, took);
    gc.close();
  }

  private static Grib2TimePartition doOnePart(File dir, String filename, FeatureCollectionConfig config) throws IOException {
    long start = System.currentTimeMillis();
    RandomAccessFile raf = new RandomAccessFile(filename, "r");
    Grib2TimePartition tp = Grib2TimePartitionBuilderFromIndex.createFromIndex("test", dir, raf, logger);  // LOOK why no config ??
    //GribCollection gc = Grib2TimePartitionBuilderFromIndex.createFromIndex("test", dir, raf, config.gribConfig, logger);
    long took = System.currentTimeMillis() - start;
    System.out.printf("that took %s msecs%n", took);
    return tp;
  }

  public static void main(String[] args) throws IOException {
    // need to configure the loggers

    File cat = new File("B:/ndfd/catalog.xml");
    org.jdom2.Document doc;
    try {
      SAXBuilder builder = new SAXBuilder();
      doc = builder.build(cat);
    } catch (Exception e) {
      e.printStackTrace();
      return;
    }
    FeatureCollectionConfig config = FeatureCollectionReader.readFeatureCollection(doc.getRootElement());

    long start = System.currentTimeMillis();

    Formatter errlog = new Formatter();
    Path topPath = Paths.get("B:/ndfd/200901/20090101");
    GribCollection gc = makeGribCollectionIndexOneDirectory(config, CollectionManager.Force.always, topPath, errlog);
    System.out.printf("%s%n", errlog);
    gc.close();


    //Path topPath = Paths.get("B:/ndfd/200906");
    //makeTimePartitionIndexOneDirectory(config, topPath);
     //rewriteIndexesPartition(config, topPath);

    long took = System.currentTimeMillis() - start;
    System.out.printf("that all took %s msecs%n", took);
  }

}
