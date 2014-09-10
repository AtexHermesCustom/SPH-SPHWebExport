/*
 * File:    SkedExportBean.java
 *
 * Copyright (c) 2012,  Atex Media Command GmbH
 *                      Kurhessenstrasse 13
 *                      64546 Moerfelden-Walldorf
 *                      Germany
 *
 * Audit:
 * v01.00  04-jun-2012  st  Initial version.
 * v00.00  24-may-2012  st  Created.
 */

package de.atex.h11.custom.sph.export.webapp;

import java.io.File;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;
import java.util.HashMap;
import java.util.Properties;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.transform.stream.StreamResult;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ViewScoped;
import javax.faces.context.FacesContext;
import javax.faces.event.AbortProcessingException;
import javax.faces.event.ValueChangeEvent;
import javax.servlet.http.HttpSession;
import javax.annotation.PostConstruct;
import org.apache.log4j.Logger;
import com.unisys.media.cr.adapter.ncm.model.data.datasource.NCMDataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;

/**
 *
 * @author tstuehler
 */
@ManagedBean
@ViewScoped
public class SkedExportBean extends AbstractExportBean {

    /**
     * Creates a new instance of SkedExportBean
     */
    public SkedExportBean() {}
    
    @PostConstruct
    public void init () {
        NCMDataSource ds = null;
        
        try {
            Properties props = getProperties();
            applicationBean.tableItemLimit = Integer.parseInt(props.getProperty("tableItemLimit", "-1"));

            publications = new TreeMap(new StringComparator());
            editionsMap = new HashMap();
            ds = getDataSource();
            Newspaper np = new Newspaper(ds);
            String[] nps = np.getNewspapers();
            if (nps != null) {
                Edition edt = new Edition(ds);
                for (int i = 0; i < nps.length; i++) {
                    publications.put(nps[i], nps[i]);
                    TreeMap<String,String> edtsMap = new TreeMap(new StringComparator());
                    String[] edts = edt.getEditions(nps[i]);
                    if (edts != null) {
                        for (int j = 0; j < edts.length; j++) {
                            if (edts[j] == null || edts[j].equals("*****")) continue;
                            else edtsMap.put(edts[j], edts[j]);
                        }
                        editionsMap.put(nps[i], edtsMap);
                    }
                }
                if (!publications.isEmpty())
                    publication = publications.values().toArray(new String[publications.size()])[0];
            }
            
            String strStylesheet = props.getProperty("skedTransform");
            if (strStylesheet != null && strStylesheet.length() > 0) {
                skedStylesheet = new File(strStylesheet);
                if (!skedStylesheet.exists()) {
                    throw new RuntimeException("Stylesheet " + skedStylesheet.getPath() + ": does not exist.");
                } else if (!skedStylesheet.isFile()) {
                    throw new RuntimeException("Stylesheet " + skedStylesheet.getPath() + ": is not a file.");
                }
                if (!skedStylesheet.canRead()) {
                    throw new RuntimeException("Stylesheet " + skedStylesheet.getPath() + ": is not readable.");
                }
            }           
            else {
                throw new RuntimeException("Sked Transform Stylesheet not configured.");
            }
            
            if (props.containsKey("web.checkbox.disabled")) {
                bWebCheckboxDisabled = props.getProperty("web.checkbox.disabled").equalsIgnoreCase("true");
            }
            if (props.containsKey("mktg.checkbox.disabled")) {
                bMktgCheckboxDisabled = props.getProperty("mktg.checkbox.disabled").equalsIgnoreCase("true");
            }
            
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (ds != null) ds.logout();
        }
    }
    
