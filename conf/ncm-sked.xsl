<?xml version="1.0" encoding="UTF-8"?>

<!--
    Document   : ncm-sked.xsl
    Created on : 25. Mai 2012, 17:06
    Author     : tstuehler
    Description:
        Purpose of transformation follows.
    Revision 
        20130919 jpm use different xpaths for getting metadata, based on the pub
                     - using local:metadataNamingMode local function
        20120920 aki select metadata from correct publication metadata group 
        20120913 aki adding of OBJ_PRIORITY in attribute priority
        20120718 jpm check edition parameter against the ncm-physical-page's edition (not the ncm-object's edition)
            e.g. object may have been created in FIRST edition, but laid out on SUPP1 edition - use the SUPP1 edition
-->

<xsl:stylesheet version="2.0" 
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:fn="http://www.w3.org/2005/xpath-functions"
    xmlns:xdt="http://www.w3.org/2005/xpath-datatypes"
    xmlns:err="http://www.w3.org/2005/xqt-errors"
    xmlns:local="http://www.atex.de/local"
    exclude-result-prefixes="xsl xs xdt err fn local">
    
    <xsl:output method="xml" indent="yes"/>

    <xsl:param name="editions" select="''"/>
    
    <xsl:template match="/">
        <xsl:element name="nitf">
            <xsl:element name="definition">
                <xsl:attribute name="type">SKED</xsl:attribute>
            </xsl:element>
            <xsl:variable name="physPages" 
                select="//ncm-physical-page[(string-length($editions) eq 0 or 
                     contains($editions, concat(' ', ./edition/@name, ' ')))]"/>
            <xsl:for-each-group 
                select="local:cat-sort($physPages//ncm-object[ncm-type-property/object-type/@id=17])" 
                group-by="obj_id">
                <xsl:variable name="spId" select="current-grouping-key()" as="xs:integer"/>
                <xsl:apply-templates select="(//ncm-object[ncm-type-property/object-type/@id=17 and obj_id=$spId])[1]"/>
            </xsl:for-each-group>
        </xsl:element>
    </xsl:template>

    <xsl:template match="ncm-object[ncm-type-property/object-type/@id=17]">
        <xsl:variable name="spId" select="./obj_id"/>
        <xsl:variable name="pub" select="../../edition/newspaper-level/level/@name"/>
        <xsl:element name="story">
            <xsl:attribute name="id_string">
                <xsl:value-of select="replace(replace(./name, ' ', '-'), '_', '-')"/>
            </xsl:attribute>
            <xsl:attribute name="id">
                <xsl:value-of select="./obj_id"/>
            </xsl:attribute>
            <xsl:attribute name="cat-code">
                <xsl:choose>
                    <xsl:when test="local:metadataNamingMode($pub)='1'">
                        <xsl:value-of select="(./extra-properties/OBJECT/WEBCAT1, ./extra-properties/OBJECT/WEBCAT2)[1]"/>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:value-of select="(./extra-properties/*[name()=$pub]/OBJ_WEBCAT1, ./extra-properties/*[name()=$pub]/OBJ_WEBCAT2)[1]"/>
                    </xsl:otherwise>
                </xsl:choose>                
            </xsl:attribute>
            <xsl:attribute name="page">
                <xsl:value-of select="(//ncm-physical-page[descendant::ncm-object/obj_id=$spId])[1]/seq_number"/>
            </xsl:attribute>
            <xsl:attribute name="priority">
                <xsl:choose>
                    <xsl:when test="local:metadataNamingMode($pub)='1'">
                        <xsl:value-of select="./extra-properties/OBJECT/PRIORITY"/>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:value-of select="./extra-properties/*[name()=$pub]/OBJ_PRIORITY"/>
                    </xsl:otherwise>
                </xsl:choose>                
            </xsl:attribute>
        </xsl:element>
    </xsl:template>
    
    <xsl:function name="local:cat-sort">
        <xsl:param name="in"/>
	<xsl:variable name="pub" select="$in/../../edition/newspaper-level/level/@name"/>
        <xsl:choose>
            <xsl:when test="local:metadataNamingMode($pub)='1'">
                <xsl:perform-sort select="$in">
                    <xsl:sort select="(./extra-properties/OBJECT/WEBCAT1, ./extra-properties/OBJECT/WEBCAT2)[1]" order="ascending"/>
                </xsl:perform-sort>
            </xsl:when>
            <xsl:otherwise>
                <xsl:perform-sort select="$in">
                    <xsl:sort select="(./extra-properties/*[name()=$pub]/OBJ_WEBCAT1, ./extra-properties/*[name()=$pub]/OBJ_WEBCAT2)[1]" order="ascending"/>
                </xsl:perform-sort>
            </xsl:otherwise>
        </xsl:choose>        
    </xsl:function>
    
    <xsl:function name="local:metadataNamingMode">
        <xsl:param name="pub"/>
        <xsl:choose>
            <xsl:when test="$pub='ST' or $pub='MY' or $pub='TABL'">
                <xsl:value-of select="1"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="0"/>
            </xsl:otherwise>            
        </xsl:choose>
    </xsl:function>    

</xsl:stylesheet>
