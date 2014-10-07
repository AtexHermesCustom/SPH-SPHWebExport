/*
 * File:    FormattedWebExportServlet.java
 *
 * Copyright (c) 2012,  Atex Media Command GmbH
 *                      Kurhessenstrasse 13
 *                      64546 Moerfelden-Walldorf
 *                      Germany
 *
 * Audit:
 *         20141007     jpm -updated cropping related methods
 *         20140909     jpm -value for <byline> element -grouping of byline, email, title and twitter -tagged text
 *         20140903     jpm -properties file name can be passed as a parameter
 *         20131112     jpm -accept "destination" parameter to allow multiple target destinations
 *         20131002     jpm -add special tag handling (use TagSpecialHandler class)
 *         20130906     jpm -differentiate between photo and graphic objects by using different xml tag names (photo vs graphic)        
 *         20130807     jpm add processing of 'abstract' and 'video' elements
 *         20120722     jpm 1. look for byline, etc from summary objects in addition to text and header objects
 *                          2. teaser and subtitle components of headline go to h2
 *         20120719     jpm 1. handling for [ and ] reserved chars (tag markers)
 *         20120718     jpm 1. call StyleReader.getTagCategories after loading the properties file
 *         20120717     jpm 1. no need to get kicker and subheadline text from h1.
 *                          -kicker text already set in the xsl (taken from 'Supertitle' component of headline)
 *                          -subheadline text to be taken from Summary object
 *                          2. always create a copyright element if a caption exists
 * v01.00  06-jun-2012  st  Initial version.
 * v00.00  18-apr-2012  st  Created.
 */

package de.atex.h11.custom.sph.export.webapp;

import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.net.URL;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.util.Map;
import java.util.Date;
import java.util.Locale;
import java.util.TreeSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;
import java.text.NumberFormat;
import java.text.DecimalFormat;
import java.text.ParseException;
import javax.imageio.ImageIO;
import javax.imageio.IIOImage;
import javax.imageio.ImageWriter;
import javax.imageio.ImageWriteParam;
import javax.imageio.stream.ImageOutputStream;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Node;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Document;
import org.w3c.dom.ProcessingInstruction;
import org.apache.log4j.Logger;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.codec.binary.Base64;
import com.unisys.media.cr.adapter.ncm.model.data.datasource.NCMDataSource;
import com.unisys.media.cr.adapter.ncm.model.data.values.NCMObjectValueClient;
import com.unisys.media.cr.adapter.ncm.common.data.pk.NCMObjectPK;
import com.unisys.media.cr.adapter.ncm.common.data.values.NCMObjectBuildProperties;
import com.unisys.media.cr.adapter.ncm.common.data.types.NCMObjectNodeType;
import java.awt.image.Raster;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import javax.imageio.IIOException;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 *
 * @author tstuehler
 */
public class FormattedWebExportServlet extends HttpServlet {

    /**
     * Processes requests for both HTTP
     * <code>GET</code> and
     * <code>POST</code> methods.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = response.getWriter();
        NCMDataSource ds = null;

        String strUser = request.getParameter("user");
        String strPasswd = request.getParameter("password");
        String strSessionId = request.getParameter("sessionid");
        String strObjId = request.getParameter("id");
        String strNodeType = request.getParameter("nodetype");
        String strDestination = request.getParameter("destination");
        String strPropsFileName = request.getParameter("properties");
        
        if (strObjId == null) {
            strObjId = request.getParameter("ncm-obj-id");
            if (strObjId != null && strNodeType == null)
                strNodeType = "ncm-object";
        }

        if (strUser == null && strPasswd == null) {
            // TODO: check if we got a http auth header !!!
            strUser = DEFAULT_USER;
            strPasswd = DEFAULT_PASSWORD;
        }

        logger.debug(request.getParameterMap().toString());  // For debugging only!

        if (strNodeType == null) {
            response.sendError(response.SC_BAD_REQUEST, "Node type required.");
            return;
        }

        if (!strNodeType.equals("ncm-object")) {
            response.sendError(response.SC_BAD_REQUEST, "Provided object is of wrong type.");
            return;
        }

        if ((strUser == null || strPasswd == null) && strSessionId == null) {
            response.sendError(response.SC_UNAUTHORIZED, "User credentials required!");
            return;
        }
        
        if (strDestination == null || strDestination.isEmpty()) {
            strDestination = DEFAULT_DESTINATION;      // default destination URL
        }
        
        if (strPropsFileName == null || strPropsFileName.isEmpty()) {
            strPropsFileName = DEFAULT_PROPS_FILE_NAME;      // default
        }        
        
        try {
            props = getProperties(strPropsFileName);
            
            String strStyleFile = props.getProperty("styleFile");      // style tag classes
            if (strStyleFile != null) {
                tcHM = StyleReader.getTagCategories(strStyleFile);                         
            }            
            String strTextConvert = props.getProperty("text.convert"); // converter format
            
            // special tags handling
            String strSpecialTagMap = props.getProperty("specialTagMap");
            if (strSpecialTagMap != null) {
                tagSpecialHandler = 
                    new TagSpecialHandler(docBuilder.parse(strSpecialTagMap), xp);
            }              
            
            // Prepare the URL.
            String strUrl = props.getProperty(strDestination);
            if (strUrl == null) {
                throw new RuntimeException("Required property " + strDestination + " not found.");
            }
            URL url = new URL(strUrl);
            
            File webTransformStylesheet = null;
            String strStylesheet = props.getProperty("webTransform");
            if (strStylesheet != null && strStylesheet.length() > 0) {
                webTransformStylesheet = new File(strStylesheet);
                if (!webTransformStylesheet.exists()) {
                    throw new RuntimeException("Stylesheet " + webTransformStylesheet.getPath() + ": does not exist.");
                } else if (!webTransformStylesheet.isFile()) {
                    throw new RuntimeException("Stylesheet " + webTransformStylesheet.getPath() + ": is not a file.");
                }
                if (!webTransformStylesheet.canRead()) {
                    throw new RuntimeException("Stylesheet " + webTransformStylesheet.getPath() + ": is not readable.");
                }
            }           
            else {
                throw new RuntimeException("Web Transform Stylesheet not configured.");
            }                 

            if (strSessionId != null)
                ds = (NCMDataSource) DataSource.newInstance(strSessionId);
            else
                ds = (NCMDataSource) DataSource.newInstance(strUser, strPasswd);

            String[] sa = strObjId.split(":");
            NCMObjectPK objPK = null;
            if (props.getProperty("ignoreLayoutId", "false").equalsIgnoreCase("true")) {
                objPK = new NCMObjectPK(Integer.parseInt(sa[0]));
            } else {
                objPK = new NCMObjectPK(Integer.parseInt(sa[0]),
                        Integer.parseInt(sa[1]), NCMObjectPK.LAST_VERSION, 
                        NCMObjectPK.ACTIVE);                
            }

            NCMObjectBuildProperties objProps = new NCMObjectBuildProperties();
            NCMObjectValueClient currentObj = (NCMObjectValueClient) ds.getNode(objPK, objProps);
            objProps.setIncludeObjContent(true);
            if (currentObj.getType() != NCMObjectNodeType.OBJ_STORY_PACKAGE) {
                int spId = currentObj.getSpId();
                if (spId > 0) {
                    objPK = new NCMObjectPK(spId);
                } else {
                    response.sendError(response.SC_BAD_REQUEST, "Object not part of a package.");
                    return;
                }
            }
            
            StoryPackage sp = new StoryPackage(ds);
            Document doc = docBuilder.newDocument();
            DOMSource source = new DOMSource(sp.getDocument(objPK, strTextConvert));
            DOMResult result = new DOMResult(doc);
            Transformer t = transformerFactory.newTransformer(new StreamSource(webTransformStylesheet));
            Iterator iter = props.stringPropertyNames().iterator();
            while (iter.hasNext()) {
                String strProp = (String) iter.next();
                if (strProp.startsWith("xslt.param.")) {
                    t.setParameter(strProp.replaceFirst("xslt.param.", ""), 
                            props.getProperty(strProp) == null ? "" : props.getProperty(strProp));
                }
            }
            t.transform(source, result);

            NodeList nl3 = doc.getDocumentElement().getChildNodes();
            for (int k = 0; k < nl3.getLength(); k++) {
                Node n = nl3.item(k);
                Document storyDoc = docBuilder.newDocument();
                storyDoc.appendChild(storyDoc.importNode(n, true));
            
                // retrieve Newsroom tags and finalize text formatting
                handleTags(storyDoc);

                // include image files
                float jpegQuality = Float.parseFloat(props.getProperty("jpegQuality", "0.5"));
                
                // check if this is a STORY or an IMAGE/GRAPHIC xml file
                boolean isImageXML = ((Boolean) xp.evaluate("/nitf/head/docdata/definition/@type!='STORY'", 
                        doc, XPathConstants.BOOLEAN)).booleanValue();
                
                // 20120607 jpm: process photo nodes of both STORY and IMAGE xml files
                //NodeList nl = (NodeList) xp.evaluate("/nitf[head/docdata/definition/@type='STORY']/body/photo", storyDoc, XPathConstants.NODESET);
                NodeList nl = (NodeList) xp.evaluate("/nitf/body/photo | /nitf/body/graphic", storyDoc, XPathConstants.NODESET);
                for (int i = 0; i < nl.getLength(); i++) {
                    int rotationAngle = getRotationAngle(nl.item(i));
                    Dimension dimension = getDimension(nl.item(i));
                    Rectangle cropRect = getCropRect(nl.item(i));
                    boolean flipX = getFlipX(nl.item(i));
                    boolean flipY = getFlipY(nl.item(i));                    
                    String strHighResPath = getHighResImagePath(nl.item(i));
                    String strMedResPath = getMedResImagePath(nl.item(i));
                    String strLowResPath = getLowResImagePath(nl.item(i));

                    String objTypeStr;
                    if (nl.item(i).getNodeName().equals("graphic")) {
                        objTypeStr = "graphic";
                    }
                    else {
                        objTypeStr = "image";
                    }                    
                    
                    // get the destination file name
                    String strThumbnailTarget = null;
                    String strLowResTarget = null;
                    Node node = (Node) xp.evaluate("./" + objTypeStr + "_thumbnail", nl.item(i), XPathConstants.NODE);
                    if (node != null)
                        strThumbnailTarget = node.getTextContent();
                    node = (Node) xp.evaluate("./" + objTypeStr + "_low", nl.item(i), XPathConstants.NODE);
                    if (node != null)
                        strLowResTarget = node.getTextContent();

                    // dump the files
                    /*write(url, strLowResTarget, crop(new File(strMedResPath), cropRect, dimension, rotationAngle));
                    logger.debug("Wrote " + strLowResTarget + " to " + url + ".");
                    write(url, strThumbnailTarget, crop(new File(strLowResPath), cropRect, dimension, rotationAngle));
                    logger.debug("Wrote " + strThumbnailTarget + " to " + url + ".");*/
                    File highResFile = null;
                    File medResFile = null;
                    File lowResFile = null;
                    if (strHighResPath != null) {
                        highResFile = new File(strHighResPath);
                    }
                    if (strMedResPath != null) {
                        medResFile = new File(strMedResPath);
                    }
                    if (strLowResPath != null) {
                        lowResFile = new File(strLowResPath);
                    }
                    
