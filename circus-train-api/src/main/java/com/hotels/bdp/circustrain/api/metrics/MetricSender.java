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
package com.hotels.bdp.circustrain.api.metrics;

import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface MetricSender {

  static final Logger LOG = LoggerFactory.getLogger(MetricSender.class);

  public static final MetricSender DEFAULT_LOG_ONLY = new MetricSender() {

    @Override
    public void send(String name, long value) {
      LOG.info("{} : {}", name, value);
    }

    @Override
    public void send(Map<String, Long> metrics) {
      for (Entry<String, Long> metric : metrics.entrySet()) {
        send(metric.getKey(), metric.getValue());
      }
    }

  };

  void send(String name, long value);

  void send(Map<String, Long> metrics);

}
