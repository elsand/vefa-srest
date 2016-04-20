package no.sr.ringo.guice.jdbc;

import com.google.inject.Inject;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Looks for all @Transactional annotations and injects code for starting and stopping transactions.
 *
 * User: andy
 * Date: 8/13/12
 * Time: 2:19 PM
 */
public class TransactionalMethodInterceptor implements MethodInterceptor {
    static final Logger log = LoggerFactory.getLogger(TransactionalMethodInterceptor.class);

    @Inject
    JdbcTxManager jdbcTxManager;

    /**
     * Starts a jdbc transaction if a transaction doesnt already exist.
     * Joins the transaction if one exists.
     *
     * @param invocation the method invocation joinpoint
     * @return the result of the call to {@link
     *         org.aopalliance.intercept.Joinpoint#proceed()}, might be intercepted by the
     *         interceptor.
     * @throws Throwable if the interceptors or the
     *                   target-object throws an exception.
     *         IllegalStateException if there already exists a connection which is not transactional
     */
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {

        //we need to find out whether or not there is an existing transaction or an existing Connection
        final boolean transaction = jdbcTxManager.isTransaction();
        final boolean connection = jdbcTxManager.isConnection();

        //If there is a transaction running do nothing as the transaction will be cleaned up by the
        //code which created the transaction
        if (transaction) {
            //tx already exists so continues operation.
            jdbcTxManager.trace(String.format("Transaction already exists so not starting a new one when calling method: %s", invocation.getMethod().getName()));
            return invocation.proceed();
        }

        //If there is a connection we have decided that this is an error because it means that
        //a non transactional method in a repository is calling a transactional method elsewhere.
        //which we believe is BAD DESIGN. (It would be possible to implement using a separate variable
        //for the transactional connection if we ever change our minds ;))
        if (connection) {
            throw new IllegalStateException("Unable to start a transaction, there already exists a connection which is not transactional" + invocation.getMethod().getName());
        }

        try {
            // Starts the transaction by setting the autocommit value to be false on the connection.
            jdbcTxManager.newConnection(false);
            jdbcTxManager.trace("Started new transaction due to annotation on method: "  + invocation.getMethod().getName());
            //makes the call to the method that is being wrapped.
            Object returnValue = invocation.proceed();

            // Tries to commit the transaction.
            // it is still possible that the TxManager will rollback the transaction,
            // but as far as we are concerned our code worked as expected
            jdbcTxManager.commit();

            //returns the result of the wrapped method call
            return returnValue;
        } catch (Throwable thr) {
            //if an exception is thrown we need to rollback the transaction.
            jdbcTxManager.trace("Rolling back transaction due to exception: " + thr.getMessage());
            jdbcTxManager.rollback();
            jdbcTxManager.trace("Rolling back transaction ok");
            //rethrows the exception so that it can be handled by the calling code
            throw thr;
        } finally {
            //Essential that we clean up as we are placing connections on thread local
            jdbcTxManager.cleanUp();
        }
    }
}
