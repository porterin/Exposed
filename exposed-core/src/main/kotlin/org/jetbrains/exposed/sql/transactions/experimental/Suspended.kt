package org.jetbrains.exposed.sql.transactions.experimental

import kotlinx.coroutines.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.exposedLogger
import org.jetbrains.exposed.sql.transactions.*
import org.slf4j.LoggerFactory
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

private val logger = LoggerFactory.getLogger("org.jetbrains.exposed.sql.transactions.experimental.SuspendedKt")

internal class TransactionContext(val manager: TransactionManager?, val transaction: Transaction?)

internal class TransactionScope(internal val tx: Transaction, parent: CoroutineContext) : CoroutineScope, CoroutineContext.Element {
    private val baseScope = CoroutineScope(parent)
    override val coroutineContext get() = baseScope.coroutineContext + this
    override val key = Companion

    companion object : CoroutineContext.Key<TransactionScope>
}

internal class TransactionCoroutineElement(val newTransaction: Transaction, manager: TransactionManager) : ThreadContextElement<TransactionContext> {
    override val key: CoroutineContext.Key<TransactionCoroutineElement> = Companion
    private val tlManager = manager as? ThreadLocalTransactionManager

    override fun updateThreadContext(context: CoroutineContext): TransactionContext {
        val currentTransaction = TransactionManager.currentOrNull()
        val currentManager = currentTransaction?.db?.transactionManager
        tlManager?.let {
            it.threadLocal.set(newTransaction)
            TransactionManager.resetCurrent(it)
        }
        return TransactionContext(currentManager, currentTransaction)
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: TransactionContext) {

        if (oldState.transaction == null)
            tlManager?.threadLocal?.remove()
        else
            tlManager?.threadLocal?.set(oldState.transaction)
        TransactionManager.resetCurrent(oldState.manager)
    }

    companion object : CoroutineContext.Key<TransactionCoroutineElement>
}

suspend fun <T> newSuspendedTransaction(context: CoroutineDispatcher? = null, db: Database? = null, statement: suspend Transaction.() -> T): T =
    withTransactionScope(context, null, db) {
        val jobId = this.hashCode()
        val txId = tx.id
        val connectionCode = tx.connection.hashCode()

        logger.debug("Executing {} newSuspendedTransaction in transaction: {} and connection: {}", jobId, txId, connectionCode)
        suspendedTransactionAsyncInternal(true, statement).await()
            .also { logger.debug("Execution {} newSuspendedTransaction in transaction: {} and connection: {} complete", jobId, txId, connectionCode) }
    }

suspend fun <T> Transaction.suspendedTransaction(context: CoroutineDispatcher? = null, statement: suspend Transaction.() -> T): T =
    withTransactionScope(context, this) {
        val jobId = this.hashCode()
        val txId = tx.id
        val connectionCode = tx.connection.hashCode()

        logger.debug("Executing {} suspendedTransaction in transaction: {} and connection: {}", jobId, txId, connectionCode)
        suspendedTransactionAsyncInternal(false, statement).await()
            .also { logger.debug("Execution {} suspendedTransaction in transaction: {} and connection: {} complete", jobId, txId, connectionCode) }
    }

suspend fun <T> continueSuspendedTransaction(context: CoroutineDispatcher? = null, db: Database? = null, statement: suspend Transaction.() -> T): T {
    val currentTransaction = coroutineContext[TransactionScope]?.tx
    logger.debug("continueSuspendTransaction called with transaction: {} and connection: {}", currentTransaction?.id, currentTransaction?.connection?.hashCode())
    return when (currentTransaction) {
        null -> newSuspendedTransaction(context, db, statement)
        else -> currentTransaction.suspendedTransaction(context, statement)
    }
}

private fun Transaction.commitInAsync() {
    val currentTransaction = TransactionManager.currentOrNull()
    try {
        val temporaryManager = this.db.transactionManager
        (temporaryManager as? ThreadLocalTransactionManager)?.threadLocal?.set(this)
        TransactionManager.resetCurrent(temporaryManager)
        try {
            commit()
            try {
                currentStatement?.let {
                    it.closeIfPossible()
                    currentStatement = null
                }
                closeExecutedStatements()
            } catch (e: Exception) {
                exposedLogger.warn("Statements close failed", e)
            }
            closeLoggingException { exposedLogger.warn("Transaction close failed: ${it.message}. Statement: $currentStatement", it) }
        } catch (e: Exception) {
            rollbackLoggingException { exposedLogger.warn("Transaction rollback failed: ${it.message}. Statement: $currentStatement", it) }
            throw e
        }
    } finally {
        val transactionManager = currentTransaction?.db?.transactionManager
        (transactionManager as? ThreadLocalTransactionManager)?.threadLocal?.set(currentTransaction)
        TransactionManager.resetCurrent(transactionManager)
    }
}

suspend fun <T> suspendedTransactionAsync(context: CoroutineDispatcher? = null, db: Database? = null,
                                          statement: suspend Transaction.() -> T) : Deferred<T> {
    val currentTransaction = TransactionManager.currentOrNull()
    return withTransactionScope(context, null, db) {
        suspendedTransactionAsyncInternal(currentTransaction != tx, statement)
    }
}

private suspend fun <T> withTransactionScope(context: CoroutineContext?,
                                             currentTransaction: Transaction?,
                                             db: Database? = null,
                                             body: suspend TransactionScope.() -> T) : T {
    val currentScope = coroutineContext[TransactionScope]
    suspend fun newScope(_tx: Transaction?) : T {
        val manager = (_tx?.db ?: db)?.transactionManager ?: TransactionManager.manager

        val tx = _tx ?: manager.newTransaction(manager.defaultIsolationLevel)

        val element = TransactionCoroutineElement(tx, manager)

        val newContext = context ?: coroutineContext

       return TransactionScope(tx, newContext + element).body()
    }
    val sameTransaction = currentTransaction == currentScope?.tx
    val sameContext = context == coroutineContext
    return when {
        currentScope == null -> newScope(currentTransaction)
        sameTransaction && sameContext -> currentScope.body()
        else -> newScope(currentTransaction)
    }
}

private fun <T> TransactionScope.suspendedTransactionAsyncInternal(shouldCommit: Boolean,
                                                          statement: suspend Transaction.() -> T) : Deferred<T>
    = async {
            try {
                tx.statement()
            } catch (e: Throwable) {
                tx.rollbackLoggingException { exposedLogger.warn("Transaction rollback failed: ${it.message}. Statement: ${tx.currentStatement}", it) }
                throw e
            } finally {
                if (shouldCommit) tx.commitInAsync()
            }
        }
