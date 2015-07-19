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

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import org.cacheonix.ShutdownException;
import org.cacheonix.ShutdownMode;
import org.cacheonix.exceptions.CacheonixException;
import org.cacheonix.impl.cache.distributed.partitioned.CacheProcessor;
import org.cacheonix.impl.cache.distributed.partitioned.ShutdownCacheProcessorMessage;
import org.cacheonix.impl.clock.Clock;
import org.cacheonix.impl.net.ClusterNodeAddress;
import org.cacheonix.impl.net.multicast.sender.MulticastSender;
import org.cacheonix.impl.net.multicast.server.MulticastServerListener;
import org.cacheonix.impl.net.processor.AbstractRequestProcessor;
import org.cacheonix.impl.net.processor.Command;
import org.cacheonix.impl.net.processor.Frame;
import org.cacheonix.impl.net.processor.Message;
import org.cacheonix.impl.net.processor.Prepareable;
import org.cacheonix.impl.net.processor.Request;
import org.cacheonix.impl.net.processor.Response;
import org.cacheonix.impl.net.processor.Router;
import org.cacheonix.impl.net.processor.SenderInetAddressAware;
import org.cacheonix.impl.net.processor.UUID;
import org.cacheonix.impl.net.serializer.Serializer;
import org.cacheonix.impl.net.serializer.SerializerFactory;
import org.cacheonix.impl.net.serializer.Wireable;
import org.cacheonix.impl.util.Assert;
import org.cacheonix.impl.util.exception.ExceptionUtils;
import org.cacheonix.impl.util.logging.Logger;
import org.cacheonix.impl.util.thread.ActionableTimeout;
import org.cacheonix.impl.util.time.Timeout;

/**
 * MulticastConnectionImpl
 * <p/>
 *
 * @author <a href="mailto:simeshev@cacheonix.org">Slava Imeshev</a>
 * @noinspection JavaDoc, ClassEscapesDefinedScope @since Mar 20, 2008 3:59:09 PM
 */
public final class ClusterProcessor extends AbstractRequestProcessor implements MulticastServerListener {

   /**
    * Logger.
    *
    * @noinspection UNUSED_SYMBOL, UnusedDeclaration
    */
   private static final Logger LOG = Logger.getLogger(ClusterProcessor.class); // NOPMD


   private final long gracefulShutdownTimeoutMillis;

   /**
    * A list of messages awaiting delivery notification.
    */
   private final Queue<DeliveryNotificationEntry> awaitingDeliveryNotification = new ConcurrentLinkedQueue<DeliveryNotificationEntry>();

   /**
    * Frames received from the network.
    */
   private final Queue<Frame> receivedFrames = new ConcurrentLinkedQueue<Frame>();

   /**
    * Public
    */
   private final MessageAssembler messageAssembler = new MessageAssembler();

   /**
    * A timeout to control loss of a marker.
    */
   private final ActionableTimeout obtainMarkerTimeout;

   /**
    * A timeout to control leaving cluster gracefully.
    */
   private final AtomicReference<ActionableTimeout> leaveTimeout = new AtomicReference<ActionableTimeout>(null);

   /**
    * Helper to sends multicast messages.
    */
   private final MulticastSender multicastSender;

   /**
    * Cluster announcer.
    */
   private final ClusterAnnouncer clusterAnnouncer;

   /**
    * A list of subscribers waiting for messages.
    */
   private final MulticastMessageListenerList multicastMessageListeners = new MulticastMessageListenerList();

   /**
    * Setting latch to non-null value is used to let the marker know that this ClusterProcessor should begin to leave
    * the cluster. Opening of this latch is used to receive a notification that shutdown process has been complete.
    */
   private final AtomicReference<CountDownLatch> shutdownLatch = new AtomicReference<CountDownLatch>(null);

   /**
    * A utility to use to partition load.
    */
   private final PayloadPartitioner partitioner = new PayloadPartitioner();


   /**
    * An optional cause of shutdown.
    */
   private CacheonixException shutdownCause;

