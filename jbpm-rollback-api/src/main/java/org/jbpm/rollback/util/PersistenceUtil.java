package org.jbpm.rollback.util;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.persistence.EntityManager;
import javax.persistence.TransactionRequiredException;
import javax.transaction.NotSupportedException;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.UserTransaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PersistenceUtil {

	private static final Logger logger = LoggerFactory.getLogger(PersistenceUtil.class);
    private static final String[] KNOWN_UT_JNDI_KEYS = new String[] {"UserTransaction", "java:jboss/UserTransaction", System.getProperty("jbpm.ut.jndi.lookup")};

	private final boolean isJTA;
	
	public PersistenceUtil(boolean isJTA) {
		this.isJTA = isJTA;
	}

    /**
     * This method opens a new transaction, if none is currently running, and joins the entity manager/persistence context
     * to that transaction. 
     * @param em The entity manager we're using. 
     * @return {@link UserTransaction} If we've started a new transaction, then we return it so that it can be closed. 
     * @throws NotSupportedException 
     * @throws SystemException 
     * @throws Exception if something goes wrong. 
     */
    public UserTransaction joinTransaction(EntityManager em) {
        boolean newTx = false;
        UserTransaction ut = null;
        if (isJTA) {
	        try {
	        	em.joinTransaction();
	        } catch (TransactionRequiredException e) {
				ut = findUserTransaction();
				try {
					if( ut != null && ut.getStatus() == Status.STATUS_NO_TRANSACTION ) { 
		                ut.begin();
		                newTx = true;
		                // since new transaction was started em must join it
		                em.joinTransaction();
		            } 
				} catch(Exception ex) {
					throw new IllegalStateException("Unable to find or open a transaction: " + ex.getMessage(), ex);
				}
				if (!newTx) {
	            	// rethrow TransactionRequiredException if UserTransaction was not found or started
	            	throw e;
	            }
			}
	        if( newTx ) { 
	            return ut;
	        }
        }
        return null;
    }

    /**
     * This method closes the entity manager and transaction. It also makes sure that any objects associated 
     * with the entity manager/persistence context are detached. 
     * </p>
     * Obviously, if the transaction returned by the {@link #joinTransaction(EntityManager)} method is null, 
     * nothing is done with the transaction parameter.
     * @param em The entity manager.
     * @param ut The (user) transaction.
     */
    public void commitTransaction(EntityManager em, UserTransaction ut) {
        em.flush(); // This saves any changes made
        em.clear(); // This makes sure that any returned entities are no longer attached to this entity manager/persistence context
        em.close(); // and this closes the entity manager
        try { 
            if( ut != null ) { 
                // There's a tx running, close it.
                ut.commit();
            }
        } catch(Exception e) { 
            logger.error("Unable to commit transaction: ", e);
        }
    }

    protected UserTransaction findUserTransaction() {
    	InitialContext context = null;
    	try {
            context = new InitialContext();
            return (UserTransaction) context.lookup( "java:comp/UserTransaction" );
        } catch ( NamingException ex ) {
        	for (String utLookup : KNOWN_UT_JNDI_KEYS) {
        		if (utLookup != null) {
		        	try {
		        		UserTransaction ut = (UserTransaction) context.lookup(utLookup);
		        		return ut;
					} catch (NamingException e) {
						logger.debug("User Transaction not found in JNDI under {}", utLookup);
						
					}
        		}
        	}
        	logger.warn("No user transaction found under known names");
        	return null;
        }
    }
}