    public String submitButtonAction () {
        NCMDataSource ds = null;
        
        try {
            // make sure an Export checkbox is ticked
            if (!bExportForWeb && !bExportForMktg) {
                return "SkedExport";
            }            
            String strUser = userBean.getUserName();
            Properties props = getProperties();
            String strFileName = null;
            if (selectedEditions == null || selectedEditions.length == 0) {
                // no editions selected
                strFileName = publication + "_" + sdf.format(publishingDate) + "_sked.xml";
            } else {
                // has selected editions
                StringBuilder edtSB = new StringBuilder();
                for (int i = 0; i < selectedEditions.length; i++) {
                    edtSB.append("_");
                    edtSB.append(selectedEditions[i]);
                }
                strFileName = publication + "_" + sdf.format(publishingDate) 
                            + edtSB.toString() + "_sked.xml";
            }
            File fileForWeb = null;
            File fileForMktg = null;
            if (bExportForWeb) {
                if (props.containsKey("sked.dir")) {
                    fileForWeb = new File(props.getProperty("sked.dir"), strFileName);
                } else {
                    File tmpFile = File.createTempFile("tmp", ".xml");
                    File tmpDir = tmpFile.getParentFile();
                    tmpFile.delete();
                    fileForWeb = new File(tmpDir, strFileName);
                }
                logger.debug("Sked Export file for Web is " + fileForWeb.getCanonicalPath());  
            }
            if (bExportForMktg) {
                if (props.containsKey("sked.mktg.dir")) {
                    fileForMktg = new File(props.getProperty("sked.mktg.dir"), strFileName);
                } else {
                    File tmpFile = File.createTempFile("mktg", ".xml");
                    File tmpDir = tmpFile.getParentFile();
                    tmpFile.delete();
                    fileForMktg = new File(tmpDir, strFileName);
                }
                logger.debug("Sked Export file for Marketing is " + fileForMktg.getCanonicalPath());
            }            
            ds = getDataSource();

            DOMSource source = null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            StreamResult result = new StreamResult(out);
            Transformer t = transformerFactory.newTransformer(new StreamSource(skedStylesheet));
            if (selectedEditions != null && selectedEditions.length > 0) {
                StringBuilder edtSB = new StringBuilder();
                for (int i = 0; i < selectedEditions.length; i++) {
                    edtSB.append(" ");
                    edtSB.append(selectedEditions[i]);
                }
                if (edtSB.length() > 0)
                    edtSB.append(" ");
                t.setParameter("editions", edtSB.toString());
                logger.debug("Exporting editions:" + edtSB.toString());
            }
            Newspaper np = new Newspaper(ds);
            source = new DOMSource(np.getDocument(publication, Integer.parseInt(sdf.format(publishingDate)), null));
            t.transform(source, result);
            byte[] outBytes = out.toByteArray();
            out.close();   
            if (bExportForWeb) {
                streamCopy(new ByteArrayInputStream(outBytes), new FileOutputStream(fileForWeb));
                logger.info("Exported Sked for Web. File: " + fileForWeb.getCanonicalPath());
            }
            if (bExportForMktg) {
                streamCopy(new ByteArrayInputStream(outBytes), new FileOutputStream(fileForMktg));
                logger.info("Exported Sked for Marketing. File: " + fileForMktg.getCanonicalPath());
            }            

            String strSelectedEditions = null;
            if (selectedEditions != null && selectedEditions.length > 0) {
                StringBuilder edtSB = new StringBuilder();
                for (int i = 0; i < selectedEditions.length; i++) {
                    if (edtSB.length() > 0) edtSB.append("/");
                    edtSB.append(selectedEditions[i]);
                }
                strSelectedEditions = edtSB.toString();
            }
            applicationBean.addToSkedList(strUser, sdf.format(publishingDate), 
                    publication, strSelectedEditions != null ? strSelectedEditions : "-");
        } catch (Exception e) {
            Throwable t = e.getCause();
            while (t != null && !(t instanceof javax.ejb.FinderException)) t = t.getCause();
            if (t != null && t instanceof javax.ejb.FinderException) {
                logger.info("Selection (" + publication 
                            + "," + sdf.format(publishingDate) 
                            + ") did not return a result.");
                setMessage("Selection did not return a result.");
                return null;
            } else {
                logger.error("", e);
                throw new RuntimeException(e);
            }
        } finally {
            if (ds != null) ds.logout();
        }
        
        return "SkedExport";
    }

