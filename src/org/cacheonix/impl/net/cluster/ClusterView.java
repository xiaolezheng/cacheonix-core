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
package org.cacheonix.impl.net.cluster;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.cacheonix.impl.net.ClusterNodeAddress;
import org.cacheonix.impl.net.processor.ReceiverAddress;
import org.cacheonix.impl.net.processor.UUID;
import org.cacheonix.impl.net.serializer.SerializerUtils;
import org.cacheonix.impl.net.serializer.Wireable;
import org.cacheonix.impl.net.serializer.WireableBuilder;
import org.cacheonix.impl.util.Assert;
import org.cacheonix.impl.util.array.HashSet;
import org.cacheonix.impl.util.logging.Logger;

/**
 * Marker list.
 * <p/>
 *
 * @author <a href="mailto:simeshev@cacheonix.org">Slava Imeshev</a>
 * @noinspection NonFinalFieldReferenceInEquals, NonFinalFieldReferencedInHashCode , RedundantIfStatement @since Mar 26,
 * 2008 9:30:48 PM
 */
public final class ClusterView implements Wireable {

   /**
    * Maker used by WireableFactory.
    */
   public static final WireableBuilder BUILDER = new Builder();

   /**
    * Logger.
    *
    * @noinspection UNUSED_SYMBOL, UnusedDeclaration
    */
   private static final Logger LOG = Logger.getLogger(ClusterView.class); // NOPMD

   /**
    * Cluster ID
    */
   private UUID clusterUUID = null;

   /**
    * Originator of this cluster view.
    */
   private ClusterNodeAddress originator = null;

   /**
    * Local node address.
    */
   private ClusterNodeAddress owner = null;

   /**
    * List of cluster node addresses.
    */
   private List<ClusterNodeAddress> clusterNodeList = null;


   /**
    * Required by externalizable.
    */
   public ClusterView() {

   }


   /**
    * Constructor.
    *
    * @param clusterUUID     cluster UUID
    * @param clusterNodeList of ProcessIDs.
    * @throws IllegalArgumentException if the marker list does not contain self.
    */
   public ClusterView(final UUID clusterUUID, final ClusterNodeAddress originator,
                      final List<JoiningNode> clusterNodeList, final ClusterNodeAddress owner)
           throws IllegalArgumentException {

      Assert.assertNotNull(clusterNodeList, "clusterNodeList cannot be null");
      Assert.assertTrue(!clusterNodeList.isEmpty(), "clusterNodeList cannot be null or empty");
      this.clusterUUID = clusterUUID;
      this.originator = originator;
      this.owner = owner;
      this.clusterNodeList = new ArrayList<ClusterNodeAddress>(clusterNodeList.size());
      for (final JoiningNode joiningNode : clusterNodeList) {
         this.insert(owner, joiningNode);
      }
      if (!this.clusterNodeList.contains(owner)) {
         throw new IllegalArgumentException("Node is not a member of the list: " + owner);
      }
   }


   /**
    * Creates a cluster view consisting only of the owner itself.
    *
    * @param clusterUUID
    * @param owner       owner address
    */
   public ClusterView(final UUID clusterUUID, final ClusterNodeAddress owner) {

      this.clusterNodeList = new ArrayList<ClusterNodeAddress>(1);
      this.clusterUUID = clusterUUID;
      this.originator = owner;
      this.owner = owner;
      this.insert(owner, new JoiningNode(owner));
   }


   public void setOwner(final ClusterNodeAddress owner) {

      this.owner = owner;
   }


   public boolean isRepresentative() {

      return owner.equals(originator);
   }


   public ClusterNodeAddress getNextElement() {

      final int i = clusterNodeList.indexOf(owner);
      if (i == clusterNodeList.size() - 1) {
         return clusterNodeList.get(0);
      } else {
         return clusterNodeList.get(i + 1);
      }
   }


   public int getSize() {

      return clusterNodeList.size();
   }


   public boolean remove(final ClusterNodeAddress clusterNodeAddress) {

      // Remove
      return clusterNodeList.remove(clusterNodeAddress);
   }


   public void insert(final ClusterNodeAddress predecessor, final JoiningNode joiningNode) {

      // Add to the node list
      clusterNodeList.add(clusterNodeList.indexOf(predecessor) + 1, joiningNode.getAddress());
   }


   /**
    * @return representative.
    */
   public ClusterNodeAddress getRepresentative() {

      return originator;
   }


