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
package com.hotels.bdp.circustrain.s3s3copier;

import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.Bucket;
import com.hotels.bdp.circustrain.common.test.junit.rules.S3ProxyRule;

@RunWith(MockitoJUnitRunner.class)
public class DummyTest {

  public @Rule S3ProxyRule s3Proxy = S3ProxyRule.builder().build();

  private AmazonS3 client;

  @Before
  public void setUp() throws Exception {
    client = newClient();
  }

  @Test
  public void listBuckets() {
    List<Bucket> buckets = client.listBuckets();
    System.out.println(buckets);
  }

  private AmazonS3 newClient() {
    EndpointConfiguration endpointConfiguration = new EndpointConfiguration(s3Proxy.getProxyUrl(),
        Regions.DEFAULT_REGION.getName());
    AmazonS3 newClient = AmazonS3ClientBuilder
        .standard()
        .withEndpointConfiguration(endpointConfiguration)
        .build();
    return newClient;
  }

}