   /**
    * Cache processors owned by this cluster node.
    */
   private final Map<String, CacheProcessor> cacheProcessors = new ConcurrentHashMap<String, CacheProcessor>(11);

   /**
    * Cluster processor state.
    */
   private final ClusterProcessorState processorState = new ClusterProcessorStateImpl();


   /**
    * Creates a new ClusterProcessor with cluster view initialised to self.
    *
    * @param clusterName
    * @param timer
    * @param router
    * @param multicastSender
    * @param self
    * @param homeAloneTimeout
    * @param worstCaseLatencyMillis
    * @param gracefulShutdownTimeoutMillis
    * @param clusterSurveyTimeoutMillis
    * @param initialClusterUUID
    */
   public ClusterProcessor(final String clusterName, final Clock clock, final Timer timer,
                           final Router router, final MulticastSender multicastSender, final ClusterNodeAddress self,
                           final long homeAloneTimeout, final long worstCaseLatencyMillis,
                           final long gracefulShutdownTimeoutMillis, final long clusterSurveyTimeoutMillis,
                           final long clusterAnnouncementTimeoutMillis, final UUID initialClusterUUID) {

      super(clock, timer, "ClusterProcessor:" + self.getTcpPort(), self, router);

      this.processorState.setClusterName(clusterName);
      this.processorState.setAddress(self);

      // Adjust timeouts to make sure they are set appropriately
      final long adjustedClusterSurveyTimeoutMillis = adjustClusterSurveyTimeout(clusterSurveyTimeoutMillis, clusterAnnouncementTimeoutMillis);
      final long adjustedHomeAloneTimeoutMillis = adjustHomeAloneTimeout(homeAloneTimeout, adjustedClusterSurveyTimeoutMillis);

      // Set fields
      this.processorState.setHomeAloneTimeout(new Timeout(adjustedHomeAloneTimeoutMillis));
      this.processorState.setClusterAnnouncementTimeoutMillis(clusterAnnouncementTimeoutMillis);
      this.gracefulShutdownTimeoutMillis = gracefulShutdownTimeoutMillis;
      this.processorState.setWorstCaseLatencyMillis(worstCaseLatencyMillis);
      this.processorState.setClusterView(new ClusterView(initialClusterUUID, self));
      this.obtainMarkerTimeout = new ObtainMulticastMarkerTimeout(this);
      this.multicastSender = multicastSender;
      this.clusterAnnouncer = new ClusterAnnouncer(clock, this.multicastSender, clusterName, self);
      this.processorState.setJoinStatus(new JoinStatus(adjustedClusterSurveyTimeoutMillis));
      this.processorState.getHomeAloneTimeout().reset();
      this.reset();
   }


   /**
    * Returns submittal queue.
    *
    * @return submittal queue.
    */
   public void announceCluster(final boolean operational) throws IOException {

      //noinspection ControlFlowStatementWithoutBraces

      clusterAnnouncer.announce(processorState.getClusterView().getClusterUUID(), processorState.getClusterView().getRepresentative(),
              processorState.getClusterView().getSize(), operational);
   }


   public void sendMulticastFrame(final Frame frame) throws IOException {

      multicastSender.sendFrame(frame);
   }


   public void processMessage(final Message message) throws InterruptedException, IOException {

      processMessage(message, true);
   }


   void processMessage(final Message message, final boolean checkSameCluster)
           throws InterruptedException, IOException {

      // Check if the message should be limited to the same cluster

      if (checkSameCluster && message.isRequiresSameCluster() && !processorState.getClusterView().getClusterUUID().equals(message.getClusterUUID())) {

         //noinspection ControlFlowStatementWithoutBraces
         if (LOG.isDebugEnabled()) LOG.debug("Received message from other cluster: " + message); // NOPMD
         if (message instanceof Request) {

            final Request request = (Request) message;
            if (request.isResponseRequired()) {

               post(request.createResponse(Response.RESULT_RETRY));
            }
         }
      } else {
         super.processMessage(message);
      }
   }


