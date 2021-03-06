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
package com.hotels.bdp.circustrain.s3mapreducecp.aws;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import org.junit.Test;

import com.amazonaws.services.s3.model.StorageClass;

public class AwsUtilTest {

  @Test
  public void toStorageClass() {
    assertThat(AwsUtil.toStorageClass("REDUCED_REDUNDANCY"), is(StorageClass.ReducedRedundancy));
  }

  @Test
  public void toNullStorageClass() {
    assertThat(AwsUtil.toStorageClass(null), is(nullValue()));
  }

  @Test
  public void toStorageClassCaseSensitiveness() {
    assertThat(AwsUtil.toStorageClass("glacier"), is(StorageClass.Glacier));
    assertThat(AwsUtil.toStorageClass("gLaCiEr"), is(StorageClass.Glacier));
  }

  @Test(expected = IllegalArgumentException.class)
  public void toInvalidStorageClass() {
    AwsUtil.toStorageClass("DEFAULT_STORAGE_CLASS");
  }

}
