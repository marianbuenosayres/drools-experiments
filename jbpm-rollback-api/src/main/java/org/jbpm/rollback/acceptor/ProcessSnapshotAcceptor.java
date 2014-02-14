package org.jbpm.rollback.acceptor;

public interface ProcessSnapshotAcceptor {

	boolean accept(String processId, long processInstanceId);
}
