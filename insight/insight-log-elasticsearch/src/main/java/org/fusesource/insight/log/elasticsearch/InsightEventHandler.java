/**
 * Copyright (C) FuseSource, Inc.
 * http://fusesource.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.fusesource.insight.log.elasticsearch;

import org.elasticsearch.action.index.IndexRequest;
import org.fusesource.insight.elasticsearch.ElasticSender;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.fusesource.insight.log.elasticsearch.InsightUtils.formatDate;
import static org.fusesource.insight.log.elasticsearch.InsightUtils.getIndex;
import static org.fusesource.insight.log.elasticsearch.InsightUtils.quote;

public class InsightEventHandler implements EventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(InsightLogAppender.class);

    private String name;
    private String index;
    private String type;
    private ElasticSender sender;


    public void setName(String name) {
        this.name = name;
    }

    public void setIndex(String index) {
        this.index = index;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setSender(ElasticSender sender) {
        this.sender = sender;
    }

    public void handleEvent(final Event event) {
        try {
            StringBuilder writer = new StringBuilder();
            writer.append("{ \"host\": ");
            quote(name, writer);
            writer.append(", \"topic\": ");
            quote(event.getTopic(), writer);
            writer.append(", \"properties\": { ");
            boolean first = true;

            long timestamp = 0;

            for (String name : event.getPropertyNames()) {
                if (first) {
                    first = false;
                } else {
                    writer.append(", ");
                }
                quote(name, writer);
                writer.append(": ");
                Object value = event.getProperty(name);
                if (value == null) {
                    writer.append("null");
                } else if (EventConstants.TIMESTAMP.equals(name) && value instanceof Long) {
                    timestamp = (Long) value;
                    quote(formatDate(timestamp), writer);
                } else if (value.getClass().isArray()) {
                    writer.append(" [ ");
                    boolean vfirst = true;
                    for (Object v : ((Object[]) value)) {
                        if (!vfirst) {
                            writer.append(", ");
                        } else {
                            vfirst = false;
                        }
                        quote(v.toString(), writer);
                    }
                    writer.append(" ] ");
                } else {
                    quote(value.toString(), writer);
                }
            }
            writer.append(" } }");

            if (timestamp == 0) {
                timestamp = System.currentTimeMillis();
            }

            IndexRequest request = new IndexRequest()
                    .index(getIndex(this.index, timestamp))
                    .type(type)
                    .source(writer.toString())
                    .create(true);

            sender.push(request);
        } catch (Exception e) {
            LOGGER.warn("Error appending log to elastic search", e);
        }
    }

}
