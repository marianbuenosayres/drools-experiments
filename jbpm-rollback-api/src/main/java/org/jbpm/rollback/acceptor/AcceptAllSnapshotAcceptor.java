package org.jbpm.rollback.acceptor;

public class AcceptAllSnapshotAcceptor implements ProcessSnapshotAcceptor {

	public boolean accept(String processId, long processInstanceId) {
		return true;
	}

}
