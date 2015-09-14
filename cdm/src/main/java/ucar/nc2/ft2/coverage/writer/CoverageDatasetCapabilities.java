/*
 * Copyright 1998-2015 John Caron and University Corporation for Atmospheric Research/Unidata
 *
 *  Portions of this software were developed by the Unidata Program at the
 *  University Corporation for Atmospheric Research.
 *
 *  Access and use of this software shall impose the following obligations
 *  and understandings on the user. The user is granted the right, without
 *  any fee or cost, to use, copy, modify, alter, enhance and distribute
 *  this software, and any derivative works thereof, and its supporting
 *  documentation for any purpose whatsoever, provided that this entire
 *  notice appears in all copies of the software, derivative works and
 *  supporting documentation.  Further, UCAR requests that the user credit
 *  UCAR/Unidata in any publications that result from the use of this
 *  software or in any product that includes this software. The names UCAR
 *  and/or Unidata, however, may not be used in any advertising or publicity
 *  to endorse or promote any products or commercial entity unless specific
 *  written permission is obtained from UCAR/Unidata. The user also
 *  understands that UCAR/Unidata is not obligated to provide the user with
 *  any support, consulting, training or assistance of any kind with regard
 *  to the use, operation and performance of this software nor to provide
 *  the user with any updates, revisions, new versions or "bug fixes."
 *
 *  THIS SOFTWARE IS PROVIDED BY UCAR/UNIDATA "AS IS" AND ANY EXPRESS OR
 *  IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  DISCLAIMED. IN NO EVENT SHALL UCAR/UNIDATA BE LIABLE FOR ANY SPECIAL,
 *  INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING
 *  FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT,
 *  NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION
 *  WITH THE ACCESS, USE OR PERFORMANCE OF THIS SOFTWARE.
 *
 */
package ucar.nc2.ft2.coverage.writer;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import ucar.ma2.DataType;
import ucar.nc2.Attribute;
import ucar.nc2.constants.AxisType;
import ucar.nc2.ft2.coverage.*;
import ucar.nc2.ncml.NcMLWriter;
import ucar.nc2.time.CalendarDateRange;
import ucar.nc2.util.Misc;
import ucar.unidata.geoloc.LatLonRect;
import ucar.unidata.geoloc.ProjectionRect;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Helper class to create a DatasetCapabilities XML document for CoverageDataset
 *
 * @author caron
 * @since 5/7/2015
 */
public class CoverageDatasetCapabilities {
  private CoverageDataset gcd;
  private String path;

  public CoverageDatasetCapabilities(CoverageDataset gds, String path) {
    this.gcd = gds;
    this.path = path;
  }

  /**
   * Write the information as an XML document
   *
   * @param doc write XML for this Document
   * @return String output
   */
  public String writeXML(Document doc) {
    XMLOutputter fmt = new XMLOutputter(Format.getPrettyFormat());
    return fmt.outputString(doc);
  }

  /**
   * Write the information as an XML document
   *
   * @param doc write XML for this Document
   * @param os  write to this output stream
   * @throws java.io.IOException on write error
   */
  public void writeXML(Document doc, OutputStream os) throws IOException {
    XMLOutputter fmt = new XMLOutputter(Format.getPrettyFormat());
    fmt.output(doc, os);
  }

  /**
   * Create the "Dataset Description" XML document from this GridDataset
   *
   * @return a JDOM Document
   */
  public Document makeDatasetDescription() throws IOException {
    Element rootElem = new Element("gridDataset");
    Document doc = new Document(rootElem);
    rootElem.setAttribute("location", gcd.getName());
    if (null != path)
      rootElem.setAttribute("path", path);

    // coordinate axes
    for (CoverageCoordAxis axis : gcd.getCoordAxes()) {
      rootElem.addContent(writeAxis(axis));
    }

    // gridSets
    for (CoordSysSet gridset : gcd.getCoverageSets()) {
      rootElem.addContent(writeCoverageSet(gridset.getCoordSys(), gridset.getCoverages(), gcd.getProjBoundingBox()));
    }

    // coordinate transforms
    for (CoverageTransform ct : gcd.getCoordTransforms()) {
      rootElem.addContent(writeCoordTransform(ct));
    }

 		/* global attributes
     Iterator atts = gds.getGlobalAttributes().iterator();
     while (atts.hasNext()) {
       ucar.nc2.Attribute att = (ucar.nc2.Attribute) atts.next();
       rootElem.addContent(ucar.nc2.ncml.NcMLWriter.writeAttribute(att, "attribute", null));
     } */

    // add lat/lon bounding box
    LatLonRect bb = gcd.getLatLonBoundingBox();
    if (bb != null)
      rootElem.addContent(writeBoundingBox(bb));

    // add date range
    CalendarDateRange calDateRange = gcd.getCalendarDateRange();
    if (calDateRange != null) {
      Element dateRange = new Element("TimeSpan");
      dateRange.addContent(new Element("begin").addContent(calDateRange.getStart().toString()));
      dateRange.addContent(new Element("end").addContent(calDateRange.getEnd().toString()));
      rootElem.addContent(dateRange);
    }

    return doc;
  }

