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
package org.cacheonix;

import junit.framework.TestCase;

/**
 * Tester for ShutdownException.
 */
@SuppressWarnings("ThrowableInstanceNeverThrown")
public final class ShutdownExceptionTest extends TestCase {

   private static final String TEST_MESSAGE = "Test message";


   public void testDefaultConstructor() {

      assertNotNull(new ShutdownException().toString());
   }


   public void testConstructor() {

      final ShutdownException exception = new ShutdownException(TEST_MESSAGE);
      assertTrue(exception.getMessage().contains(TEST_MESSAGE));
   }
}
