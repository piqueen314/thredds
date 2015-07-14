/*
 * Copyright 1998-2014 University Corporation for Atmospheric Research/Unidata
 *
 *   Portions of this software were developed by the Unidata Program at the
 *   University Corporation for Atmospheric Research.
 *
 *   Access and use of this software shall impose the following obligations
 *   and understandings on the user. The user is granted the right, without
 *   any fee or cost, to use, copy, modify, alter, enhance and distribute
 *   this software, and any derivative works thereof, and its supporting
 *   documentation for any purpose whatsoever, provided that this entire
 *   notice appears in all copies of the software, derivative works and
 *   supporting documentation.  Further, UCAR requests that the user credit
 *   UCAR/Unidata in any publications that result from the use of this
 *   software or in any product that includes this software. The names UCAR
 *   and/or Unidata, however, may not be used in any advertising or publicity
 *   to endorse or promote any products or commercial entity unless specific
 *   written permission is obtained from UCAR/Unidata. The user also
 *   understands that UCAR/Unidata is not obligated to provide the user with
 *   any support, consulting, training or assistance of any kind with regard
 *   to the use, operation and performance of this software nor to provide
 *   the user with any updates, revisions, new versions or "bug fixes."
 *
 *   THIS SOFTWARE IS PROVIDED BY UCAR/UNIDATA "AS IS" AND ANY EXPRESS OR
 *   IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *   WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *   DISCLAIMED. IN NO EVENT SHALL UCAR/UNIDATA BE LIABLE FOR ANY SPECIAL,
 *   INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING
 *   FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT,
 *   NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION
 *   WITH THE ACCESS, USE OR PERFORMANCE OF THIS SOFTWARE.
 */

package thredds.server.cdmrfeature;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.LastModified;
import thredds.core.AllowedServices;
import thredds.core.StandardService;
import thredds.core.TdsRequestedDataset;
import thredds.server.config.TdsContext;
import thredds.server.exception.ServiceNotAllowed;
import thredds.server.ncss.params.NcssGridParamsBean;
import thredds.servlet.ServletUtil;
import thredds.util.ContentType;
import thredds.util.TdsPathUtils;
import ucar.ma2.Array;
import ucar.ma2.InvalidRangeException;
import ucar.ma2.Section;
import ucar.nc2.ft2.coverage.*;
import ucar.nc2.ft2.coverage.remote.CdmrfWriter;
import ucar.nc2.iosp.IospHelper;
import ucar.nc2.stream.NcStream;
import ucar.nc2.stream.NcStreamProto;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.DeflaterOutputStream;

/**
 * Controller for CdmrFeature service.
 *
 * @author caron
 */
