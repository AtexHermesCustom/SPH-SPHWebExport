/*
 * File:    UserBean.java
 *
 * Copyright (c) 2012,  Atex Media Command GmbH
 *                      Kurhessenstrasse 13
 *                      64546 Moerfelden-Walldorf
 *                      Germany
 *
 * Audit:
 * v01.00  31-may-2012  st  Initial version.
 * v00.00  31-may-2012  st  Created.
 */

package de.atex.h11.custom.sph.export.webapp;

import java.io.IOException;
import java.io.Serializable;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.KeyGenerator;
import javax.crypto.SealedObject;
import javax.crypto.BadPaddingException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.SessionScoped;
import org.apache.log4j.Logger;

/**
 *
 * @author tstuehler
 */
@ManagedBean
@SessionScoped
public class UserBean implements Serializable {

    /**
     * Creates a new instance of UserBean
     */
    public UserBean() {
        try {
            this.secretKey = KeyGenerator.getInstance("DES").generateKey();
        } catch (Exception e) {
            logger.error("", e);
            throw new RuntimeException(e);
        }
    }
    
    public void setUserName (String strUserName) {
        this.strUserName = strUserName;
    }
    
    public String getUserName () {
        return this.strUserName;
    }
    
    public void setPassword (String strPassword)
            throws IOException, NoSuchAlgorithmException, 
                   InvalidKeySpecException, NoSuchPaddingException, 
                   InvalidKeyException, IllegalBlockSizeException {
        this.sealedPassword = encrypt(strPassword);
        strPassword = null;
    }

    public String getPassword ()
            throws IOException, InvalidKeySpecException,  
                   NoSuchAlgorithmException, NoSuchPaddingException, 
                   InvalidKeyException, IllegalBlockSizeException,
                   BadPaddingException, ClassNotFoundException {
        return decrypt(this.sealedPassword);
    }
    
    private SealedObject encrypt (Serializable object) 
            throws IOException, NoSuchAlgorithmException, 
                   InvalidKeySpecException, NoSuchPaddingException, 
                   InvalidKeyException, IllegalBlockSizeException {
        Cipher cipher = Cipher.getInstance("DES");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        return new SealedObject(object, cipher);
    }

    private String decrypt (SealedObject sealedObject)
            throws IOException, InvalidKeySpecException,  
                   NoSuchAlgorithmException, NoSuchPaddingException, 
                   InvalidKeyException, IllegalBlockSizeException,
                   BadPaddingException, ClassNotFoundException {
        String algorithmName = sealedObject.getAlgorithm();
        Cipher cipher = Cipher.getInstance(algorithmName);
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        return (String) sealedObject.getObject(cipher);
    }
    
    private String strUserName = null;
    private SealedObject sealedPassword = null;
    private SecretKey secretKey = null;

    private static final Logger logger = Logger.getLogger(UserBean.class.getName());
}
