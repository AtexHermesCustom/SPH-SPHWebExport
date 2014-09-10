/*
 * File:    LoginBean.java
 *
 * Copyright (c) 2012,  Atex Media Command GmbH
 *                      Kurhessenstrasse 13
 *                      64546 Moerfelden-Walldorf
 *                      Germany
 *
 * Audit:
 * v01.00  01-jun-2012  st  Initial version.
 * v00.00  04-may-2012  st  Created.
 */

package de.atex.h11.custom.sph.export.webapp;

import java.io.File;
import java.util.Properties;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ViewScoped;
import javax.faces.event.AbortProcessingException;
import javax.faces.event.ValueChangeEvent;
import org.apache.log4j.Logger;
import com.unisys.media.cr.adapter.ncm.model.data.datasource.NCMDataSource;
import com.unisys.media.ncm.cfg.model.values.UserHermesCfgValueClient;
import com.unisys.media.ncm.cfg.common.data.values.UserValue;

/**
 *
 * @author tstuehler
 */
@ManagedBean
@ViewScoped
public class LoginBean extends AbstractExportBean {

    /**
     * Creates a new instance of LoginBean
     */
    public LoginBean() {}
    
    @Override
    public void processValueChange (ValueChangeEvent ev) throws AbortProcessingException {
        String strCompId = ev.getComponent().getId();
        logger.debug("Component Id: " + strCompId + " has changed.");
    }
    
    public String submitButtonAction () {
        if (user == null || user.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            setMessage("Permission denied.");
            return null;
        }
        
        NCMDataSource ds = null;
        try {
            Properties props = getProperties();
            ds = (NCMDataSource) DataSource.newInstance(user.toUpperCase(), password);
            UserHermesCfgValueClient cfgVC = ds.getUserHermesCfg();
            UserValue uv = cfgVC.getUserDataById(cfgVC.getUserId());
            String strOffice = uv.getOffice();
            String strOfficesProp = props.getProperty("webExportUserOffices");
            if (strOfficesProp != null && !strOfficesProp.trim().isEmpty()) {
                boolean bHasValidOffice = false;
                String[] offices = strOfficesProp.split(",");
                for (int i = 0; strOffice != null && i < offices.length; i++) {
                    if (offices[i].trim().equals(strOffice.trim()))
                        bHasValidOffice = true;
                }
                if (!bHasValidOffice) {
                    logger.info("User " + user.toUpperCase() 
                            + " is not authorized to use this application.");
                    setMessage("Permission denied.");
                    return null;
                }
            }
        } catch (Exception e) {
            // check for login failure
            // javax.security.auth.login.LoginException: Login Failure: all modules ignored
            Throwable t = e.getCause();
            while (t != null && !(t instanceof javax.security.auth.login.LoginException)) t = t.getCause();
            if (t != null && t instanceof javax.security.auth.login.LoginException) {
                logger.info("Either user " + user.toUpperCase() + " is invalid or provided an invalid password.");
                setMessage("Invalid user or password.");
                return null;
            } else {
                logger.error("", e);
                throw new RuntimeException(e);
            }
        } finally {
            if (ds != null) ds.logout();
        }

        try {
            userBean.setUserName(user);
            userBean.setPassword(password);
            user = null;
            password = null;
        } catch (Exception e) {
            logger.error("", e);
            throw new RuntimeException(e);
        }
        
        logger.info("Authenticated user " + userBean.getUserName() + ".");
        
        return "ManualExport";
    }

    public String getPassword () {
        return password;
    }

    public void setPassword (String password) {
        this.password = password;
    }

    public String getUser () {
        return user;
    }

    public void setUser (String user) {
        this.user = user;
    }

    
    protected String user = null;
    protected String password = null;
    
    private static final Logger logger = Logger.getLogger(LoginBean.class.getName());
}
