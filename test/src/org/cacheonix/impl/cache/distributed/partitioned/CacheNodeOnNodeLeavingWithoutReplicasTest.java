/*
 * Cacheonix Systems licenses this file to You under the LGPL 2.1
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
package org.cacheonix.impl.cache.distributed.partitioned;

import org.cacheonix.impl.util.logging.Logger;

/**
 * Tests clustered cache
 *
 * @noinspection ProhibitedExceptionDeclared, ProhibitedExceptionDeclared, ConstantNamingConvention,
 * ConstantNamingConvention, ConstantNamingConvention, ConstantNamingConvention, ConstantNamingConvention
 */
public final class CacheNodeOnNodeLeavingWithoutReplicasTest extends CacheNodeOnNodeLeavingWithoutReplicasTestDriver {

   /**
    * Logger.
    *
    * @noinspection UNUSED_SYMBOL, UnusedDeclaration
    */
   private static final Logger LOG = Logger.getLogger(CacheNodeOnNodeLeavingWithoutReplicasTest.class); // NOPMD

   /**
    * Cacheonix condifurations, one per cluster.
    */
   private static final String[] NODE_CONFIGURATIONS = {
           "cacheonix-config-cluster-member-1.xml",
           "cacheonix-config-cluster-member-2.xml",
           "cacheonix-config-cluster-member-3.xml",
   };


   public CacheNodeOnNodeLeavingWithoutReplicasTest() {

      super(NODE_CONFIGURATIONS);
   }
}