   /**
    * Starts up underlying services.
    *
    * @throws IOException
    */
   public void startup() {

      super.startup();
      // Get marker list

      Assert.assertTrue(processorState.getClusterView().getSize() == 1, "Initial cluster view can only be of size 1: {0}", processorState.getClusterView());

      // Alone

      this.processorState.setState(ClusterProcessorState.STATE_BLOCKED);

      multicastMessageListeners.notifyNodeBlocked();

      final BlockedMarker blockedMarker = new BlockedMarker(processorState.getClusterView().getClusterUUID());
      blockedMarker.setTargetMajorityMarkerListSize(processorState.getTargetMajoritySize());
      blockedMarker.setNextAnnouncementTime(getClock().currentTime());
      blockedMarker.setReceiver(getAddress());
      post(blockedMarker);
   }


   public Queue<Frame> getReceivedFrames() {

      return receivedFrames;
   }


   /**
    * Subscribes a listener for multicast messages. There can be only one listener.
    * <p/>
    * The listener receives a message once it is received.
    * <p/>
    * REVIEWME: simeshev@cacheonix.org - 2008-03-20 - consider moving to a constructor.
    *
    * @param listener to addGroupMembershipSubscriber.
    */
   public void subscribeMulticastMessageListener(final MulticastMessageListener listener) {

      multicastMessageListeners.add(listener);
   }


   /**
    *
    */
   @SuppressWarnings("TooBroadScope")
   public void shutdown() {

      shutdown(ShutdownMode.GRACEFUL_SHUTDOWN);
   }


   public void shutdown(final ShutdownMode shutdownMode) {

      if (shutdownMode.equals(ShutdownMode.FORCED_SHUTDOWN)) {

         beginForcedShutdown();
      } else {

         beginGracefulShutdown();
      }

      waitForShutdown(0);
   }


   /**
    * Waits until a Marker with leave set to us circle twice. The marker than posts a shutdown command that does the
    * hard shut down.
    */
   private void beginGracefulShutdown() {

      Assert.assertTrue(!isProcessorThread(), "This method cannot be called from the processor thread");

      if (isShutdown()) {
         return;
      }

      //
      // Try to decide early if has to shutdown forcibly
      //
      boolean shutdownCacheProcessorsGracefully;
      try {

         // Request size of the ClusterView
         final Request clusterViewSizeRequest = new GetClusterViewSizeRequest();
         clusterViewSizeRequest.setReceiver(getAddress());
         final Integer clusterViewSize = execute(clusterViewSizeRequest);

         // Single size means that it is pointless to wait for cache members to finish
         shutdownCacheProcessorsGracefully = clusterViewSize > 1;
      } catch (final Exception ignored) {

         shutdownCacheProcessorsGracefully = false;
      }

      // Post shutdown messages to cache processors
      beginCacheProcessorShutdown(shutdownCacheProcessorsGracefully);

      // Wait for cache processors to stop
      waitForCacheProcessorsToShutdown();


      // Rise shutdown latch to let the marker know that this
      // ClusterProcessor should start leaving the cluster.
      if (shutdownLatch.compareAndSet(null, new CountDownLatch(1))) {

         // We are in control of shutdown

         //
         // Start timeout in case this ClusterProcessor does not exit in time
         //
         if (leaveTimeout.compareAndSet(null, new LeaveTimeout(this))) {

            leaveTimeout.get().reset();
         }
      }
   }


   /**
    * Shuts down cluster service without waiting.
    *
    * @see MulticastMarker#forward()
    * @see BlockedMarker#forward()
    * @see ShutdownClusterProcessorCommand#execute()
    */
   final void beginForcedShutdown() {

      try {

         // First check if the processor did shutdown but
         // didn't yet cancel the leave timeout.
         if (isShutdown()) {
            return;
         }

         // Create shutdown command
         final ShutdownClusterProcessorCommand shutdownCommand = new ShutdownClusterProcessorCommand(this);

         // Enqueue
         enqueue(shutdownCommand);
      } catch (final InterruptedException e) {

         ExceptionUtils.ignoreException(e, "already shutdown");
      } catch (final ShutdownException e) {

         ExceptionUtils.ignoreException(e, "already shutdown");
      } catch (final RuntimeException e) {

         LOG.warn("Unexpected exception while processing leave timeout: " + e, e); // NOPMD
      }
   }


