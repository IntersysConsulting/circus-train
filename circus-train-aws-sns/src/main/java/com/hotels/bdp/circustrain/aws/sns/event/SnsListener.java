/**
 * Copyright (C) 2016-2017 Expedia Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hotels.bdp.circustrain.aws.sns.event;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.sns.AmazonSNSAsyncClient;
import com.amazonaws.services.sns.model.PublishRequest;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.annotations.VisibleForTesting;

import com.hotels.bdp.circustrain.api.CompletionCode;
import com.hotels.bdp.circustrain.api.event.CopierListener;
import com.hotels.bdp.circustrain.api.event.EventPartition;
import com.hotels.bdp.circustrain.api.event.EventReplicaCatalog;
import com.hotels.bdp.circustrain.api.event.EventSourceCatalog;
import com.hotels.bdp.circustrain.api.event.EventTable;
import com.hotels.bdp.circustrain.api.event.EventTableReplication;
import com.hotels.bdp.circustrain.api.event.LocomotiveListener;
import com.hotels.bdp.circustrain.api.event.ReplicaCatalogListener;
import com.hotels.bdp.circustrain.api.event.SourceCatalogListener;
import com.hotels.bdp.circustrain.api.event.TableReplicationListener;
import com.hotels.bdp.circustrain.api.metrics.Metrics;

@Component
public class SnsListener implements LocomotiveListener, SourceCatalogListener, ReplicaCatalogListener,
    TableReplicationListener, CopierListener {

  private static final Logger LOG = LoggerFactory.getLogger(SnsListener.class);

  /** http://docs.aws.amazon.com/sns/latest/dg/large-payload-raw-message.html */
  private static final int SNS_MESSAGE_SIZE_LIMIT = 256 * 1024;

  private final AmazonSNSAsyncClient sns;
  private final ListenerConfig config;
  private final ObjectWriter startWriter;
  private final Clock clock;

  private Metrics metrics;
  private List<EventPartition> partitionsToCreate;
  private List<EventPartition> partitionsToAlter;
  private String startTime;
  private EventSourceCatalog sourceCatalog;
  private EventReplicaCatalog replicaCatalog;

  @Autowired
  public SnsListener(AmazonSNSAsyncClient sns, ListenerConfig config) {
    this(sns, config, Clock.DEFAULT);
  }

  SnsListener(AmazonSNSAsyncClient sns, ListenerConfig config, Clock clock) {
    this.clock = clock;
    LOG.info("Starting listener, topics: start={}, success={}, fail={}", config.getStartTopic(),
        config.getSuccessTopic(), config.getFailTopic());
    this.sns = sns;
    this.config = config;
    ObjectMapper mapper = new ObjectMapper();
    mapper.setSerializationInclusion(Include.NON_NULL);
    startWriter = mapper.writer();
  }

  @Override
  public void circusTrainStartUp(String[] args, EventSourceCatalog sourceCatalog, EventReplicaCatalog replicaCatalog) {
    this.sourceCatalog = sourceCatalog;
    this.replicaCatalog = replicaCatalog;
  }

  @Override
  public void copierEnd(Metrics metrics) {
    this.metrics = metrics;
  }

  @Override
  public void tableReplicationStart(EventTableReplication tableReplication, String eventId) {
    startTime = clock.getTime();
    SnsMessage message = new SnsMessage("START", config.getHeaders(), startTime, null, eventId, sourceCatalog.getName(),
        replicaCatalog.getName(), tableReplication.getSourceTable().getQualifiedName(),
        tableReplication.getQualifiedReplicaName(), null, null, null);
    publish(config.getStartTopic(), message);
  }

  @Override
  public void tableReplicationSuccess(EventTableReplication tableReplication, String eventId) {
    String endTime = clock.getTime();
    SnsMessage message = new SnsMessage("SUCCESS", config.getHeaders(), startTime, endTime, eventId,
        sourceCatalog.getName(), replicaCatalog.getName(), tableReplication.getSourceTable().getQualifiedName(),
        tableReplication.getQualifiedReplicaName(), getModifiedPartitions(partitionsToAlter, partitionsToCreate),
        getBytesReplicated(), null);
    publish(config.getSuccessTopic(), message);
  }

  @Override
  public void tableReplicationFailure(EventTableReplication tableReplication, String eventId, Throwable t) {
    if (startTime == null) {
      startTime = clock.getTime();
    }
    String endTime = clock.getTime();
    SnsMessage message = new SnsMessage("FAILURE", config.getHeaders(), startTime, endTime, eventId,
        sourceCatalog.getName(), replicaCatalog.getName(), tableReplication.getSourceTable().getQualifiedName(),
        tableReplication.getQualifiedReplicaName(), getModifiedPartitions(partitionsToAlter, partitionsToCreate),
        getBytesReplicated(), t.getMessage());
    publish(config.getFailTopic(), message);
  }

  private Long getBytesReplicated() {
    if (metrics != null) {
      return metrics.getBytesReplicated();
    }
    return 0L;
  }

  @Override
  public void partitionsToCreate(List<EventPartition> partitions) {
    partitionsToCreate = partitions;
  }

  @Override
  public void partitionsToAlter(List<EventPartition> partitions) {
    partitionsToAlter = partitions;
  }

  @VisibleForTesting
  static List<List<String>> getModifiedPartitions(
      List<EventPartition> partitionsToAlter,
      List<EventPartition> partitionsToCreate) {
    if (partitionsToAlter == null && partitionsToCreate == null) {
      return null;
    }
    List<List<String>> partitionValues = new ArrayList<>();
    for (List<EventPartition> partitions : Arrays.asList(partitionsToAlter, partitionsToCreate)) {
      if (partitions != null) {
        for (EventPartition partition : partitions) {
          partitionValues.add(partition.getValues());
        }
      }
    }
    return partitionValues;
  }

  private void publish(final String topic, SnsMessage message) {
    if (topic != null) {
      try {
        final String jsonMessage = startWriter.writeValueAsString(message);
        if (jsonMessage.getBytes("UTF-8").length > SNS_MESSAGE_SIZE_LIMIT) {
          LOG.warn("Message length execeeds SNS limit ({} bytes).", SNS_MESSAGE_SIZE_LIMIT);
        }
        LOG.debug("Attempting to send message to topic '{}': {}", topic, jsonMessage);
        PublishRequest request = new PublishRequest(topic, jsonMessage);
        if (config.getSubject() != null) {
          request.setSubject(config.getSubject());
        }
        try {
          sns.publish(request);
        } catch (AmazonClientException e) {
          LOG.error("Could not publish message to SNS topic '{}'.", topic, e);
        }
      } catch (JsonProcessingException e) {
        LOG.error("Could not serialize message '{}'.", message, e);
      } catch (UnsupportedEncodingException e) {
        LOG.error("Could not UTF-8 encode message '{}'.", message, e);
      }
    }
  }

  @PreDestroy
  public void flush() throws InterruptedException {
    LOG.debug("Terminating...");
    sns.getExecutorService().shutdown();
    sns.getExecutorService().awaitTermination(60, TimeUnit.SECONDS);
    sns.shutdown();
    LOG.debug("Terminated.");
  }

  @Override
  public void copierStart(String copierImplementation) {}

  @Override
  public void resolvedReplicaLocation(URI location) {}

  @Override
  public void existingReplicaPartitions(List<EventPartition> partitions) {}

  @Override
  public void deprecatedReplicaLocations(List<URI> locations) {}

  @Override
  public void resolvedMetaStoreSourceTable(EventTable table) {}

  @Override
  public void resolvedSourcePartitions(List<EventPartition> partitions) {}

  @Override
  public void resolvedSourceLocation(URI location) {}

  @Override
  public void circusTrainShutDown(CompletionCode completionCode, Map<String, Long> metrics) {}

}
