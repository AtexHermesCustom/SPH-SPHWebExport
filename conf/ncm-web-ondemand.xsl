<?xml version="1.0" encoding="UTF-8"?>

<!--
    Document   : ncm-web-ondemand.xsl
    Created on : 18. April 2012, 13:04
    Author     : tstuehler
    Description:
        Purpose of transformation follows.
    Revision History:
        20140909 jpm -add "hyperlink" element    
        20130919 jpm - use different xpaths for getting metadata, based on the pub
                        -using local:metadataNamingMode local function    
                     - strip space for "par" elements
        20130906 jpm -include story package level in export
                     -differentiate between photo and graphic objects by using different xml tag names (photo vs graphic)        
        20130807 jpm save WEB_SUMMARY (obj type=16) content to 'abstract' element
                     save WEB_VIDEOLINK (obj type=11) content to 'video' element
        20130710 jpm added 'premium' element - get value from OBJ_PREMIUM metadata
        20130606 jpm for the object name, no need to replace spaces and underscores with a dash.
                    SPH wants to keep the original name
        20130218 jpm 1. modification of special char handling
                        - to also include non-glyph styles that may be using special char fonts (e.g. EuropeanPi)     
        20130110 jpm add handling for headlines in an Adobe environment    
        20121126 jpm corrections in setting of copyright
                    - if story came from the wires, set to NONSPH
                    - else, use OBJ_COPYRIGHT metadata value (if present)
                    - else, set to SPH as default    
        20121119 jpm if story is taken from the wire (the 'wc' command is present),
                    - set the origin to 'AGENCY'
                    - set the copyright to 'NONSPH'
        20120919 aki select metadata from correct publication metadata group
        20120722 jpm 1. separate teaser and subtitle components of headline    
        20120720 jpm added special char map lookup for glyphs    
        20120719 jpm replace reserved chars [ and ] (used for tags) with markers.
        20120717 jpm 
            1. get 'kicker' content from 'Supertitle' component of headline (if there's 'Maintitle' content)
            non-Supertitle component content go to 'h1'
            2. handle merge copy tags (MC): remove '_MCn' suffix, e.g. CAPTION_MC1 -> CAPTION    
        20120629 jpm when package is not paginated:
            -use package's exp_pubdate for the pubdate
            -use package's first level path for the pub
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
    <xsl:strip-space elements="par"/>    
    
    <xsl:param name="copyright" select="'SPH'"/>
    <xsl:param name="origin" select="'SPH'"/>
    <xsl:param name="author" select="'Editorial Dept'"/>
    <xsl:param name="channel" select="'WEB'"/>
    <xsl:param name="specialCharMap" select="''"/>    

    <xsl:variable name="specialCharMapDoc" select="document($specialCharMap)/lookup"/>    
    
    <xsl:template match="/">
        <xsl:element name="components">
            <xsl:apply-templates select="//ncm-object[ncm-type-property/object-type/@id=17]"/>
        </xsl:element>
    </xsl:template>

    <xsl:template match="ncm-object[ncm-type-property/object-type/@id=17]">
        <xsl:variable name="spId" select="./obj_id"/>
        <xsl:variable name="pub">
            <xsl:choose>
                <xsl:when test="(./edition/newspaper-level/@level-path)[1]">
                    <xsl:value-of select="(./edition/newspaper-level/@level-path)[1]"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:value-of select="tokenize(./level/@path, '/')[1]"/>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <xsl:variable name="textObjs" select="./child-of-a-ncm-sp-object/ncm-object[ncm-type-property/object-type/@id=1 and (channel/@name=$channel or not(string(channel/@name)))]"/>
        <xsl:element name="nitf">
            <xsl:element name="head">
                <xsl:element name="docdata">
                    <xsl:variable name="pubdate">
                        <xsl:choose>
                            <xsl:when test="(.//ncm-layout/pub_date)[1]">
                                <xsl:value-of select="local:convertNcmDate((.//ncm-layout/pub_date)[1])"/>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:value-of select="local:convertNcmDate(./exp_pubdate )"/>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:variable>
                    <xsl:processing-instruction name="file-name" select="concat($pub, '_', $pubdate, '_', replace(./name, ' ', ''), '_', ./obj_id, '.xml')"/>
                    <xsl:element name="doc-id">
                        <xsl:attribute name="id_string">
                            <xsl:value-of select="replace(./name, ' ', '')"/>
                        </xsl:attribute>
                    </xsl:element>
                    <xsl:element name="doc-id">
                        <xsl:attribute name="id">
                            <xsl:value-of select="./obj_id"/>
                        </xsl:attribute>
                    </xsl:element>
                    <xsl:element name="date.release">
                        <xsl:attribute name="norm">
                            <xsl:value-of select="$pubdate"/>
                        </xsl:attribute>
                    </xsl:element>
                    <xsl:element name="definition">
                        <xsl:attribute name="type">STORY</xsl:attribute>
                    </xsl:element>
                    <xsl:element name="story">
                        <xsl:attribute name="author"><xsl:value-of select="./creator/name"/></xsl:attribute>
                    </xsl:element>
                    <xsl:element name="story">
                        <xsl:choose>
                            <xsl:when test="local:metadataNamingMode($pub)='1'">
                                <xsl:attribute name="prodcode"><xsl:value-of select="./extra-properties/OBJECT/PRODCODE"/></xsl:attribute>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:attribute name="prodcode"><xsl:value-of select="./extra-properties/*[name()=$pub]/OBJ_PRODCODE"/></xsl:attribute>
                            </xsl:otherwise>
                        </xsl:choose>                        
                    </xsl:element>
                    <xsl:element name="story">
                        <xsl:attribute name="origin">
                            <xsl:choose>
                                <xsl:when test="$textObjs//content-property[@type='NCMText']/formatted/uupscommand[@type='wc' and @value='1']">
                                    <xsl:value-of select="'AGENCY'"/>
                                </xsl:when>
                                <xsl:otherwise>
                                     <xsl:value-of select="'SPH'"/>
                                </xsl:otherwise>
                            </xsl:choose>                            
                        </xsl:attribute>
                    </xsl:element>
                    <xsl:element name="story">
                        <xsl:attribute name="level">
                            <xsl:value-of select="./level/@path"/>
                        </xsl:attribute>
                    </xsl:element>                    
                </xsl:element>
            </xsl:element>
            <xsl:element name="body">
                <xsl:element name="category">
                    <xsl:attribute name="level">1</xsl:attribute>
                    <xsl:choose>
                        <xsl:when test="local:metadataNamingMode($pub)='1'">
                            <xsl:value-of select="./extra-properties/OBJECT/WEBCAT1"/>
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:value-of select="./extra-properties/*[name()=$pub]/OBJ_WEBCAT1"/>
                        </xsl:otherwise>
                    </xsl:choose>                    
                </xsl:element>
                <xsl:element name="category">
                    <xsl:attribute name="level">2</xsl:attribute>
                    <xsl:choose>
                        <xsl:when test="local:metadataNamingMode($pub)='1'">
                            <xsl:value-of select="./extra-properties/OBJECT/WEBCAT2"/>
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:value-of select="./extra-properties/*[name()=$pub]/OBJ_WEBCAT2"/>
                        </xsl:otherwise>
                    </xsl:choose>                    
                </xsl:element>
                <xsl:element name="category">
                    <xsl:attribute name="level">3</xsl:attribute>
                </xsl:element>
                <xsl:element name="keyword"/>
                <xsl:element name="audio"/>
                <!-- web video link objects -->
                <xsl:apply-templates select="./child-of-a-ncm-sp-object/ncm-object[ncm-type-property/object-type/@id=11 and (channel/@name=$channel or not(string(channel/@name)))]"/>                    
                <!-- web summary objects -->
                <xsl:apply-templates select="./child-of-a-ncm-sp-object/ncm-object[ncm-type-property/object-type/@id=16 and (channel/@name=$channel or not(string(channel/@name)))]"/>                    
                <xsl:element name="fixture"/>
                <xsl:element name="series"/>
                <xsl:element name="urgency">
                    <xsl:attribute name="type">section</xsl:attribute>
                </xsl:element>
                <xsl:element name="urgency">
                    <xsl:attribute name="type">news</xsl:attribute>
                </xsl:element>
                <xsl:element name="topstory"/>
                <xsl:element name="premium">
                    <xsl:choose>
                        <xsl:when test="local:metadataNamingMode($pub)='1'">
                            <xsl:value-of select="./extra-properties/OBJECT/PREMIUM"/>
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:value-of select="./extra-properties/*[name()=$pub]/OBJ_PREMIUM"/>
                        </xsl:otherwise>
                    </xsl:choose>                    
                </xsl:element>                
                <xsl:variable name="copyrightValue">
                    <xsl:choose>
                        <xsl:when test="$textObjs//content-property[@type='NCMText']/formatted/uupscommand[@type='wc' and @value='1']">
                            <xsl:value-of select="'NONSPH'"/>
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:choose>
                                <xsl:when test="local:metadataNamingMode($pub)='1'">
                                    <xsl:value-of select="(./extra-properties/OBJECT/COPYRIGHT, $copyright)[1]"/>
                                </xsl:when>
                                <xsl:otherwise>
                                    <xsl:value-of select="(./extra-properties/*[name()=$pub]/OBJ_COPYRIGHT, $copyright)[1]"/>
                                </xsl:otherwise>
                            </xsl:choose>                            
                        </xsl:otherwise>
                    </xsl:choose>
                </xsl:variable>                
                <xsl:element name="copyright"><xsl:value-of select="$copyrightValue"/></xsl:element>
                <!-- images -->
                <xsl:apply-templates select="./child-of-a-ncm-sp-object/ncm-object[ncm-type-property/object-type/@id!=1 and ncm-type-property/object-type/@id!=2 and ncm-type-property/object-type/@id!=11 and ncm-type-property/object-type/@id!=16 and ncm-type-property/object-type/@id!=17 
                    and (channel/@name=$channel or not(string(channel/@name)))]"/>
                <xsl:element name="person"/>
                <xsl:element name="twitter"/>
                <xsl:element name="title"/>
                <xsl:element name="country"/>
                <xsl:element name="hyperlink">
                    <xsl:value-of select="./extra-properties/PRINT/HYPERLINK"/>
                </xsl:element>               
                <!-- headline objects -->
                <xsl:apply-templates select="./child-of-a-ncm-sp-object/ncm-object[ncm-type-property/object-type/@id=2 and (channel/@name=$channel or not(string(channel/@name)))]"/>
                <!-- text objects -->
                <xsl:apply-templates select="./child-of-a-ncm-sp-object/ncm-object[ncm-type-property/object-type/@id=1 and (channel/@name=$channel or not(string(channel/@name)))]"/>
            </xsl:element>
        </xsl:element>
        <!-- standalone images -->
        <xsl:apply-templates select="./child-of-a-ncm-sp-object/ncm-object[ncm-type-property/object-type/@id=6 and (channel/@name=$channel or not(string(channel/@name)))]" mode="standalone"/>
    </xsl:template>

    <xsl:template match="ncm-object[ncm-type-property/object-type/@id=1]">
        <xsl:element name="content">
            <xsl:choose>
                <xsl:when test="./convert-property[@format='Neutral']/story">
                    <xsl:apply-templates select="./convert-property[@format='Neutral']/story" mode="content"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:text></xsl:text>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:element>
    </xsl:template>
    
    <xsl:template match="ncm-object[ncm-type-property/object-type/@id=2]">
        <xsl:choose>
            <xsl:when test="local:isAdobe(./mediatype)=1"><!-- Adobe environment -->
                <xsl:element name="h1">
                    <xsl:choose>
                        <xsl:when test=".//convert-property[@format='Neutral']/story">
                            <xsl:apply-templates select=".//convert-property[@format='Neutral']/story" mode="content"/>
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:text></xsl:text>
                        </xsl:otherwise>
                    </xsl:choose>
                </xsl:element>            
            </xsl:when>
            <xsl:otherwise><!-- Newsroom environment -->
                <xsl:element name="kick">
                    <xsl:choose>
                        <xsl:when test=".//convert-property[@format='Neutral']/story">
                            <xsl:choose>
                                <xsl:when test=".//convert-property[@format='Neutral']/story/headline/component[@name='Maintitle']/par">
                                    <xsl:apply-templates select=".//convert-property[@format='Neutral']/story/headline/component[@name='Supertitle']" mode="content"/>
                                </xsl:when>
                                <xsl:otherwise>
                                    <xsl:text></xsl:text>
                                </xsl:otherwise>
                            </xsl:choose>
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:text></xsl:text>
                        </xsl:otherwise>
                    </xsl:choose>                
                </xsl:element>
                <xsl:element name="h1">
                    <xsl:choose>
                        <xsl:when test=".//convert-property[@format='Neutral']/story">
                            <xsl:choose>
                                <xsl:when test=".//convert-property[@format='Neutral']/story/headline/component[@name='Maintitle']/par">
                                    <xsl:apply-templates select=".//convert-property[@format='Neutral']/story/headline/component[@name='Maintitle']" mode="content"/>
                                </xsl:when>
                                <xsl:when test=".//convert-property[@format='Neutral']/story/headline/component[@name='Supertitle']/par">
                                    <xsl:apply-templates select=".//convert-property[@format='Neutral']/story/headline/component[@name='Supertitle']" mode="content"/>
                                </xsl:when>
                                <xsl:otherwise>
                                    <xsl:text></xsl:text>
                                </xsl:otherwise>                        
                            </xsl:choose>
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:text></xsl:text>
                        </xsl:otherwise>
                    </xsl:choose>
                </xsl:element>
                <xsl:element name="hteaser">
                    <xsl:choose>
                        <xsl:when test=".//convert-property[@format='Neutral']/story">
                            <xsl:apply-templates select=".//convert-property[@format='Neutral']/story/headline/component[@name='Teaser']" mode="content"/>
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:text></xsl:text>
                        </xsl:otherwise>
                    </xsl:choose>
                </xsl:element>        
                <xsl:element name="hsubtitle">
                    <xsl:choose>
                        <xsl:when test=".//convert-property[@format='Neutral']/story">
                            <xsl:apply-templates select=".//convert-property[@format='Neutral']/story/headline/component[@name='Subtitle']" mode="content"/>
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:text></xsl:text>
                        </xsl:otherwise>
                    </xsl:choose>
                </xsl:element>            
            </xsl:otherwise>
        </xsl:choose>                
    </xsl:template>
    
    <xsl:template match="ncm-object[ncm-type-property/object-type/@id=6 or ncm-type-property/object-type/@id=9]">
        <xsl:variable name="spId" select="./sp_id"/>
        <xsl:variable name="objId" select="./obj_id"/>
        <xsl:variable name="objType" select="./ncm-type-property/object-type/@id"/>
        <xsl:variable name="reference" select=".//ncm-layout/reference"/>
        <xsl:variable name="subreference" select=".//ncm-layout/sub_reference" as="xs:integer"/>
        <xsl:variable name="pub">
            <xsl:choose>
                <xsl:when test="(../../edition/newspaper-level/level/@name)[1]">
                    <xsl:value-of select="(../../edition/newspaper-level/level/@name)[1]"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:value-of select="tokenize(../../level/@path, '/')[1]"/>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <xsl:variable name="pubdate">
            <xsl:choose>
                <xsl:when test="(.//ncm-layout/pub_date)[1]">
                    <xsl:value-of select="local:convertNcmDate((.//ncm-layout/pub_date)[1])"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:value-of select="local:convertNcmDate(../../exp_pubdate )"/>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>          
        <xsl:element name="{if ($objType=9) then 'graphic' else 'photo'}">
            <xsl:attribute name="id">
                <!-- <xsl:value-of select="local:crObjId(.)"/> -->
                <xsl:value-of select="./obj_id"/>
            </xsl:attribute>
            <xsl:processing-instruction name="highres-imagepath" select="./content-property/file-property/original-file/server-path"/>
            <xsl:processing-instruction name="medres-imagepath" select="./content-property/file-property/medium-preview/server-path"/>
            <xsl:processing-instruction name="lowres-imagepath" select="./content-property/file-property/low-preview/server-path"/>
            <xsl:processing-instruction name="variant_of_obj_id" select="./variant_of_obj_id"/>
            <xsl:processing-instruction name="dimension" select="concat(./content-property/image-size/width, ' ', ./content-property/image-size/height)"/>
            <xsl:if test="./content-property/crop-rect">
                <xsl:processing-instruction name="crop-rect" select="concat(./content-property/crop-rect/@bottom, ' ', ./content-property/crop-rect/@left, ' ', ./content-property/crop-rect/@top, ' ', ./content-property/crop-rect/@right)"/>
            </xsl:if>
            <xsl:if test="./content-property/xy-transf">
                <xsl:processing-instruction name="rotate" select="./content-property/xy-transf/@rotate"/>
                <xsl:processing-instruction name="flip-x" select="./content-property/xy-transf/@flip-x"/>
                <xsl:processing-instruction name="flip-y" select="./content-property/xy-transf/@flip-y"/>
            </xsl:if>
            <xsl:element name="{if ($objType=9) then 'graphic_thumbnail' else 'image_thumbnail'}">
                <xsl:value-of select="concat($pub, '_', $pubdate, '_', replace(./name, ' ', ''), '_', ./obj_id, 't.jpg')"/>               
            </xsl:element>
            <xsl:element name="{if ($objType=9) then 'graphic_low' else 'image_low'}">
                <xsl:value-of select="concat($pub, '_', $pubdate, '_', replace(./name, ' ', ''), '_', ./obj_id, '.jpg')"/>              
            </xsl:element>
            <xsl:choose>
                <xsl:when test="../ncm-object[sp_id=$spId and ncm-type-property/object-type/@id=3 and relation_obj_id=$objId and (channel=$channel or not(string(channel/@name)))]">
                    <xsl:apply-templates select="../ncm-object[sp_id=$spId and ncm-type-property/object-type/@id=3 and relation_obj_id=$objId and (channel=$channel or not(string(channel/@name)))]" mode="picture"/>
                </xsl:when>
                <xsl:when test="../ncm-object[sp_id=$spId and ncm-type-property/object-type/@id=3 and layouts-of-a-ncm-object/ncm-layout[reference=$reference and xs:integer(sub_reference) ne 0 and xs:integer(sub_reference) eq $subreference]]">
                    <xsl:apply-templates select="../ncm-object[sp_id=$spId and ncm-type-property/object-type/@id=3 and layouts-of-a-ncm-object/ncm-layout[reference=$reference and xs:integer(sub_reference) ne 0 and xs:integer(sub_reference) eq $subreference]]" mode="picture"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:for-each select="../ncm-object[sp_id=$spId and ncm-type-property/object-type/@id=3]">
                        <xsl:variable name="caption_reference" select=".//ncm-layout/reference"/>
                        <xsl:variable name="caption_subreference" select=".//ncm-layout/sub_reference" as="xs:integer"/>
                        <xsl:choose>
                            <!-- test if we have already a valid photo reference -->
                            <xsl:when test="../ncm-object[sp_id=$spId and (ncm-type-property/object-type/@id=6 or ncm-type-property/object-type/@id=9) and layouts-of-a-ncm-object/ncm-layout[reference=$caption_reference and xs:integer(sub_reference) ne 0 and xs:integer(sub_reference) eq $caption_subreference]]"/>
                            <xsl:otherwise>
                                <xsl:apply-templates select="." mode="picture"/>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:for-each>
                </xsl:otherwise>
            </xsl:choose>
            <!-- comment out the following block since object type=16 is used as WEB_SUMMARY, not as CREDIT
            <xsl:choose>
                <xsl:when test="../ncm-object[sp_id=$spId and ncm-type-property/object-type/@id=16 and relation_obj_id=$objId]">
                    <xsl:apply-templates select="../ncm-object[sp_id=$spId and ncm-type-property/object-type/@id=16 and relation_obj_id=$objId]"  mode="picture"/>
                </xsl:when>
                <xsl:when test="../ncm-layout[reference=$reference and xs:integer(sub_reference) eq $subreference]//ncm-object[sp_id=$spId and ncm-type-property/object-type/@id=16]">
                    <xsl:apply-templates select="../ncm-layout[reference=$reference and xs:integer(sub_reference) eq $subreference]//ncm-object[sp_id=$spId and ncm-type-property/object-type/@id=16 and relation_obj_id=$objId]"  mode="picture"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:for-each select="../ncm-object[sp_id=$spId and ncm-type-property/object-type/@id=16]">
                        <xsl:variable name="credit_reference" select=".//ncm-layout/reference"/>
                        <xsl:variable name="credit_subreference" select=".//ncm-layout/sub_reference" as="xs:integer"/>
                        <xsl:choose>
                            <!-#- test if we have already a valid credit reference -#->
                            <xsl:when test="../ncm-layout[reference=$credit_reference and xs:integer(sub_reference) ne 0 and xs:integer(sub_reference) eq $credit_subreference]//ncm-object[sp_id=$spId and (ncm-type-property/object-type/@id=6 or ncm-type-property/object-type/@id=9)]"/>
                            <xsl:otherwise>
                                <xsl:apply-templates select="." mode="picture"/>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:for-each>
                </xsl:otherwise>
            </xsl:choose>
            -->
        </xsl:element>
    </xsl:template>

    <xsl:template match="ncm-object[ncm-type-property/object-type/@id=14]">
        <xsl:element name="summary">
            <xsl:choose>
                <xsl:when test="./convert-property[@format='Neutral']/story">
                    <xsl:apply-templates select="./convert-property[@format='Neutral']/story" mode="content"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:text></xsl:text>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:element>
    </xsl:template>
    
    <xsl:template match="ncm-object[ncm-type-property/object-type/@id=4]">
        <xsl:element name="header">
            <xsl:choose>
                <xsl:when test="./convert-property[@format='Neutral']/story">
                    <xsl:apply-templates select="./convert-property[@format='Neutral']/story" mode="content"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:text></xsl:text>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:element>
    </xsl:template>
    
    <xsl:template match="ncm-object[ncm-type-property/object-type/@id=11]">
        <xsl:element name="video">
            <xsl:choose>
                <xsl:when test="./convert-property[@format='Neutral']/story">
                    <xsl:apply-templates select="./convert-property[@format='Neutral']/story" mode="content"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:text></xsl:text>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:element>
    </xsl:template>
        
    <xsl:template match="ncm-object[ncm-type-property/object-type/@id=16]">
        <xsl:element name="abstract">
            <xsl:choose>
                <xsl:when test="./convert-property[@format='Neutral']/story">
                    <xsl:apply-templates select="./convert-property[@format='Neutral']/story" mode="content"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:text></xsl:text>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:element>
    </xsl:template>        
    
    <xsl:template match="ncm-object[ncm-type-property/object-type/@id=3]" mode="picture">
        <xsl:element name="caption">
            <xsl:choose>
                <xsl:when test="./convert-property[@format='Neutral']/story">
                    <xsl:apply-templates select="./convert-property[@format='Neutral']/story" mode="content"/>
                </xsl:when>   
                <xsl:otherwise>
                    <xsl:text></xsl:text>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:element>
    </xsl:template>
    
    <!-- comment out the following block since object type=16 is used as WEB_SUMMARY, not as CREDIT
    <xsl:template match="ncm-object[ncm-type-property/object-type/@id=16]" mode="picture">
        <xsl:element name="copyright">
            <xsl:choose>
                <xsl:when test="./convert-property[@format='Neutral']/story">
                    <xsl:apply-templates select="./convert-property[@format='Neutral']/story" mode="content"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:text></xsl:text>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:element>
    </xsl:template>
    -->
    
    <xsl:template match="ncm-object[ncm-type-property/object-type/@id=6 or ncm-type-property/object-type/@id=9]" mode="standalone">
        <xsl:variable name="objId" select="./obj_id"/>
        <xsl:variable name="objType" select="./ncm-type-property/object-type/@id"/>
        <xsl:element name="nitf">
            <xsl:element name="head">
                <xsl:element name="docdata">
                    <xsl:variable name="pub">
                        <xsl:choose>
                            <xsl:when test="(../../edition/newspaper-level/level/@name)[1]">
                                <xsl:value-of select="(../../edition/newspaper-level/level/@name)[1]"/>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:value-of select="tokenize(../../level/@path, '/')[1]"/>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:variable>
                    <xsl:variable name="pubdate">
                        <xsl:choose>
                            <xsl:when test="(.//ncm-layout/pub_date)[1]">
                                <xsl:value-of select="local:convertNcmDate((.//ncm-layout/pub_date)[1])"/>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:value-of select="local:convertNcmDate(../../exp_pubdate )"/>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:variable>                    
                    <xsl:processing-instruction name="file-name" select="concat($pub, '_', $pubdate, '_', replace(./name, ' ', ''), '_', ./obj_id, '.xml')"/>
                    <xsl:element name="doc-id">
                        <xsl:attribute name="id_string">
                            <xsl:value-of select="replace(./name, ' ', '')"/>
                        </xsl:attribute>
                    </xsl:element>
                    <xsl:element name="doc-id">
                        <xsl:attribute name="id">
                            <xsl:value-of select="./obj_id"/>
                        </xsl:attribute>
                    </xsl:element>
                    <xsl:element name="doc-id">
                        <xsl:attribute name="nicaid">
                            <xsl:call-template name="getNicaId">
                                <xsl:with-param name="ncmObject" select="."/>
                            </xsl:call-template>
                        </xsl:attribute>
                    </xsl:element>
                    <xsl:element name="doc-id">
                        <xsl:attribute name="publication">
                            <xsl:value-of select="$pub"/>
                        </xsl:attribute>
                    </xsl:element>
                    <xsl:element name="date.release">
                        <xsl:attribute name="norm">
                            <xsl:value-of select="$pubdate"/>
                        </xsl:attribute>
                    </xsl:element>
                    <xsl:element name="definition">
                        <xsl:value-of select="if ($objType=9) then 'GRAPHIC' else 'IMAGE'"/>
                    </xsl:element>
                </xsl:element>
            </xsl:element>
            <xsl:element name="body">
                <xsl:element name="urgency">
                    <xsl:attribute name="type">section</xsl:attribute>
                </xsl:element>
                <xsl:element name="urgency">
                    <xsl:attribute name="type">news</xsl:attribute>
                </xsl:element>
                <xsl:apply-templates select="."/>
            </xsl:element>
        </xsl:element>
    </xsl:template>
    
    <xsl:template match="story|component" mode="content">
        <!-- loop through all par nodes -->
	<xsl:for-each select=".//par">
            <xsl:variable name="tag" select="local:handleMergeCopy(@name)"/>
            <xsl:apply-templates select="text()|char" mode="content">
                <xsl:with-param name="tag" select="$tag"/>
            </xsl:apply-templates>
            <xsl:if test="position() != last()">
                <xsl:text>&lt;br/&gt;</xsl:text><!-- separate paragraphs with br -->
            </xsl:if>
	</xsl:for-each>
    </xsl:template>
     
    <xsl:template match="char" mode="content">
        <xsl:variable name="tag" 
            select="if (exists(@override-by)) then local:handleMergeCopy(@override-by) else local:handleMergeCopy(@name)"/>
        <xsl:choose>
            <xsl:when test="$tag='note'">
                <!-- print nothing: remove notice mode text -->
            </xsl:when>
            <xsl:otherwise>
                <xsl:variable name="fontId" select="@font-id"/>
                <xsl:variable name="orig" select="text()"/>
                <xsl:variable name="replacement"
                    select="$specialCharMapDoc/map[@tag=$tag and @font-id=$fontId and @orig-text=$orig]"/>
                <xsl:choose>
                    <xsl:when test="$replacement='clear'">
                        <xsl:value-of select="''"/><!-- remove text -->
                    </xsl:when>
                    <xsl:when test="$replacement">
                        <xsl:value-of select="$replacement"/><!-- replacement exists, replace string -->
                    </xsl:when>                    
                    <xsl:otherwise>
                        <xsl:apply-templates select="text()" mode="content"><!-- just output orig string -->
                            <xsl:with-param name="tag" select="$tag"/>                    
                        </xsl:apply-templates>
                        <!-- <xsl:value-of select="concat('[/', $tag, ']')"/> --><!-- close char tag -->
                    </xsl:otherwise>                    
                </xsl:choose>                                       
            </xsl:otherwise>
	</xsl:choose>
    </xsl:template>
    
    <xsl:template match="text()" mode="content">
        <xsl:param name="tag"/>
        <xsl:value-of select="concat('[', $tag, ']')"/><!-- open tag -->        
        <!-- convert some chars, don't normalize space -->
        <!-- replace reserved chars -->
        <!-- replace any newlines with <br/> -->
        <xsl:sequence 
            select="replace(local:replaceReservedChars(local:convertUnicodeChars(.)), '&#x0A;', '&lt;br/&gt;')"/>
    </xsl:template>    
    
    <xsl:template match="text()"/><!-- dont print out -->

    <xsl:template name="getNicaId">
        <xsl:param name="ncmObject"/>
        <xsl:if test="$ncmObject/obj_comment">
            <xsl:analyze-string select="$ncmObject/obj_comment" regex="(&lt;NICA:.*?:.*?&gt;)">
                <xsl:matching-substring>
                    <xsl:value-of select="tokenize(replace(replace(regex-group(1), '&lt;', ''), '&gt;', ''), ':')[3]"/>
                </xsl:matching-substring>
            </xsl:analyze-string>
        </xsl:if>
    </xsl:template>

    <xsl:function name="local:crObjId">
        <xsl:param name="ncmObject"/>
        <xsl:sequence select="concat('object-', $ncmObject/obj_id, '-', $ncmObject/ncm-type-property/object-type/@id)"/>
    </xsl:function>
    
    <xsl:function name="local:convertNcmDate">
        <xsl:param name="ncmDateStr"/>
        <xsl:variable name="dateParts" select="tokenize(replace($ncmDateStr, ',', ''), '\s+')"/>
        <xsl:choose>
            <xsl:when test="string-length($dateParts[2])=1">
                <xsl:sequence select="concat($dateParts[3], local:shortMonthToNum($dateParts[1]), '0', $dateParts[2])"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:sequence select="concat($dateParts[3], local:shortMonthToNum($dateParts[1]), $dateParts[2])"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:function>
    
    <xsl:function name="local:shortMonthToNum">
        <xsl:param name="shortMonthStr"/>
        <xsl:choose>
            <xsl:when test="$shortMonthStr='Jan'">
                <xsl:sequence select="'01'"/>
            </xsl:when>
            <xsl:when test="$shortMonthStr='Feb'">
                <xsl:sequence select="'02'"/>
            </xsl:when>
            <xsl:when test="$shortMonthStr='Mar'">
                <xsl:sequence select="'03'"/>
            </xsl:when>
            <xsl:when test="$shortMonthStr='Apr'">
                <xsl:sequence select="'04'"/>
            </xsl:when>
            <xsl:when test="$shortMonthStr='May'">
                <xsl:sequence select="'05'"/>
            </xsl:when>
            <xsl:when test="$shortMonthStr='Jun'">
                <xsl:sequence select="'06'"/>
            </xsl:when>
            <xsl:when test="$shortMonthStr='Jul'">
                <xsl:sequence select="'07'"/>
            </xsl:when>
            <xsl:when test="$shortMonthStr='Aug'">
                <xsl:sequence select="'08'"/>
            </xsl:when>
            <xsl:when test="$shortMonthStr='Sep'">
                <xsl:sequence select="'09'"/>
            </xsl:when>
            <xsl:when test="$shortMonthStr='Oct'">
                <xsl:sequence select="'10'"/>
            </xsl:when>
            <xsl:when test="$shortMonthStr='Nov'">
                <xsl:sequence select="'11'"/>
            </xsl:when>
            <xsl:when test="$shortMonthStr='Dec'">
                <xsl:sequence select="'12'"/>
            </xsl:when>
        </xsl:choose>
    </xsl:function>
    
    <xsl:function name="local:convertNcmDate2">
        <xsl:param name="ncmDateStr"/>
        <xsl:variable name="dateParts" select="tokenize(replace($ncmDateStr, ',', ''), '\s+')"/>
        <xsl:sequence select="concat($dateParts[2], $dateParts[1], fn:substring($dateParts[3], 3, 2))"/>
    </xsl:function>
    
    <xsl:function name="local:convertUnicodeChars">
        <xsl:param name="text"/>
        <xsl:variable name="uniChars">&#x2018;&#x2019;&#x201B;&#x2032;&#x2035;&#x201C;&#x201D;&#x201F;&#x2033;&#x2036;&#x2010;&#x2011;&#x2012;&#x2013;&#x2014;&#x2015;</xsl:variable>
        <xsl:variable name="repChars">'''''"""""------</xsl:variable>
        <xsl:value-of select="translate($text, $uniChars, $repChars)"/>
    </xsl:function>    

    <xsl:function name="local:replaceReservedChars">
        <xsl:param name="text"/>
        <!-- square brackets mark newsroom tags. replace them with markers -->
        <xsl:value-of 
            select="replace(replace($text, '\[', '__OPEN_SQR_BRACKET__'), '\]', '__CLOSE_SQR_BRACKET__')"/>
    </xsl:function>

    <xsl:function name="local:handleMergeCopy">
        <xsl:param name="tag"/>
        <!-- remove '_MCn' suffix, e.g. 'CAPTION_MC1' becomes 'CAPTION' -->
        <!-- remove square brackets in tag names, e.g. [No paragraph style] -->
        <xsl:value-of 
            select="replace(
                        replace(
                            replace($tag, '_MC\d+$', ''),
                        '\]', ''),
                    '\[', '')"/>
    </xsl:function>
    
    <xsl:function name="local:isAdobe">
        <xsl:param name="mediatype"/>
        <xsl:choose>
            <xsl:when test="contains(lower-case($mediatype), 'incopy') or contains(lower-case($mediatype), 'indesign')">
                <xsl:value-of select="1"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="0"/>
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