  private Element writeAxis(CoverageCoordAxis axis) throws IOException {

    Element varElem = new Element("axis");
    varElem.setAttribute("name", axis.getName());
    varElem.setAttribute("shape", Misc.showInts(axis.getShape()));

    DataType dt = axis.getDataType();
    varElem.setAttribute("type", dt.toString());

    AxisType axisType = axis.getAxisType();
    if (null != axisType)
      varElem.setAttribute("axisType", axisType.toString());

    if (axis.getDependsOn() != null && axis.getDependsOn().trim().length() > 0)
      varElem.setAttribute("dependsOn", axis.getDependsOn().trim());

    // attributes
    for (Attribute att : axis.getAttributes()) {
      varElem.addContent(ucar.nc2.ncml.NcMLWriter.writeAttribute(att, "attribute", null));
    }

		/*
        f.format("%s  npts: %d [%f,%f] spacing=%s", indent, ncoords, startValue, endValue, spacing);
    if (getResolution() != 0.0)
      f.format(" resolution=%f", resolution);
    f.format(" %s :", getDependenceType());
    for (String s : dependsOn)
		 */

    Element values;
    if (axis.isRegular()) {
      values = new Element("values");
    } else {
      values = ucar.nc2.ncml.NcMLWriter.writeValues(axis.getCoordsAsArray(), null, false);
    }

    values.setAttribute("spacing", axis.getSpacing().toString());
    values.setAttribute("npts", Long.toString(axis.getNcoords()));
    values.setAttribute("start", Double.toString(axis.getStartValue()));
    values.setAttribute("end", Double.toString(axis.getEndValue()));
    if (axis.getResolution() != 0.0)
      values.setAttribute("resolution", Double.toString(axis.getResolution()));

    varElem.addContent(values);
    return varElem;
  }


  /**
   * Returns a WKT polygon with the dataset boundaries
   *
   * @return WKT string
   *
  public String getDatasetBoundariesWKT(){

  return GridBoundariesExtractor.valueOf(gds).getDatasetBoundariesWKT();
  }  */


  // display name plus the dimensions
  private String getShapeString(int[] shape) {
    StringBuilder buf = new StringBuilder();
    for (int i = 0; i < shape.length; i++) {
      if (i != 0) buf.append(" ");
      buf.append(shape[i]);
    }
    return buf.toString();
  }


  private Element writeBoundingBox(LatLonRect bb) {

    Element bbElem = new Element("LatLonBox");
    //LatLonPoint llpt = bb.getLowerLeftPoint();
    //LatLonPoint urpt = bb.getUpperRightPoint();

    //bbElem.addContent(new Element("west").addContent(ucar.unidata.util.Format.dfrac(llpt.getLongitude(), 4)));
    bbElem.addContent(new Element("west").addContent(ucar.unidata.util.Format.dfrac(bb.getLonMin(), 4)));
    //bbElem.addContent(new Element("east").addContent(ucar.unidata.util.Format.dfrac(urpt.getLongitude(), 4)));
    bbElem.addContent(new Element("east").addContent(ucar.unidata.util.Format.dfrac(bb.getLonMax(), 4)));
    //bbElem.addContent(new Element("south").addContent(ucar.unidata.util.Format.dfrac(llpt.getLatitude(), 4)));
    bbElem.addContent(new Element("south").addContent(ucar.unidata.util.Format.dfrac(bb.getLatMin(), 4)));
    //bbElem.addContent(new Element("north").addContent(ucar.unidata.util.Format.dfrac(urpt.getLatitude(), 4)));
    bbElem.addContent(new Element("north").addContent(ucar.unidata.util.Format.dfrac(bb.getLatMax(), 4)));

    return bbElem;

  }

