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
package com.hotels.bdp.circustrain.gcp.context;

import static org.apache.commons.lang.StringUtils.isNotBlank;

import org.apache.hadoop.conf.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import com.hotels.bdp.circustrain.context.CommonBeans;
import com.hotels.bdp.circustrain.gcp.BindGoogleHadoopFileSystem;
import com.hotels.bdp.circustrain.gcp.GCPCredentialConfigurer;

@Component
public class GCPBeanPostProcessor implements BeanPostProcessor {

  private static final Logger LOG = LoggerFactory.getLogger(GCPBeanPostProcessor.class);

  private final GCPSecurity security;

  @Autowired
  public GCPBeanPostProcessor(GCPSecurity gcpSecurity) {
    this.security = gcpSecurity;
  }

  @Override
  public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
    return bean;
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
    if (CommonBeans.BEAN_BASE_CONF.equals(beanName)) {
      Configuration baseConf = (Configuration) bean;
      setHadoopConfiguration(baseConf);
      return baseConf;
    }
    return bean;
  }

  private void setHadoopConfiguration(Configuration configuration) {
    LOG.debug("Configuring google hadoop connector");
    if (isNotBlank(security.getCredentialProvider())) {
      BindGoogleHadoopFileSystem binder = new BindGoogleHadoopFileSystem(configuration);
      binder.bindFileSystem();
      GCPCredentialConfigurer configurer = new GCPCredentialConfigurer(configuration, security);
      configurer.configureCredentials();
    }
  }
}
