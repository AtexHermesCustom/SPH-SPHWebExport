/*
 * File:    ApplicationBean.java
 *
 * Copyright (c) 2012,  Atex Media Command GmbH
 *                      Kurhessenstrasse 13
 *                      64546 Moerfelden-Walldorf
 *                      Germany
 *
 * Audit:
 * v01.00  04-jun-2012  st  Initial version.
 * v00.00  25-may-2012  st  Created.
 */

package de.atex.h11.custom.sph.export.webapp;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.IOException;
import java.io.Serializable;
import java.io.BufferedReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TreeSet;
import java.util.Iterator;
import java.util.Comparator;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ApplicationScoped;
import org.apache.log4j.Logger;

/**
 *
 * @author tstuehler
 */
@ManagedBean
@ApplicationScoped
public class ApplicationBean implements Serializable {

    /**
     * Creates a new instance of ApplicationBean
     */
    public ApplicationBean() {
        try {
            this.skedList = getReportExecutionList(new File(SKEDEXPLISTFILE));
            this.exportList = getReportExecutionList(new File(WEBEXPLISTFILE));
        } catch (Exception e) {
            logger.error("", e);
            throw new RuntimeException(e);
        }
    }
    
    public ReportExecution[] getSkedReportExecutions () {
        if (tableItemLimit > 0 && this.skedList != null && this.skedList.size() > tableItemLimit) {
            ReportExecution re = null;
            Iterator<ReportExecution> iter = this.skedList.iterator();
            for (int i = 0; i <= tableItemLimit && iter.hasNext(); i++) re = iter.next();
            return this.skedList.headSet(re).toArray(new ReportExecution[tableItemLimit]);
        } else
            return this.skedList == null ? null : this.skedList.toArray(new ReportExecution[this.skedList.size()]);
    }
    
    public ReportExecution[] getExportReportExecutions () {
        if (tableItemLimit > 0 && this.exportList != null && this.exportList.size() > tableItemLimit) {
            ReportExecution re = null;
            Iterator<ReportExecution> iter = this.exportList.iterator();
            for (int i = 0; i <= tableItemLimit && iter.hasNext(); i++) re = iter.next();
            return this.exportList.headSet(re).toArray(new ReportExecution[tableItemLimit]);
        } else
            return this.exportList == null ? null : this.exportList.toArray(new ReportExecution[this.exportList.size()]);
    }

    public synchronized void addToSkedList (String userName, String pubDate, 
                               String publication, String edition) 
            throws IOException {
        if (this.skedList == null)
            this.skedList = new TreeSet(new ReportExecutionComparator());
        ReportExecution re = new ReportExecution(userName, pubDate, publication,
                                        edition, "-", sdf.format(new Date()));
        this.skedList.add(re);
        updateReportExecutions(new File(SKEDEXPLISTFILE), re);
    }
    
    public synchronized void addToExportList (String userName, String pubDate, 
                                 String publication, String edition,
                                 String pageRange) 
            throws IOException {
        if (this.exportList == null)
            this.exportList = new TreeSet(new ReportExecutionComparator());
        ReportExecution re = new ReportExecution(userName, pubDate, publication, 
                                edition, pageRange, sdf.format(new Date()));
        this.exportList.add(re);
        updateReportExecutions(new File(WEBEXPLISTFILE), re);
    }
    
    private synchronized TreeSet<ReportExecution> getReportExecutionList (File file) throws IOException {
        TreeSet<ReportExecution> list = new TreeSet(new ReportExecutionComparator());
        File listFile = null;
        if (file.isAbsolute())
            listFile = file;
        else {
            String strJBossHomeDir = System.getProperty("jboss.server.home.dir");
            String strJBossDataDir = strJBossHomeDir + File.separator + "data";
            String strListFile = strJBossDataDir + File.separator + file.getName();
            listFile = new File(strListFile);
            if (!listFile.exists()) {
                listFile.createNewFile();
                logger.info(listFile.getCanonicalPath() + ": created.");
            }
        }
        
        logger.info("Loading report executions from " + listFile.getCanonicalPath() + ".");
        
        BufferedReader in = new BufferedReader(new FileReader(listFile));
        String strLine = null;
        while ((strLine = in.readLine()) != null) {
            String[] sa = strLine.split(",");
            if (sa.length != 6) continue;
            list.add(new ReportExecution(sa[0], sa[1], sa[2], sa[3], sa[4], sa[5]));
        }
        in.close();
        
        return list.isEmpty() ? null : list;
    }

    private synchronized void updateReportExecutions (File file, ReportExecution re) 
            throws IOException {
        File listFile = null;
        if (file.isAbsolute())
            listFile = file;
        else {
            String strJBossHomeDir = System.getProperty("jboss.server.home.dir");
            String strJBossDataDir = strJBossHomeDir + File.separator + "data";
            String strListFile = strJBossDataDir + File.separator + file.getName();
            listFile = new File(strListFile);
            if (!listFile.exists())
                listFile.createNewFile();
        }
        PrintWriter out = new PrintWriter(new FileWriter(listFile, true));
        String s = re.getUserName() + "," + re.getPubDate() + "," 
                 + re.getPublication() + "," + re.getEdition() + "," 
                 + re.getPageRange() + "," + re.getTimestamp() + "\n";
        out.append(s);
        out.close();
        
        logger.info(listFile.getCanonicalPath() + ": updated.");
    }
    
    private class ReportExecutionComparator implements Comparator<ReportExecution> {
        @Override
        public int compare (ReportExecution o1, ReportExecution o2) {
            String ts1 = o1.getTimestamp();
            String ts2 = o2.getTimestamp();
            return ts1.compareTo(ts2) * -1;
        }
    }
    
    public class ReportExecution {
        public ReportExecution (String userName, String pubDate, 
                                   String publication, String edition,
                                   String pageRange, String timestamp) {
            this.userName = userName;
            this.pubDate = pubDate;
            this.publication = publication;
            this.edition = edition;
            this.pageRange = pageRange;
            this.timestamp = timestamp;
        }
        
        public String getUserName () {
            return this.userName;
        }
        
        public String getPubDate () {
            return this.pubDate;
        }
        
        public String getPublication () {
            return this.publication;
        }
        
        public String getEdition () {
            return this.edition;
        }
        
        public String getPageRange () {
            return this.pageRange;
        }
        
        public String getTimestamp () {
            return this.timestamp;
        }
        
        private String userName = null;
        private String pubDate = null;
        private String publication = null;
        private String edition = null;
        private String pageRange = null;
        private String timestamp = null;
    }

    protected int tableItemLimit = -1;

    private TreeSet<ReportExecution> skedList = null;
    private TreeSet<ReportExecution> exportList = null;
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd'T'HHmmss");
    
    private static final String SKEDEXPLISTFILE = "sked-exports.lst";
    private static final String WEBEXPLISTFILE = "web-exports.lst";
    
    private static final Logger logger = Logger.getLogger(ApplicationBean.class.getName());    
}