   /**
    * @param elementAfter existing element after that to return a element
    * @throws IllegalStateException if the element is not in the list
    */
   public ClusterNodeAddress getNextElement(final ClusterNodeAddress elementAfter)
           throws IllegalStateException {

      int foundIndex = -1;
      for (int i = 0; i < clusterNodeList.size(); i++) {
         final ClusterNodeAddress clusterNodeAddress = clusterNodeList.get(i);
         if (clusterNodeAddress.equals(elementAfter)) {
            foundIndex = i;
            break;
         }
      }
      Assert.assertTrue(foundIndex != -1, "elementAfter should be in the list: {0}", elementAfter);

      final int nextElementIndex;
      if (foundIndex == clusterNodeList.size() - 1) {
         // hit the bottom
         nextElementIndex = 0;
      } else {
         nextElementIndex = foundIndex + 1;
      }
      return clusterNodeList.get(nextElementIndex);
   }


   /**
    * Returns <code>true</code> if this marker list has a majority over te other list that we have common a ancestor
    * with. In other words, we and the another list a parts of some previous list.
    *
    * @param previousView
    * @return <code>true</code> if this marker list has a majority over other list.
    */
   public boolean hasMajorityOver(final ClusterView previousView) {

      // if there is no other view, we *definitely* have a majority
      if (previousView == null) {
         return true;
      }

      final List<ClusterNodeAddress> otherList = previousView.clusterNodeList;
      final int otherListSize = otherList.size();

      // Check if we have strict majority
      // REVIEWME: simeshev@cacheonix.org - 2008-04-26 - This is a very simplistic approach. We
      // might actually check if this is the real intersection.
      final boolean strictMajority = clusterNodeList.size() > otherListSize >> 1;
      if (strictMajority) {
         return true;
      }

      // Check if we win strict majority by having both same size but also a greater member
      final Object ourGreatestMember = Collections.max(clusterNodeList);
      final Object theirGreatestMember = Collections.max(otherList);
      return clusterNodeList.size() == otherListSize >> 1 && ourGreatestMember.equals(theirGreatestMember);

   }


   /**
    * Returns a copy of the process list.
    *
    * @returns a copy of the process list.
    */
   public List<ClusterNodeAddress> getClusterNodeList() {

      return new ArrayList<ClusterNodeAddress>(clusterNodeList);
   }


   /**
    * {@inheritDoc}
    */
   public ClusterView copy() {

      final ClusterView result = new ClusterView();
      result.clusterUUID = clusterUUID;
      result.originator = originator;
      result.owner = owner;
      result.clusterNodeList = new ArrayList<ClusterNodeAddress>(clusterNodeList);
      return result;
   }


   /**
    * Returns this cluster's unique ID.
    *
    * @return this cluster's unique ID.
    */
   public UUID getClusterUUID() {

      return clusterUUID;
   }


   /**
    * Returns <code>true</code> if the cluster view contains active node.
    *
    * @param address ClusterNodeAddress to check.
    * @return <code>true</code> if the cluster view contains active node.
    */
   public boolean contains(final ClusterNodeAddress address) {

      return clusterNodeList.contains(address);
   }


   /**
    * Returns <code>true</code> if the cluster view contains active node.
    *
    * @param address ClusterNodeAddress to check.
    * @return <code>true</code> if the cluster view contains active node.
    */
   public boolean contains(final ReceiverAddress address) {

      for (final ClusterNodeAddress clusterNodeAddress : clusterNodeList) {

         if (address.isAddressOf(clusterNodeAddress)) {

            return true;
         }
      }

      return false;
   }


   /**
    * Calculates a collection of members that have left as compared to the previous view.
    *
    * @param previousClusterView previous cluster view
    * @return a collection of members that have left as compared to this previous view.
    */
   public Set<ClusterNodeAddress> calculateNodesLeft(final ClusterView previousClusterView) {

      if (previousClusterView == null) {

         return new HashSet<ClusterNodeAddress>(1);
      }

      final List<ClusterNodeAddress> previousNodeList = previousClusterView.clusterNodeList;
      final Set<ClusterNodeAddress> result = new HashSet<ClusterNodeAddress>(previousNodeList.size());
      for (final ClusterNodeAddress previousAddress : previousNodeList) {

         if (!clusterNodeList.contains(previousAddress)) {

            result.add(previousAddress);
         }
      }
      return result;
   }


