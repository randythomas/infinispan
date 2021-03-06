/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009 Red Hat Inc. and/or its affiliates and other
 * contributors as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a full listing of
 * individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.infinispan;

import org.infinispan.api.BasicCache;
import org.infinispan.config.Configuration;
import org.infinispan.lifecycle.ComponentStatus;
import org.infinispan.loaders.CacheStore;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.notifications.Listenable;
import org.infinispan.util.concurrent.NotifyingFuture;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * The central interface of Infinispan.  A Cache provides a highly concurrent, optionally distributed data structure
 * with additional features such as:
 * <p/>
 * <ul> <li>JTA transaction compatibility</li> <li>Eviction support for evicting entries from memory to prevent {@link
 * OutOfMemoryError}s</li> <li>Persisting entries to a {@link CacheStore}, either when they are evicted as an overflow,
 * or all the time, to maintain persistent copies that would withstand server failure or restarts.</li> </ul>
 * <p/>
 * <p/>
 * <p/>
 * For convenience, Cache extends {@link ConcurrentMap} and implements all methods accordingly, although methods like
 * {@link ConcurrentMap#keySet()}, {@link ConcurrentMap#values()} and {@link ConcurrentMap#entrySet()} are expensive
 * (prohibitively so when using a distributed cache) and frequent use of these methods is not recommended.
 * <p /> 
 * Other methods such as {@link #size()} provide an approximation-only, and should not be relied on for an accurate picture
 * as to the size of the entire, distributed cache.  Remote nodes are <i>not</i> queried and in-fly transactions are not
 * taken into account, even if {@link #size()} is invoked from within such a transaction.
 * <p/>
 * Also, like many {@link ConcurrentMap} implementations, Cache does not support the use of <tt>null</tt> keys or
 * values.
 * <p/>
 * <h3>Unsupported operations</h3>
 * <p>{@link #containsValue(Object)}</p>
 * <h3>Asynchronous operations</h3> Cache also supports the use of "async" remote operations.  Note that these methods
 * only really make sense if you are using a clustered cache.  I.e., when used in LOCAL mode, these "async" operations
 * offer no benefit whatsoever.  These methods, such as {@link #putAsync(Object, Object)} offer the best of both worlds
 * between a fully synchronous and a fully asynchronous cache in that a {@link NotifyingFuture} is returned.  The
 * <tt>NotifyingFuture</tt> can then be ignored or thrown away for typical asynchronous behaviour, or queried for
 * synchronous behaviour, which would block until any remote calls complete.  Note that all remote calls are, as far as
 * the transport is concerned, synchronous.  This allows you the guarantees that remote calls succeed, while not
 * blocking your application thread unnecessarily.  For example, usage such as the following could benefit from the
 * async operations:
 * <pre>
 *   NotifyingFuture f1 = cache.putAsync("key1", "value1");
 *   NotifyingFuture f2 = cache.putAsync("key2", "value2");
 *   NotifyingFuture f3 = cache.putAsync("key3", "value3");
 *   f1.get();
 *   f2.get();
 *   f3.get();
 * </pre>
 * The net result is behavior similar to synchronous RPC calls in that at the end, you have guarantees that all calls
 * completed successfully, but you have the added benefit that the three calls could happen in parallel.  This is
 * especially advantageous if the cache uses distribution and the three keys map to different cache instances in the
 * cluster.
 * <p/>
 * Also, the use of async operations when within a transaction return your local value only, as expected.  A
 * NotifyingFuture is still returned though for API consistency.
 * <p/>
 * <h3>Constructing a Cache</h3> An instance of the Cache is usually obtained by using a {@link org.infinispan.manager.CacheContainer}.
 * <pre>
 *   CacheManager cm = new DefaultCacheManager(); // optionally pass in a default configuration
 *   Cache c = cm.getCache();
 * </pre>
 * See the {@link org.infinispan.manager.CacheContainer} interface for more details on providing specific configurations, using multiple caches
 * in the same JVM, etc.
 * <p/>
 * Please see the <a href="http://www.jboss.org/infinispan/docs">Infinispan documentation</a> and/or the <a
 * href="http://www.jboss.org/community/wiki/5minutetutorialonInfinispan">5 Minute Usage Tutorial</a> for more details.
 * <p/>
 *
 * @author Mircea.Markus@jboss.com
 * @author Manik Surtani
 * @author Galder Zamarreño
 * @see org.infinispan.manager.CacheContainer
 * @see DefaultCacheManager
 * @see <a href="http://www.jboss.org/infinispan/docs">Infinispan documentation</a>
 * @see <a href="http://www.jboss.org/community/wiki/5minutetutorialonInfinispan">5 Minute Usage Tutorial</a>
 * @since 4.0
 */
public interface Cache<K, V> extends BasicCache<K, V>, Listenable {
   /**
    * Under special operating behavior, associates the value with the specified key. <ul> <li> Only goes through if the
    * key specified does not exist; no-op otherwise (similar to {@link ConcurrentMap#putIfAbsent(Object, Object)})</i>
    * <li> Force asynchronous mode for replication to prevent any blocking.</li> <li> invalidation does not take place.
    * </li> <li> 0ms lock timeout to prevent any blocking here either. If the lock is not acquired, this method is a
    * no-op, and swallows the timeout exception.</li> <li> Ongoing transactions are suspended before this call, so
    * failures here will not affect any ongoing transactions.</li> <li> Errors and exceptions are 'silent' - logged at a
    * much lower level than normal, and this method does not throw exceptions</li> </ul> This method is for caching data
    * that has an external representation in storage, where, concurrent modification and transactions are not a
    * consideration, and failure to put the data in the cache should be treated as a 'suboptimal outcome' rather than a
    * 'failing outcome'.
    * <p/>
    * An example of when this method is useful is when data is read from, for example, a legacy datastore, and is cached
    * before returning the data to the caller.  Subsequent calls would prefer to get the data from the cache and if the
    * data doesn't exist in the cache, fetch again from the legacy datastore.
    * <p/>
    * See <a href="http://jira.jboss.com/jira/browse/JBCACHE-848">JBCACHE-848</a> for details around this feature.
    * <p/>
    *
    * @param key   key with which the specified value is to be associated.
    * @param value value to be associated with the specified key.
    * @throws IllegalStateException if {@link #getStatus()} would not return {@link ComponentStatus#RUNNING}.
    */
   void putForExternalRead(K key, V value);

   /**
    * Evicts an entry from the memory of the cache.  Note that the entry is <i>not</i> removed from any configured cache
    * stores or any other caches in the cluster (if used in a clustered mode).  Use {@link #remove(Object)} to remove an
    * entry from the entire cache system.
    * <p/>
    * This method is designed to evict an entry from memory to free up memory used by the application.  This method uses
    * a 0 lock acquisition timeout so it does not block in attempting to acquire locks.  It behaves as a no-op if the
    * lock on the entry cannot be acquired <i>immediately</i>.
    * <p/>
    * Important: this method should not be called from within a transaction scope.  
    *
    * @param key key to evict
    */
   void evict(K key);

   @Deprecated
   Configuration getConfiguration();
   
   org.infinispan.configuration.cache.Configuration getCacheConfiguration();

   /**
    * Starts a batch.  All operations on the current client thread are performed as a part of this batch, with locks
    * held for the duration of the batch and any remote calls delayed till the end of the batch.
    * <p/>
    *
    * @return true if a batch was successfully started; false if one was available and already running.
    */
   public boolean startBatch();

   /**
    * Completes a batch if one has been started using {@link #startBatch()}.  If no batch has been started, this is a
    * no-op.
    * <p/>
    *
    * @param successful if true, the batch completes, otherwise the batch is aborted and changes are not committed.
    */
   public void endBatch(boolean successful);

   /**
    * Retrieves the cache manager responsible for creating this cache instance.
    *
    * @return a cache manager
    */
   EmbeddedCacheManager getCacheManager();

   AdvancedCache<K, V> getAdvancedCache();

   /**
    * Method that releases object references of cached objects held in the cache by serializing them to byte buffers.
    * Cached objects are lazily de-serialized when accessed again, based on the calling thread's context class loader.
    * <p/>
    * This can be expensive, based on the effort required to serialize cached objects.
    * <p/>
    */
   void compact();

   ComponentStatus getStatus();

   /**
    * Returns a set view of the keys contained in this cache. This set is immutable, so it cannot be modified 
    * and changes to the cache won't be reflected in the set. When this method is called on a cache configured with 
    * distribution mode, the set returned only contains the keys locally available in the cache instance. To avoid 
    * memory issues, there will be not attempt to bring keys from other nodes.
    * <p/>
    * This method should only be used for debugging purposes such as to verify that the cache contains all the keys 
    * entered. Any other use involving execution of this method on a production system is not recommended.
    * <p/>
    * 
    * @return a set view of the keys contained in this cache.
    */
   Set<K> keySet();

   /**
    * Returns a collection view of the values contained in this cache. This collection is immutable, so it cannot be modified 
    * and changes to the cache won't be reflected in the set. When this method is called on a cache configured with 
    * distribution mode, the collection returned only contains the values locally available in the cache instance. To avoid 
    * memory issues, there is not attempt to bring values from other nodes.
    * <p/>
    * This method should only be used for testing or debugging purposes such as to verify that the cache contains all the 
    * values entered. Any other use involving execution of this method on a production system is not recommended.
    * <p/>
    * 
    * @return a collection view of the values contained in this map.
    */
   Collection<V> values();
   
   /**
    * Returns a set view of the mappings contained in this cache. This set is immutable, so it cannot be modified 
    * and changes to the cache won't be reflected in the set. Besides, each element in the returned set is an immutable 
    * {@link Map.Entry}. When this method is called on a cache configured with distribution mode, the set returned only 
    * contains the mappings locally available in the cache instance. To avoid memory issues, there will be not attempt 
    * to bring mappings from other nodes.
    * <p/>
    * This method should only be used for debugging purposes such as to verify that the cache contains all the mappings 
    * entered. Any other use involving execution of this method on a production system is not recommended.
    * <p/>
    * 
    * @return a set view of the mappings contained in this cache.
    */
   Set<Map.Entry<K, V>> entrySet();
}