    public String clearButtonAction () {
        if (!this.publications.isEmpty())
            this.publication = this.publications.values().toArray(new String[this.publications.size()])[0];
        this.selectedEditions = null;
        this.publishingDate = new Date();
        return "SkedExport";
    }
    
    public String backButtonAction () {
        // destroy session context
        HttpSession session = (HttpSession) FacesContext.getCurrentInstance().getExternalContext().getSession(false);
        if (session != null) {
            logger.debug("Invalidating session " + session.getId() 
                       + " for user " + userBean.getUserName() + ".");
            session.invalidate();
        }
        return "Login";
    }
    
    public String objectButtonAction () {
        return "ManualExport";
    }
    
    @Override
    public void processValueChange (ValueChangeEvent ev) throws AbortProcessingException {
        String strCompId = ev.getComponent().getId();
        logger.debug("Component Id: " + strCompId);
        if (strCompId.equals("skedPublicationMenu")) {
            String strNewValue = (String) ev.getNewValue();
            if (strNewValue != null) {
                logger.debug("New Publication: " + strNewValue);
                editions = editionsMap.get(strNewValue);
                selectedEditions = null;
            }
        }
    }
    
    public String getPublication () {
        return publication;
    }

    public void setPublication (String publication) {
        this.publication = publication;
    }
    
    public Map<String,String> getPublications () {
        return publications;
    }
    
   
    public void setPublications (Map<String,String> publications) {
        // ignore
    }
    
    public Map<String,String> getEditions () {
        editions = editionsMap.get(publication);
        return editions;
    }

    public void setEditions (Map<String,String> editions) {
        // Ignore
    }
    
    public Date getPublishingDate() {
        return publishingDate;
    }

    public void setPublishingDate(Date publishingDate) {
        this.publishingDate = publishingDate;
    }
    
    public String[] getSelectedEditions () {
        return this.selectedEditions;
    }
    
    public void setSelectedEditions (String[] sa) {
        this.selectedEditions = sa;
    }
    
    public boolean getExportForWeb() {
        return this.bExportForWeb;
    }
    
    public void setExportForWeb(boolean bExportForWeb) {
        this.bExportForWeb = bExportForWeb;
    }    
    
    public boolean getExportForMktg() {
        return this.bExportForMktg;
    }
    
    public void setExportForMktg (boolean bExportForMktg) {
        this.bExportForMktg = bExportForMktg;
    }        
    
    public boolean getWebCheckboxDisabled() {
        return this.bWebCheckboxDisabled;
    }
    
    public void setWebCheckboxDisabled (boolean bWebCheckboxDisabled) {
        this.bWebCheckboxDisabled = bWebCheckboxDisabled;
    }     
    
    public boolean getMktgCheckboxDisabled() {
        return this.bMktgCheckboxDisabled;
    }
    
    public void setMktgCheckboxDisabled (boolean bMktgCheckboxDisabled) {
        this.bMktgCheckboxDisabled = bMktgCheckboxDisabled;
    }    
   
    protected Date publishingDate = new Date();
    protected Integer fromPage = null;
    protected Integer toPage = null;
    protected String publication = null;
    protected Map<String,String> publications = null;
    protected Map<String,String> editions = null;
    protected Map<String,Map<String,String>> editionsMap = null;
    String[] selectedEditions = null;
    protected boolean bExportForWeb = true;
    protected boolean bExportForMktg = false;     
    protected boolean bWebCheckboxDisabled = false;
    protected boolean bMktgCheckboxDisabled = false;
    
    private File skedStylesheet = null;
    
    private TransformerFactory transformerFactory = TransformerFactory.newInstance();

    private static final Logger logger = Logger.getLogger(SkedExportBean.class.getName());
}
