/*
 * File:    Newspaper.java
 *
 * Copyright (c) 2012,  Atex Media Command GmbH
 *                      Kurhessenstrasse 13
 *                      64546 Moerfelden-Walldorf
 *                      Germany
 *
 * Audit:
 * v01.00  20-apr-2012  st  Initial version.
 * v00.00  20-apr-2012  st  Created.
 */

package de.atex.h11.custom.sph.export.webapp;

import java.io.IOException;
import java.io.OutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Vector;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;
import com.unisys.media.cr.adapter.ncm.model.data.datasource.NCMDataSource;
import com.unisys.media.ncm.cfg.model.values.UserHermesCfgValueClient;
import com.unisys.media.cr.adapter.ncm.common.data.NCMNewspaperIdentificator;
import com.unisys.media.cr.adapter.ncm.common.data.values.NCMNewspaperBuildProperties;
import com.unisys.media.cr.adapter.ncm.model.data.values.NCMNewspaperValueClient;
import com.unisys.media.ncm.cfg.common.data.values.LevelValue;
import com.unisys.media.extension.common.serialize.xml.XMLSerializeWriter;
import com.unisys.media.extension.common.serialize.xml.XMLSerializeWriterException;
import com.unisys.media.ncm.cfg.common.data.values.LevelTreeValue;

/**
 *
 * @author tstuehler
 */
public class Newspaper {
    
    public Newspaper (NCMDataSource ds) throws ParserConfigurationException {
        this.ds = ds;
        this.docBuilder = docBuilderFactory.newDocumentBuilder();
    }
    
    public String[] getNewspapers () {
        String[] strNewspapers = null;
        UserHermesCfgValueClient cfgVC = ds.getUserHermesCfg();
        LevelTreeValue rootLTV = cfgVC.getAllLevels();
        byte[] levelId = rootLTV.getId();
        String strLevelName = rootLTV.getName();
        System.out.println(strLevelName);
        List<LevelTreeValue> childLevels = rootLTV.getChildLevelsList();
        Vector<String> npV = new Vector();
        for (LevelTreeValue ltv : childLevels) {
            if (ltv == null) continue;
            npV.add(ltv.getName());
        }
        if (!npV.isEmpty()) {
            strNewspapers = new String[npV.size()];
            npV.toArray(strNewspapers);
        }
        return strNewspapers;
    }
    
    public Document getDocument (String strLevel, int pubDate, String strTextConvert) 
            throws UnsupportedEncodingException, IOException, 
                   XMLSerializeWriterException, SAXException {
        UserHermesCfgValueClient cfgVC = ds.getUserHermesCfg();
        LevelValue levelV = cfgVC.findLevelByName(strLevel);
        return getDocument(levelV.getId(), pubDate, strTextConvert);
    }
    
    
    public Document getDocument (byte[] level, int pubDate, String strTextConvert) 
            throws UnsupportedEncodingException, IOException, 
                   XMLSerializeWriterException, SAXException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(8*1024*1024);
        write(level, pubDate, strTextConvert, out);
        byte[] bytes = out.toByteArray();
        out.close();
        return docBuilder.parse(new ByteArrayInputStream(bytes));
    }
    
    
    public void write (String strLevel, int pubDate, String strTextConvert, OutputStream out) 
            throws UnsupportedEncodingException, IOException, 
                   XMLSerializeWriterException {
        UserHermesCfgValueClient cfgVC = ds.getUserHermesCfg();
        LevelValue levelV = cfgVC.findLevelByName(strLevel);
        write(levelV.getId(), pubDate, strTextConvert, out);
    }
    
    
    public void write (byte[] level, int pubDate, String strTextConvert, OutputStream out)
            throws UnsupportedEncodingException, IOException, 
                   XMLSerializeWriterException {
        NCMNewspaperIdentificator pk = new NCMNewspaperIdentificator(level, pubDate);
        NCMNewspaperBuildProperties props = new NCMNewspaperBuildProperties();
        props.setIncludeEditions(true);
        props.setIncludePhysPages(true);
        props.setIncludeLogPages(true);
        props.setIncludeObjects(true);
        props.setIncludeIPTC(true);
        props.setIncludeObjContent(true);
        props.setIncludeMetadataGroups(new Vector());
        props.setXhtmlNestedAsXml(true);
        props.setNeutralNestedAsXml(true);
        if (strTextConvert != null && (strTextConvert.equals("Neutral")
                || strTextConvert.equals("Xhtml")
                || strTextConvert.equals("Icml")
                || strTextConvert.equals("FlatText")
                || strTextConvert.equals("NewsRoom")
                || strTextConvert.equals("InCopy"))) {
            // InCopy, NewsRoom, FlatText, Icml, Xhtml, Neutral
            props.setIncludeConvertTo(strTextConvert);
        }        
        NCMNewspaperValueClient npVC = (NCMNewspaperValueClient) ds.getNode(pk, props);
        XMLSerializeWriter w = new XMLSerializeWriter(out);
        w.writeObject(npVC, props);
        w.close();
    }
            
    private DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
    private DocumentBuilder docBuilder = null;
    private NCMDataSource ds = null;
}
