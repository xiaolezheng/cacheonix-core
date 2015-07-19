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
package org.cacheonix.impl.net.multicast.server;

import java.io.IOException;

import org.cacheonix.impl.util.Shutdownable;

/**
 * Multicast server that receives multicast packets.
 */
public interface MulticastServer extends Shutdownable {

   void addListener(MulticastServerListener listener);

   void startup() throws IOException;
}
