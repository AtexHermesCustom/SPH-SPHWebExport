/*
 * File:    ManualExportBean.java
 *
 * Copyright (c) 2012,  Atex Media Command GmbH
 *                      Kurhessenstrasse 13
 *                      64546 Moerfelden-Walldorf
 *                      Germany
 *
 * Audit:
 * 20140417 - enable the page range fields when publication is selected
 *            remove the check based on edition selection
 * v01.00  04-jun-2012  st  Initial version.
 * v00.00  04-may-2012  st  Created.
 */

package de.atex.h11.custom.sph.export.webapp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;
import java.util.HashMap;
import java.util.Properties;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.transform.stream.StreamResult;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ViewScoped;
import javax.faces.context.FacesContext;
import javax.faces.event.AbortProcessingException;
import javax.faces.event.ValueChangeEvent;
import javax.servlet.http.HttpSession;
import javax.annotation.PostConstruct;
import org.w3c.dom.Document;
import org.apache.log4j.Logger;
import com.unisys.media.cr.adapter.ncm.model.data.datasource.NCMDataSource;

/**
 *
 * @author tstuehler
 */
@ManagedBean
@ViewScoped
public class ManualExportBean extends AbstractExportBean {

    /**
     * Creates a new instance of ManualExportBean
     */
    public ManualExportBean() {}
    
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
                    edtsMap.put("", "N/A");
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
                edition = "N/A";
            }
            
            String strStylesheet = null;
            
            strStylesheet = props.getProperty("pageFilter");
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

            strStylesheet = props.getProperty("graphicsFilter");
            if (strStylesheet != null && strStylesheet.length() > 0) {
                graphicsFilterStylesheet = new File(strStylesheet);
                if (!graphicsFilterStylesheet.exists()) {
                    throw new RuntimeException("Stylesheet " + graphicsFilterStylesheet.getPath() + ": does not exist.");
                } else if (!graphicsFilterStylesheet.isFile()) {
                    throw new RuntimeException("Stylesheet " + graphicsFilterStylesheet.getPath() + ": is not a file.");
                }
                if (!graphicsFilterStylesheet.canRead()) {
                    throw new RuntimeException("Stylesheet " + graphicsFilterStylesheet.getPath() + ": is not readable.");
                }
            }           
            else {
                throw new RuntimeException("Graphics Filter Stylesheet not configured.");
            }           
            
            if (props.containsKey("web.checkbox.disabled")) {
                bWebCheckboxDisabled = props.getProperty("web.checkbox.disabled").equalsIgnoreCase("true");
            }
            if (props.containsKey("mktg.checkbox.disabled")) {
                bMktgCheckboxDisabled = props.getProperty("mktg.checkbox.disabled").equalsIgnoreCase("true");
            }
            
        } catch (Exception e) {
            logger.error("", e);
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
                return "ManualExport";
            }
            String strUser = userBean.getUserName();
            Properties props = getProperties();
            String strFileName = publication + "_" 
                    + ((edition != null && !edition.trim().isEmpty() && !edition.equals("N/A")) ? (edition + "_") : "") 
                    + sdf.format(publishingDate) + "_" + strUser + "_" + (new Date()).getTime() + ".xml";
            File fileForWeb = null;
            File fileForMktg = null;
            // export for web
            if (bExportForWeb) {
                if (props.containsKey("export.dir")) {
                    fileForWeb = new File(props.getProperty("export.dir"), strFileName);
                } else {
                    File tmpFile = File.createTempFile("tmp", ".xml");
                    File tmpDir = tmpFile.getParentFile();
                    tmpFile.delete();
                    fileForWeb = new File(tmpDir, strFileName);
                }         
                logger.debug("Export file for Web is " + fileForWeb.getCanonicalPath());                
            }
            // export for marketing
            if (bExportForMktg) {
                if (props.containsKey("export.mktg.dir")) {
                    fileForMktg = new File(props.getProperty("export.mktg.dir"), strFileName);
                } else {
                    File tmpFile = File.createTempFile("mktg", ".xml");
                    File tmpDir = tmpFile.getParentFile();
                    tmpFile.delete();
                    fileForMktg = new File(tmpDir, strFileName);
                }              
                logger.debug("Export file for Marketing is " + fileForMktg.getCanonicalPath());
            }
            String strTextConvert = props.getProperty("text.convert"); // converter format
            ds = getDataSource();
            logger.debug("Got a NCMDataSource.");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!bExportGraphic || fromPage != null) {
                logger.debug("Pre-filtering required.");
                Source source = null;
                StreamResult result = new StreamResult(out);
                if (edition != null && !edition.trim().isEmpty() && !edition.equals("N/A")) {
                    logger.debug("Exporting edition: " + publication + "/" + edition);
                    Edition edt = new Edition(ds);
                    source = new DOMSource(edt.getDocument(publication, edition, Integer.parseInt(sdf.format(publishingDate)), strTextConvert));
                } else {
                    logger.debug("Exporting publication: " + publication);
                    Newspaper np = new Newspaper(ds);
                    source = new DOMSource(np.getDocument(publication, Integer.parseInt(sdf.format(publishingDate)), strTextConvert));
                }
                if (fromPage != null) {
                    logger.debug("Running page filter: " + fromPage.toString() + " - " + (toPage != null ? toPage.toString() : fromPage.toString()));
                    Transformer t = transformerFactory.newTransformer(new StreamSource(pageFilterStylesheet));
                    t.setParameter("pageRangeLowerBound", fromPage);
                    t.setParameter("pageRangeUpperBound", toPage != null ? toPage : fromPage);
                    if (bExportGraphic)
                        t.transform(source, result);
                    else {
                        // needs further processing, so save it in a document
                        DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
                        Document doc = docBuilder.newDocument();
                        DOMResult intermediateResult = new DOMResult(doc);
                        t.transform(source, intermediateResult);
                        source = new DOMSource(doc);
                    }
                }
                if (!bExportGraphic) {
                    logger.debug("Running graphics filter");
                    Transformer t = transformerFactory.newTransformer(new StreamSource(graphicsFilterStylesheet));
                    t.transform(source, result);
                }
            } else {
                logger.debug("No filtering required.");
                if (edition != null && !edition.trim().isEmpty() && !edition.equals("N/A")) {
                    logger.debug("Exporting edition: " + publication + "/" + edition);
                    Edition edt = new Edition(ds);
                    edt.write(publication, edition, Integer.parseInt(sdf.format(publishingDate)), strTextConvert, out);
                } else {
                    logger.debug("Exporting publication: " + publication);
                    Newspaper np = new Newspaper(ds);
                    np.write(publication, Integer.parseInt(sdf.format(publishingDate)), strTextConvert, out);
                }
            }
            byte[] outBytes = out.toByteArray();
            out.close();            
            if (bExportForWeb) {
                streamCopy(new ByteArrayInputStream(outBytes), new FileOutputStream(fileForWeb));
                logger.info("Exported for Web. File: " + fileForWeb.getCanonicalPath());
            }
            if (bExportForMktg) {
                streamCopy(new ByteArrayInputStream(outBytes), new FileOutputStream(fileForMktg));
                logger.info("Exported for Marketing. File: " + fileForMktg.getCanonicalPath());
            }
            
            applicationBean.addToExportList(strUser, sdf.format(publishingDate), 
                    publication, (edition != null && !edition.trim().isEmpty() && !edition.equals("N/A")) ? edition : "-", 
                    fromPage != null ? fromPage + "-" + (toPage != null ? toPage : fromPage) : "-");
        } catch (Exception e) {
            Throwable t = e.getCause();
            while (t != null && !(t instanceof javax.ejb.FinderException)) t = t.getCause();
            if (t != null && t instanceof javax.ejb.FinderException) {
                logger.info("Selection (" + publication 
                        + ((edition != null && !edition.trim().isEmpty() && !edition.equals("N/A")) ? "," + edition : "")
                        + "," + sdf.format(publishingDate) + ") did not return a result.");
                setMessage("Selection did not return a result.");
                return null;
            } else {
                logger.error("", e);
                throw new RuntimeException(e);
            }
        } finally {
            if (ds != null) ds.logout();
        }
        
        return "ManualExport";
    }

    public String clearButtonAction () {
        if (!this.publications.isEmpty())
            this.publication = this.publications.values().toArray(
                                new String[this.publications.size()])[0];
        this.edition = "N/A";
        this.publishingDate = new Date();
        this.bExportGraphic = false;
        this.bPageRangeDisabled = true;
        this.fromPage = null;
        this.toPage = null;
        return "ManualExport";
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
    
    public String skedButtonAction () {
        return "SkedExport";
    }

    @Override
    public void processValueChange (ValueChangeEvent ev) throws AbortProcessingException {
        String strCompId = ev.getComponent().getId();
        logger.debug("Component Id: " + strCompId);
        if (strCompId.equals("publicationMenu")) {
            String strNewValue = (String) ev.getNewValue();
            if (strNewValue != null) {
                this.editions = this.editionsMap.get(strNewValue);
                this.edition = "N/A";
                this.bPageRangeDisabled = false;
            }
            else {
                this.bPageRangeDisabled = true;                
            }
            this.fromPage = null;
            this.toPage = null;
        } else if (strCompId.equals("editionMenu")) {
            String strNewValue = (String) ev.getNewValue();
            if (strNewValue == null || strNewValue.equals("N/A") || strNewValue.trim().isEmpty()) {
               // no action required
            }
            this.fromPage = null;
            this.toPage = null;            
        }
    }
    
    public Integer getFromPage () {
        if (this.publication == null || this.publication.equals("N/A") || this.publication.trim().isEmpty() || publications == null) {
            this.fromPage = null;
        }
        return this.fromPage;
    }

    public void setFromPage (Integer fromPage) {
        if (fromPage != null && fromPage.intValue() == 0) {
            this.fromPage = null;
            this.toPage = null;
        } else
            this.fromPage = fromPage;
        if (this.fromPage == null)
            this.toPage = null;
        if (this.toPage != null && this.fromPage != null && toPage.intValue() < this.fromPage.intValue())
            this.toPage = this.fromPage;
    }

    public Integer getToPage() {
        if (this.publication == null || this.publication.equals("N/A") || this.publication.trim().isEmpty() || publications == null) {
            this.toPage = null;
        }
        return this.toPage;
    }

    public void setToPage (Integer toPage) {
        if (fromPage == null || (toPage != null && toPage.intValue() == 0))
            this.toPage = null;
        else
            this.toPage = toPage;
    }
    
    public String getPublication () {
        return publication;
    }

    public void setPublication (String publication) {
        this.publication = publication;
        if (publication == null || publication.equals("N/A") || publication.trim().isEmpty()) {
            this.bPageRangeDisabled = true;
            this.fromPage = null;
            this.toPage = null;
        }        
    }
    
    public Map<String,String> getPublications () {
        return publications;
    }
    
    public void setPublications (Map<String,String> publications) {
        // ignore
    }
    
    public String getEdition () {
        return edition;
    }

    public void setEdition (String edition) {
        this.edition = edition;
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
    
    public boolean getExportGraphic () {
        return this.bExportGraphic;
    }
    
    public void setExportGraphic (boolean bExportGraphic) {
        this.bExportGraphic = bExportGraphic;
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

    public boolean getPageRangeDisabled () {
        if (publication == null || publication.equals("N/A") || publication.trim().isEmpty() || publications == null)
            this.bPageRangeDisabled = true;
        else
            this.bPageRangeDisabled = false;
        return this.bPageRangeDisabled;
    }
    
    public void setPageRangeDisabled (boolean bPageRangeDisabled) {
        // ignore
    }
    
    protected Date publishingDate = new Date();
    protected Integer fromPage = null;
    protected Integer toPage = null;
    protected String publication = null;
    protected String edition = null;
    protected boolean bExportGraphic = false;
    protected boolean bPageRangeDisabled = true;
    protected boolean bExportForWeb = true;
    protected boolean bExportForMktg = false;    
    protected boolean bWebCheckboxDisabled = false;
    protected boolean bMktgCheckboxDisabled = false;    
    protected Map<String,String> publications = null;
    protected Map<String,String> editions = null;
    protected Map<String,Map<String,String>> editionsMap = null;
    
    private TransformerFactory transformerFactory = TransformerFactory.newInstance();
    private DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
    
    private File pageFilterStylesheet = null;
    private File graphicsFilterStylesheet = null;

    private static final Logger logger = Logger.getLogger(ManualExportBean.class.getName());
}
