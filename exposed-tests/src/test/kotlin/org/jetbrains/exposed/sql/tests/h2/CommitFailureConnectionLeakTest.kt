package org.jetbrains.exposed.sql.tests.h2

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.statements.StatementInterceptor
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.experimental.continueSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.transactions.transactionManager
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.sql.SQLException
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A connection borrowed by a suspended transaction must go back to the pool however the transaction
 * ends, including when commit() itself throws (a statement timeout on COMMIT, a deferred constraint,
 * or a DAO entity flush). Pools such as Hikari only evict connections on fatal SQL states, so a
 * connection that is never closed stays checked out for the life of the JVM.
 */
class CommitFailureConnectionLeakTest {

    object UniqueKeys : Table("COMMIT_LEAK_UNIQUE_KEYS") {
        val k = varchar("k", 50).uniqueIndex()
    }

    object UniqueKeyRecords : IntIdTable("COMMIT_LEAK_UNIQUE_KEY_RECORDS") {
        val k = varchar("k", 50).uniqueIndex()
    }

    class UniqueKeyRecord(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<UniqueKeyRecord>(UniqueKeyRecords)

        var k by UniqueKeyRecords.k
    }

    private val failingCommit = object : StatementInterceptor {
        override fun beforeCommit(transaction: Transaction) {
            throw SQLException("commit failed")
        }
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
            jdbcUrl = "jdbc:h2:mem:commit_leak_${UUID.randomUUID()};DB_CLOSE_DELAY=-1"
            maximumPoolSize = 1
            connectionTimeout = 250
        })
        db = Database.connect(dataSource)
        transaction(db) {
            SchemaUtils.create(UniqueKeys, UniqueKeyRecords)
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

    @Test
    fun returnsTheConnectionWhenTheTransactionCommits() {
        suspendedTransaction { UniqueKeys.insert { it[k] = "a" } }

        assertEquals(0, activeConnections)
    }

    @Test
    fun returnsTheConnectionWhenTheTransactionBlockThrows() {
        assertFailsWith<IllegalStateException> {
            suspendedTransaction {
                UniqueKeys.insert { it[k] = "a" }
                throw IllegalStateException("boom")
            }
        }

        assertEquals(0, activeConnections)
    }

    @Test
    fun returnsTheConnectionWhenCommitThrows() {
        var blockCompleted = false

        assertFailsWith<SQLException> {
            suspendedTransaction {
                registerInterceptor(failingCommit)
                UniqueKeys.insert { it[k] = "a" }
                blockCompleted = true
            }
        }

        assertTrue(blockCompleted, "the failure must come from commit(), not from a statement in the block")
        assertEquals(0, activeConnections)
    }

    @Test
    fun servesTheNextTransactionAfterCommitThrows() {
        assertFailsWith<SQLException> {
            suspendedTransaction {
                registerInterceptor(failingCommit)
                UniqueKeys.insert { it[k] = "a" }
            }
        }

        // With maximumPoolSize = 1 a leaked connection makes this fail with
        // "Connection is not available, request timed out after 250ms".
        suspendedTransaction { UniqueKeys.insert { it[k] = "b" } }
        assertEquals(0, activeConnections)
    }

    @Test
    fun dslConstraintViolationThrowsInsideTheBlockAndReturnsTheConnection() {
        var blockCompleted = false

        assertFailsWith<SQLException> {
            suspendedTransaction {
                UniqueKeys.insert { it[k] = "dup" }
                UniqueKeys.insert { it[k] = "dup" }
                blockCompleted = true
            }
        }

        assertFalse(blockCompleted, "the DSL executes each insert immediately, so the second one fails inside the block")
        assertEquals(0, activeConnections)
    }

    @Test
    fun daoReturnsTheConnectionWhenTheEntityFlushAtCommitThrows() {
        var blockCompleted = false

        assertFailsWith<SQLException> {
            suspendedTransaction {
                UniqueKeyRecord.new { k = "dup" }
                UniqueKeyRecord.new { k = "dup" }
                blockCompleted = true
            }
        }

        assertTrue(blockCompleted, "DAO inserts are buffered and only flushed by the beforeCommit hook inside commit()")
        assertEquals(0, activeConnections)
    }
}
