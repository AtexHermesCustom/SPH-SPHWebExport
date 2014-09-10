/*
 * File:    FormattedWebExportServlet.java
 *
 * Copyright (c) 2011,  Atex Media Command GmbH
 *                      Kurhessenstrasse 13
 *                      64546 Moerfelden-Walldorf
 *                      Germany
 *
 * Audit:
 * v01.00  26-apr-2012  st  Initial version.
 * v00.00  02-nov-2011  st  Created.
 */

package de.atex.h11.custom.sph.export.webapp;

import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Properties;
import org.jboss.util.property.Property;
import com.unisys.media.cr.adapter.ncm.model.data.datasource.NCMDataSource;
import com.unisys.media.cr.adapter.ncm.common.data.pk.NCMObjectPK;
import com.unisys.media.cr.adapter.ncm.common.data.types.NCMObjectNodeType;
import com.unisys.media.cr.adapter.ncm.common.data.values.NCMObjectBuildProperties;
import com.unisys.media.cr.adapter.ncm.model.data.values.NCMObjectValueClient;

/**
 *
 * @author tstuehler
 */
public class BasicWebExportServlet extends HttpServlet {
   
    /** 
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code> methods.
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
        if (strObjId == null) {
            strObjId = request.getParameter("ncm-obj-id");
            if (strObjId != null && strNodeType == null)
                strNodeType = "ncm-object";
        }

        if (strUser == null && strPasswd == null) {
            // TODO: check if we got a http auth header !!!
            strUser = "BATCH";
            strPasswd = "BATCH";
        }

        log(request.getParameterMap().toString());  // For debugging only!

        if (!strNodeType.equals("ncm-object")) {
            throw new IllegalArgumentException("Provided object is of wrong type!");
        }

        if ((strUser == null || strPasswd == null) && strSessionId == null)
            throw new IllegalArgumentException("User credentials required!");

        try {
            Properties props = getProperties();
                    
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
            
            StoryPackage spExp = new StoryPackage(ds);
            File file = new File(props.getProperty("export.basic.dir", "/tmp"), sa[0] + ".xml");
            String strtextConvert = props.getProperty("text.convert");
            spExp.export(objPK, file, strtextConvert);

            out.println("<html>");
            out.println("<head>");
            out.println("<title>BasicWebExportServlet</title>");
            out.println("</head>");
            out.println("<body>");
            out.println("<h1>" + "Object " + strObjId
                    + " has been exported to " + file + "." + "</h1>");
            out.println("</body>");
            out.println("</html>");

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
     * Handles the HTTP <code>GET</code> method.
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
     * Handles the HTTP <code>POST</code> method.
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
     * @return a String containing servlet description
     */
    public String getServletInfo() {
        return "Basic Web Export";
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
