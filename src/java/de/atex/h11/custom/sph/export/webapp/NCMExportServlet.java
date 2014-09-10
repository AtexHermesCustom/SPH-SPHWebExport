/*
 * File:    NCMExportServlet.java
 *
 * Copyright (c) 2012,  Atex Media Command GmbH
 *                      Kurhessenstrasse 13
 *                      64546 Moerfelden-Walldorf
 *                      Germany
 *
 * Audit:
 *         20120625     jpm allow export of objects
 *                          include page range option for newspaper and edition exports
 * v01.00  24-apr-2012  st  Initial version.
 * v00.00  20-apr-2012  st  Created.
 */

package de.atex.h11.custom.sph.export.webapp;

import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.unisys.media.cr.adapter.ncm.model.data.datasource.NCMDataSource;
import com.unisys.media.cr.adapter.ncm.common.data.pk.NCMObjectPK;
import com.unisys.media.cr.adapter.ncm.common.data.values.NCMObjectBuildProperties;
import com.unisys.media.cr.adapter.ncm.model.data.values.NCMObjectValueClient;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Properties;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

/**
 *
 * @author tstuehler
 */
public class NCMExportServlet extends HttpServlet {

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
        response.setContentType("text/xml;charset=UTF-8");
        //PrintWriter out = response.getWriter();
        ServletOutputStream out = response.getOutputStream();
        NCMDataSource ds = null;

        String strUser = request.getParameter("user");
        String strPasswd = request.getParameter("password");
        String strSessionId = request.getParameter("sessionid");
        String strNodeType = request.getParameter("nodetype");

        if (strUser == null && strPasswd == null) {
            // TODO: check if we got a http auth header !!!
            strUser = "BATCH";
            strPasswd = "BATCH";
        }

        if (!strNodeType.equals("obj") && !strNodeType.equals("object") 
                && !strNodeType.equals("sp") && !strNodeType.equals("storypackage") 
                && !strNodeType.equals("newspaper") && !strNodeType.equals("edition")) {
            throw new IllegalArgumentException("Usupported node type!");
        }

        if ((strUser == null || strPasswd == null) && strSessionId == null)
            throw new IllegalArgumentException("User credentials required!");

