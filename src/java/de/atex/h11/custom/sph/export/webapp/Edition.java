/*
 * File:    Newspaper.java
 *
 * Copyright (c) 2012,  Atex Media Command GmbH
 *                      Kurhessenstrasse 13
 *                      64546 Moerfelden-Walldorf
 *                      Germany
 *
 * Audit:
 * 20140417 jpm in getEditions(), return all editions, not just top-level editions
 * v01.00  31-may-2012  st  Initial version.
 * v00.00  24-apr-2012  st  Created.
 */

package de.atex.h11.custom.sph.export.webapp;

import java.io.IOException;
import java.io.OutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Vector;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;
import com.unisys.media.cr.adapter.ncm.model.data.datasource.NCMDataSource;
import com.unisys.media.ncm.cfg.model.values.UserHermesCfgValueClient;
import com.unisys.media.cr.adapter.ncm.common.data.NCMEditionIdentificator;
import com.unisys.media.cr.adapter.ncm.common.data.values.NCMEditionBuildProperties;
import com.unisys.media.cr.adapter.ncm.model.data.values.NCMEditionValueClient;
import com.unisys.media.ncm.cfg.common.data.values.LevelValue;
import com.unisys.media.ncm.cfg.common.data.values.EditionValue;
import com.unisys.media.extension.common.serialize.xml.XMLSerializeWriter;
import com.unisys.media.extension.common.serialize.xml.XMLSerializeWriterException;

/**
 *
 * @author tstuehler
 */
public class Edition {
    
    public Edition (NCMDataSource ds) throws ParserConfigurationException {
        this.ds = ds;
        this.docBuilder = docBuilderFactory.newDocumentBuilder();
    }
    
    
    public String[] getEditions (String strLevel) {
        UserHermesCfgValueClient cfgVC = ds.getUserHermesCfg();
        LevelValue levelV = cfgVC.findLevelByName(strLevel);
        return getEditions(levelV.getId());
    }
    

    public String[] getEditions (byte[] level) {
        String[] strEditions = null;
        UserHermesCfgValueClient cfgVC = ds.getUserHermesCfg();
        EditionValue[] edtVs = cfgVC.getEditionsByLevelId(level, false);
        if (edtVs != null) {
            strEditions = new String[edtVs.length];
            for (int i = 0; i < edtVs.length; i++) {
                // 20140417 include child editions, not just top-most editions
                // WAS if (edtVs[i] == null || edtVs[i].getMasterEditionId() > 0) continue;
                if (edtVs[i] == null) continue;
                strEditions[i] = edtVs[i].getName();
            }
        }
        return strEditions;
    }
    
    
    public Document getDocument (String strLevel, String strName, int pubDate, String strTextConvert) 
            throws UnsupportedEncodingException, IOException, 
                   XMLSerializeWriterException, SAXException {
        UserHermesCfgValueClient cfgVC = ds.getUserHermesCfg();
        LevelValue levelV = cfgVC.findLevelByName(strLevel);
        int editionId = cfgVC.getEditionByName(levelV.getId(), strName).getEditionId();
        return getDocument(levelV.getId(), (short) editionId, pubDate, strTextConvert);
    }
    
    
    public Document getDocument (byte[] level, short edtId, int pubDate, String strTextConvert) 
            throws UnsupportedEncodingException, IOException, 
                   XMLSerializeWriterException, SAXException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(8*1024*1024);
        write(level, edtId, pubDate, strTextConvert, out);
        byte[] bytes = out.toByteArray();
        out.close();
        return docBuilder.parse(new ByteArrayInputStream(bytes));
    }
    
    
    public void write (String strLevel, String strName, int pubDate, String strTextConvert, OutputStream out) 
            throws UnsupportedEncodingException, IOException, 
                   XMLSerializeWriterException {
        UserHermesCfgValueClient cfgVC = ds.getUserHermesCfg();
        LevelValue levelV = cfgVC.findLevelByName(strLevel);
        int editionId = cfgVC.getEditionByName(levelV.getId(), strName).getEditionId();
        write(levelV.getId(), (short) editionId, pubDate, strTextConvert, out);
    }
    
    
    public void write (byte[] level, short edtId, int pubDate, String strTextConvert, OutputStream out)
            throws UnsupportedEncodingException, IOException, 
                   XMLSerializeWriterException {
        NCMEditionIdentificator pk = new NCMEditionIdentificator(level, pubDate, (short) 0, edtId);
        NCMEditionBuildProperties props = new NCMEditionBuildProperties();
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
        NCMEditionValueClient npVC = (NCMEditionValueClient) ds.getNode(pk, props);
        XMLSerializeWriter w = new XMLSerializeWriter(out);
        w.writeObject(npVC, props);
        w.close();
    }
            
    private DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
    private DocumentBuilder docBuilder = null;
    private NCMDataSource ds = null;    
}
