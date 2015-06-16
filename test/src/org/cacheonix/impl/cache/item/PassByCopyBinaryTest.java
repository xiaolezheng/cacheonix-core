/*
 * Cacheonix systems licenses this file to You under the LGPL 2.1
 * (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.cacheonix.com/products/cacheonix/license-lgpl-2.1.htm
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.cacheonix.impl.cache.item;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.cacheonix.CacheonixTestCase;
import org.cacheonix.impl.net.serializer.Serializer;
import org.cacheonix.impl.net.serializer.SerializerFactory;
import org.cacheonix.impl.util.logging.Logger;

/**
 * Tester for  PartitionElementValueByCopy.
 *
 * @noinspection JavaDoc
 */
public final class PassByCopyBinaryTest extends CacheonixTestCase {

   /**
    * Logger.
    *
    * @noinspection UNUSED_SYMBOL, UnusedDeclaration
    */
   private static final Logger LOG = Logger.getLogger(PassByCopyBinaryTest.class); // NOPMD

   private static final byte[] TEST_VALUE = "test_value".getBytes();

   private PassByCopyBinary binary;


   /**
    * Tests setValue()
    */
   public void testCreate() {

      assertEquals(TEST_VALUE, (byte[]) binary.getValue());
      assertNotSame(TEST_VALUE, binary.getValue());
   }


   /**
    * Tests setting null value.
    */
   public void testSetNullValue() throws InvalidObjectException {

      assertNull(new PassByCopyBinary(null).getValue());
   }


   public void testSerializeDeserialize() throws IOException, ClassNotFoundException {

      final Serializer ser = SerializerFactory.getInstance().getSerializer(Serializer.TYPE_JAVA);
      assertEquals((byte[]) binary.getValue(), (byte[]) ((Binary) ser.deserialize(ser.serialize(binary))).getValue());
   }


   public void testReadWriteExternal() throws IOException, ClassNotFoundException {

      final ByteArrayOutputStream baos = new ByteArrayOutputStream(100);
      final ObjectOutputStream oos = new ObjectOutputStream(baos);
      oos.writeObject(binary);
      oos.flush();

      assertEquals((byte[]) binary.getValue(), (byte[]) ((Binary) new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray())).readObject()).getValue());
   }


   protected final void setUp() throws Exception {

      super.setUp();
      binary = new PassByCopyBinary(TEST_VALUE);
   }


   public final String toString() {

      return "ElementValueByCopyTest{" +
              "item=" + binary +
              '}';
   }
}