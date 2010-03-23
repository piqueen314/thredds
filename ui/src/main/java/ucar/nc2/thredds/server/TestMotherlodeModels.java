/*
 * Copyright 1998-2009 University Corporation for Atmospheric Research/Unidata
 *
 * Portions of this software were developed by the Unidata Program at the
 * University Corporation for Atmospheric Research.
 *
 * Access and use of this software shall impose the following obligations
 * and understandings on the user. The user is granted the right, without
 * any fee or cost, to use, copy, modify, alter, enhance and distribute
 * this software, and any derivative works thereof, and its supporting
 * documentation for any purpose whatsoever, provided that this entire
 * notice appears in all copies of the software, derivative works and
 * supporting documentation.  Further, UCAR requests that the user credit
 * UCAR/Unidata in any publications that result from the use of this
 * software or in any product that includes this software. The names UCAR
 * and/or Unidata, however, may not be used in any advertising or publicity
 * to endorse or promote any products or commercial entity unless specific
 * written permission is obtained from UCAR/Unidata. The user also
 * understands that UCAR/Unidata is not obligated to provide the user with
 * any support, consulting, training or assistance of any kind with regard
 * to the use, operation and performance of this software nor to provide
 * the user with any updates, revisions, new versions or "bug fixes."
 *
 * THIS SOFTWARE IS PROVIDED BY UCAR/UNIDATA "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL UCAR/UNIDATA BE LIABLE FOR ANY SPECIAL,
 * INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING
 * FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT,
 * NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION
 * WITH THE ACCESS, USE OR PERFORMANCE OF THIS SOFTWARE.
 */
package ucar.nc2.thredds.server;

import ucar.nc2.ui.StopButton;
import ucar.nc2.thredds.ThreddsDataFactory;
import ucar.nc2.dataset.NetcdfDataset;

import java.io.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.*;
import java.util.Formatter;

import thredds.catalog.crawl.CatalogCrawler;
import thredds.catalog.*;

import javax.swing.*;

/**
 * Run through the named catalogs, readd a random dataset from each collection
 * default is to run over the idd/models.xml catalog.
 */
public class TestMotherlodeModels implements CatalogCrawler.Listener {
  private String catUrl;
  private int type;
  private boolean skipDatasetScan;

  private InvCatalogFactory catFactory = InvCatalogFactory.getDefaultFactory(true);
  private ThreddsDataFactory tdataFactory = new ThreddsDataFactory();

  private StopButton stopButton;
  private JLabel label;
  private PrintStream out;
  private int countDatasets, countNoAccess, countNoOpen;
  private boolean verbose = true;

  TestMotherlodeModels(String name, String catURL, int type, boolean skipDatasetScan) throws IOException {
    this.catUrl = catURL;
    this.type = type;
    this.skipDatasetScan = skipDatasetScan;

    JPanel p = new JPanel();
    p.setBorder( BorderFactory.createLineBorder(Color.black ));

    p.add(new JLabel(name+":"));
    label = new JLabel();
    p.add(label);
    stopButton = new StopButton("stopit goddammit!");
    p.add(stopButton);
    main.add( p);

    //FileOutputStream fout = new FileOutputStream(name+".txt");
    out = System.out; // new PrintStream( new BufferedOutputStream( fout));

  }

  public void extract() throws IOException {

    out.println("Read " + catUrl);

    InvCatalogImpl cat = catFactory.readXML(catUrl);
    StringBuilder buff = new StringBuilder();
    boolean isValid = cat.check(buff, false);
    if (!isValid) {
      System.out.println("***Catalog invalid= " + catUrl + " validation output=\n" + buff);
      out.println("***Catalog invalid= " + catUrl + " validation output=\n" + buff);
      return;
    }
    out.println("catalog <" + cat.getName() + "> is valid");
    out.println(" validation output=\n" + buff);

    countDatasets = 0;
    countNoAccess = 0;
    countNoOpen = 0;
    int countCatRefs = 0;
    CatalogCrawler crawler = new CatalogCrawler(type, skipDatasetScan, this);
    long start = System.currentTimeMillis();
    try {
      countCatRefs = crawler.crawl(cat, stopButton, verbose ? out : null, null);
    } finally {
      int took = (int) (System.currentTimeMillis() - start) / 1000;

      out.println("***Done " + catUrl + " took = " + took + " secs\n" +
              "   datasets=" + countDatasets + " no access=" + countNoAccess + " open failed=" + countNoOpen + " total catalogs=" + countCatRefs);

    }
  }

  public void getDataset(InvDataset ds, Object context) {
    countDatasets++;

    ThreddsMetadata.GeospatialCoverage gc = ds.getGeospatialCoverage();
    //assert gc != null;

    NetcdfDataset ncd = null;
    try {
      Formatter log = new Formatter();
      ncd = tdataFactory.openDataset( ds,  false, null, log);

      if (ncd == null)
        out.println("**** failed= "+ds.getName()+" err="+log);
      else if (verbose)
        out.println("   "+ds.getName()+" ok");

    } catch (IOException e) {
      out.println("**** failed= "+ds.getName()+" err= "+e.getMessage());
    } finally {
      if (ncd != null) try {
        ncd.close();
      } catch (IOException e) {
      }
    }

  }
  public boolean getCatalogRef(InvCatalogRef dd, Object context) { return true; }
   
  public static JPanel main;
  public static void main(String args[]) throws IOException {
    String server = "http://motherlode.ucar.edu:9080/thredds";
    if (args.length > 0)
      server = args[0];

    String catalog = "/idd/models.xml";
    String problemCat = // "/catalog/fmrc/NCEP/RUC2/CONUS_20km/surface/catalog.xml";
                        "/catalog/fmrc/NCEP/RUC2/CONUS_20km/hybrid/catalog.xml";
    String models = "/idd/models.xml";
    String chizModels = "/idd/rtmodel.xml";
    String gribtonc = "/idd/allModels.TDS-nc.xml";

    //"http://motherlode.ucar.edu:9080/thredds/idd/models_old.xml"

    // HEY LOOK
    //ucar.nc2.dods.DODSNetcdfFile.setAllowSessions( true);

    JFrame frame = new JFrame("TestTDS");
    frame.addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) {
        System.exit(0);
      }
    });

    main = new JPanel();
    main.setLayout( new BoxLayout(main, BoxLayout.Y_AXIS));

    //TestMotherlodeModels problem = new TestMotherlodeModels("problem", server+problemCat, CatalogCrawler.USE_RANDOM_DIRECT, false);
    TestMotherlodeModels all_models = new TestMotherlodeModels("models", server+catalog, CatalogCrawler.USE_RANDOM_DIRECT, false);
    //TestMotherlodeModels chiz_models = new TestMotherlodeModels("chiz_models", server+chizModels, CatalogCrawler.USE_RANDOM_DIRECT, false);
    //TestMotherlodeModels nc_models = new TestMotherlodeModels("gribtonc", server+gribtonc, CatalogCrawler.USE_RANDOM_DIRECT, false);
    //TestMotherlodeModels localAll = new TestMotherlodeModels("localAll", "http://localhost:8080/thredds/catalog.xml", CatalogCrawler.USE_ALL, false);

    frame.getContentPane().add(main);
    frame.pack();
    frame.setLocation(40, 300);
    frame.setVisible(true);

    //problem.extract();
    //while (true) 
      all_models.extract();
    //chiz_models.extract();
    //nc_models.extract();
    //localAll.extract();
  }

}
