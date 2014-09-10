<?xml version="1.0" encoding="UTF-8"?>

<!--
    Document   : ncm-graphics-filter.xsl
    Created on : 29. Mai 2012, 12:16
    Author     : tstuehler
    Description:
        Purpose of transformation follows.
-->

<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:fn="http://www.w3.org/2005/xpath-functions"
    xmlns:xdt="http://www.w3.org/2005/xpath-datatypes"
    xmlns:err="http://www.w3.org/2005/xqt-errors"
    exclude-result-prefixes="xsl xs xdt err fn">
        
    <xsl:output method="xml" indent="no"/>

    <xsl:template match="@*|node()|text()" priority="0.1">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="node()|@*" mode="ignore" priority="1">
        <xsl:apply-templates select="child::*" mode="ignore"/>
    </xsl:template>

    <xsl:template match="text()" mode="ignore" priority="1"/>

    <xsl:template match="ncm-object[ncm-type-property/object-type/@id=9]">
        <xsl:apply-templates select="child::*" mode="ignore"/>
    </xsl:template>

</xsl:stylesheet>
