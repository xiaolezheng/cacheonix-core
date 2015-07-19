/*
 * Cacheonix systems licenses this file to You under the LGPL 2.1
 * (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.cacheonix.org/products/cacheonix/license-lgpl-2.1.htm
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.cacheonix.plugin.mybatis.v300;

/**
 * Tests the configuration with per-select cache enabled.
 */
public final class MyBatisCachePerSelectCacheTest extends MyBatisCacheTestDriver {

   public void setUp() throws Exception {

      super.setUp();

      myBatisCache.setEnablePerSelectCaching(Boolean.TRUE.toString());
   }
}