   /**
    * Shuts down cluster service without waiting. This method is called by a {@link ShutdownClusterProcessorCommand}
    * which is a last command the processor is going to execute.
    *
    * @param shutdownCause the exception that caused the shutdown.
    * @see MulticastMarker#forward()
    * @see BlockedMarker#forward()
    * @see ShutdownClusterProcessorCommand#execute()
    */
   final void forceShutdown(final CacheonixException shutdownCause) {

      Assert.assertTrue(isProcessorThread(), "This method may be called only from the processor thread");

      if (isShutdown()) {
         return;
      }

      // Set shutdown reason
      this.shutdownCause = shutdownCause;

      // Post shutdown messages to cache processors requesting them to shutdown now
      beginCacheProcessorShutdown(false);

      // Wait for cache processors to stop
      waitForCacheProcessorsToShutdown();

      leaveTimeout.compareAndSet(null, new LeaveTimeout(this));
      leaveTimeout.get().shutdown();

      // Shutdown marker timeout
      obtainMarkerTimeout.shutdown();

      // Shutdown message processor
      super.shutdown();

      // Open latch. We use compare and set to make next possible
      // calls to shutdownSoft observe an already open latch.
      shutdownLatch.compareAndSet(null, new CountDownLatch(1));
      shutdownLatch.get().countDown();
   }


   /**
    * Posts {@link ShutdownCacheProcessorMessage} to all CacheProcessors.
    *
    * @param shutdownCacheProcessorsGracefully
    *         true if the CacheProcessor should announce leaving. false if the CacheProcessor should die now.
    */
   private void beginCacheProcessorShutdown(final boolean shutdownCacheProcessorsGracefully) {

      for (final Map.Entry<String, CacheProcessor> entry : cacheProcessors.entrySet()) {

         // Post shutdown message to the cache processor
         try {

            final CacheProcessor cacheProcessor = entry.getValue();
            final String cacheName = cacheProcessor.getCacheName();
            final Message message = new ShutdownCacheProcessorMessage(cacheName, shutdownCacheProcessorsGracefully);
            message.setReceiver(getAddress());
            cacheProcessor.post(message);

            LOG.debug("Requested cache processor: " + cacheName + ':' + getAddress() + " to shutdown");
         } catch (final Exception e) {
            ExceptionUtils.ignoreException(e, "Shutdown in progress");
         }
      }
   }


   /**
    * Waits for all CacheProcessors to die.
    */
   private void waitForCacheProcessorsToShutdown() {

      for (final Map.Entry<String, CacheProcessor> entry : cacheProcessors.entrySet()) {

         final CacheProcessor cacheProcessor = entry.getValue();
         final String cacheName = cacheProcessor.getCacheName();
         final boolean success = cacheProcessor.waitForShutdown(gracefulShutdownTimeoutMillis);
         final String stringResult = success ? "shutdown" : "didn't shutdown";
         LOG.debug("Cache processor: " + cacheName + ':' + getAddress() + ' ' + stringResult + " before timeout " + gracefulShutdownTimeoutMillis + " ms expired ");
      }
   }


   /**
    * Returns a queue that receives message parts and assembles whole messages. Once a whole message is assembled, the
    * queue pushes it out by calling a listener.
    *
    * @return a queue that receives message parts and assembles whole messages.
    */
   public MessageAssembler getMessageAssembler() {

      return messageAssembler;
   }


