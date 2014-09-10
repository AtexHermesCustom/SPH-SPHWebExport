/*
 * File:    AbstractExportBean.java
 *
 * Copyright (c) 2012,  Atex Media Command GmbH
 *                      Kurhessenstrasse 13
 *                      64546 Moerfelden-Walldorf
 *                      Germany
 *
 * Audit:
 * v01.00  31-may-2012  st  Initial version.
 * v00.00  24-may-2012  st  Created.
 */

package de.atex.h11.custom.sph.export.webapp;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Comparator;
import java.util.Properties;
import java.text.SimpleDateFormat;
import javax.faces.bean.ManagedProperty;
import javax.faces.context.FacesContext;
import javax.faces.application.FacesMessage;
import javax.faces.event.ValueChangeListener;
import org.apache.log4j.Logger;
import com.unisys.media.cr.adapter.ncm.model.data.datasource.NCMDataSource;

/**
 *
 * @author tstuehler
 */
public abstract class AbstractExportBean implements Serializable, ValueChangeListener {
    
    @ManagedProperty("#{applicationBean}")
    protected ApplicationBean applicationBean;

    public void setApplicationBean (ApplicationBean bean) {
        this.applicationBean = bean;
    }

    public ApplicationBean getApplicationBean () {
        return this.applicationBean;
    }
    
    @ManagedProperty("#{userBean}")
    protected UserBean userBean;

    public void setUserBean (UserBean bean) {
        this.userBean = bean;
    }

    public UserBean getUserBean () {
        return this.userBean;
    }
    
    protected Properties getProperties () throws IOException {
        Properties props = new Properties();

        String strJBossHomeDir = System.getProperty("jboss.server.home.dir");
        String strPropsFile = strJBossHomeDir + File.separator + "conf"
                                + File.separator + "SPHWebExport.properties";
        File propsFile = new File(strPropsFile);
        try {
            props.load(new FileInputStream(propsFile));
        } catch (FileNotFoundException fnf) {
            logger.warn(propsFile.getCanonicalPath(), fnf);
        }

        return props;
    }
    
    protected NCMDataSource getDataSource () throws Exception {
        return (NCMDataSource) DataSource.newInstance(userBean.getUserName().toUpperCase(), userBean.getPassword());
    }
    
    protected void setMessage (String msg) {
        FacesContext facesContext = FacesContext.getCurrentInstance();
        FacesMessage facesMessage = new FacesMessage(FacesMessage.SEVERITY_INFO, msg, null);
        facesContext.addMessage(null, facesMessage);
    }
    
    protected class StringComparator implements Comparator<String> {
        @Override
        public int compare(String s1, String s2) {
            if (s1 == null) return -1;
            if (s2 == null) return 1;
            return s1.compareTo(s2);
        }
    }
    
    protected void streamCopy (InputStream in, OutputStream out)
            throws IOException {
        byte[] buf = new byte[8192];
        int bytesRead;
        do {
            bytesRead = in.read(buf);
            if (bytesRead > 0)
                out.write(buf, 0, bytesRead);
        } while (bytesRead >= 0);

    }    
    
    protected SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
    protected SimpleDateFormat sdf2 = new SimpleDateFormat("yyyyMMdd'T'HHmmss");

    private static final Logger logger = Logger.getLogger(AbstractExportBean.class.getName());    
}
