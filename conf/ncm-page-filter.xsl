<?xml version="1.0" encoding="UTF-8"?>

<!--
    Document   : ncm-page-filter.xsl
    Created on : 29. Mai 2012, 15:48
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

    <xsl:param name="pageRangeLowerBound" select="1" as="xs:integer"/>
    <xsl:param name="pageRangeUpperBound" select="99999" as="xs:integer"/>

    <xsl:template match="@*|node()|text()" priority="0.1">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="node()|@*" mode="ignore" priority='1'>
        <xsl:apply-templates select="child::*" mode="ignore"/>
    </xsl:template>

    <xsl:template match="text()" mode="ignore"/>

    <xsl:template match="ncm-physical-page[xs:integer(seq_number) lt $pageRangeLowerBound or xs:integer(seq_number) gt $pageRangeUpperBound]">
        <xsl:apply-templates select="child::*" mode="ignore"/>
    </xsl:template>

</xsl:stylesheet>