   /**
    * Calculates a collection of members that have joined as compared to the previous view.
    *
    * @param previousClusterView previous cluster view
    * @return a collection of members that have joined as compared to this previous view.
    */
   public Set<ClusterNodeAddress> calculateNodesJoined(final ClusterView previousClusterView) {

      if (previousClusterView == null) {
         return new HashSet<ClusterNodeAddress>(clusterNodeList);
      }
      final Set<ClusterNodeAddress> result = new HashSet<ClusterNodeAddress>(clusterNodeList.size());
      for (final ClusterNodeAddress address : clusterNodeList) {
         if (!previousClusterView.clusterNodeList.contains(address)) {
            result.add(address);
         }
      }
      return result;
   }


   private boolean isHostInClusterNodeList(final ClusterNodeAddress address1, final int checkUpToNode) {

      for (int j = 0; j < checkUpToNode; j++) {

         if (clusterNodeList.get(j).sameHost(address1)) {

            return true;
         }
      }
      return false;
   }


   /**
    * {@inheritDoc}
    */
   public void readWire(final DataInputStream in) throws IOException {

      owner = SerializerUtils.readAddress(in);
      originator = SerializerUtils.readAddress(in);
      clusterUUID = SerializerUtils.readUuid(in);
      final int listSize = in.readInt();
      clusterNodeList = new ArrayList<ClusterNodeAddress>(listSize);
      for (int i = 0; i < listSize; i++) {
         clusterNodeList.add(SerializerUtils.readAddress(in));
      }
   }


   public int getWireableType() {

      return TYPE_CLUSTER_VIEW;
   }


   /**
    * {@inheritDoc}
    */
   public void writeWire(final DataOutputStream out) throws IOException {

      SerializerUtils.writeAddress(owner, out);
      SerializerUtils.writeAddress(originator, out);
      SerializerUtils.writeUuid(clusterUUID, out);
      final int listSize = clusterNodeList.size();
      out.writeInt(listSize);
      for (final ClusterNodeAddress address : clusterNodeList) {
         SerializerUtils.writeAddress(address, out);
      }
   }


   public boolean equals(final Object o) {

      if (this == o) {
         return true;
      }
      if (o == null || getClass() != o.getClass()) {
         return false;
      }

      final ClusterView that = (ClusterView) o;

      if (clusterNodeList != null ? !clusterNodeList.equals(that.clusterNodeList) : that.clusterNodeList != null) {
         return false;
      }
      if (clusterUUID != null ? !clusterUUID.equals(that.clusterUUID) : that.clusterUUID != null) {
         return false;
      }
      if (originator != null ? !originator.equals(that.originator) : that.originator != null) {
         return false;
      }
      if (owner != null ? !owner.equals(that.owner) : that.owner != null) {
         return false;
      }

      return true;
   }


   public int hashCode() {

      int result = clusterUUID != null ? clusterUUID.hashCode() : 0;
      result = 31 * result + (originator != null ? originator.hashCode() : 0);
      result = 31 * result + (owner != null ? owner.hashCode() : 0);
      result = 31 * result + (clusterNodeList != null ? clusterNodeList.hashCode() : 0);
      return result;
   }


   public String toString() {

      return "ClusterView{" +
              "clusterNodeList=" + clusterNodeList +
              ", originator=" + originator +
              ", owner=" + owner +
              ", clusterUUID=" + clusterUUID +
              '}';
   }


   public ClusterNodeAddress getNextElement(final ReceiverAddress elementAfter) {

      int foundIndex = -1;
      for (int i = 0; i < clusterNodeList.size(); i++) {
         final ClusterNodeAddress clusterNodeAddress = clusterNodeList.get(i);
         if (elementAfter.isAddressOf(clusterNodeAddress)) {
            foundIndex = i;
            break;
         }
      }
      Assert.assertTrue(foundIndex != -1, "elementAfter should be in the list: {0}", elementAfter);

      final int nextElementIndex;
      if (foundIndex == clusterNodeList.size() - 1) {
         // hit the bottom
         nextElementIndex = 0;
      } else {
         nextElementIndex = foundIndex + 1;
      }
      return clusterNodeList.get(nextElementIndex);
   }


   /**
    * A class factory.
    */
   final static class Builder implements WireableBuilder {

      public Wireable create() {

         return new ClusterView();
      }
   }
}
