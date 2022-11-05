package org.example;

import org.apache.commons.lang3.StringEscapeUtils;

public class TextBlock {

    private String text = "";

    private Float x = 0.0f;

    TextBlock (String text, Float x) {
        this.text = StringEscapeUtils.escapeXml10(text);
        this.x = x;
    }

    public String getText() {
        return text;
    }

    public Float getX() {
        return x;
    }
}
