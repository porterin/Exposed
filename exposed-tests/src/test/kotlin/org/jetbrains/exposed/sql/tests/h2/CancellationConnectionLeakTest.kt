package org.jetbrains.exposed.sql.tests.h2

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.experimental.continueSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.experimental.suspendedTransactionAsync
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.transactions.transactionManager
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A connection borrowed by a suspended transaction must go back to the pool when the calling coroutine
 * is cancelled, whether the cancellation arrives before the transaction block starts or while it runs.
 *
 * newSuspendedTransaction reads tx.connection for a debug log before it starts the async block whose
 * finally closes the transaction. If the caller is already cancelled at that point, the async block
 * never starts, so its finally never runs. Upstream fixed this class of bug in Exposed 1.3.0 (EXPOSED-790).
 */
class CancellationConnectionLeakTest {

    private companion object {
        const val KNOWN_LEAK = "Known leak: the connection newSuspendedTransaction acquires for its debug log is never closed " +
            "when the caller is cancelled before the transaction block is dispatched. Fails when enabled, see " +
            "porterin/Exposed test/cancellation-connection-leak PR body."
    }

    object Keys : Table("CANCEL_LEAK_KEYS") {
        val k = varchar("k", 50)
    }

    private lateinit var dataSource: HikariDataSource
    private lateinit var db: Database
    private var currentDB: Database? = null

    private val activeConnections get() = dataSource.hikariPoolMXBean.activeConnections

    @Before
    fun before() {
        if (TransactionManager.isInitialized()) {
            currentDB = TransactionManager.currentOrNull()?.db
        }
        connect(connectionTimeoutMs = 250)
    }

    private fun connect(connectionTimeoutMs: Long) {
        dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:h2:mem:cancel_leak_${UUID.randomUUID()};DB_CLOSE_DELAY=-1"
            maximumPoolSize = 1
            connectionTimeout = connectionTimeoutMs
        })
        db = Database.connect(dataSource)
        transaction(db) {
            SchemaUtils.create(Keys)
        }
    }

    @After
    fun after() {
        dataSource.close()
        TransactionManager.resetCurrent(currentDB?.transactionManager)
    }

    private fun <T> suspendedTransaction(statement: suspend Transaction.() -> T): T = runBlocking(Dispatchers.IO) {
        continueSuspendedTransaction(db = db, statement = statement)
    }

    /**
     * Runs [transactionCall] in a coroutine that has already cancelled itself, and asserts that it ended with
     * a CancellationException without running the transaction block.
     */
    private fun callFromCancelledCoroutine(transactionCall: suspend (block: suspend Transaction.() -> Unit) -> Unit) {
        var blockRan = false
        var cancelled = false

        runBlocking(Dispatchers.IO) {
            launch {
                coroutineContext.cancel()
                try {
                    transactionCall { blockRan = true }
                } catch (e: CancellationException) {
                    cancelled = true
                    throw e
                }
            }.join()
        }

        assertTrue(cancelled, "the transaction call must end with a CancellationException")
        assertFalse(blockRan, "the caller was cancelled before the transaction block was dispatched")
    }

    private fun assertConnectionReturnedAndPoolServesNextTransaction() {
        assertEquals(0, activeConnections)
        // With maximumPoolSize = 1 a leaked connection makes this fail with
        // "Connection is not available, request timed out after ...ms".
        suspendedTransaction { Keys.insert { it[k] = "next" } }
        assertEquals(0, activeConnections)
    }

    // Case 1: the calling coroutine is cancelled before the transaction block runs.

    @Test
    @Ignore(KNOWN_LEAK)
    fun newSuspendedTransactionReturnsTheConnectionWhenTheCallerIsAlreadyCancelled() {
        callFromCancelledCoroutine { block -> newSuspendedTransaction(db = db, statement = block) }

        assertConnectionReturnedAndPoolServesNextTransaction()
    }

    @Test
    @Ignore(KNOWN_LEAK)
    fun continueSuspendedTransactionReturnsTheConnectionWhenTheCallerIsAlreadyCancelled() {
        callFromCancelledCoroutine { block -> continueSuspendedTransaction(db = db, statement = block) }

        assertConnectionReturnedAndPoolServesNextTransaction()
    }

    @Test
    fun suspendedTransactionAsyncReturnsTheConnectionWhenTheCallerIsAlreadyCancelled() {
        callFromCancelledCoroutine { block -> suspendedTransactionAsync(db = db, statement = block).await() }

        assertConnectionReturnedAndPoolServesNextTransaction()
    }

    // Case 2 (control): the calling coroutine is cancelled while the transaction block runs.

    @Test
    fun returnsTheConnectionWhenATimeoutCancelsTheTransactionBlock() {
        var blockStarted = false

        runBlocking(Dispatchers.IO) {
            try {
                withTimeout(50) {
                    newSuspendedTransaction(db = db) {
                        Keys.insert { it[k] = "a" }
                        blockStarted = true
                        delay(1000)
                    }
                }
            } catch (e: CancellationException) {
            }
        }

        assertTrue(blockStarted, "the timeout must fire while the transaction block is suspended")
        assertConnectionReturnedAndPoolServesNextTransaction()
    }

    @Test
    fun returnsTheConnectionWhenTheCallerIsCancelledWhileTheTransactionBlockIsSuspended() {
        val blockStarted = CompletableDeferred<Unit>()

        runBlocking(Dispatchers.IO) {
            val job = launch {
                newSuspendedTransaction(db = db) {
                    Keys.insert { it[k] = "a" }
                    blockStarted.complete(Unit)
                    delay(Long.MAX_VALUE)
                }
            }
            blockStarted.await()
            job.cancelAndJoin()
        }

        assertConnectionReturnedAndPoolServesNextTransaction()
    }

    // Case 3: the caller is cancelled while it waits for a pooled connection, which is what request timeouts
    // do when the pool is exhausted. Today newSuspendedTransaction waits in the debug log's tx.connection read,
    // before the transaction block is dispatched, so the connection it then gets is never closed.

    @Test
    @Ignore(KNOWN_LEAK)
    fun returnsTheConnectionWhenTheCallerIsCancelledWhileWaitingForAPooledConnection() {
        // Hikari reads connectionTimeout only when the pool starts, so this case needs its own pool with
        // enough headroom for the steps below.
        dataSource.close()
        connect(connectionTimeoutMs = 5_000)
        val heldConnection = dataSource.connection

        runBlocking(Dispatchers.IO) {
            val job = launch {
                newSuspendedTransaction(db = db) { Keys.insert { it[k] = "a" } }
            }
            // The pool's only connection is held above, so the transaction now blocks in getConnection().
            withTimeout(4_000) {
                while (dataSource.hikariPoolMXBean.threadsAwaitingConnection != 1) delay(1)
            }
            job.cancel()
            heldConnection.close()
            job.join()
        }

        assertConnectionReturnedAndPoolServesNextTransaction()
    }
}
