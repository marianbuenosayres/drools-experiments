package org.jbpm.rollback;

public class ProcessRollbackException extends RuntimeException {

	private static final long serialVersionUID = 8045689452846889990L;

	public ProcessRollbackException() {
		super();
	}

	public ProcessRollbackException(String message, Throwable cause) {
		super(message, cause);
	}

	public ProcessRollbackException(String message) {
		super(message);
	}

	public ProcessRollbackException(Throwable cause) {
		super(cause);
	}
}