   /**
    * Resets context by cleaning send and receive queue and resetting all counters.
    */
   public void reset() {

      processorState.setCurrent(null);
      processorState.getReceivedList().clear();
      messageAssembler.clear();
      processorState.getSubmittalQueue().clear();
      processorState.setHighestSequenceNumberDelivered(null);
      awaitingDeliveryNotification.clear();

      // Handle waiters
      getWaiterList().notifyReset();
   }


   public boolean isShuttingDown() {

      return shutdownLatch.get() != null;
   }


   /**
    * @return timeout
    */
   public ActionableTimeout getObtainMarkerTimeout() {

      return obtainMarkerTimeout;
   }


   // -------------------------------------------------------------------------------------
   //
   // Implementing MulticastListener
   //
   // -------------------------------------------------------------------------------------


   /**
    * {@inheritDoc}
    *
    * @param frame
    */
   public final void receiveFrame(final Frame frame) {

      if (frame.getSequenceNumber() >= 0) {

         // This is a reliable multicast frame
         final int state = processorState.getState();
         if (state == ClusterProcessorState.STATE_NORMAL || state == ClusterProcessorState.STATE_CLEANUP) {

            if (frame.getClusterUUID().equals(processorState.getClusterView().getClusterUUID())) {

               receivedFrames.add(frame);
            }
         }
      } else {

         // This is a single-frame, unreliable, message
         try {

            final SerializerFactory instance = SerializerFactory.getInstance();
            final Serializer serializer = instance.getSerializer(frame.getSerializerType());
            final Message message = (Message) serializer.deserialize(frame.getPayload());

            // Set sender IOP address if supported
            if (message instanceof SenderInetAddressAware) {

               ((SenderInetAddressAware) message).setSenderInetAddress(frame.getSenderInetAddress());
            }

            // Right now we support only ClusterAnnouncement
            if (message instanceof ClusterAnnouncement) {

               if (!isShutdown()) {

                  // Adjust time
                  getClock().adjust(message.getTimestamp());

                  // Enqueue
                  enqueue(message);
               }
            }
         } catch (final InterruptedException ignored) {

            Thread.currentThread().interrupt();
         } catch (final Exception e) {

            LOG.error(e.toString(), e);
         }
      }
   }


   /**
    * Delivers assembled requests accumulated in the request assembler.
    */
   public void deliverAssembledMulticastMessages() throws IOException {

      // NOTE: simeshev@cacheonix.org - 2010-09-16 - We call processMessage() with cluster UID
      // check set to <code>false</code> because if the messages are there it means they have been received
      // within the same cluster. If the node would have joined an other cluster, reset() would
      // have cleaned messages up and there wouldn't be messages.

      for (AssembledMessage assembledMessage = messageAssembler.poll(); assembledMessage != null; assembledMessage = messageAssembler.poll()) {

         // Get the actual message
         final Message message = assembledMessage.getMessage();

         // Adjust clock
         getClock().adjust(message.getTimestamp());

         // Set frame number if the message is in the awaitingDeliveryNotification
         if (message instanceof Request && message.getSender().equals(getAddress())) {

            final Request request = (Request) message;

            // Find the message in the list of messages waiting for delivery notification and set frame number
            for (final DeliveryNotificationEntry entry : awaitingDeliveryNotification) {

               if (entry.getRequest().getUuid().equals(request.getUuid())) {

                  // Found the message, the start frame number
                  entry.setStartFrameNumber(assembledMessage.getStartFrameNumber());

                  // Stop iterating
                  break;
               }
            }
         }

         // Deliver according to the destination
         switch (message.getDestination()) {

            case Wireable.DESTINATION_REPLICATED_STATE:

               try {

//                  if (assembledMessage.getStartFrameNumber() < 20) {
                  //noinspection ControlFlowStatementWithoutBraces
//                     if (LOG.isDebugEnabled()) LOG.debug("message: " + message); // NOPMD
//                  }
                  processMessage(message, false);
               } catch (final InterruptedException ignored) {
                  Thread.currentThread().interrupt();
               }
               break;
            case Wireable.DESTINATION_MULTICAST_CLIENT:

               multicastMessageListeners.notify(message);
               break;
            default:
               LOG.warn("Unknown multicast message destination: " + message.getDestination());
               break;
         }
      }
   }