        try {
            Properties props = getProperties();
            String strTextConvert = props.getProperty("text.convert"); // converter format
            
            File pageFilterStylesheet = null;
            String strStylesheet = props.getProperty("pageFilter");
            if (strStylesheet != null && strStylesheet.length() > 0) {
                pageFilterStylesheet = new File(strStylesheet);
                if (!pageFilterStylesheet.exists()) {
                    throw new RuntimeException("Stylesheet " + pageFilterStylesheet.getPath() + ": does not exist.");
                } else if (!pageFilterStylesheet.isFile()) {
                    throw new RuntimeException("Stylesheet " + pageFilterStylesheet.getPath() + ": is not a file.");
                }
                if (!pageFilterStylesheet.canRead()) {
                    throw new RuntimeException("Stylesheet " + pageFilterStylesheet.getPath() + ": is not readable.");
                }
            }           
            else {
                throw new RuntimeException("Page Filter Stylesheet not configured.");
            }               
            
            if (strSessionId != null)
                ds = (NCMDataSource) DataSource.newInstance(strSessionId);
            else
                ds = (NCMDataSource) DataSource.newInstance(strUser, strPasswd);
            
            if (strNodeType.equals("newspaper")) {
                String strLevel = request.getParameter("level");
                String strPubDate = request.getParameter("pubdate");
                String strPageRange = request.getParameter("pagerange");                
                if (strLevel == null || strPubDate == null)
                    throw new IllegalArgumentException("Required parameter level or pubdate is missing!");
                Newspaper np = new Newspaper(ds);                
                if (strPageRange != null) {     // export specified pages only
                    String[] pageRange = strPageRange.split(":");
                    Integer fromPage, toPage;
                    fromPage = toPage = Integer.parseInt(pageRange[0]);
                    if (pageRange.length > 1) toPage = Integer.parseInt(pageRange[1]);
                    if (fromPage > toPage) toPage = fromPage;
                    Source source = 
                        new DOMSource(np.getDocument(strLevel, Integer.parseInt(strPubDate), strTextConvert));
                    StreamResult result = new StreamResult(out);
                    TransformerFactory transformerFactory = TransformerFactory.newInstance();
                    Transformer t = transformerFactory.newTransformer(new StreamSource(pageFilterStylesheet));
                    t.setParameter("pageRangeLowerBound", fromPage);
                    t.setParameter("pageRangeUpperBound", toPage);
                    t.transform(source, result);
                }
                else {  // export all pages
                    np.write(strLevel, Integer.parseInt(strPubDate), strTextConvert, out);
                }
                
            } else if (strNodeType.equals("edition")) {
                String strLevel = request.getParameter("level");
                String strName = request.getParameter("name");
                String strPubDate = request.getParameter("pubdate");
                String strPageRange = request.getParameter("pagerange");                
                if (strLevel == null || strName == null || strPubDate == null)
                    throw new IllegalArgumentException("Required parameter level, name or pubdate is missing!");
                Edition edt = new Edition(ds);                
                if (strPageRange != null) {     // export specified pages only
                    String[] pageRange = strPageRange.split(":");
                    Integer fromPage, toPage;
                    fromPage = toPage = Integer.parseInt(pageRange[0]);
                    if (pageRange.length > 1) toPage = Integer.parseInt(pageRange[1]);
                    if (fromPage > toPage) toPage = fromPage;
                    Source source = 
                        new DOMSource(edt.getDocument(strLevel, strName, Integer.parseInt(strPubDate), strTextConvert));
                    StreamResult result = new StreamResult(out);
                    TransformerFactory transformerFactory = TransformerFactory.newInstance();
                    Transformer t = transformerFactory.newTransformer(new StreamSource(pageFilterStylesheet));
                    t.setParameter("pageRangeLowerBound", fromPage);
                    t.setParameter("pageRangeUpperBound", toPage);
                    t.transform(source, result);                    
                }
                else {  // export all pages
                    edt.write(strLevel, strName, Integer.parseInt(strPubDate), strTextConvert, out);              
                }
                
            } else if (strNodeType.equals("sp") || strNodeType.equals("storypackage")) {
                String strObjId = request.getParameter("id");
                if (strObjId == null)
                    throw new IllegalArgumentException("Required parameter id is missing!");
                String[] sa = strObjId.split(":");
                NCMObjectPK spPK = null;
                if (sa.length > 1)
                    spPK = new NCMObjectPK(Integer.parseInt(sa[0]),
                        Integer.parseInt(sa[1]), NCMObjectPK.LAST_VERSION, 
                        NCMObjectPK.ACTIVE);
                else
                    spPK = new NCMObjectPK(Integer.parseInt(sa[0]));
                StoryPackage sp = new StoryPackage(ds);
                sp.write(spPK, strTextConvert, out);
                
            } else if (strNodeType.equals("obj") || strNodeType.equals("object")) {
                String strObjId = request.getParameter("id");
                if (strObjId == null)
                    throw new IllegalArgumentException("Required parameter id is missing!");
                String[] sa = strObjId.split(":");
                NCMObjectPK objPK = null, spPK = null;
                if (sa.length > 1)
                    objPK = new NCMObjectPK(Integer.parseInt(sa[0]),
                        Integer.parseInt(sa[1]), NCMObjectPK.LAST_VERSION, 
                        NCMObjectPK.ACTIVE);
                else
                    objPK = new NCMObjectPK(Integer.parseInt(sa[0]));
                // get parent package
                NCMObjectBuildProperties objProps = new NCMObjectBuildProperties();
                NCMObjectValueClient currentObj = (NCMObjectValueClient) ds.getNode(objPK, objProps);
                objProps.setIncludeObjContent(true);
                int spId = currentObj.getSpId();
                if (spId > 0) {
                    spPK = new NCMObjectPK(spId);  // package object id
                } else {
                    response.sendError(response.SC_BAD_REQUEST, "Object not part of a package.");
                    return;
                }
                StoryPackage sp = new StoryPackage(ds);
                sp.write(spPK, strTextConvert, out);            
                
            } else {
                throw new IllegalArgumentException("Unsupported object type!");            
            }
            
        } catch (Exception e) {
            log("", e);
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
        return "NCM Export";
    }// </editor-fold>

    private Properties getProperties () throws IOException {
        Properties props = new Properties();

        String strJBossHomeDir = System.getProperty("jboss.server.home.dir");
        String strPropsFile = strJBossHomeDir + File.separator + "conf"
                                + File.separator + "SPHWebExport.properties";
        File propsFile = new File(strPropsFile);
        try {
            props.load(new FileInputStream(propsFile));
        } catch (FileNotFoundException fnf) {
            log("", fnf);
        }

        return props;
    }
}
