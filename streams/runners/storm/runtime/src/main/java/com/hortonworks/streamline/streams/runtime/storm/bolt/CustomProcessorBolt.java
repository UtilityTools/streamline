/**
  * Copyright 2017 Hortonworks.
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at

  *   http://www.apache.org/licenses/LICENSE-2.0

  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
 **/
package com.hortonworks.streamline.streams.runtime.storm.bolt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hortonworks.registries.common.Schema;
import com.hortonworks.streamline.common.util.Utils;
import com.hortonworks.streamline.streams.StreamlineEvent;
import com.hortonworks.streamline.streams.Result;
import com.hortonworks.streamline.streams.common.StreamlineEventImpl;
import com.hortonworks.streamline.streams.exception.ProcessingException;
import com.hortonworks.streamline.streams.runtime.CustomProcessorRuntime;
import org.apache.commons.lang.StringUtils;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.apache.storm.utils.TupleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bolt for supporting custom processors components in an Streamline topology
 */
public class CustomProcessorBolt extends AbstractProcessorBolt {
    private static final Logger LOG = LoggerFactory.getLogger(CustomProcessorBolt.class);
    private static final ConcurrentHashMap<String, CustomProcessorRuntime> customProcessorConcurrentHashMap = new ConcurrentHashMap<>();
    private CustomProcessorRuntime customProcessorRuntime;
    private String customProcessorImpl;
    private Map<String, Object> config;
    private Schema inputSchema;
    private Map<String, Schema> outputSchema = new HashMap<>();

    public CustomProcessorBolt customProcessorImpl (String customProcessorImpl) {
        this.customProcessorImpl = customProcessorImpl;
        return this;
    }

    /**
     * Associate output schema that is a json string
     * @param outputSchemaJson
     * @return
     */
    public CustomProcessorBolt outputSchema (String outputSchemaJson) {
        ObjectMapper mapper = new ObjectMapper();
        Map<String,Schema> outputSchema = new HashMap<>();
        try {
            Map<String, Map> output = mapper.readValue(outputSchemaJson, Map.class);
            for (Map.Entry<String, Map> entry: output.entrySet()) {
                outputSchema.put(entry.getKey(), Utils.getSchemaFromConfig(entry.getValue()));
            }
        } catch (IOException e) {
            LOG.error("Error during deserialization of output schema JSON string: {}", outputSchemaJson, e);
            throw new RuntimeException(e);
        }
        return outputSchema(outputSchema);
    }

    /**
     * Associate output schema
     * @param outputSchema
     * @return
     */
    public CustomProcessorBolt outputSchema (Map<String, Schema> outputSchema) {
        this.outputSchema = outputSchema;
        return this;
    }

    /**
     * Associate input schema that is a json string
     * @param inputSchemaJson
     * @return
     */
    public CustomProcessorBolt inputSchema (String inputSchemaJson) {
        ObjectMapper mapper = new ObjectMapper();
        Schema inputSchema;
        try {
            inputSchema = mapper.readValue(inputSchemaJson, Schema.class);
        } catch (IOException e) {
            LOG.error("Error during deserialization of input schema JSON string: {}", inputSchemaJson, e);
            throw new RuntimeException(e);
        }
        return inputSchema(inputSchema);
    }

    /**
     * Associcate input schema
     * @param inputSchema
     * @return
     */
    public CustomProcessorBolt inputSchema (Schema inputSchema) {
        this.inputSchema = inputSchema;
        return this;
    }

    /**
     * Associate config as a json string
     * @param configJson
     * @return
     */
    public CustomProcessorBolt config (String configJson) {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> config;
        try {
            config = mapper.readValue(configJson, Map.class);
        } catch (IOException e) {
            LOG.error("Error during deserialization of config JSON string: {}", configJson, e);
            throw new RuntimeException(e);
        }
        return config(config);
    }

    /**
     * Associate config as a Map of String to Object
     * @param config
     * @return
     */
    public CustomProcessorBolt config (Map<String, Object> config) {
        this.config = config;
        return this;
    }

    @Override
    public void prepare (Map stormConf, TopologyContext context, OutputCollector collector) {
        super.prepare(stormConf, context, collector);
        String message;
        if (StringUtils.isEmpty(customProcessorImpl)) {
            message = "Custom processor implementation class not specified.";
            LOG.error(message);
            throw new RuntimeException(message);
        }
        customProcessorRuntime = getCustomProcessorRuntime();
        customProcessorRuntime.initialize(config);
    }

    @Override
    protected void process (Tuple input, StreamlineEvent event) {
        try {
            List<Result> results = customProcessorRuntime.process(new StreamlineEventImpl(event, event.getDataSourceId(), event
                    .getId(), event.getHeader(), input.getSourceStreamId()));
            if (results != null) {
                for (Result result : results) {
                    for (StreamlineEvent e : result.events) {
                        collector.emit(result.stream, input, new Values(e));
                    }
                }
            }
        } catch (ProcessingException e) {
            LOG.error("Custom Processor threw a ProcessingException. ", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void declareOutputFields (OutputFieldsDeclarer declarer) {
        if (outputSchema == null || outputSchema.keySet().isEmpty()) {
            String message = "Custom processor config must have at least one output stream and associated schema.";
            LOG.error(message);
            throw new RuntimeException(message);
        }
        for (String outputStream: outputSchema.keySet()) {
            declarer.declareStream(outputStream, new Fields(StreamlineEvent.STREAMLINE_EVENT));
        }
    }

    @Override
    public void cleanup () {
        customProcessorRuntime.cleanup();
    }

    private CustomProcessorRuntime getCustomProcessorRuntime() {
        CustomProcessorRuntime customProcessorRuntime = customProcessorConcurrentHashMap.get(customProcessorImpl);
        if (customProcessorRuntime == null) {
            try {
                customProcessorRuntime = (CustomProcessorRuntime) Class.forName(customProcessorImpl).newInstance();
                customProcessorConcurrentHashMap.put(customProcessorImpl, customProcessorRuntime);
            } catch (ClassNotFoundException|InstantiationException|IllegalAccessException e) {
                throw new RuntimeException("Failed to load custom processor: " + customProcessorImpl, e);
            }
        }
        return customProcessorRuntime;
    }
}