@Controller
@RequestMapping("/cdmrfeature/grid")
public class CdmrGridController implements LastModified {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CdmrGridController.class);
  private static final boolean showReq = true;
  private static final boolean showRes = true;

  @Autowired
  TdsContext tdsContext;

  @Autowired
  private AllowedServices allowedServices;

  @Override
  public long getLastModified(HttpServletRequest req) {
    String path = TdsPathUtils.extractPath(req, "cdmrfeature/");
    return TdsRequestedDataset.getLastModified(path);
  }

  /* private Validator validator = new CdmrFeatureQueryBeanValidator();

  @InitBinder
  protected void initBinder(WebDataBinder binder) {
    binder.setValidator(validator);
  }  */

  ////////////////////////////////////////////////////////////////////

  @RequestMapping(value = "/**", method = RequestMethod.GET, params = "req=header")
  public void handleHeaderRequest(HttpServletRequest request, HttpServletResponse response, OutputStream out) throws IOException {
    if (showReq)
      System.out.printf("CdmrFeatureController '%s?%s'%n", request.getRequestURI(), request.getQueryString());

    if (!allowedServices.isAllowed(StandardService.cdmrFeatureGrid))
      throw new ServiceNotAllowed(StandardService.cdmrFeatureGrid.toString());

    String datasetPath = TdsPathUtils.extractPath(request, StandardService.cdmrFeatureGrid.getBase());

    try (CoverageDataset gridCoverageDataset = TdsRequestedDataset.getGridCoverage(request, response, datasetPath)) {
      if (gridCoverageDataset == null) return;

      response.setContentType(ContentType.binary.getContentHeader());
      response.setHeader("Content-Description", "ncstream");
      CdmrfWriter writer = new CdmrfWriter(gridCoverageDataset, ServletUtil.getRequestBase(request));
      long size = writer.sendHeader(out);
      out.flush();

      if (showRes)
        System.out.printf(" CdmrFeatureController.getHeader sent, message size=%s%n", size);
    }
  }

  @RequestMapping(value = "/**", method = RequestMethod.GET, params = "req=form")
  public ResponseEntity<String> handleFormRequest(HttpServletRequest request, HttpServletResponse response, OutputStream out) throws IOException {
    if (showReq)
      System.out.printf("CdmrFeatureController '%s?%s'%n", request.getRequestURI(), request.getQueryString());

    if (!allowedServices.isAllowed(StandardService.cdmrFeatureGrid))
      throw new ServiceNotAllowed(StandardService.cdmrFeatureGrid.toString());

    String datasetPath = TdsPathUtils.extractPath(request, StandardService.cdmrFeatureGrid.getBase());
    HttpHeaders responseHeaders;

    try (CoverageDataset gridCoverageDataset = TdsRequestedDataset.getGridCoverage(request, response, datasetPath)) {
      if (gridCoverageDataset == null) return null;

      String text = gridCoverageDataset.toString();

      if (showRes)
        System.out.printf(" CdmrFeatureController.getHeader sent, message size=%s%n", text.length());

      responseHeaders = new HttpHeaders();
      responseHeaders.set(ContentType.HEADER, ContentType.text.getContentHeader());
      return new ResponseEntity<>(text, responseHeaders, HttpStatus.OK);

    }
  }

  /* var=gridName
     Z=value
   */

  @RequestMapping(value = "/**", method = RequestMethod.GET, params = "req=data")
  public void handleDataRequest(HttpServletRequest request, HttpServletResponse response,
                                @Valid NcssGridParamsBean qb, BindingResult validationResult, OutputStream out) throws IOException, BindException, InvalidRangeException {

    if (showReq)
      System.out.printf("CdmrFeatureController '%s?%s'%n", request.getRequestURI(), request.getQueryString());

    if (!allowedServices.isAllowed(StandardService.cdmrFeatureGrid))
      throw new ServiceNotAllowed(StandardService.cdmrFeatureGrid.toString());

    if (validationResult.hasErrors())
      throw new BindException(validationResult);

    String datasetPath = TdsPathUtils.extractPath(request, StandardService.cdmrFeatureGrid.getBase());

    try (CoverageDataset gridCoverageDataset = TdsRequestedDataset.getGridCoverage(request, response, datasetPath)) {
      if (gridCoverageDataset == null) return;

      response.setContentType(ContentType.binary.getContentHeader());
      response.setHeader("Content-Description", "ncstream");

      // construct the subsetted dataset
      SubsetParams subset = qb.makeSubset(gridCoverageDataset.getCalendar());
      CoverageSubset helper = new CoverageSubset(gridCoverageDataset, qb.getVar(), subset);
      CoverageDataset subsetDataset = helper.makeCoverageDatasetSubset();

      // write the data to the stream
      // write the data to the new file.
      for (Coverage grid : subsetDataset.getCoverages()) {
        ArrayWithCoordinates array = grid.readData(subset);
        sendData(grid, array.getData(), out, true);
      }

      out.flush();

    } catch (Throwable t) {
      t.printStackTrace();
    }
  }

  private long sendData(Coverage grid, Array data, OutputStream out, boolean deflate) throws IOException, InvalidRangeException {

    // length of data uncompressed
    long uncompressedLength = data.getSizeBytes();

    long size = 0;
    size += writeBytes(out, NcStream.MAGIC_DATA); // magic
    NcStreamProto.Data dataProto = NcStream.encodeDataProto(grid.getName(), grid.getDataType(), new Section(data.getShape()), deflate, (int) uncompressedLength);
    byte[] datab = dataProto.toByteArray();
    size += NcStream.writeVInt(out, datab.length); // dataProto len
    size += writeBytes(out, datab); // dataProto

    // regular arrays
    if (deflate) {
      // write to an internal buffer, so we can find out the size
      ByteArrayOutputStream bout = new ByteArrayOutputStream();
      DeflaterOutputStream dout = new DeflaterOutputStream(bout);
      IospHelper.copyToOutputStream(data, dout);

      // write internal buffer to output stream
      dout.close();
      int deflatedSize = bout.size();
      size += NcStream.writeVInt(out, deflatedSize);
      bout.writeTo(out);
      size += deflatedSize;
      float ratio = ((float) uncompressedLength) / deflatedSize;
      if (showRes)
        System.out.printf(" CdmrFeatureController.sendData grid='%s' org/compress= %d/%d = %f%n", grid.getName(), uncompressedLength, deflatedSize, ratio);

    } else {

      size += NcStream.writeVInt(out, (int) uncompressedLength); // data len or number of objects
      if (showRes) System.out.printf(" CdmrFeatureController.sendData grid='%s' data len=%d%n", grid.getName(), uncompressedLength);

      size += IospHelper.copyToOutputStream(data, out);
    }

    return size;
  }

  private int writeBytes(OutputStream out, byte[] b) throws IOException {
    out.write(b);
    return b.length;
  }

}