  private Element writeCoverageSet(CoverageCoordSys cs, List<Coverage> grids, ProjectionRect rect) {
    Element csElem = new Element("gridSet");
    csElem.setAttribute("name", cs.getName());

    if (rect != null) {
      Element projBBOX = new Element("projectionBox");
      Element minx = new Element("minx");
      minx.addContent(Double.valueOf(rect.getMinX()).toString());
      projBBOX.addContent(minx);
      Element maxx = new Element("maxx");
      maxx.addContent(Double.valueOf(rect.getMaxX()).toString());
      projBBOX.addContent(maxx);
      Element miny = new Element("miny");
      miny.addContent(Double.valueOf(rect.getMinY()).toString());
      projBBOX.addContent(miny);
      Element maxy = new Element("maxy");
      maxy.addContent(Double.valueOf(rect.getMaxY()).toString());
      projBBOX.addContent(maxy);

      csElem.addContent(projBBOX);
    }

    for (String axisName : cs.getAxisNames()) {
      Element axisElem = new Element("axisRef");
      axisElem.setAttribute("name", axisName);
      csElem.addContent(axisElem);
    }

    for (String ctName : cs.getTransformNames()) {
      Element elem = new Element("coordTransRef");
      elem.setAttribute("name", ctName);
      csElem.addContent(elem);
    }

    Collections.sort(grids, new GridCoverageComparator());
    for (Coverage grid : grids) {
      csElem.addContent(writeGrid(grid));
    }

    return csElem;
  }

 	/* private Element writeCoordSys(GridCoordSystem cs) {
     Element csElem = new Element("coordSys");
     csElem.setAttribute("name", cs.getName());
     List axes = cs.getCoordinateAxes();
     for (int i = 0; i < axes.size(); i++) {
       CoordinateAxis axis = (CoordinateAxis) axes.get(i);
       Element axisElem = new Element("axisRef");
       axisElem.setAttribute("name", axis.getName());
       csElem.addContent(axisElem);
     }
     List cts = cs.getCoordinateTransforms();
     for (int j = 0; j < cts.size(); j++) {
       CoordinateTransform ct = (CoordinateTransform) cts.get(j);
       Element elem = new Element("coordTransRef");
       elem.setAttribute("name", ct.getName());
       csElem.addContent(elem);
     }
     return csElem;
   } */

  private Element writeCoordTransform(CoverageTransform ct) {
    Element ctElem = new Element("coordTransform");
    ctElem.setAttribute("name", ct.getName());
    ctElem.setAttribute("transformType", ct.isHoriz() ? "Projection" : "Vertical");
    for (Attribute param : ct.getAttributes()) {
      Element pElem = NcMLWriter.writeAttribute(param, "parameter", null);
      ctElem.addContent(pElem);
    }
    return ctElem;
  }

  private Element writeGrid(Coverage grid) {
    Element varElem = new Element("grid");
    varElem.setAttribute("name", grid.getName());

    String desc = grid.getDescription() != null ? grid.getDescription() : "No description";
    varElem.setAttribute("desc", desc);

 		/* StringBuilder buff = new StringBuilder();
 		List dims = grid.getDimensions();
 		for (int i = 0; i < dims.size(); i++) {
 			Dimension dim = (Dimension) dims.get(i);
 			if (i > 0) buff.append(" ");
 			if (dim.isShared())
 				buff.append(dim.getShortName());
 			else
 				buff.append(dim.getLength());
 		}
 		if (buff.length() > 0)
 			varElem.setAttribute("shape", buff.toString()); */

    DataType dt = grid.getDataType();
    if (dt != null)
      varElem.setAttribute("type", dt.toString());

    //GridCoordSystem cs = grid.getCoordinateSystem();
    //varElem.setAttribute("coordSys", cs.getName());

    // attributes
    for (ucar.nc2.Attribute att : grid.getAttributes()) {
      varElem.addContent(ucar.nc2.ncml.NcMLWriter.writeAttribute(att, "attribute", null));
    }

    return varElem;
  }

  // sort by domain size, then name
  private static class GridCoverageComparator implements Comparator<Coverage> {
    public int compare(Coverage grid1, Coverage grid2) {
      return grid1.getName().compareTo(grid2.getName());
    }
  }
}