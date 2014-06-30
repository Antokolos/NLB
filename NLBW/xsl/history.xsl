<?xml version="1.0" encoding="utf-8"?>
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:output method="html" encoding="utf-8" indent="yes"/>
    <xsl:param name="mainNLBId"/>
    <xsl:template match="/">
        <html>
            <head>
                <base target="_parent"/>
            </head>
            <body>
                <xsl:element name="a">
                    <xsl:attribute name="href">
                        <xsl:text>/nlb/</xsl:text>
                        <xsl:value-of select="$mainNLBId"/>
                        <xsl:text>/start</xsl:text>
                    </xsl:attribute>
                    <xsl:text>Start over</xsl:text>
                </xsl:element>
                <ul>
                    <xsl:for-each select="/history/decision-point">
                        <li>
                            <xsl:element name="a">
                                <xsl:attribute name="href">
                                    <xsl:text>/nlb/</xsl:text>
                                    <xsl:value-of select="bookId"/>
                                    <xsl:if test="is-link-info/text() = 'true'">
                                        <xsl:text>/link/</xsl:text>
                                        <xsl:value-of select="fromPageId"/>
                                        <xsl:text>/</xsl:text>
                                        <xsl:value-of select="linkId"/>
                                    </xsl:if>
                                    <xsl:if test="is-link-info/text() = 'false'">
                                        <xsl:text>/page/</xsl:text>
                                        <xsl:value-of select="toPageId"/>
                                    </xsl:if>
                                    <xsl:text>?rollback=true&amp;visit-count=</xsl:text>
                                    <xsl:value-of select="visit-count"/>
                                </xsl:attribute>
                                <xsl:value-of select="text"/>
                            </xsl:element>
                        </li>
                    </xsl:for-each>
                </ul>
            </body>
        </html>
    </xsl:template>
</xsl:stylesheet>