                    if (props.getProperty("useOriginalAsLowres", "false").equalsIgnoreCase("true")) {
                        String strSuffix = null;
                        int pos = highResFile.getName().lastIndexOf('.');
                        if (pos > 0) strSuffix = highResFile.getName().substring(pos);
                        if (!strSuffix.equalsIgnoreCase(".jpg")) {
                            pos = strLowResTarget.lastIndexOf('.');
                            if (pos > 0) {
                                strLowResTarget = strLowResTarget.substring(0, pos) + strSuffix.toLowerCase();
                                Element e = (Element) xp.evaluate("./" + objTypeStr + "_low", nl.item(i), XPathConstants.NODE);
                                if (e != null) e.setTextContent(strLowResTarget);
                            }
                        }
                        if (isImageXML) {
                            if (cropRect != null && props.getProperty("cropLowres", "true").equalsIgnoreCase("true") && strSuffix.equalsIgnoreCase(".jpg")) {
                                byte[] imageBytes = crop(props, highResFile, medResFile, cropRect, dimension, rotationAngle, flipX, flipY);
                                write(url, strLowResTarget, imageBytes);
                            } else {
                                write(url, strLowResTarget, new FileInputStream(highResFile));
                            }
                            logger.debug("Wrote " + strLowResTarget + " to " + url + ".");
                        }
                    } else {
                        if (isImageXML) {
                            if (cropRect != null && props.getProperty("cropLowres", "true").equalsIgnoreCase("true")) {
                                byte[] imageBytes = crop(props, medResFile, medResFile, cropRect, dimension, rotationAngle, flipX, flipY);
                                write(url, strLowResTarget, imageBytes);
                            } else {
                                write(url, strLowResTarget, new FileInputStream(medResFile));
                            }
                            logger.debug("Wrote " + strLowResTarget + " to " + url + ".");
                        }
                    }
                    