   /**
    * Notify messages waiting for the delivery notification
    *
    * @param frameNumbersAllDeliveredUpTo
    */
   public void notifyDeliveredToAll(final long frameNumbersAllDeliveredUpTo) {

      for (final Iterator<DeliveryNotificationEntry> iter = awaitingDeliveryNotification.iterator(); iter.hasNext(); ) {

         final DeliveryNotificationEntry entry = iter.next();

         if (entry.hasStartFrameNumber() && entry.getStartFrameNumber() <= frameNumbersAllDeliveredUpTo) {

            // Remove
            iter.remove();

            // Notify
            final Request request = entry.getRequest();
            final DeliveryAware waiter = (DeliveryAware) request.getWaiter();
            waiter.notifyDelivered();
         }
      }
   }


   public MulticastMessageListenerList getMulticastMessageListeners() {

      return multicastMessageListeners;
   }


   public void registerCacheProcessor(final CacheProcessor cacheProcessor) {

      cacheProcessors.put(cacheProcessor.getCacheName(), cacheProcessor);
   }


   /**
    * Removes a cache processor from the registry of cache processors.
    *
    * @param cacheName name of the cache processor.
    * @return the removed cache processor or null if there is no a cache processor with the given name.
    */
   public CacheProcessor unregisterCacheProcessor(final String cacheName) {

      return cacheProcessors.remove(cacheName);
   }


   /**
    * {@inheritDoc}
    */
   public void enqueue(final Command command) throws InterruptedException, ShutdownException {

      // Check if already shutdown
      if (isShutdown()) {


         if (shutdownCause != null) {

            throw shutdownCause;
         } else {

            throw new ShutdownException();
         }
      }

      //
      if (command instanceof Message) {

         // Process message
         final Message message = (Message) command;

         switch (message.getDestination()) {

            case Wireable.DESTINATION_MULTICAST_CLIENT:
            case Wireable.DESTINATION_REPLICATED_STATE:

               // Set time
               message.setTimestamp(getClock().currentTime());

               // Send for prepare or put into submittal queue
               if (message instanceof Prepareable) {

                  final Prepareable prepareable = (Prepareable) message;
                  if (prepareable.isPrepared()) {

                     addToSubmittalQueue(message);
                  } else {

                     // First run for prepareable
                     super.enqueue(message);
                  }
               } else {

                  addToSubmittalQueue(message);
               }
               break;
            default:

               // Not an mcast message
               super.enqueue(message);
               break;
         }
      } else {

         super.enqueue(command);
      }
   }


   /**
    * Partitions a message into frames and adds the message to the submittal queue.
    *
    * @param message the message to add to the submittal queue.
    */
   private void addToSubmittalQueue(final Message message) {

      // Add to the list of delivery-aware messages
      if (message instanceof Request && ((Request) message).getWaiter() instanceof DeliveryAware) {

         awaitingDeliveryNotification.add(new DeliveryNotificationEntry((Request) message));
      }

      // Add to the queue
      processorState.getSubmittalQueue().add(partitioner.partition(message));
   }


   private static long adjustClusterSurveyTimeout(final long clusterSurveyTimeoutMillis,
                                                  final long clusterAnnouncementTimeoutMillis) {

      return clusterSurveyTimeoutMillis < clusterAnnouncementTimeoutMillis * 4 ? clusterAnnouncementTimeoutMillis * 4 : clusterSurveyTimeoutMillis;
   }


   private static long adjustHomeAloneTimeout(final long homeAloneTimeoutMillis,
                                              final long clusterSurveyTimeoutMillis) {

      return homeAloneTimeoutMillis < clusterSurveyTimeoutMillis * 3 ? clusterSurveyTimeoutMillis * 3 : homeAloneTimeoutMillis;
   }


   public ClusterProcessorState getProcessorState() {

      return processorState;
   }
}
