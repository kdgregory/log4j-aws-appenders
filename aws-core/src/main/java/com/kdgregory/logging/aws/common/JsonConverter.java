// Copyright (c) Keith D Gregory
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.kdgregory.logging.aws.common;

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;

/**
 *  Transforms a Map into a JSON string. Transforms strings, numbers, booleans,
 *  and null as expected; maps become nested objects; arrays and collections
 *  (list, set, whatever) become arrays; dates become strings formatted as
 *  ISO-8601 timestamps; anything else is converted to a string.
 *  <p>
 *  Instances are not thread-safe
 */
public class JsonConverter
{
    private SimpleDateFormat dateFormatter;

    public JsonConverter()
    {
        dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        dateFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
    }


    public String convert(Map<String,Object> map)
    {
        StringBuilder builder = new StringBuilder(1024);
        appendMap(builder, map);
        return builder.toString();
    }


    private void append(StringBuilder builder, String key, Object value)
    {
        appendString(builder, key);
        builder.append(':');
        appendValue(builder, value);
    }


    private void appendValue(StringBuilder builder, Object value)
    {
        if (value instanceof String)            appendString(builder, (String)value);
        else if (value instanceof Number)       appendNumber(builder, (Number)value);
        else if (value instanceof Boolean)      appendBoolean(builder, (Boolean)value);
        else if (value instanceof Date)         appendDate(builder, (Date)value);
        else if (value instanceof Object[])     appendArray(builder, (Object[])value);
        else if (value instanceof Collection)   appendCollection(builder, (Collection<Object>)value);
        else if (value instanceof Map)          appendMap(builder, (Map<String,Object>)value);
        else if (value == null)                 appendNull(builder);
        else                                    appendString(builder, String.valueOf(value));
    }


    private void appendString(StringBuilder builder, String value)
    {
        builder.append("\"");
        int len = value.length();
        for (int ii = 0 ; ii < len ; ii++)
        {
            char c = value.charAt(ii);
            if (c == '"')
                builder.append("\\\"");
            else if (c == '\\')
                builder.append("\\\\");
            else if ((c >= 32) && (c <= 126))
                builder.append(c);
            else if ((c >= '\u00A0') && (c <= '\uD7FF'))
                builder.append(c);
            else if (c >= '\uE000')
                builder.append(c);
            else if (c == '\u0007')
                builder.append("\\b");
            else if (c == '\f')
                builder.append("\\f");
            else if (c == '\n')
                builder.append("\\n");
            else if (c == '\r')
                builder.append("\\r");
            else if (c == '\t')
                builder.append("\\t");
        }
        builder.append("\"");
    }


    private void appendNumber(StringBuilder builder, Number value)
    {
        builder.append(value);
    }


    private void appendBoolean(StringBuilder builder, Boolean value)
    {
        builder.append(value.booleanValue() ? "true" : "false");
    }


    private void appendDate(StringBuilder builder, Date value)
    {

        appendString(builder, dateFormatter.format(value));
    }


    private void appendNull(StringBuilder builder)
    {
        builder.append("null");
    }


    private void appendArray(StringBuilder builder, Object[] value)
    {
        builder.append("[");
        for (Object entry : value)
        {
            optAppendComma(builder, '[');
            appendValue(builder, entry);
        }
        builder.append("]");
    }


    private void appendCollection(StringBuilder builder, Collection<Object> value)
    {
        builder.append("[");
        for (Object entry : value)
        {
            optAppendComma(builder, '[');
            appendValue(builder, entry);
        }
        builder.append("]");
    }

    private void appendMap(StringBuilder builder, Map<String,Object> map)
    {
        builder.append("{");
        for (Map.Entry<String,Object> entry : map.entrySet())
        {
            optAppendComma(builder, '{');
            append(builder, entry.getKey(), entry.getValue());
        }
        builder.append("}");
    }


    private void optAppendComma(StringBuilder builder, char valueInitiator)
    {
        if (builder.charAt(builder.length() - 1) != valueInitiator)
            builder.append(",");
    }
}