                    if (props.getProperty("omitThumbnail", "false").equalsIgnoreCase("false")) {
                        if (isImageXML) {
                            if (cropRect != null && props.getProperty("cropThumbnail", "true").equalsIgnoreCase("true")) {
                                byte[] imageBytes = crop(props, lowResFile, medResFile, cropRect, dimension, rotationAngle, flipX, flipY);
                                write(url, strThumbnailTarget, imageBytes);
                            } else {
                                write(url, strThumbnailTarget, new FileInputStream(lowResFile));
                            }
                            logger.debug("Wrote " + strThumbnailTarget + " to " + url + ".");
                        }
                    } else {
                        Element e = (Element) xp.evaluate("./" + objTypeStr + "_thumbnail", nl.item(i), XPathConstants.NODE);
                        if (e != null) e.setTextContent("");
                    }
                }

                String strFileName = getFileName(storyDoc);

                // Get rid of remaining processing instructions.
                nl = (NodeList) xp.evaluate("//processing-instruction()",
                                            storyDoc, XPathConstants.NODESET);
                for (int i = 0; i < nl.getLength(); i++) {
                    String strTarget = ((ProcessingInstruction) nl.item(i)).getTarget();
                    nl.item(i).getParentNode().removeChild(nl.item(i));
                }

                // dump the XML document
                write(url, strFileName, storyDoc);
                logger.debug("Wrote " + strFileName + " to " + url + ".");
            }
            
            out.println("<html>");
            out.println("<head>");
            out.println("<title>FormattedWebExportServlet</title>");
            out.println("</head>");
            out.println("<body>");
            out.println("<h1>" + "Object " + strObjId
                    + " has been exported successfully." + "</h1>");
            out.println("</body>");
            out.println("</html>");
        } catch (Exception e) {
            logger.error("", e);
            response.sendError(response.SC_CONFLICT, e.toString());
        } finally {            
            out.close();
            if (ds != null) ds.logout();
        }
    }

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /**
     * Handles the HTTP
     * <code>GET</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Handles the HTTP
     * <code>POST</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    public String getServletInfo() {
        return "Formatted Web Export";
    }// </editor-fold>
    

    public void init () throws ServletException {
        try {
            docBuilder = docBuilderFactory.newDocumentBuilder();
            transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.STANDALONE, "yes");
            transformer.setOutputProperty(OutputKeys.CDATA_SECTION_ELEMENTS, 
                "caption copyright person title country kick content h1 h2 video abstract byline hyperlink");
            transformer.setOutputProperty("{http://xml.apache.org/xsl}indent-amount", "4");
        } catch (Exception ex) {
            throw new ServletException(ex);
        }
    }
    
    private Properties getProperties(String propsFileName) throws IOException {
        Properties props = new Properties();

        String strJBossHomeDir = System.getProperty("jboss.server.home.dir");
        String strPropsFile = strJBossHomeDir + File.separator + "conf"
                                + File.separator + propsFileName + ".properties";
        File propsFile = new File(strPropsFile);
        try {
            props.load(new FileInputStream(propsFile));
        } catch (FileNotFoundException fnf) {
            logger.error("Properties file not found: " + strPropsFile, fnf);
        }

        return props;
    }

    
    private Dimension getDimension (Node contNode) throws ParseException {
        Dimension dimension = null;
        String strValue = getProcessingInstructionValue(contNode, "dimension");
        if (strValue != null) {
            String[] s = strValue.split(" ");
            if (s.length == 2) {
                int width = df.parse(s[0]).intValue();
                int height = df.parse(s[1]).intValue();
                dimension = new Dimension(width, height);
            }
        }
        return dimension;
    }

    private Rectangle getCropRect (Node contNode) throws ParseException {
        Rectangle cropRect = null;
        String strValue = getProcessingInstructionValue(contNode, "crop-rect");
        if (strValue != null) {
            String[] s = strValue.split(" ");
            if (s.length == 4) {
                int bottom = Integer.parseInt(s[0]);
                int left = Integer.parseInt(s[1]);
                int top = Integer.parseInt(s[2]);
                int right = Integer.parseInt(s[3]);
                cropRect = new Rectangle(left, top, right - left, bottom - top);
            } else {
            }            
        }
        return cropRect;
    }
    
    private int getRotationAngle (Node contNode) throws ParseException {
        int rotationAngle = 0;
        String strValue = getProcessingInstructionValue(contNode, "rotate");
        if (strValue != null) rotationAngle = df.parse(strValue).intValue();
        return rotationAngle;
    }
    
    private boolean getFlipX (Node contNode) throws ParseException {
        boolean flipX = false;
        String strValue = getProcessingInstructionValue(contNode, "flip-x");
        if (strValue != null) flipX = strValue.equalsIgnoreCase("true");
        return flipX;
    }    
    
    private boolean getFlipY (Node contNode) throws ParseException {
        boolean flipY = false;
        String strValue = getProcessingInstructionValue(contNode, "flip-y");
        if (strValue != null) flipY = strValue.equalsIgnoreCase("true");
        return flipY;
    }          

    private String getHighResImagePath (Node contNode) throws ParseException {
        String strImagePath = getProcessingInstructionValue(contNode, "highres-imagepath");
        return strImagePath;
    }

    private String getMedResImagePath (Node contNode) throws ParseException {
        String strImagePath = getProcessingInstructionValue(contNode, "medres-imagepath");
        return strImagePath;
    }

    private String getLowResImagePath (Node contNode) throws ParseException {
        String strImagePath = getProcessingInstructionValue(contNode, "lowres-imagepath");
        return strImagePath;
    }
    
    private String getProcessingInstructionValue (Node contNode, String key) throws ParseException {
        String strValue = null;
        NodeList nl = contNode.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            if (nl.item(i).getNodeType() == Node.PROCESSING_INSTRUCTION_NODE) {
                String strTarget = ((ProcessingInstruction) nl.item(i)).getTarget();
                if (strTarget.equals(key)) {
                    strValue = ((ProcessingInstruction) nl.item(i)).getData();  // get value
                    // remove the processing instruction
                    Node parent = nl.item(i).getParentNode();                    
                    parent.removeChild(nl.item(i));
                    break;
                }
            }
        }
        return strValue;
    }      
        
    private String getFileName (Node node) 
            throws ParseException, XPathExpressionException {
        String strFileName = null;

        NodeList nl = (NodeList) xp.evaluate("//processing-instruction()",
                                            node, XPathConstants.NODESET);
        for (int i = 0; i < nl.getLength(); i++) {
            if (nl.item(i).getNodeType() == Node.PROCESSING_INSTRUCTION_NODE) {
                // remove the processing instruction
                String strTarget = ((ProcessingInstruction) nl.item(i)).getTarget();
                if (strTarget.equals("file-name")) {
                    strFileName = ((ProcessingInstruction) nl.item(i)).getData();
                    Node parent = nl.item(i).getParentNode();
                    parent.removeChild(nl.item(i));
                }
            }
        }

        return strFileName;
    }


    private byte[] crop (Properties props, File srcFile, File medRes, Rectangle cropRect, Dimension dimension, 
            int rotationAngle, boolean flipX, boolean flipY) throws IOException, InterruptedException {
        byte[] imageBytes = null;

        File tempFile = File.createTempFile("export", ".jpg");
        try {
            crop(props, srcFile, medRes, tempFile, 
                cropRect, dimension, rotationAngle, flipX, flipY);
            FileInputStream in = new FileInputStream(tempFile);
            imageBytes = new byte[(int) tempFile.length()];
            int bytesRead = 0;
            do {
                bytesRead = in.read(imageBytes, bytesRead, imageBytes.length - bytesRead);
            } while (bytesRead >= 0);
            in.close();
        } finally {
            tempFile.delete();
        }
        
        return imageBytes;
    }    
    

    private void crop (Properties props, File srcFile, File medFile, File dstFile, 
            Rectangle cropRect, Dimension dimension, 
            int rotationAngle, boolean flipX, boolean flipY) throws IOException, InterruptedException {
                
        Rectangle adjustedCropRect = null;
        if (cropRect != null && dimension != null) {
            // get source/high-res image dimensions
            Dimension srcDim = getImageDimensions(srcFile, props);
            int srcW = srcDim.width;
            int srcH = srcDim.height;

            // get med-res image dimensions
            Dimension medDim = getImageDimensions(medFile, props);
            int medW = medDim.width;
            int medH = medDim.height;  
            
            // compute adjusted crop
            /*
            logger.finer("Image file " + srcFile.getName() + 
                    ": origres-dim=" + srcW + "x" + srcH +
                    ", medres-dim=" + medW + "x" + medH +
                    ", api-dim=" + dimension.width + "x" + dimension.height);
            */
            /* change computation of ratio
            float ratioX = (float) dimension.width / (float) srcW;
            float ratioY = (float) dimension.height / (float) srcH;      
            */
            float ratioX, ratioY;
            // determine whether the high-res or med-res was used
            if (dimension.width == srcW && dimension.height == srcH) {
                ratioX = 1;
                ratioY = 1;                   
            }
            else {
                ratioX = (float) medW / (float) srcW;
                ratioY = (float) medH / (float) srcH;                      
            }
            //logger.finer("Image file " + srcFile.getName() + ": x-ratio=" + ratioX + ", y-ratio=" + ratioY);
            int cropX = (int) ((float) cropRect.x / ratioX);
            int cropY = (int) ((float) cropRect.y / ratioY);
            int cropW = (int) ((float) cropRect.width / ratioX);
            int cropH = (int) ((float) cropRect.height / ratioY);
            //logger.finer("Image file " + srcFile.getName() + 
            //    ": adjusted: cropX=" + cropX + ", cropY=" + cropY + ", cropW=" + cropW + ", cropH=" + cropH);
            adjustedCropRect = new Rectangle(cropX, cropY, cropW, cropH);            
        }
        
        if (props.containsKey("converterProgArgs")) {
            String progArgsStr = props.getProperty("converterProgArgs");
            cropImage(srcFile.getCanonicalPath(), dstFile.getCanonicalPath(),
                      adjustedCropRect, dimension, rotationAngle, flipX, flipY, progArgsStr);
        } else {
            cropImage(props, srcFile, dstFile, 
                      adjustedCropRect, dimension, rotationAngle, flipX, flipY, false);
        }
    }
    
    
    private void cropImage (Properties props, File srcFile, File dstFile, Rectangle cropRect,
                              Dimension dimension, int rotationAngle, boolean flipX, boolean flipY,
                              boolean bConvertToRGB)
            throws IOException {
        Object[] logParams = new Object[9];
        logParams[0] = props;
        logParams[1] = srcFile;
        logParams[2] = dstFile;
        logParams[3] = cropRect;
        logParams[4] = dimension;
        logParams[5] = new Integer(rotationAngle);
        logParams[5] = flipX;
        logParams[6] = flipY;
        logParams[8] = bConvertToRGB;
        //logger.entering(getClass().getName(), "cropImage", logParams);

        // read the source file
        //BufferedImage srcImage = ImageIO.read(srcFile); // note: this doesn't work for CMYK images
        BufferedImage srcImage = readJPGFile(srcFile);      // this can read CMYK
        srcImage = convertCMYK2RGB(srcImage);

        BufferedImage croppedImage = srcImage.getSubimage(cropRect.x, cropRect.y, 
                                                          cropRect.width, cropRect.height);
        BufferedImage dstImage = croppedImage;

        // see if color space conversion is required
        ColorSpace colorSpace = srcImage.getColorModel().getColorSpace();
        boolean bNeedsConversion = false;
        switch (colorSpace.getType()) {
            case ColorSpace.TYPE_CMY:
            case ColorSpace.TYPE_CMYK:
                bNeedsConversion = true;
                break;
        }

        if (bConvertToRGB && bNeedsConversion) {
            // convert the cropped image to rgb
            ColorConvertOp op = new ColorConvertOp(
                    croppedImage.getColorModel().getColorSpace(),
                    ColorSpace.getInstance(ColorSpace.CS_sRGB), null);
            dstImage = op.createCompatibleDestImage(croppedImage, null);
            op.filter(croppedImage, dstImage);
        }

        // Find a jpeg writer
        ImageWriter writer = null;
        Iterator iter = ImageIO.getImageWritersByFormatName("jpg");
        if (iter.hasNext()) {
            writer = (ImageWriter) iter.next();
        }

        // Prepare output file
        ImageOutputStream ios = ImageIO.createImageOutputStream(dstFile);
        writer.setOutput(ios);

        // Set the compression quality
        ImageWriteParam writeParam = writer.getDefaultWriteParam();
        writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        writeParam.setCompressionQuality(
                Float.parseFloat(props.getProperty("jpegQuality", "0.5")));

        // Write the image
        writer.write(null, new IIOImage(dstImage, null, null), writeParam);

        // Cleanup
        ios.flush();
        writer.dispose();
        ios.close();

        //logger.exiting(getClass().getName(), "cropImage");
    }

    
    private void cropImage (String srcFileName, String dstFileName, Rectangle cropRect,
                              Dimension dimension, int rotationAngle, boolean flipX, boolean flipY, 
                              String progArgs)
            throws IOException {
        Object[] logParams = new Object[8];
        logParams[0] = srcFileName;
        logParams[1] = dstFileName;
        logParams[2] = cropRect;
        logParams[3] = dimension;
        logParams[4] = new Integer(rotationAngle);
        logParams[5] = flipX;
        logParams[6] = flipY;
        logParams[7] = progArgs;
        //logger.entering(getClass().getName(), "cropImage", logParams);

        if (progArgs != null) {
            // Build the external program's argument list.
            try {
                List<String> argList = new LinkedList<String>();
                Scanner scanner = new Scanner(progArgs);
                scanner.useDelimiter("\\s");
                while (scanner.hasNext()) {
                    String token = scanner.next();
                    if (token.contains("$INFILE")) {
                        argList.add(token.replace("$INFILE", srcFileName));
                    } else if (token.contains("$OUTFILE")) {
                        argList.add(token.replace("$OUTFILE", dstFileName));
                    } else if (token.contains("$CROPRECT")) {
                        if (cropRect != null) {
                            argList.add(token.replace("$CROPRECT", "-crop"));
                            String s = Integer.toString(cropRect.width) + "x" + Integer.toString(cropRect.height) + "+" +
                                       Integer.toString(cropRect.x) + "+" + Integer.toString(cropRect.y);
                            argList.add(s);
                        }                        
                    } else if (token.contains("$FLIPX")) {
                        if (flipX) argList.add(token.replace("$FLIPX", "-flop"));
                    } else if (token.contains("$FLIPY")) {
                        if (flipY) argList.add(token.replace("$FLIPY", "-flip"));
                    } else if (token.contains("$ROTATE")) {
                        if (rotationAngle != 0) {
                            argList.add(token.replace("$ROTATE", "-rotate"));
                            String s = Integer.toString(rotationAngle);
                            argList.add(s);
                        }
                    } else {
                        argList.add(token);
                    }
                }
                runProgram(argList);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        } else throw new IllegalArgumentException("progArgs == null");

        //logger.exiting(getClass().getName(), "cropImage");
    }
    
    
    private Dimension getImageDimensions(File imgFile, Properties props) throws IOException, InterruptedException {
        //logger.entering(getClass().getName(), "getImageDimensions", imgFile.getName());
        
        int width = 0;
        int height = 0;
        
        if (props.containsKey("imageTestProgArgs") &&
            props.containsKey("imageTestWidthPattern") &&
            props.containsKey("imageTestHeightPattern")) {
            // use external program to get dimensions
            List<String> argList = new LinkedList<String>();
            Scanner scanner = new Scanner(props.getProperty("imageTestProgArgs"));
            scanner.useDelimiter("\\s");
            while (scanner.hasNext()) {
                String token = scanner.next();
                if (token.contains("$INFILE")) {
                    argList.add(token.replace("$INFILE", imgFile.getCanonicalPath()));
                } else {
                    argList.add(token);
                }
            }
            String response = runProgramGetResp(argList);
            String widthStr = response.replaceAll(props.getProperty("imageTestWidthPattern"), "$1");
            String heightStr = response.replaceAll(props.getProperty("imageTestHeightPattern"), "$1");
            //logger.finer("getImageDimensions - parsed values: width=" + widthStr + ", height=" + heightStr);
            width = Integer.parseInt(widthStr);
            height = Integer.parseInt(heightStr);
        }
        else {
            // get dimensions
            //BufferedImage srcImage = ImageIO.read(imgFile);   // note: this doesn't work for CMYK images
            BufferedImage img = readJPGFile(imgFile);           // this can read CMYK
            // get image dimensions
            width = img.getWidth();
            height = img.getHeight();
        }
        
        Dimension dimension = new Dimension(width, height);
        
        //logger.exiting(getClass().getName(), "getImageDimensions", dimension);
        return dimension;        
    }
    
    
    private void runProgram (List<String> args)
            throws IOException, InterruptedException {
       
        ProcessBuilder procBuilder = new ProcessBuilder(args);
        Process proc = procBuilder.start();
        proc.waitFor();

        // Evaluate exit value.
        int ev = proc.exitValue();
        if (ev != 0) {
            StringBuffer errSB = new StringBuffer();
            errSB.append(args.get(0) + ": returned " + ev + ".");
            InputStreamReader err = new InputStreamReader(proc.getErrorStream());
            char[] cbuf = new char[8192];
            int length = err.read(cbuf);
            if (length > 0) {
                errSB.append("\n" + new String(cbuf, 0, length));
            }
            err.close();
            throw new IOException(errSB.toString());
        }
    }
    
    
    private String runProgramGetResp (List<String> args)
            throws IOException, InterruptedException {
        String response = "";
        
        ProcessBuilder procBuilder = new ProcessBuilder(args);
        Process proc = procBuilder.start();
        proc.waitFor();

        // Evaluate exit value.
        int ev = proc.exitValue();
        if (ev != 0) {
            StringBuffer errSB = new StringBuffer();
            errSB.append(args.get(0) + ": returned " + ev + ".");
            InputStreamReader err = new InputStreamReader(proc.getErrorStream());
            char[] cbuf = new char[8192];
            int length = err.read(cbuf);
            if (length > 0) {
                errSB.append("\n" + new String(cbuf, 0, length));
            }
            err.close();
            throw new IOException(errSB.toString());
        }
        
        InputStream is = proc.getInputStream();     // to capture output from command
        response = convertStreamToStr(is);
        return response;
    }        
    
    private String convertStreamToStr(InputStream is) throws IOException {
        Writer writer = new StringWriter();
        char[] buffer = new char[1024];
        try {
            Reader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            int n;
            while ((n = reader.read(buffer)) != -1) {
                writer.write(buffer, 0, n);
            }
        }
        finally {
            is.close();
        }
        return writer.toString();
    }    
    
   private BufferedImage readJPGFile(File srcFile) 
    	throws IOException {
    	
        // Find a JPEG reader which supports reading Rasters.
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("JPEG");
        ImageReader reader = null;
        while (readers.hasNext()) {
            reader = (ImageReader) readers.next();
            if (reader.canReadRaster())
                break;
        }

        // Set the input.
        ImageInputStream input = ImageIO.createImageInputStream(srcFile);
        reader.setInput(input);

        // Create the image.
        BufferedImage image = null;
        try {
            // Try reading an image (including color conversion).
            image = reader.read(0);
        } catch(IIOException e) {
            // Try reading a Raster (no color conversion).
            Raster raster = reader.readRaster(0, null);

            // Arbitrarily select a BufferedImage type.
            int imageType;
            switch (raster.getNumBands()) {
                case 1:
                    imageType = BufferedImage.TYPE_BYTE_GRAY;
                    break;
                case 3:
                    imageType = BufferedImage.TYPE_3BYTE_BGR;
                    break;
                case 4:
                    imageType = BufferedImage.TYPE_4BYTE_ABGR;
                    break;
                default:
                    throw new UnsupportedOperationException();
            }

            // Create a BufferedImage.
            image = new BufferedImage(raster.getWidth(), raster.getHeight(), imageType);

            // Set the image data.
            image.getRaster().setRect(raster);
        }
        
        return image;
    }	        
   
    private BufferedImage convertCMYK2RGB(BufferedImage image) throws IOException{
        //Create a new RGB image
        BufferedImage rgbImage = new BufferedImage(image.getWidth(), image.getHeight(),
        		BufferedImage.TYPE_3BYTE_BGR);
        // then do a funky color convert
        ColorConvertOp op = new ColorConvertOp(null);
        op.filter(image, rgbImage);
        return rgbImage;
    }       
    
    
    private void write (URL destURL, String destFileName, Document doc)
            throws ProtocolException, FileNotFoundException, IOException,
            UnsupportedEncodingException, TransformerException {
        HttpURLConnection http = null;
        PrintWriter out = null;
        FTPClient ftp = null;
        Map httpHeaderFields = null;
        String httpErrDescr = null;
        String httpResponse = null;
        int httpResponseCode = 0;

        if (destFileName == null) {
            destFileName = new Date().getTime() + "-"
                    + Thread.currentThread().getId() + ".xml";
        }

        if (destURL.getProtocol().equals("file")) {
            String path = destURL.getPath();
            File file = new File(path + File.separator + destFileName);
            out = new PrintWriter(file, "UTF-8");
        } else if (destURL.getProtocol().equals("ftp")) {
            ftp = getFtpConnection(destURL);
            out = new PrintWriter(new OutputStreamWriter(
                    ftp.storeFileStream(destFileName), "UTF-8"));
        } else if (destURL.getProtocol().equals("http")) {
            String encoding = props.getProperty("http.contentEncoding", defaultContentEncoding);
            String contentType = props.getProperty("http.contentType", defaultContentType);
            http = (HttpURLConnection) destURL.openConnection();
            http.setDoOutput(true);
            http.setDoInput(true);
            http.setRequestMethod("POST");
            http.setRequestProperty("Content-Type", contentType + "; charset=" + encoding);
            http.setRequestProperty("Content-Disposition", "filename=" + destFileName);
            http.setRequestProperty("Accept", contentType);
            if (destURL.getUserInfo() != null) {
                byte[] bytes = Base64.encodeBase64(destURL.getUserInfo().getBytes());
                http.setRequestProperty("Authorization", "Basic " + new String(bytes));
            }
            http.setInstanceFollowRedirects(true);
            http.connect();
            out = new PrintWriter(new OutputStreamWriter(http.getOutputStream(), encoding));
        } else {
            throw new ProtocolException("Unsupported protocol: "
                                        + destURL.getProtocol());
        }

        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(out);
        transformer.transform(source, result);
        out.close();

        if (ftp != null) ftp.disconnect();
        if (http != null) {
            httpHeaderFields = http.getHeaderFields();
            httpResponseCode = http.getResponseCode();
            if (httpResponseCode != HttpURLConnection.HTTP_OK
                    && httpResponseCode != HttpURLConnection.HTTP_CREATED
                    && httpResponseCode != HttpURLConnection.HTTP_ACCEPTED) {
                try {
                    InputStream errStream = http.getErrorStream();
                    if (errStream != null) {
                        BufferedReader err = new BufferedReader(
                            new InputStreamReader(errStream));
                        StringBuffer errSB = new StringBuffer(2000);
                        String errStr = null;
                        while ((errStr = err.readLine()) != null) {
                            errSB.append(errStr);
                        }
                        err.close();
                        httpErrDescr = errSB.toString();
                        throw new IOException("Response code: " + httpResponseCode + " - " + httpErrDescr);
                    } else {
                        throw new IOException("Response code: " + httpResponseCode);
                    }
                } catch (Exception e) {}
            } else {
                try {
                    InputStream inStream = http.getInputStream();
                    if (inStream != null) {
                        BufferedReader resp = new BufferedReader(
                            new InputStreamReader(inStream));
                        StringBuffer sb = new StringBuffer(2000);
                        String str = null;
                        while ((str = resp.readLine()) != null) {
                            sb.append(str);
                        }
                        resp.close();
                        httpResponse = sb.toString();
                    }
                } catch (Exception e) {}
            }
            http.disconnect();
        }
    }

    
    private void write (URL destURL, String destFileName, InputStream in)
            throws ProtocolException, FileNotFoundException, IOException,
            UnsupportedEncodingException {
        HttpURLConnection http = null;
        OutputStream out = null;
        FTPClient ftp = null;
        Map httpHeaderFields = null;
        String httpErrDescr = null;
        String httpResponse = null;
        int httpResponseCode = 0;

        if (destFileName == null) {
            destFileName = new Date().getTime() + "-"
                    + Thread.currentThread().getId() + ".xml";
        }

        if (destURL.getProtocol().equals("file")) {
            String path = destURL.getPath();
            File file = new File(path + File.separator + destFileName);
            out = new FileOutputStream(file);
        } else if (destURL.getProtocol().equals("ftp")) {
            ftp = getFtpConnection(destURL);
            out = ftp.storeFileStream(destFileName);
        } else if (destURL.getProtocol().equals("http")) {
            http = (HttpURLConnection) destURL.openConnection();
            http.setDoOutput(true);
            http.setDoInput(true);
            http.setRequestMethod("POST");
            http.setRequestProperty("Content-Type", "application/octet-stream");
            http.setRequestProperty("Content-Disposition", "filename=" + destFileName);
            if (destURL.getUserInfo() != null) {
                byte[] bytes = Base64.encodeBase64(destURL.getUserInfo().getBytes());
                http.setRequestProperty("Authorization", "Basic " + new String(bytes));
            }
            http.setInstanceFollowRedirects(true);
            http.connect();
            out = http.getOutputStream();
        } else {
            throw new ProtocolException("Unsupported protocol: "
                                        + destURL.getProtocol());
        }

        streamCopy(in, out);
        in.close();
        out.close();

        if (ftp != null) ftp.disconnect();
        if (http != null) {
            httpHeaderFields = http.getHeaderFields();
            httpResponseCode = http.getResponseCode();
            if (httpResponseCode != HttpURLConnection.HTTP_OK
                    && httpResponseCode != HttpURLConnection.HTTP_CREATED
                    && httpResponseCode != HttpURLConnection.HTTP_ACCEPTED) {
                try {
                    InputStream errStream = http.getErrorStream();
                    if (errStream != null) {
                        BufferedReader err = new BufferedReader(
                            new InputStreamReader(errStream));
                        StringBuffer errSB = new StringBuffer(2000);
                        String errStr = null;
                        while ((errStr = err.readLine()) != null) {
                            errSB.append(errStr);
                        }
                        err.close();
                        httpErrDescr = errSB.toString();
                        throw new IOException("Response code: " + httpResponseCode + " - " + httpErrDescr);
                    } else {
                        throw new IOException("Response code: " + httpResponseCode);
                    }
                } catch (Exception e) {}
            } else {
                try {
                    InputStream inStream = http.getInputStream();
                    if (inStream != null) {
                        BufferedReader resp = new BufferedReader(
                            new InputStreamReader(inStream));
                        StringBuffer sb = new StringBuffer(2000);
                        String str = null;
                        while ((str = resp.readLine()) != null) {
                            sb.append(str);
                        }
                        resp.close();
                        httpResponse = sb.toString();
                    }
                } catch (Exception e) {}
            }
            http.disconnect();
        }
    }

    
    private void write (URL destURL, String destFileName, byte[] bytes)
            throws ProtocolException, FileNotFoundException, IOException,
            UnsupportedEncodingException {
        write(destURL, destFileName, new ByteArrayInputStream(bytes));
    }
    
    
    private void streamCopy (InputStream in, OutputStream out)
            throws IOException {
        byte[] buf = new byte[8192];
        int bytesRead;
        do {
            bytesRead = in.read(buf);
            if (bytesRead > 0)
                out.write(buf, 0, bytesRead);
        } while (bytesRead >= 0);
    }

    
    private FTPClient getFtpConnection (URL url) throws IOException {
        String protocol = url.getProtocol();
        String host = url.getHost();
        String userInfo = url.getUserInfo();
        String path = url.getPath();

        String[] credentials = userInfo.split(":");

        FTPClient ftp = new FTPClient();
        ftp.connect(host);
        ftp.login(credentials[0], credentials[1]);
        int reply = ftp.getReplyCode();
        if (reply == FTPReply.NOT_LOGGED_IN) {
            try { ftp.disconnect(); ftp = null; } catch (Exception e) {}
            throw new IOException("Login failed on FTP host " + host + ".");
        }
        String sysName = ftp.getSystemName();
        System.err.println("FTP system is: " + sysName);
        boolean bStatus = ftp.changeToParentDirectory();
        bStatus = ftp.changeWorkingDirectory(path);
        if (!bStatus) {
            try { ftp.logout(); ftp.disconnect(); ftp = null; } catch (Exception e) {}
            throw new IOException("Changing working directory to " + path
                                    + " failed on FTP host " + host + ".");
        }
        if (props.getProperty("ftpPassiveMode", "false").equalsIgnoreCase("true")) {
            ftp.enterLocalPassiveMode();
        }
        if (!ftp.setFileType(FTP.BINARY_FILE_TYPE)) {
            try { ftp.logout(); ftp.disconnect(); ftp = null; } catch (Exception e) {}
            throw new IOException("Failed to set binary transfer mode.");
        }

        return ftp;
    }

    
    private void handleTags (Document doc) throws XPathExpressionException {
        String strContent = null;
        String strHeadline = null;
        String strByline = null;
        String strEmail = null;
        String strKicker = null;
        String strTitle = null;
        String strPlace = null;
        String strTwitter = null;        
        String strSubHeadline = null;
        String strHeader = null;
        String strVideoLink = null;
        String strAbstract = null;
        
        StringBuilder sbByline = new StringBuilder();
        StringBuilder sbTitle = new StringBuilder();
        StringBuilder sbPlace = new StringBuilder();
        StringBuilder sbEmail = new StringBuilder();
        StringBuilder sbTwitter = new StringBuilder();        
        
        // content
        NodeList nl = (NodeList) xp.evaluate("//body/content", doc, XPathConstants.NODESET);
        for (int i = 0; i < nl.getLength(); i++) {
            Element e = (Element) nl.item(i);
            String strText = e.getTextContent();
            if (tagSpecialHandler != null) {
                strText = tagSpecialHandler.ProcessSpecialTags(strText);    // special tags
            }
            StringBuilder sbText = new StringBuilder(strText);
            getDesignations(sbText, sbByline, sbTitle, sbPlace, sbEmail, sbTwitter, true);
            strText = sbText.toString();     // get remaining text
            strText = strText.replaceAll("\\[.*?\\]", "").trim();   // remove tags
            while (strText.startsWith(LINEBREAK)) {
                strText = strText.substring(LINEBREAK.length()).trim();
            }
            if (i > 0) {
                // we have more than a single content
                strContent += LINEBREAK + strText.trim();
                e.getParentNode().removeChild(e);   // get rid of this node
            }
            else 
                strContent = strText.trim();
        }
        if (nl.getLength() == 0) {
            // add a content element
            Node n = (Node) xp.evaluate("/nitf[head/docdata/definition/@type='STORY']/body", doc, XPathConstants.NODE);
            if (n != null) n.appendChild(doc.createElement("content"));
        }
        
        // check if we have a headline
        nl = (NodeList) xp.evaluate("//body/h1", doc, XPathConstants.NODESET);
        if (nl.getLength() == 0) {
            // no headline, add one
            nl = (NodeList) xp.evaluate("/nitf[head/docdata/definition/@type='STORY']/body/content", doc, XPathConstants.NODESET);
            if (nl.getLength() > 0)
                nl.item(0).getParentNode().insertBefore(doc.createElement("h1"), nl.item(0));
        }
        
        // check if we have a subheadline
        nl = (NodeList) xp.evaluate("//body/h2", doc, XPathConstants.NODESET);
        if (nl.getLength() == 0) {
            // no subheadline, add one
            nl = (NodeList) xp.evaluate("/nitf[head/docdata/definition/@type='STORY']/body/content", doc, XPathConstants.NODESET);
            if (nl.getLength() > 0)
                nl.item(0).getParentNode().insertBefore(doc.createElement("h2"), nl.item(0));
        }
        
        // headline
        nl = (NodeList) xp.evaluate("//body/h1", doc, XPathConstants.NODESET);
        for (int i = 0; i < nl.getLength(); i++) {
            Element e = (Element) nl.item(i);
            String strText = e.getTextContent();
            if (tagSpecialHandler != null) {
                strText = tagSpecialHandler.ProcessSpecialTags(strText);    // special tags
            }
            strText = strText.replaceAll("\\[.*?\\]", "").trim();   // remove tags
            if (i > 0) {
                // we have more than a single headline
                strHeadline += " " + strText.trim();
                e.getParentNode().removeChild(e);   // get rid of this node
            }
            else 
                strHeadline = strText;
        }
        
        // kicker
        nl = (NodeList) xp.evaluate("//body/kick", doc, XPathConstants.NODESET);
        for (int i = 0; i < nl.getLength(); i++) {
            Element e = (Element) nl.item(i);
            String strText = e.getTextContent();
            if (tagSpecialHandler != null) {
                strText = tagSpecialHandler.ProcessSpecialTags(strText);    // special tags
            }
            strText = strText.replaceAll("\\[.*?\\]", "").trim();   // remove tags
            if (i > 0) {
                // we have more than a single kicker
                strKicker += " " + strText.trim();
                e.getParentNode().removeChild(e);   // get rid of this node
            }
            else 
                strKicker = strText;
        }             
        
        // teaser component of headline
        nl = (NodeList) xp.evaluate("//body/hteaser", doc, XPathConstants.NODESET);
        for (int i = 0; i < nl.getLength(); i++) {
            Element e = (Element) nl.item(i);
            String strText = e.getTextContent();
            if (tagSpecialHandler != null) {
                strText = tagSpecialHandler.ProcessSpecialTags(strText);    // special tags
            }
            strText = strText.replaceAll("\\[.*?\\]", "").trim();   // remove tags
            if (strSubHeadline == null)
                strSubHeadline = strText;
            else
                strSubHeadline += " " + strText;
            e.getParentNode().removeChild(e);
        }
        
        // subtitle component of headline
        nl = (NodeList) xp.evaluate("//body/hsubtitle", doc, XPathConstants.NODESET);
        for (int i = 0; i < nl.getLength(); i++) {
            Element e = (Element) nl.item(i);
            String strText = e.getTextContent();
            if (tagSpecialHandler != null) {
                strText = tagSpecialHandler.ProcessSpecialTags(strText);    // special tags
            }
            strText = strText.replaceAll("\\[.*?\\]", "").trim();   // remove tags
            if (strSubHeadline == null)
                strSubHeadline = strText;
            else
                strSubHeadline += " " + strText;
            e.getParentNode().removeChild(e);
        }        
        
        // summary
        nl = (NodeList) xp.evaluate("//body/summary", doc, XPathConstants.NODESET);
        for (int i = 0; i < nl.getLength(); i++) {
            Element e = (Element) nl.item(i);
            String strText = e.getTextContent();
            if (tagSpecialHandler != null) {
                strText = tagSpecialHandler.ProcessSpecialTags(strText);    // special tags
            }
            StringBuilder sbText = new StringBuilder(strText);
            getDesignations(sbText, sbByline, sbTitle, sbPlace, sbEmail, sbTwitter, false);
            strText = sbText.toString();     // get remaining text            
            strText = strText.replaceAll("\\[.*?\\]", "").trim();   // remove tags
            if (strSubHeadline == null)
                strSubHeadline = strText;
            else
                strSubHeadline += " " + strText;
            e.getParentNode().removeChild(e);
        }           

        // header
        nl = (NodeList) xp.evaluate("//body/header", doc, XPathConstants.NODESET);
        for (int i = 0; i < nl.getLength(); i++) {
            Element e = (Element) nl.item(i);        
            String strText = e.getTextContent();
            if (tagSpecialHandler != null) {
                strText = tagSpecialHandler.ProcessSpecialTags(strText);    // special tags
            }
            StringBuilder sbText = new StringBuilder(strText);
            getDesignations(sbText, sbByline, sbTitle, sbPlace, sbEmail, sbTwitter, true);
            strText = sbText.toString();     // get remaining text
            strText = strText.replaceAll("\\[.*?\\]", "").trim();   // remove tags
            if (strHeader == null)
                strHeader = strText;
            else
                strHeader += " " + strText;
            e.getParentNode().removeChild(e);
        }

        // photo/graphic
        NodeList nl2 = (NodeList) xp.evaluate("//body/photo | //body/graphic", doc, XPathConstants.NODESET);
        for (int j = 0; j < nl2.getLength(); j++) {
            String strCaption = null;
            String strPhotoCredit = null;
            // caption
            nl = (NodeList) xp.evaluate("caption", nl2.item(j), XPathConstants.NODESET);
            for (int i = 0; i < nl.getLength(); i++) {
                Element e = (Element) nl.item(i);
                String strText = e.getTextContent();
                if (tagSpecialHandler != null) {
                    strText = tagSpecialHandler.ProcessSpecialTags(strText);    // special tags
                }
                StringBuilder sbText = new StringBuilder(strText);
                String strText2 = getTaggedTextByCategory("PHOTO_CREDIT", sbText);
                if (strText2 == null) 
                    strText2 = "";
                if (strPhotoCredit != null)
                    strPhotoCredit += " " + strText2;
                else 
                    strPhotoCredit = strText2;
                strText = sbText.toString();    // get remaining text
                if (strText == null) 
                    strText = "";
                if (strCaption != null)
                    strCaption += " " + strText;
                else 
                    strCaption = strText;
            }  
            if (nl.getLength() > 0) {
                Element e = (Element) nl.item(0);   // first caption element
                strCaption = strCaption.replaceAll("\\[.*?\\]", "");    // remove tags
                strCaption = strCaption.replace(LINEBREAK, " ").replaceAll("\\ +", " ").trim();
                strCaption = replaceReservedCharMarkers(strCaption);
                e.setTextContent(strCaption.trim());

                Element ee = doc.createElement("copyright");    // new copyright element
                strPhotoCredit = strPhotoCredit.replaceAll("\\[.*?\\]", "");    // remove tags
                strPhotoCredit = strPhotoCredit.replace(LINEBREAK, " ").replaceAll("\\ +", " ").trim();
                strPhotoCredit = replaceReservedCharMarkers(strPhotoCredit);
                ee.setTextContent(strPhotoCredit.trim());
                e.getParentNode().appendChild(ee);
                
                // remove other caption elements, if there are any
                for (int i = 1; i < nl.getLength(); i++) {
                    nl.item(i).getParentNode().removeChild(nl.item(i));
                }                
            }
        }

        // set person
        strByline = sbByline.toString();
        if (strByline != null) {
            strByline = strByline.replace(LINEBREAK, " ").replaceAll("\\ +", " ").trim();
            nl = (NodeList) xp.evaluate("//body/person", doc, XPathConstants.NODESET);
            if (nl.getLength() == 1) {
                strByline = replaceReservedCharMarkers(strByline);
                strByline = strByline.replaceAll("\\ *,\\ *", ",");
                ((Element) nl.item(0)).setTextContent(strByline.trim());
            }
        }
        
        // set byline - combination of byline, email, twitter and title
        strByline = getByline(sbByline, sbTitle, sbEmail, sbTwitter);
        if (strByline != null) {
            strByline = strByline.replace(LINEBREAK, " ").replaceAll("\\ +", " ").trim();
            nl = (NodeList) xp.evaluate("//body/byline", doc, XPathConstants.NODESET);
            if (nl.getLength() == 1) {
                strByline = replaceReservedCharMarkers(strByline);
                ((Element) nl.item(0)).setTextContent(strByline.trim());
            }
        }  
        
        // set title
        strTitle = sbTitle.toString();
        if (strTitle != null) {
            strTitle = strTitle.replace(LINEBREAK, " ").replaceAll("\\ +", " ").trim();
            nl = (NodeList) xp.evaluate("//body/title", doc, XPathConstants.NODESET);
            if (nl.getLength() == 1) {
                strTitle = replaceReservedCharMarkers(strTitle);
                strTitle = strTitle.replaceAll("\\ *,\\ *", ",");
                ((Element) nl.item(0)).setTextContent(strTitle.trim());
            }
        }
        
        // set place
        strPlace = sbPlace.toString();
        if (strPlace != null) {
            strPlace = strPlace.replace(LINEBREAK, " ").replaceAll("\\ +", " ").trim();
            nl = (NodeList) xp.evaluate("//body/country", doc, XPathConstants.NODESET);
            if (nl.getLength() == 1) {
                strPlace = replaceReservedCharMarkers(strPlace);
                strPlace = strPlace.replaceAll("\\ *,\\ *", ",");
                ((Element) nl.item(0)).setTextContent(strPlace.trim());
            }
        }
        
        // set email
        strEmail = sbEmail.toString();
        if (strEmail != null) {
            strEmail = strEmail.replace(LINEBREAK, " ").replaceAll("\\ +", " ").trim();
            nl = (NodeList) xp.evaluate("//body/series", doc, XPathConstants.NODESET);
            if (nl.getLength() == 1) {
                strEmail = replaceReservedCharMarkers(strEmail);
                strEmail = strEmail.replaceAll("\\ *,\\ *", ",");
                ((Element) nl.item(0)).setTextContent(strEmail.trim());
            }
        }
        
        // set twitter
        strTwitter = sbTwitter.toString();
        if (strTwitter != null) {
            strTwitter = strTwitter.replace(LINEBREAK, " ").replaceAll("\\ +", " ").trim();
            nl = (NodeList) xp.evaluate("//body/twitter", doc, XPathConstants.NODESET);
            if (nl.getLength() == 1) {
                strTwitter = replaceReservedCharMarkers(strTwitter);
                strTwitter = strTwitter.replaceAll("\\ *,\\ *", ",");
                ((Element) nl.item(0)).setTextContent(strTwitter.trim());
            }
        }       
        
        // set kick
        if (strKicker != null) {
            strKicker = strKicker.replace(LINEBREAK, " ").replaceAll("\\ +", " ").trim();
            nl = (NodeList) xp.evaluate("//body/kick", doc, XPathConstants.NODESET);
            if (nl.getLength() == 1) {
                strKicker = replaceReservedCharMarkers(strKicker);
                ((Element) nl.item(0)).setTextContent(strKicker.trim());
            }
        }        
        
        // set headline
        if (strHeadline != null) {
            strHeadline = strHeadline.replace(LINEBREAK, " ").replaceAll("\\ +", " ").trim();
            nl = (NodeList) xp.evaluate("//body/h1", doc, XPathConstants.NODESET);
            if (nl.getLength() > 0) {
                strHeadline = replaceReservedCharMarkers(strHeadline);
                ((Element) nl.item(0)).setTextContent(strHeadline.trim());
            }
        }        
        
        // set subheadline
        if (strSubHeadline != null) {
            strSubHeadline = strSubHeadline.replace(LINEBREAK, " ").replaceAll("\\ +", " ").trim();
            nl = (NodeList) xp.evaluate("//body/h2", doc, XPathConstants.NODESET);
            if (nl.getLength() == 1) {
                strSubHeadline = replaceReservedCharMarkers(strSubHeadline);
                nl.item(0).setTextContent(strSubHeadline.trim());
            }
        }
        
        // set content
        if (strContent != null || strHeader != null) {
            if (strContent == null)
                strContent = "";
            if (strHeader != null) 
                strContent = strContent.trim() + LINEBREAK + strHeader.trim(); // append header to content
            
            strContent = "<p>" + strContent; //beginning para
            strContent = strContent.replace(LINEBREAK, "</p><p>"); // replace line breaks with para breaks
            strContent = strContent + "</p>"; //ending para
            
            nl = (NodeList) xp.evaluate("//body/content", doc, XPathConstants.NODESET);
            if (nl.getLength() > 0) {
                strContent = replaceReservedCharMarkers(strContent);
                ((Element) nl.item(0)).setTextContent(strContent.trim());
            }
        }
        
        // video
        nl = (NodeList) xp.evaluate("//body/video", doc, XPathConstants.NODESET);
        if (nl.getLength() > 0) {        
            for (int i = 0; i < nl.getLength(); i++) {
                Element e = (Element) nl.item(i);
                String strText = e.getTextContent();
                if (tagSpecialHandler != null) {
                    strText = tagSpecialHandler.ProcessSpecialTags(strText);    // special tags
                }
                strText = strText.replaceAll("\\[.*?\\]", "").trim();   // remove tags
                if (i > 0) {
                    // we have more than one
                    strVideoLink += " " + strText.trim();
                    e.getParentNode().removeChild(e);   // get rid of this node
                }
                else 
                    strVideoLink = strText;
            }         
            if (strVideoLink != null) {
                strVideoLink = strVideoLink.replace(LINEBREAK, " ").replaceAll("\\ +", " ").trim();
                nl = (NodeList) xp.evaluate("//body/video", doc, XPathConstants.NODESET);
                if (nl.getLength() == 1) {
                    strVideoLink = replaceReservedCharMarkers(strVideoLink);
                    nl.item(0).setTextContent(strVideoLink.trim());
                }
            }   
        }
        else {
            Node n = (Node) xp.evaluate("/nitf[head/docdata/definition/@type='STORY']/body", doc, XPathConstants.NODE);
            if (n != null) n.appendChild(doc.createElement("video"));
        }        
        
        // abstract
        nl = (NodeList) xp.evaluate("//body/abstract", doc, XPathConstants.NODESET);
        if (nl.getLength() > 0) {     
            for (int i = 0; i < nl.getLength(); i++) {
                Element e = (Element) nl.item(i);
                String strText = e.getTextContent();
                if (tagSpecialHandler != null) {
                    strText = tagSpecialHandler.ProcessSpecialTags(strText);    // special tags
                }
                strText = strText.replaceAll("\\[.*?\\]", "").trim();   // remove tags
                if (i > 0) {
                    // we have more than one
                    strAbstract += LINEBREAK + strText.trim();
                    e.getParentNode().removeChild(e);   // get rid of this node
                }
                else 
                    strAbstract = strText;
            }         
            if (strAbstract != null) {
                strAbstract = strAbstract.replaceAll("\\ +", " ").trim();
                strAbstract = "<p>" + strAbstract; //beginning para
                strAbstract = strAbstract.replace(LINEBREAK, "</p><p>"); // replace line breaks with para breaks
                strAbstract = strAbstract + "</p>"; //ending para                  
                nl = (NodeList) xp.evaluate("//body/abstract", doc, XPathConstants.NODESET);
                if (nl.getLength() == 1) {
                    strAbstract = replaceReservedCharMarkers(strAbstract);
                    nl.item(0).setTextContent(strAbstract.trim());
                }
            }        
        }
        else {
            Node n = (Node) xp.evaluate("/nitf[head/docdata/definition/@type='STORY']/body", doc, XPathConstants.NODE);
            if (n != null) n.appendChild(doc.createElement("abstract"));
        }          
    }
    
    
    private void getDesignations(StringBuilder sbSourceText, 
            StringBuilder sbByline, StringBuilder sbTitle, StringBuilder sbPlace, 
            StringBuilder sbEmail, StringBuilder sbTwitter,
            boolean removeExtractedText) {            
        String s = null;
        s = getTaggedTextByCategory("BYLINE", sbSourceText, removeExtractedText, ",", ",");
        if (s != null) {
            s = s.replace(LINEBREAK, " ").trim();
            // remove unnecessary text
            String[] bylineMarkers = props.getProperty("byline.markers").split(",");
            for (int i = 0; i < bylineMarkers.length; i++) {
                if (s.toUpperCase().startsWith(bylineMarkers[i].toUpperCase())) {
                    s = s.substring(bylineMarkers[i].length()).trim();
                }
            }               
            s = s.replaceAll("^\\s*,", "");
            s = s.replaceAll("\\s+(?i)and\\s+", ", ");  // replace " and " with a comma
            if (sbByline.length() > 0 && s.length() > 0) {
                sbByline.append(", ");
            }            
            if (sbByline.length() > 0 && s.length() > 0) sbByline.append(", ");
            sbByline.append(s);
        }
        s = getTaggedTextByCategory("BYLINE_TITLE", sbSourceText, removeExtractedText, ",", ",");
        if (s != null) {
            s = s.replace(LINEBREAK, " ").trim();
            if (sbTitle.length() > 0 && s.length() > 0) sbTitle.append(", ");
            sbTitle.append(s);
        }
        s = getTaggedTextByCategory("BYLINE_PLACE", sbSourceText, removeExtractedText, ",", ",");
        if (s != null) {
            s = s.replace(LINEBREAK, " ").trim();
            // remove unnecessary text
            if (s.toUpperCase().startsWith("IN "))
                s = s.substring(3);
            if (sbPlace.length() > 0 && s.length() > 0) sbPlace.append(", ");
            sbPlace.append(s);
        }            
        s = getTaggedTextByCategory("EMAIL", sbSourceText, removeExtractedText, ",", ",");
        if (s != null) {
            s = s.replace(LINEBREAK, " ").trim();
            if (sbEmail.length() > 0 && s.length() > 0) sbEmail.append(", ");
            sbEmail.append(s);
        }        
        s = getTaggedTextByCategory("TWITTER", sbSourceText, removeExtractedText, ",", ",");
        if (s != null) {
            s = s.replace(LINEBREAK, " ").trim();
            if (sbTwitter.length() > 0 && s.length() > 0) sbTwitter.append(", ");
            sbTwitter.append(s);
        }          
    }
    
    private String getByline(StringBuilder sbByline, StringBuilder sbTitle, 
            StringBuilder sbEmail, StringBuilder sbTwitter) {
        // person string: [name1], [email1], [twitter1], [title1] | [name2], [email2], [twitter2], [title2] | ...
        String value = "";
        String tmpValue = "";
        
        String[] bylines = sbByline.toString().split(",");
        String[] emails = sbEmail.toString().split(",");
        String[] twitters = sbTwitter.toString().split(",");
        String[] titles = sbTitle.toString().split(",");
        
        for (int i = 0; i < bylines.length; i++) {
            String byline = "";
            String email = "";
            String twitter = "";
            String title = "";
            
            if (i > 0) { value += "|"; }
            
            byline = bylines[i].trim();
            if (emails.length > i) { email = emails[i].trim(); }
            if (twitters.length > i) { twitter = twitters[i].trim(); }
            if (titles.length > i) { title = titles[i].trim(); }
            
            tmpValue = (byline + "," + email + "," + twitter + "," + title);
            
            if (tmpValue.equals(",,,"))
                tmpValue = "";
                
            value += tmpValue;
        }
        
        return value;
    }
    
    private String getTaggedTextByCategory (String strCategory, StringBuilder sbText, 
            boolean removeExtractedText, String adjacentDelimiter, String nonAdjacentDelimiter) {
        String strTaggedText = null;
        int lastStartTagStartPos = -1;
        
        // default delimiters
        if (adjacentDelimiter == null) { adjacentDelimiter = ""; }  // no space in between
        if (nonAdjacentDelimiter == null) { nonAdjacentDelimiter = " "; } // a space in between        
        
        if (tcHM.containsKey(strCategory)) {
            TreeSet<TagCategory.Pair> pairSet = tcHM.get(strCategory).getTaggedText(sbText.toString(), true);
            
            // iterate through all tagged text
            // iterate in descending order - to delete extracted text from sbText more easily
            Iterator<TagCategory.Pair> itr = pairSet.descendingIterator();
            while (itr.hasNext()) {
            	TagCategory.Pair pair = itr.next();
            	if (pair.getTaggedText().getText().length() > 0) {            	
                    if (strTaggedText != null) {
                        if (pair.getTaggedText().getEndTagEndPos() == lastStartTagStartPos) {
                            strTaggedText = pair.getTaggedText().getText() + adjacentDelimiter + strTaggedText; // adjacent tagged text
                        }
                        else {
                            strTaggedText = pair.getTaggedText().getText() + nonAdjacentDelimiter + strTaggedText; // non-adjacent tagged text
                        }                    }
	            else
                        strTaggedText = pair.getTaggedText().getText();
                }
                logger.debug(strCategory + "/" + pair.getTag() + ": " + strTaggedText);
                
                if (removeExtractedText) {
                    // delete extracted tagged text
                    sbText.delete(pair.getTaggedText().getStartTagStartPos(), 
                            pair.getTaggedText().getEndTagEndPos());
                }                
                lastStartTagStartPos = pair.getTaggedText().getStartTagStartPos();  // record start pos of last tagged text found
            }
        }

        return strTaggedText;
    }    
        
    private String getTaggedTextByCategory (String strCategory, StringBuilder sbText, boolean removeExtractedText) {
        return getTaggedTextByCategory(strCategory, sbText, removeExtractedText, null, null);
    }     
    
    private String getTaggedTextByCategory (String strCategory, StringBuilder sbText) {
        return getTaggedTextByCategory(strCategory, sbText, true, null, null);
    }    
        
    private String getTaggedTextByCategory (String strCategory, String strText) {
        String strTaggedText = null;
        
        if (tcHM.containsKey(strCategory)) {
            TreeSet<TagCategory.Pair> pairSet = tcHM.get(strCategory).getTaggedText(strText);
            if (!pairSet.isEmpty()) {
                TagCategory.Pair pair = pairSet.first();
                strTaggedText = pair.getTaggedText().getText();
                logger.debug(strCategory + "/" + pair.getTag() + ": " + strTaggedText);
            }
        }

        return strTaggedText;
    }
        
    
    private int findTagByCategory (String strCategory, String strText) {
        int pos = -1;
        
       if (tcHM.containsKey(strCategory)) {
            TreeSet<TagCategory.Pair> pairSet = tcHM.get(strCategory).getTaggedText(strText.toString(), true);
            if (!pairSet.isEmpty()) {
                TagCategory.Pair pair = pairSet.first();
                pos = pair.getTaggedText().getStartPos();
           }
       }
        
        return pos;
    }   

    
    private String removeNotes (String strText) {   // not used, notice mode text removed in xsl
        // note: (?s) - for multiline matching
        strText = strText.replaceAll("(?s)__NOTE_CMD_BEGIN__.*?__NOTE_CMD_END__", "");
        strText = strText.replace("(?s)__NOTE_CMD_BEGIN__.*?$", ""); // last note, until end of text
        strText = strText.replaceAll("__NOTE_CMD_(BEGIN|END)__", ""); // just making sure nothing's left
        return strText;
    }
    
    
    private String replaceReservedCharMarkers(String s) {
        s = s.replace(OPEN_SQRBRACKET_MARKER, "[");
        s = s.replace(CLOSE_SQRBRACKET_MARKER, "]");
        return s;
    }    

    
    private Properties props = new Properties();
    private DecimalFormat df = (DecimalFormat) NumberFormat.getNumberInstance(Locale.US);
    private HashMap<String,TagCategory> tcHM = new HashMap<String,TagCategory>();
    private DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
    private TransformerFactory transformerFactory = TransformerFactory.newInstance();
    private DocumentBuilder docBuilder = null;
    private Transformer transformer = null;
    private XPathFactory xpf = XPathFactory.newInstance();
    private XPath xp = xpf.newXPath();
    private TagSpecialHandler tagSpecialHandler = null;

    private static final String defaultContentType = "text/xml";
    private static final String defaultContentEncoding = "UTF-8";
    
    private final String LINEBREAK = "<br/>";        
    private static final String OPEN_SQRBRACKET_MARKER = "__OPEN_SQR_BRACKET__";
    private static final String CLOSE_SQRBRACKET_MARKER = "__CLOSE_SQR_BRACKET__";    

    private static final String DEFAULT_USER = "BATCH";
    private static final String DEFAULT_PASSWORD = "BATCH";
    private static final String DEFAULT_DESTINATION = "destinationURL";
    private static final String DEFAULT_PROPS_FILE_NAME = "SPHWebExport";
    
    private static final String loggerName = FormattedWebExportServlet.class.getName();
    private static final Logger logger = Logger.getLogger(loggerName);
}
