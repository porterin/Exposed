package org.jetbrains.exposed.sql.tests.h2

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.experimental.continueSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.transactions.transactionManager
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.test.assertEquals

/**
 * A suspended transaction must not borrow a pooled connection before its body runs. The body is
 * dispatched with async { }.await(), so a connection taken on the caller's thread is held while the
 * body waits for a dispatcher thread. Once every dispatcher thread is blocked waiting for a connection,
 * the pool and the dispatcher deadlock until connectionTimeout fires.
 */
class SuspendedTransactionConnectionBorrowTest {

    object Rows : Table("SUSPENDED_BORROW_ROWS") {
        val v = integer("v")
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
        dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:h2:mem:suspended_borrow_${UUID.randomUUID()};DB_CLOSE_DELAY=-1"
            maximumPoolSize = 1
            connectionTimeout = 250
        })
        db = Database.connect(dataSource)
        transaction(db) {
            SchemaUtils.create(Rows)
        }
    }

    @After
    fun after() {
        dataSource.close()
        TransactionManager.resetCurrent(currentDB?.transactionManager)
    }

    @Test
    fun newSuspendedTransactionDoesNotBorrowAConnectionBeforeTheFirstStatement() {
        val activeAtBodyStart = runBlocking(Dispatchers.IO) {
            newSuspendedTransaction(db = db) {
                val before = activeConnections
                Rows.insert { it[v] = 1 }
                before
            }
        }

        assertEquals(0, activeAtBodyStart)
        assertEquals(0, activeConnections)
    }

    @Test
    fun continueSuspendedTransactionDoesNotBorrowAConnectionBeforeTheFirstStatement() {
        val activeAtBodyStart = runBlocking(Dispatchers.IO) {
            continueSuspendedTransaction(db = db) {
                val before = activeConnections
                Rows.insert { it[v] = 1 }
                before
            }
        }

        assertEquals(0, activeAtBodyStart)
        assertEquals(0, activeConnections)
    }

    @Test
    fun concurrentTransactionsOnASmallDispatcherDoNotDeadlockThePool() {
        // 2 dispatcher threads, 1 pooled connection, 20 concurrent transactions. With an eager borrow,
        // one caller holds the connection while its body waits for a thread, both threads block in
        // getConnection, and the waiters fail with "Connection is not available" after 250ms.
        val executor = Executors.newFixedThreadPool(2)
        try {
            val dispatcher = executor.asCoroutineDispatcher()
            runBlocking {
                withContext(dispatcher) {
                    (1..20).map { i ->
                        async { continueSuspendedTransaction(dispatcher, db) { Rows.insert { it[v] = i } } }
                    }.awaitAll()
                }
            }
        } finally {
            executor.shutdownNow()
        }

        val count = transaction(db) { Rows.selectAll().count() }
        assertEquals(20, count.toInt())
        assertEquals(0, activeConnections)
    }
}
