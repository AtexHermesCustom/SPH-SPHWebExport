/*
 * DataSource.java
 *
 * Created on 13. M�rz 2009, 18:02
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package de.atex.h11.custom.sph.export.webapp;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.Properties;
import javax.naming.Context;
import com.unisys.media.extension.common.constants.ApplicationConstants;
import com.unisys.media.extension.common.security.UPSUser;
import com.unisys.media.cr.model.data.datasource.IDataSourceClient;
import com.unisys.media.cr.model.data.datasource.DataSourceManager;
import com.unisys.media.cr.adapter.ncm.model.data.datasource.NCMDataSource;

/**
 *
 * @author tstuehler
 */
public class DataSource {
    
    /**
     * Default constructor.
     */
    public DataSource() {}
    
    public static synchronized IDataSourceClient newInstance (String sessionId)
        throws FileNotFoundException, IOException {
        return newInstance("SESSIONID" + sessionId, sessionId);
    }
    
    public static synchronized NCMDataSource newInstance (String user, String passwd)
        throws FileNotFoundException, IOException {
        
        DataSourceManager dsmgr = null;

        try {
            // Get a DataSourceManager instance while running in app server.
            //dsmgr = DataSourceManager.getInstance();
            dsmgr = DataSourceManager.getDefaultContextInstance();
        } catch (Exception e) {
            // Either read the naming properties from a file or set them based 
            // on environment variables.
            Properties jndiProps = new Properties();
            String jndiPropsName = System.getProperty(JNDI_PROPS);
            if (jndiPropsName != null) {
                jndiProps.load(new FileInputStream(jndiPropsName));
            } else {
                try {
                    // try to load them from a file in the working directory
                    jndiProps.load(new FileInputStream(JNDI_PROPS));
                } catch (FileNotFoundException fnfe) {
                    // prepare the props based on defaults and the environment
                    jndiProps.put(Context.INITIAL_CONTEXT_FACTORY, JNP_FACTORY);
                    jndiProps.put(Context.URL_PKG_PREFIXES, JNP_PKGS);
                    String host = null;
                    if ((host = System.getenv("J2EE_IP")) == null)
                        host = "localhost";
                    String port = null;
                    if ((port = System.getenv("J2EE_NAMINGPORT")) == null)
                        port = "1099";      // set to default port
                    String providerUrlStr = "jnp://" + host + ":" + port;
                    jndiProps.put(Context.PROVIDER_URL, providerUrlStr);
                }
            }

            // Get a DataSourceManager instance.
            dsmgr = DataSourceManager.getInstance(jndiProps);
        }
        
        // Login.
        return (NCMDataSource) dsmgr.getDataSource(DATASOURCE,
                new UPSUser(user, passwd), ApplicationConstants.APP_MEDIA_API_ID);
    }
    
    private static final String JNDI_PROPS = "jndi.properties";
    private static final String JNP_FACTORY = 
            "org.jnp.interfaces.NamingContextFactory";
    private static final String JNP_PKGS = "org.jnp.interfaces";

    private static final String DATASOURCE =
            "ejb/103/cr/ncm/default/DataSourceManager";
    
}
