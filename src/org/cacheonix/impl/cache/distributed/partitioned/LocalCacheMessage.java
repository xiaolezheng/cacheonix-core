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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.NotSerializableException;

import org.cacheonix.impl.util.logging.Logger;

/**
 * A cache message that can be sent only to a local cache.
 * <p/>
 *
 * @author <a href="mailto:simeshev@cacheonix.org">Slava Imeshev</a>
 * @since Nov 6, 2009 8:02:30 PM
 */
public abstract class LocalCacheMessage extends CacheMessage {

   /**
    * Logger.
    *
    * @noinspection UNUSED_SYMBOL, UnusedDeclaration
    */
   private static final Logger LOG = Logger.getLogger(LocalCacheMessage.class); // NOPMD


   LocalCacheMessage(final int type, final String cacheName) {

      super(type, cacheName);
   }


   LocalCacheMessage() {

   }


   public final void readWire(final DataInputStream in) throws IOException {

      throw new NotSerializableException(this.getClass() + " is a strictly local object");
   }


   public final void writeWire(final DataOutputStream out) throws IOException {

      throw new NotSerializableException(this.getClass() + " is a strictly local object");
   }
}
