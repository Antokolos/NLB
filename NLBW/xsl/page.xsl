<?xml version="1.0" encoding="utf-8"?>
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:output method="html" encoding="utf-8" indent="yes"/>
    <xsl:param name="mainNLBId"/>
    <xsl:param name="normalLinkBookId"/>
    <xsl:param name="traversalLinkBookId"/>
    <xsl:param name="returnLinkBookId"/>
    <xsl:param name="returnModulePageId"/>
    <xsl:variable
            name="hasReturnPageId"
            select="string-length(normalize-space(/page/return-page-id/text())) != 0"
            />

    <xsl:template match="/">
        <html>
            <head></head>
            <body>
                <div style="float: left; width: 65%;">
                    <xsl:if test="/page/usecaption/text() = 'true'">
                        <h1>
                            <xsl:value-of select="/page/caption"/>
                        </h1>
                    </xsl:if>
                    <xsl:apply-templates select="/page/text"/>
                    <xsl:apply-templates select="/page/link"/>
                </div>
                <xsl:element name="iframe">
                    <xsl:attribute name="src">
                        <xsl:text>/nlb/</xsl:text>
                        <xsl:value-of select="$mainNLBId"/>
                        <xsl:text>/history</xsl:text>
                    </xsl:attribute>
                    <xsl:attribute name="style">
                        <xsl:text>float: right; width: 25%; height: 90%</xsl:text>
                    </xsl:attribute>
                </xsl:element>
                <xsl:comment>Page ends</xsl:comment>
            </body>
        </html>
    </xsl:template>

    <xsl:template match="/page/link">
        <xsl:comment>Link</xsl:comment>
        <xsl:element name="a">
            <xsl:attribute name="href">
                <xsl:text>/nlb/</xsl:text>
                <xsl:if test="is-traversal/text() = 'true'">
                    <xsl:value-of select="$traversalLinkBookId"/>
                </xsl:if>
                <xsl:if test="not(is-traversal/text() = 'true') and is-return/text() = 'true'">
                    <xsl:value-of select="$returnLinkBookId"/>
                    <xsl:text>/page/</xsl:text>
                    <xsl:if test="$hasReturnPageId">
                        <xsl:value-of select="/page/return-page-id"/>
                    </xsl:if>
                    <xsl:if test="not($hasReturnPageId)">
                        <xsl:value-of select="$returnModulePageId"/>
                    </xsl:if>
                </xsl:if>
                <xsl:if test="not(is-traversal/text() = 'true') and not(is-return/text() = 'true')">
                    <xsl:value-of select="$normalLinkBookId"/>
                    <xsl:text>/link/</xsl:text>
                    <xsl:value-of select="/page/id"/>
                    <xsl:text>/</xsl:text>
                    <xsl:value-of select="id"/>
                </xsl:if>
            </xsl:attribute>
            <xsl:value-of select="text"/>
        </xsl:element>
        <br/>
    </xsl:template>

    <xsl:template match="/page/text">
        <p>
            <xsl:apply-templates/>
        </p>
    </xsl:template>

    <!-- The following template replaces line breaks with <br/> tags -->
    <xsl:template match="/page/text/text()">
        <xsl:analyze-string select="." regex='^(.+)$' flags="m">
            <xsl:matching-substring>
                <xsl:value-of select="regex-group(1)"/>
                <br/>
            </xsl:matching-substring>
            <!-- Please note that empty lines will be discarded -->
        </xsl:analyze-string>
    </xsl:template>
</xsl:stylesheet>
