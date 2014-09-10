/*
 * File:    StoryPackage.java
 *
 * Copyright (c) 2011,  Atex Media Command GmbH
 *                      Kurhessenstrasse 13
 *                      64546 Moerfelden-Walldorf
 *                      Germany
 *
 * Audit:
 * v01.00  20-apr-2012  st  Initial version.
 * v00.00  02-nov-2011  st  Created.
 */

package de.atex.h11.custom.sph.export.webapp;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.FileOutputStream;
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
import com.unisys.media.cr.adapter.ncm.model.data.values.NCMObjectValueClient;
import com.unisys.media.extension.common.serialize.xml.XMLSerializeWriter;
import com.unisys.media.extension.common.serialize.xml.XMLSerializeWriterException;
import com.unisys.media.cr.adapter.ncm.common.data.pk.NCMObjectPK;
import com.unisys.media.cr.adapter.ncm.common.data.values.NCMObjectBuildProperties;
import com.unisys.media.cr.adapter.ncm.common.data.types.NCMObjectNodeType;

/**
 *
 * @author tstuehler
 */
public class StoryPackage {

    public StoryPackage (NCMDataSource ds) throws ParserConfigurationException {
        this.ds = ds;
        this.docBuilder = docBuilderFactory.newDocumentBuilder();
    }
    
    
    public void export (NCMObjectPK objPK, File file)
            throws UnsupportedEncodingException, IOException,
                   XMLSerializeWriterException {
        write(objPK, null, new FileOutputStream(file));
    }

    
    public void export (NCMObjectPK objPK, File file, String strTextConvert)
            throws UnsupportedEncodingException, IOException,
                   XMLSerializeWriterException {
        write(objPK, strTextConvert, new FileOutputStream(file));
    }

    
    public Document getDocument (NCMObjectPK objPK, String strTextConvert) 
            throws UnsupportedEncodingException, IOException, 
                   XMLSerializeWriterException, SAXException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(128*1024);
        write(objPK, strTextConvert, out);
        byte[] bytes = out.toByteArray();
        out.close();
        return docBuilder.parse(new ByteArrayInputStream(bytes));
    }
    
    
    public void write (NCMObjectPK objPK, String strTextConvert, OutputStream out) 
            throws UnsupportedEncodingException, IOException, 
                   XMLSerializeWriterException {
        NCMObjectBuildProperties objProps = new NCMObjectBuildProperties();
        objProps.setIncludeObjContent(true);
        objProps.setIncludeLay(true);
        objProps.setIncludeLayContent(true);
        objProps.setIncludeLayObjContent(true);
        objProps.setIncludeAttachments(true);
        objProps.setIncludeCaption(true);
        objProps.setIncludeCreditBox(true);
        objProps.setIncludeIPTC(true);
        objProps.setIncludeTextPreview(true);
        objProps.setIncludeLinkedObject(true);
        objProps.setIncludeVariants(true);
        objProps.setIncludeSpChild(true);
        objProps.setXhtmlNestedAsXml(true);
        objProps.setNeutralNestedAsXml(true);
        objProps.setIncludeMetadataChild(true);
        objProps.setIncludeMetadataGroups(new Vector());
        if (strTextConvert != null && (strTextConvert.equals("Neutral")
                || strTextConvert.equals("Xhtml")
                || strTextConvert.equals("Icml")
                || strTextConvert.equals("FlatText")
                || strTextConvert.equals("NewsRoom")
                || strTextConvert.equals("InCopy"))) {
            // InCopy, NewsRoom, FlatText, Icml, Xhtml, Neutral
            objProps.setIncludeConvertTo(strTextConvert);
        }
        NCMObjectValueClient objVC = (NCMObjectValueClient) ds.getNode(objPK, objProps);
        if (objVC.getType() != NCMObjectNodeType.OBJ_STORY_PACKAGE)
            throw new IllegalArgumentException("Provided object is not a story package!");
        XMLSerializeWriter w = new XMLSerializeWriter(out);
        w.writeObject(objVC, objProps);
        w.close();
    }

    
    private DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
    private DocumentBuilder docBuilder = null;
    private NCMDataSource ds = null;
}
